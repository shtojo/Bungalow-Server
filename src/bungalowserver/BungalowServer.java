/*
 * Author Shawn Johnston
 */
package bungalowserver;

import bungalowserver.Log.Level;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bungalow main application
 */
public class BungalowServer implements VirtualPanel.PanelCallback {

    private static final int PORT = 11000;
    private static final Crypto.Mode ENCRYPTION = Crypto.Mode.AES128;

    // Regarding password and salt values:
    // Do not store these values as String, strings are stored in memory as
    // plain text and can not be cleared. Use char[] instead which can be cleared.
    private static final byte[] SALT = { (byte)0x1d, (byte)0x2e, (byte)0x4f,
        (byte)0x37, (byte)0x8c, (byte)0xc5, (byte)0x3e, (byte)0xf6 };

    // INTERVAL MINUTES defines how often to check for rules to be run
    // UNSECURED ZONE INTERVAL MINUTES defines how often to speak a reminder that
    // there are unsecured zones. This MUST be a multiple of INTERVAL_MINUTES
    private static final int RULE_INTERVAL = 15;  // minutes
    private static final int UNSECURED_ZONE_INTERVAL = 30;  // minutes

    private static String SETTINGS_FILE = "settings.txt";
    private static String OBF_FILE = "obf";
    private static ClientListener clientListener = null;
    private static BungalowServer mainApp = null;

    final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();


    /**
     * Main method
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        String path = System.getProperty("user.dir").replace('\\', '/') + '/';
        SETTINGS_FILE = path + SETTINGS_FILE;
        OBF_FILE = path + OBF_FILE;

        // Get server password from file and deobfuscate to char array
        // If password file does not exist then prompt user and save to file
        // Or if user specified -pwd on the command line then update the file
        boolean prompt = false;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("pw")) {
                prompt = true;
            } else {
            System.out.println("Invalid argument " + args[0]);
            System.exit(1);
            }
        }

        char[] pw;
        if (prompt) {
            pw = null;
        } else {
            pw = Crypto.deobfuscateFromFile(OBF_FILE);
        }
        if (pw == null) {
            pw = Crypto.promptForPassword();
            Crypto.obfuscateToFile(OBF_FILE, pw);
        }

        // Prepare the crypto
        Crypto.generateSecureKey(pw, SALT, ENCRYPTION);
        // Done with password and salt so clear from memory
        Arrays.fill(pw, 'x');
        Arrays.fill(SALT, (byte)0);

        // Restore settings
        Settings.setFile(SETTINGS_FILE);
        if (Settings.restore() == false) {
            return;
        }

        //Speaker.speak(3,5,7);
        //Speaker.speak(28);

        mainApp = new BungalowServer();
        mainApp.begin();

        //Thread mainThread = new Thread(new BungalowServer(), "BungalowServer");
        //mainThread.start();

        /*new Thread("BungalowServer"){
            @Override
            public void run(){
                mainApp.begin();
            }
        }.start();*/
    }

    /**
     * Called from main to start the server functions
     */
    public void begin() {

        VirtualPanel.getInstance().addListener(this);
        if (VirtualPanel.getInstance().start() == false) {
            return;
        }

        //Log.setDebugMode(true);  // debug from this point on

        // Enable event logging (to send to client) only after virtual panel is built and ready
        EventLog.enableEventLogging(true);
        EventLog.logEvent(Const.EVENT_APPLICATION_STARTED);

        // Get pw from file, if doesn't exist then prompt user

        // Start the network server
        clientListener = new ClientListener(PORT);
        clientListener.start();

        runAtInterval(RULE_INTERVAL, UNSECURED_ZONE_INTERVAL);

        //Speaker.speak(10);

        //netServer.stop();
        //panel.stop();
        //LOGGER.LogInfo("Done.");
        ////server.stop();
        //SerialHandler serial = new SerialHandler();
        //serial.close();
    }

    /**
     * Run at regular intervals to check for rules to run and unsecured zones
     * @param ruleMinutes
     * @param zoneMinutes
     */
    private static void runAtInterval(int ruleMinutes, int zoneMinutes) {
        //final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
        final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

        // This fixed delay ends one minute early then sleeps until exactly at the right interval
        // synchronized with the system clock. This fine-tunes the interval on every call to
        // ensure it is always in-sync with the clock. Initial call will sleep for a longer time.
        // This block below runs on a different thread so sleeping is fine.
        exec.scheduleWithFixedDelay(() -> {

            // Compute delay until start of next interval
            LocalTime now = LocalTime.now();
            int delay = (ruleMinutes - now.getMinute() % ruleMinutes - 1) * 60 + (60 - now.getSecond());

            // Sleep to sync with the clock
            try {
                Thread.sleep(delay * 1000);
            } catch (InterruptedException ex) {
                throw new RuntimeException("Exception in thread.sleep!", ex);
            }

            // Run the unsecured zone reminder if in that interval
            // Check for time-triggered events every 15 minutes and unsecured zone
            // reminder every hour
            if (LocalTime.now().getMinute() % zoneMinutes == 0) {
                //int[] say = Zones.getInstance().getUnsecuredZoneSpeak();
                int[] say = Zones.getUnsecuredZoneSpeak();
                if (say.length > 0) {
                    Speaker.speak(say);
                }
            }

            Rules.handleRuleTriggers();

        }, 1, ruleMinutes - 1, TimeUnit.MINUTES);  // initial delay, dealy from end of one to next call, unit
    }

    /**
     * This method is called from the VirtualPanel when an event is being
     * reported. Note: this runs on a different thread than the main
     * application.
     * @param event Event event id
     * @param zone the Zone item associated with this event, or null if no zone
     * associated
     */
    @Override
    public void panelMessageCallback(int event, Zone zone) {

        // Note on sending burglary/fire emails:
        // I should handle the burglary/fire emails after this callback because the
        // zone status message is received immediately after this and that message
        // updates the zone status.  So sending the email now will show the current
        // zone fault states, however it will not show the 'previous alarm' states,
        // instead, it will show the zone that triggered the alarm prior to this alarm.
        // So handle this by running the email send after a short delay
        switch (event) {

            case Const.EVENT_FIRE_ALARM:
                Log.print(Level.INFO, "Event: Fire");
                Speaker.speak(7, 9);
                exec.schedule(() -> {
                    if (Emailer.sendEmail("FIRE") == false) {
                        System.out.println("Error sending email, see the log file for more info.");
                        Speaker.speak(27, 26);
                    }
                }, 4, TimeUnit.SECONDS);
                break;

            case Const.EVENT_BURGLARY_ALARM:
                Log.print(Level.INFO, "Event: Burglary");
                Speaker.speak(7, 8);
                exec.schedule(() -> {
                    if (Emailer.sendEmail("BURGLARY") == false) {
                        System.out.println("Error sending email, see the log file for more info.");
                        Speaker.speak(27, 26);
                    }
                }, 4, TimeUnit.SECONDS);
                break;

            case Const.EVENT_GLASS_BREAK:
                Log.print(Level.INFO, "Event: Glass break, zone " + zone.number + ", " + zone.name);
                Speaker.speak(27, zone.speakFault);
                break;

            case Const.EVENT_DOORBELL:
                Log.print(Level.INFO, "Event: Doorbell");
                Speaker.speak(79);
                break;

            case Const.EVENT_FREEZE:
                Log.print(Level.INFO, "Event: Freeze");
                Speaker.speak(7, 77);
                break;

            case Const.EVENT_ZONE_READY:
                Log.print(Level.INFO, "Event: Zone ready, zone " + zone.number + ", " + zone.name);
                // Do nothing for zone-ready
                break;

            case Const.EVENT_ZONE_FAULTED:
                Log.print(Level.INFO, "Event: Zone fault, zone " + zone.number + ", " + zone.name);
                // if a fire zone then say the zone message for that zone
                //Speaker.speak(zone.speakFault);
                break;

            case Const.EVENT_ZONE_ERROR: // ZoneError = tamper/trouble/lost/lowbatt
                Log.print(Level.INFO, "Event: Zone error, zone " + zone.number + ", " + zone.name);
                Speaker.speak(zone.speakName, 23);
                break;

            case Const.EVENT_ZONE_ERROR_CLEARED:
                Log.print(Level.INFO, "Event: Zone error cleared, zone " + zone.number + ", " + zone.name);
                Speaker.speak(zone.speakName, 24);
                break;

            case Const.EVENT_ZONE_FORCE_ARMED:
                Log.print(Level.INFO, "Event: Zone force-armed, zone " + zone.number + ", " + zone.name);
                Speaker.speak(zone.speakName, 25);
                break;

            case Const.EVENT_ARMED: // Armed in any mode (stay or away), if "away" then this is all that occurs
                Log.print(Level.INFO, "Event: Armed");
                Speaker.speak(21);
                break;

            case Const.EVENT_ARMED_STAY: // Armed in stay mode (when armed stay there are 2 msgs: armed then armed-stay)
                Log.print(Level.INFO, "Event: Armed in stay mode");
                //Speaker.speak(21);
                break;

            case Const.EVENT_DISARMED:
                Log.print(Level.INFO, "Event: Disarmed");
                Speaker.speak(22);
                break;

            case Const.EVENT_TIMING_ENTRY:
                Log.print(Level.INFO, "Event: Timing entry");
                Speaker.speak(7, 20);
                break;

            case Const.EVENT_SENSOR_LOST:
                Log.print(Level.INFO, "Event: Sensor lost");
                // Could speak here "System is reporting a sensor was lost or has low battery"
                break;

            case Const.EVENT_ALARM_OFF:
                Log.print(Level.INFO, "Event: Alarm off");
                Speaker.speak(19);
                break;

            case Const.EVENT_INSTANT_MODE_ON:
                Log.print(Level.INFO, "Event: Instant mode on");
                Speaker.speak(17);
                break;

            case Const.EVENT_INSTANT_MODE_OFF:
                Log.print(Level.INFO, "Event: Instant mode off");
                Speaker.speak(18);
                break;

            case Const.EVENT_AC_POWER_FAIL:
                Log.print(Level.INFO, "Event: Power fail");
                Speaker.speak(7, 12);
                break;

            case Const.EVENT_AC_POWER_RESTORED:
                Log.print(Level.INFO, "Event: Power restored");
                Speaker.speak(13);
                break;

            case Const.EVENT_SMOKE_RESET:
                Log.print(Level.INFO, "Event: Smoke reset");
                Speaker.speak(14);
                break;

            case Const.EVENT_SYSTEM_BATTERY_LOW:
                Log.print(Level.INFO, "Event: System battery low");
                Speaker.speak(7, 16);
                break;

            case Const.EVENT_SYSTEM_BATTERY_OK:
                Log.print(Level.INFO, "Event: System battery okay");
                Speaker.speak(15);
                break;

            default:
                throw new RuntimeException("Unhandled event (" + event + ")!");
        }
        Rules.handleRuleTriggers();
    }
}

// To generate the encryption key for all client/server traffic requires
// a password and salt to be used to generate the encryption key.
// I could prompt the user to enter this password each time the server
// application is staretd, but this won't allow for auto server start
// (like after a power failure or reboot). Therefore, to allow automated
// server start-up, the password must be saved to a local file. This is
// not really a security concern in this application because if someone
// already has file access to the server, then they already have full
// system access. For added security however, the password can be
// encoded in some way then decoded when the application reads it in.
// The stored password can not be hashed since that is one-way (won't
// be able to get the actual password back) and can't be encrypted since
// that requires a password to unencrypt (then I have a new password to
// deal with). Therefore, obfusication is all that can be done (not
// secure, but prevents casual viewing), but again, if someone is
// already on the server then it's a moot point.

// This application is coded to use the password and salt as char/byte
// arrays rather than strings (which are immutable and stored in memory
// for the life of the program). Using arrays allows the values to be
// cleared out once they are used by the application to generate the
// key and the values will no longer be in memory at that point.