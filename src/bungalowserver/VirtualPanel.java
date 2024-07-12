package bungalowserver;

import bungalowserver.Log.Level;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Virtual panel A virtual panel in software that reflects the current state of
 * the actual panel. This is needed because when the panel reports events, it
 * sends back many settings and does not tell me which ones changed. So I need
 * to store all settings and when the panel reports a change, compare the new
 * status to the previous to report exactly what has occurred. Also, I can
 * update only what has changed and I don't need to re-read the entire panel
 * when I need to know the current status.
 */
public class VirtualPanel implements SerialHandler.SerialCallback {

    // Time waiting for arm/disarm to complete (milliseconds)
    private static final int ARM_DISARM_TIMEOUT = 4000;
    private static SerialHandler serialHandler = null;
    private static boolean panelReady;

    //<editor-fold defaultstate="collapsed" desc="System status variables">

    /**
     * Transitions (events reported by the panel over serial)
     */
    public boolean
        reportsInterfaceConfiguration = false,
        reportsZoneStatus = false,
        reportsZoneSnapshot = false,
        reportsPartitionStatus = false,
        reportsPartitionSnapshot = false,
        reportsSystemStatus = false,
        reportsX10Received = false,
        reportsPanelLoggedEvent = false,
        reportsKeypadMessageReceived = false;

    /**
     * System status variables
     */
    public boolean
        isSystemLowBattery = false,
        isSystemSirenOn = false,
        isSystemSteadySirenOn = false,
        isSystemAcPowerOn = true,
        isSystemSmokePowerReset = false,
        isSystemReadyToArm = false,
        isSystemReadyToForceArm = false,
        isSystemArmed = false, // Armed in any mode
        isSystemArmedStay = false, // Stay mode (can be on or off along with Armed)
        isSystemArmedInstant = false, // Instant mode (can be on or off along with Armed)
        isSystemTimingEntry = false,
        isSystemSensorError = false,
        isSystemFireAlarmOn = false,
        isSystemBurglaryAlarmOn = false;
    //isSystemGlobalSirenOn = false,
    //isSystemGlobalSteadySiren = false,
    //isSystemExitErrorTriggered = false,
    //isSystemFireUntilReset = false;  // turns on with fire, off after *7 smoke reset
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Singleton">
    private static VirtualPanel instance = null;

    /**
     * Constructor
     */
    protected VirtualPanel() {
        // Exists only to defeat instantiation.
    }

    /**
     * Gets the instance (synchronized to avoid possible multi-threading issues)
     * @return instance
     */
    public static synchronized VirtualPanel getInstance() {
        if (instance == null) {
            instance = new VirtualPanel();
        }
        return instance;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Callback">

    /**
     * Callback variable
     */
    private PanelCallback callBack = null;

    /**
     * Add listener
     * @param mycallback
     */
    public void addListener(PanelCallback mycallback) {
        callBack = mycallback;
    }

    /**
     * Callback interface
     */
    public interface PanelCallback {

        /**
         * Callback method required to be in the class that uses this callback
         * @param event
         * @param zone
         */
        void panelMessageCallback(int event, Zone zone);
    }
    //</editor-fold>

    /**
     * Starts the virtual panel service
     * @return true on success else false
     */
    public boolean start() {

        // Start the serial handler and subscribe to the
        // SerialCallBack event (will invoke SerialReceiveCallback)
        serialHandler = new SerialHandler();
        serialHandler.addListener(this);
        if (serialHandler.start() == false) {
            return false;
        }

        // Build the virtual panel
        // Communicates to the panel and updates the virtual/software panel to match
        Log.print(Level.INFO, "Building virtual panel...");

        // Check which panel transition messages are enabled in the panel
        // Returns false if any needed transitions are disabled or if any
        // unneeded transitions are enabled.
        if (checkPanelTransitions() == false) {
            serialHandler.close();
            return false;
        }

        // Get the zone status for all zones and if zone is in-use then get zoen name
        Log.print(Level.INFO, "Searching for zones in-use...");
        for (int zone = 1; zone <= 64; zone++) {
            // Request status of zone, callback will add the zone item to the list if valid zone
            // requestZoneStatus will populate the zones (if zone is null after this call then zone is unused)
            requestZoneStatus(zone);  // returns after message received and zone updated
            // If zone is in-use then get the zone name
            if (Zones.zoneExists(zone)) {
                requestZoneName(zone);  // gets the zone name and updates it in the zone list
                Log.print(Level.INFO, "Found zone " + zone + ": "
                        + Zones.getZoneName(zone));
            }
        }

        String ls = System.lineSeparator();
        Log.print(Level.INFO, ls + "All zones loaded from panel: "
                + ls + Zones.getZoneStatusMessage());

        // Now check that all zones defined in the panel are defined
        // in the settings file and vice-versa
        if (Zones.checkBuiltZones() == false) {
            serialHandler.close();
            return false;
        }

        // Get partition status from panel
        if (requestPartitionStatus() == false) {
            serialHandler.close();
            return false;
        }

        // Get system status from panel
        if (requestSystemStatus() == false) {
            serialHandler.close();
            return false;
        }

        // Set panel time and date
        if (setClockCalendar() == false) {
            serialHandler.close();
            return false;
        }

        panelReady = true;
        return true;
    }

    /**
     * Stops the virtual panel service
     */
    public void stop() {
        if (serialHandler != null) {
            serialHandler.close();
        }
    }

    /**
     * Check which panel transition messages are enabled in the panel
     * @return true on success, false on failure
     */
    private boolean checkPanelTransitions() {

        // Check to see what transition messages are enabled in the panel
        Log.print(Level.INFO, "Checking panel transitions...");

        boolean isSuccess = true;

        requestInterfaceConfiguration();

        if (reportsInterfaceConfiguration == false) {
            Log.print(Level.ERROR, "Please enable Interface Configuration transitions in the panel.");
            isSuccess = false;
        }

        if (reportsZoneStatus == false) {
            Log.print(Level.ERROR, "Please enable Zone Status transitions in the panel.");
            isSuccess = false;
        }

        if (reportsPartitionStatus == false) {
            Log.print(Level.ERROR, "Please enable Partition Status transitions in the panel.");
            isSuccess = false;
        }

        if (reportsSystemStatus == false) {
            Log.print(Level.ERROR, "Please enable System Status transitions in the panel.");
            isSuccess = false;
        }

        if (reportsPartitionSnapshot == true) {
            Log.print(Level.ERROR, "Please disable Partition Snapshot transitions in the panel.");
            isSuccess = false;
        }

        if (reportsZoneSnapshot == true) {
            Log.print(Level.ERROR, "Please disable Zone Snapshot transitions in the panel.");
            isSuccess = false;
        }

        if (reportsX10Received == true) {
            Log.print(Level.ERROR, "Please disable X10 transitions in the panel.");
            isSuccess = false;
        }

        if (reportsPanelLoggedEvent == true) {
            Log.print(Level.ERROR, "Please disable Log Event transitions in the panel.");
            isSuccess = false;
        }

        if (reportsKeypadMessageReceived == true) {
            Log.print(Level.ERROR, "Please disable Keypad Message transitions in the panel.");
            isSuccess = false;
        }

        return isSuccess;
    }

    /**
     * Determines if the specified bit is set or clear
     * @param dataByte Data byte (0..)
     * @param dataBit Data bit position (0..7)
     * @return true if bit is set, false if not
     */
    private static boolean isBitSet(int dataByte, int dataBit) {
        return ((dataByte >> dataBit) & 1) != 0;
    }

    /**
     * Handle interface configuration message from panel
     * @param message
     */
    @Override
    public void processInterfaceConfiguration(int[] message) {
        Log.print(Level.INFO, "Received Inteface Configuration message from panel: "
                + Tools.toHexString(message), false);

        // Transitions (events reported by panel over serial)
        reportsInterfaceConfiguration = isBitSet(message[5], 1);
        reportsZoneStatus = isBitSet(message[5], 4);
        reportsZoneSnapshot = isBitSet(message[5], 5);
        reportsPartitionStatus = isBitSet(message[5], 6);
        reportsPartitionSnapshot = isBitSet(message[5], 7);
        reportsSystemStatus = isBitSet(message[6], 0);
        reportsX10Received = isBitSet(message[6], 1);
        reportsPanelLoggedEvent = isBitSet(message[6], 2);
        reportsKeypadMessageReceived = isBitSet(message[6], 3);
    }

    /**
     * Handles zone name messages from the panel. These are normally received
     * only when building the virtual panel.
     * @param message
     */
    @Override
    public void processZoneName(int[] message) {
        int zoneNum = message[1] + 1;
        Log.print(Level.INFO, "Received Zone Name message from panel: " + Tools.toHexString(message), false);

        // Zone will have already been added by processZoneStatus(), so just update the panelName here
        // Ignore zone name messages if virtual panel was already built (should not get these since only on request)
        if (panelReady == false) {
            // Note: if zone name is blank it will be set to "Zone n" in addZone()
            int ch;
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < 18; i++) {
                ch = message[i];
                if (ch < 0x20 || ch > 0x7e) {
                    ch = 0x20;
                }
                sb.append((char) ch);
            }
            Zones.setZonePanelName(zoneNum, sb.toString().trim());
        } // otherwise, if panel was already built and a new zone comes along then error
        else if (Zones.zoneExists(zoneNum) == false) {
            String msg = "Received a zone name message for zone " + zoneNum
                    + " which did not exist when all zones were scanned! "
                    + Tools.toHexString(message);
            Log.print(Level.ERROR, msg);
            throw new RuntimeException(msg);
        }
    }

    /**
     * Handles zone status messages received from the panel. If the message is
     * for a zone that I don't already know about then the zone is created. This
     * will occur for all zones when building the virtual panel. Any created
     * zones use name "Zone n".
     * @param message Data from the panel
     */
    @Override
    public void processZoneStatus(int[] message) {

        int zoneNum = message[1] + 1;
        int PartitionMask = message[2];

        // Validate zone number
        if (zoneNum < 1 || zoneNum > 64) {
            Log.print(Level.ERROR, "Received zone status from panel for invalid zone: "
                    + Tools.toHexString(message), false);
            return;
        }

        // If zone does not belong to any partitions log a message and return
        // This will occur when initially buliding the zone data (when searching all zones
        // to pick out the ones that belong to partition 1) but should not occur after
        if (PartitionMask == 0) {
            Log.print(Level.WARN, "Received zone status for unused zone "
                    + zoneNum + ": " + Tools.toHexString(message), false);
            return;
        }

        // If zone belongs to partitions outside of partition 1 then error
        // Only partition 1 is supported, if zone belongs to other partitions
        // then need to adjust the panel settings
        if (PartitionMask != 1) {
            Log.print(Level.WARN, "Received zone status for zone "
                    + zoneNum + " belonging to other partitions: "
                    + Tools.toHexString(message), false);
        }

        // If panel build has not yet completed then allow zones to be added
        if (panelReady == false) {
            if (Zones.zoneExists(zoneNum) == false) {
                Zones.addZone(zoneNum);
            }
        } // otherwise, if panel was already built and a new zone comes along then error
        else if (Zones.zoneExists(zoneNum) == false) {
            throw new RuntimeException("Received a zone status for zone "
                    + zoneNum
                    + " which did not exist when all zones were scanned! "
                    + Tools.toHexString(message));
        }

        // Get a handle to the current zone (so I dont need to keep referencing as zones[ZoneNum]
        Zone zone = Zones.getZone(zoneNum);

        // I don't currently monitor or update these:
        // Zone.isTypeFireZone = bits.Get(B4+0);
        // Zone.isTypeTwentyFourHour = bits.Get(B4+1);
        // Zone.isTypeEntryExitDelay = (bits.Get(B4+4) || bits.Get(B4+5));  // delay 1 or 2
        // Zone.isTypeInterior = bits.Get(B4+6);
        // Zone.isTypeEntryGuard = bits.Get(B4+7);
        Log.print(Level.INFO, "Received zone status from panel: "
                + Tools.toHexString(message), false);
        String preMsg = "Zone " + zoneNum + " " + Zones.getZoneName(zoneNum) + ", ";

        // Now detect status bit changes

        // ZONE FAULTED
        boolean bit;
        bit = isBitSet(message[6], 0);
        if (bit != zone.isFaulted) { // if changed state (became faulted or became ready)
            zone.isFaulted = bit;  // update the saved bit state
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) { // if faulted
                    // handle glass-break
                    switch (zone.type) {
                        case Const.ZONE_TYPE_GLASS:
                            Log.print(Level.INFO, preMsg + "glass-break detected");
                            EventLog.logEvent(Const.EVENT_GLASS_BREAK, zoneNum);
                            reportEvent(Const.EVENT_GLASS_BREAK, zoneNum);
                            break;
                        case Const.ZONE_TYPE_DOORBELL:
                            Log.print(Level.INFO, preMsg + "doorbell rang");
                            EventLog.logEvent(Const.EVENT_DOORBELL, zoneNum);
                            reportEvent(Const.EVENT_DOORBELL, zoneNum);
                            break;
                        case Const.ZONE_TYPE_FREEZE:
                            Log.print(Level.INFO, preMsg + "freeze warning");
                            EventLog.logEvent(Const.EVENT_FREEZE, zoneNum);
                            reportEvent(Const.EVENT_FREEZE, zoneNum);
                            break;
                        default:
                            // Report open/fault events for all (doors,
                            // windows, fire/smoke, glass-break, motion, etc)
                            Log.print(Level.INFO, preMsg + "faulted");
                            EventLog.logEvent(Const.EVENT_ZONE_FAULTED, zoneNum);
                            reportEvent(Const.EVENT_ZONE_FAULTED, zoneNum);
                            break;
                    }
                } else {

                    // Don't log ready/closed/restored messages for sensors that
                    // change back quickly, like motion sensors and glass-breaks.
                    //
                    // Log these:
                    //      ZONE_TYPE_DOOR, ZONE_TYPE_WINDOW,
                    //      ZONE_TYPE_FIRE, ZONE_TYPE_FREEZE
                    // Not these:
                    //      ZONE_TYPE_MOTION, ZONE_TYPE_GLASS,
                    //      ZONE_TYPE_KEYFOB, ZONE_TYPE_DOORBELL,
                    //      ZONE_TYPE_INFO, ZONE_TYPE_UNKNOWN

                    if (zone.type == Const.ZONE_TYPE_DOOR
                        || zone.type == Const.ZONE_TYPE_WINDOW
                        || zone.type == Const.ZONE_TYPE_FIRE
                        || zone.type == Const.ZONE_TYPE_FREEZE) {

                        Log.print(Level.INFO, preMsg + "ready");
                        EventLog.logEvent(Const.EVENT_ZONE_READY, zoneNum);
                        reportEvent(Const.EVENT_ZONE_READY, zoneNum);
                    }
                }
            }
        }

        // ZONE TAMPER/TROUBLE/LOWBATT/LOST
        bit = isBitSet(message[6], 1) || isBitSet(message[6], 2)
                || isBitSet(message[6], 5) || isBitSet(message[6], 6);
        if (bit != zone.isError) {
            zone.isError = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    Log.print(Level.INFO, preMsg + "error (tamper/trouble/lowbatt/lost)");
                    EventLog.logEvent(Const.EVENT_ZONE_ERROR, zoneNum);
                    reportEvent(Const.EVENT_ZONE_ERROR, zoneNum);
                } else {
                    Log.print(Level.INFO, preMsg + "error cleared");
                    EventLog.logEvent(Const.EVENT_ZONE_ERROR_CLEARED, zoneNum);
                }
            }
        }

        // ZONE FORCE-ARMED
        bit = isBitSet(message[6], 4);
        if (bit != zone.isForceArmed) {
            zone.isForceArmed = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    Log.print(Level.INFO, preMsg + "force-armed");
                    EventLog.logEvent(Const.EVENT_ZONE_FORCE_ARMED, zoneNum);
                }
            }
        }

        // ZONE ALARM MEMORY
        zone.isAlarmMemory = isBitSet(message[7], 0);
    }

    /**
     * Handle partition status message from panel
     * @param message
     */
    @Override
    public void processPartitionStatus(int[] message) {
        boolean bit;

        int partition = message[1] + 1;

        if (partition != 1) // first partition
        {
            // Warn and continue
            Log.print(Level.WARN, "Ignoring partition status for partition "
                    + partition + ": " + Tools.toHexString(message), false);
            return;
        }

        Log.print(Level.INFO, "Received partition status: " + Tools.toHexString(message), false);

        // From testing it appears that the 'reserved' bit at byte-6/bit-3
        // actually indicates if arming in stay vs away mode, can use it to
        // determine if arming in stay mode vs away mode. SilentExit bit
        // appears to do the same so could use that instead. Otherwise, the
        // actual armed-stay bit is not set when the armed bit is set
        // but is actually set a few responses later.
        //bool armedBit = bits.Get(B3+6);      // Armed (true if armed in any mode, stay or exit, instant or delayed)
        //bool instantBit = bits.Get(B3+7);    // Instant (true if armed in instant mode)
        //bool silenExitBit = bits.Get(B5+1);  // This bit is on if armed in stay mode
        //bool armedStayBit = bits.Get(B5+2);  // ArmedStay (true if armed in stay mode)
        //bool reservedBit = bits.Get(B6+3);   // Reserverd bit, seems to indicate if arming in away mode
        // ARM STAY: reports: [Armed/SilentExit/ReserverdBit] ... [Stay]
        // 84-02-01-58-13-F0-08-00
        // 84-03-01-58-13-F0-08-00
        // 86-00-40-00-CA-48-62-84-80   40=Armed, CA=SilentExit, 48=ReservedBit
        // 86-00-40-00-CA-48-62-84-A0
        // 86-00-40-00-CA-48-62-04-A0   04=BeepEnd
        // 86-00-40-00-CA-48-62-04-A0
        // 86-00-40-00-CE-48-62-04-A0   CE=EntryGuard/StayMode
        // ARM AWAY: reports: [Armed]
        // 86-00-40-00-C8-40-62-84-80   40=Armed
        // 86-00-40-00-C8-40-62-84-80
        // 86-00-40-00-C8-40-62-04-80   04=BeepEnd
        // 86-00-40-00-C8-40-62-04-80
        // ARMED (armed in any mode (stay or away))
        bit = isBitSet(message[2], 6);
        if (bit != isSystemArmed) {
            isSystemArmed = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (isSystemArmed) {
                    Log.print(Level.INFO, "System is armed");
                    EventLog.logEvent(Const.EVENT_ARMED);
                    reportEvent(Const.EVENT_ARMED);
                } else {
                    Log.print(Level.INFO, "System is disarmed");
                    EventLog.logEvent(Const.EVENT_DISARMED);
                    reportEvent(Const.EVENT_DISARMED);
                }
            }
        }

        // STAY MODE
        bit = isBitSet(message[4], 2);
        if (bit != isSystemArmedStay) {
            isSystemArmedStay = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (isSystemArmedStay) {
                    Log.print(Level.INFO, "System is armed in stay mode");
                    EventLog.logEvent(Const.EVENT_ARMED_STAY);
                    reportEvent(Const.EVENT_ARMED_STAY);
                }
            }
        }

        // INSTANT (instant mode toggled)
        bit = isBitSet(message[2], 7);
        if (bit != isSystemArmedInstant) {
            isSystemArmedInstant = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    Log.print(Level.INFO, "System is armed in instant mode");
                    EventLog.logEvent(Const.EVENT_INSTANT_MODE_ON);
                    reportEvent(Const.EVENT_INSTANT_MODE_ON);
                } else {
                    Log.print(Level.INFO, "System is armed in normal (delayed-entry) mode");
                    EventLog.logEvent(Const.EVENT_INSTANT_MODE_OFF);
                    reportEvent(Const.EVENT_INSTANT_MODE_OFF);
                }
            }
        }

        // ---------------------------------------------------
        // Could use the system global vars to do this or use the partition vars here
        // Create events FireSiren, BurglarySiren and SirenOff
        // from bits SirenOn/Off and SteadySirenOn/Off
        // Avoids sending multiple messages when an event occurs
        // (ie, Fire event sends all three: SirenOn, SteadySirenOn, PulsingBuzzer)
        // So change this so it only sends the relevant message (SteadySirenOn)
        boolean sirenBit = isBitSet(message[3], 1);         // SirenOn
        boolean steadySirenBit = isBitSet(message[3], 2);   // SteadySirenOn

        if (steadySirenBit && !isSystemSteadySirenOn) // steady siren turned on
        {
            isSystemFireAlarmOn = true;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                Log.print(Level.INFO, "FIRE ALARM!");
                EventLog.logEvent(Const.EVENT_FIRE_ALARM);
                reportEvent(Const.EVENT_FIRE_ALARM);
            }
        } else if (sirenBit && !isSystemSirenOn) // burglary siren turned on
        {
            isSystemBurglaryAlarmOn = true;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                Log.print(Level.INFO, "BURGLARY ALARM!");
                EventLog.logEvent(Const.EVENT_BURGLARY_ALARM);
                reportEvent(Const.EVENT_BURGLARY_ALARM);
            }
        } else if (!sirenBit && isSystemSirenOn) // siren turned off
        {
            isSystemFireAlarmOn = false;
            isSystemBurglaryAlarmOn = false;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                Log.print(Level.INFO, "Siren is off");
                EventLog.logEvent(Const.EVENT_ALARM_OFF);
                reportEvent(Const.EVENT_ALARM_OFF);
            }
        }

        isSystemSirenOn = sirenBit;
        isSystemSteadySirenOn = steadySirenBit;
        isSystemReadyToArm = isBitSet(message[7], 2);

        // ENTRY
        bit = isBitSet(message[4], 4);
        if (bit != isSystemTimingEntry) {
            isSystemTimingEntry = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    Log.print(Level.INFO, "System is timing entry");
                    EventLog.logEvent(Const.EVENT_TIMING_ENTRY);
                    reportEvent(Const.EVENT_TIMING_ENTRY);
                }
            }
        }

        // SENSOR BATT LOW or SENSOR LOST
        bit = isBitSet(message[5], 6) || isBitSet(message[5], 7);
        if (bit != isSystemSensorError) {
            isSystemSensorError = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    Log.print(Level.INFO, "System detected sensor trouble!");
                    EventLog.logEvent(Const.EVENT_SENSOR_LOST);
                    reportEvent(Const.EVENT_SENSOR_LOST);
                }
            }
        }

        // READY TO FORCE-ARM
        bit = isBitSet(message[7], 3);
        if (bit != isSystemReadyToForceArm) {
            isSystemReadyToForceArm = bit;
            // Perform action (unless just started and still setting inital states)
            //if (panelReady) {
            //if (bit) {
            //    EventLog.logEvent(Const.EVENT_READY_TO_FORCE_ARM);
            //    ReportEvent(Const.EVENT_READY_TO_FORCE_ARM);
            //}}
        }
    }

    /**
     * Handle system status message from panel
     * @param message
     */
    @Override
    public void processSystemStatus(int[] message) {
        Log.print(Level.INFO, "Received System Status from panel: "
                + Tools.toHexString(message), false);
        boolean bit;

        // SYSTEM LOW BATTERY
        bit = isBitSet(message[3], 6);
        if (bit != isSystemLowBattery) {
            isSystemLowBattery = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    EventLog.logEvent(Const.EVENT_SYSTEM_BATTERY_LOW);
                    reportEvent(Const.EVENT_SYSTEM_BATTERY_LOW);
                    Log.print(Level.INFO, "System battery is low!");
                } else {
                    EventLog.logEvent(Const.EVENT_SYSTEM_BATTERY_OK);
                    Log.print(Level.INFO, "System battery is ok");
                }
            }
        }

        // SYSTEM AC POWER ON
        bit = isBitSet(message[6], 1); // this indicates immediate current ac power status
        if (bit != isSystemAcPowerOn) {
            isSystemAcPowerOn = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    EventLog.logEvent(Const.EVENT_AC_POWER_RESTORED);
                    reportEvent(Const.EVENT_AC_POWER_RESTORED);
                    Log.print(Level.INFO, "Ac power is on");
                } else {
                    EventLog.logEvent(Const.EVENT_AC_POWER_FAIL);
                    reportEvent(Const.EVENT_AC_POWER_FAIL);
                    Log.print(Level.INFO, "Ac power is off!");
                }
            }
        }

        // SYSTEM SMOKE POWER RESET
        bit = isBitSet(message[6], 5);
        if (bit != isSystemSmokePowerReset) {
            isSystemSmokePowerReset = bit;
            // Perform action (unless just started and still setting inital states)
            if (panelReady) {
                if (bit) {
                    EventLog.logEvent(Const.EVENT_SMOKE_RESET);
                    reportEvent(Const.EVENT_SMOKE_RESET);
                    Log.print(Level.INFO, "Smoke detector reset");
                }
            }
        }
    }

    /**
     * Report the event back to the main thread so it can act on it if desired
     * @param event
     */
    private void reportEvent(int event) {
        reportEvent(event, 0);
    }

    /**
     * Report the event back to the main thread so it can act on it if desired
     * @param event
     * @param zone the Zone, or null if event is not associated with a zone
     */
    private void reportEvent(int event, int zoneNumber) {

        if (callBack == null) {
            Log.print(Level.ERROR, "No callback assigned for panel events!");
            return;
        }

        Zone zone = null;
        if (zoneNumber != 0) {
            zone = Zones.getZone(zoneNumber);
        }

        // Invoke the callback with the event information
        callBack.panelMessageCallback(event, zone);
    }

    /**
     * Gets the security status string to send to the client
     * @return status string to send to client
     */
    public String getSecurityStatusForClient() {
        StringBuilder sb = new StringBuilder(2);
        try {
            if (isSystemBurglaryAlarmOn) {
                sb.append(Const.SEC_BURGLARY);
            } else if (isSystemFireAlarmOn) {
                sb.append(Const.SEC_FIRE);
            } else if (isSystemArmedInstant) {
                sb.append(Const.SEC_ARMED_STAY_INSTANT);
            } else if (isSystemArmedStay) {
                sb.append(Const.SEC_ARMED_STAY);
            } else if (isSystemArmed) {
                sb.append(Const.SEC_ARMED_AWAY);
            } else if (isSystemReadyToForceArm) {
                sb.append(Const.SEC_READY_TO_FORCE_ARM);
            } else if (isSystemReadyToArm) {
                sb.append(Const.SEC_READY_TO_ARM);
            } else {
                sb.append(Const.SEC_NOT_READY);
            }

            if (!isSystemAcPowerOn && isSystemLowBattery) {
                sb.append(Const.SEC2_AC_PWR_OFF_AND_SYS_BAT_LOW);
            } else if (!isSystemAcPowerOn) {
                sb.append(Const.SEC2_AC_POWER_OFF);
            } else if (isSystemLowBattery) {
                sb.append(Const.SEC2_SYSTEM_BATTERY_LOW);
            } else {
                sb.append(Const.SEC2_NORMAL);
            }
        } catch (Exception ex) {
            Log.print(Level.ERROR, "Error getting security status! " + ex.getMessage());
            throw new RuntimeException("Error getting security status!", ex);
        }
        return sb.toString();
    }

    /**
     * Check if string is numeric
     * @param str string
     * @return true if string represents an numeric value
     */
    public static boolean isNumeric(String str) {
        try {
            Long.parseLong(str);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    //<editor-fold defaultstate="collapsed" desc="Panel Commands">
    // --------------------- Panel Commands - Begin ----------------------------
    // Command notes:
    // Many of these functions like the quick arm one button arming feature must
    // be programmed in the panel to allow the function, for example, if the
    // quick arm is not enabled in the panel, it will not function via serial.
    /**
     * Requests interface configuration from the panel
     * @return true = success, false = failure
     */
    public boolean requestInterfaceConfiguration() {
        Log.print(Level.INFO, "Requesting interface configuration...", false);
        return serialHandler.sendMessageRaw(0x7E, 0x01, 0x21, 0x22, 0x23);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x21);
    }

    /**
     * Request partition status from the panel
     * @return true = success, false = failure
     */
    public boolean requestPartitionStatus() {
        Log.print(Level.INFO, "Requesting partition status...", false);
        return serialHandler.sendMessageRaw(0x7E, 0x02, 0x26, 0x00, 0x28, 0x52);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x26, 0x00);
    }

    /**
     * Request partition snapshot from the panel
     * @return true = success, false = failure
     */
    public boolean requestPartitionSnapshot() {
        Log.print(Level.INFO, "Requesting partition snapshot...", false);
        return serialHandler.sendMessageRaw(0x7E, 0x02, 0x27, 0x00, 0x29, 0x54);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x27, 0x00);
    }

    /**
     * Request system status from the panel
     * @return true = success, false = failure
     */
    public boolean requestSystemStatus() {
        Log.print(Level.INFO, "Requesting system status...", false);
        return serialHandler.sendMessageRaw(0x7E, 0x01, 0x28, 0x29, 0x2A);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x28);
    }

    /**
     * Request zone name from the panel
     * @param zone Zone number (1..64)
     * @return true = success, false = failure
     */
    public boolean requestZoneName(int zone) {
        Log.print(Level.INFO, "Requesting zone " + zone + " name...", false);
        if (zone < 1) {
            throw new RuntimeException("Invalid zone in requestZoneName!");
        }
        return serialHandler.sendMessage(0x23, zone - 1);
    }

    /**
     * Request zone status from the panel
     * @param zone Zone number (1..64)
     * @return true = success, false = failure
     */
    public boolean requestZoneStatus(int zone) {
        Log.print(Level.INFO, "Requesting zone " + zone + " status...", false);
        if (zone < 1 || zone > 64) {
            throw new RuntimeException("Invalid zone (" + zone + ") in requestZoneStatus!");
        }
        return serialHandler.sendMessage(0x24, zone - 1);
    }

    /**
     * Request zone snapshot from the panel
     * @param zone Zone number (1..64)
     * @return true = success, false = failure
     */
    public boolean requestZoneSnapshot(int zone) {
        Log.print(Level.INFO, "Requesting zone " + zone + "snapshot...", false);
        if (zone < 1) {
            throw new RuntimeException("Invalid zone in requestZoneSnapshot!");
        }
        return serialHandler.sendMessage(0x25, zone - 1);
    }

    /**
     * Send X10 message
     * @param house
     * @param unit
     * @param command
     * @return true = success, false = failure
     */
    public boolean sendX10Message(byte house, byte unit, byte command) {
        Log.print(Level.INFO, "Sending X10 message, house " + house + ", unit "
                + unit + ", command " + command);
        return serialHandler.sendMessage(0x29, house, unit, command);
    }

    /**
     * Get a single log event by event log index from the panel
     * @param eventNumber Event log index
     * @return true = success, false = failure
     */
    public boolean requestlogEvent(int eventNumber) {
        Log.print(Level.INFO, "Requesting log event " + eventNumber + "...", false);
        return serialHandler.sendMessage(0x2a, eventNumber);
    }

    /**
     * Set the clock/calendar on the panel using the computers date/time
     * @return true = success, false = failure
     */
    public boolean setClockCalendar() {
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeStr = time.format(formatter); // "1986-04-08 12:30"

        Log.print(Level.INFO, "Setting panel date & time to " + timeStr + "...");

        // Tranlate java year to 2 digits
        int year = time.getYear();
        if (year > 2000) {
            year -= 2000;
        }
        if (year > 99) {
            year = year % 100;
        }

        // Translate java 1=monday to panel 1=sunday
        int dayofweek = time.getDayOfWeek().getValue() + 1;
        if (dayofweek > 7) {
            dayofweek = 1;
        }

        return serialHandler.sendMessage(
                0x3b,
                year, // year 0-99
                time.getMonthValue(), // month 1-12
                time.getDayOfMonth(), // day 1-31
                time.getHour(), // hour 0-23
                time.getMinute(), // minute 0-59
                dayofweek); // weekday (1=Sunday)
    }

    /**
     * Alarm off
     * @return true = success, false = failure
     */
    public boolean alarmOff() {
        // Uses panel primary command 0
        Log.print(Level.INFO, "Turning alarm off...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3D, 0x00, 0x01, 0x41, 0xC4);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3D, 0x00, 0x01);
    }

    /**
     * Alarm off with pin
     * @param pin
     * @return true = success, false = failure
     */
    public boolean alarmOff(String pin) {
        // Uses panel primary command 0
        Log.print(Level.INFO, "Turning alarm off with pin...");

        int[] pinb = pinToByes(pin);
        if (pinb == null) {
            return false;
        }
        return serialHandler.sendMessage(
                0x3c, pinb[0], pinb[1], pinb[2], 0x00, 0x01);
    }

    //<editor-fold defaultstate="collapsed" desc="Disarm">
    /**
     * Disarm with pin (panel primary command 1)
     * @param pin 4 or 6 digit pin
     * @param wait true = wait for panel to report the armed state change or
     * timeout (blocking)
     * @return true = success, false = failure
     */
    public boolean disarm(String pin, boolean wait) {
        // if not armed then just return
        if (isSystemArmed == false) {
            return true;
        }

        Log.print(Level.INFO, "Disarming...");

        // note: command to disarm without pin (if allowed by panel)
        // serialHandler.sendMessageRaw(0x7E, 0x03, 0x3D, 0x01, 0x01, 0x42, 0xC6);
        // or serialHandler.SendCommand(0x3D, 0x01, 0x01);

        int[] pinb = pinToByes(pin);
        if (pinb == null) {
            return false;
        }
        boolean success = serialHandler.sendMessage(
                0x3c, pinb[0], pinb[1], pinb[2], 0x01, 0x01);

        if (success && wait) {
            // Wait for panel to update status
            long startTime = System.currentTimeMillis();
            while (isSystemArmed) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    success = false;
                    break;
                }
                if (System.currentTimeMillis() - startTime > ARM_DISARM_TIMEOUT) {
                    success = false;
                    break;
                }
            }
        }
        return success;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Arm Away">
    /**
     * Arm away (panel primary command 2)
     * @param pin 4 or 6 digit pin or null if arming without pin
     * @param wait true = wait for panel to report the armed state change or
     * timeout (blocking)
     * @return true = success, false = failure
     */
    public boolean armAway(String pin, boolean wait) {
        // if already armed away then return
        if (isSystemArmed && (isSystemArmedStay == false)) {
            return true;
        }

        Log.print(Level.INFO, "Arming in away mode...");

        boolean success = false;
        if (pin == null) {
            success = serialHandler.sendMessageRaw(
                    0x7E, 0x03, 0x3D, 0x02, 0x01, 0x43, 0xC8);
            // or serialHandler.SendCommand(0x3D, 0x02, 0x01);
        } else if (isNumeric(pin)) {
            int[] pinb = pinToByes(pin);
            if (pinb == null) {
                return false;
            }
            success = serialHandler.sendMessage(
                    0x3c, pinb[0], pinb[1], pinb[2], 0x02, 0x01);
        }

        if (success && wait) {
            // Wait for panel to update status
            long startTime = System.currentTimeMillis();
            while (isSystemArmed == false) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    success = false;
                    break;
                }
                if (System.currentTimeMillis() - startTime > ARM_DISARM_TIMEOUT) {
                    success = false;
                    break;
                }
            }
        }
        return success;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Arm Stay">
    /**
     * Arm stay (panel primary command 3)
     * @param pin 4 or 6 digit pin or null if arming without pin
     * @param wait true = wait for panel to report the armed state change or
     * timeout (blocking)
     * @return true = success, false = failure
     */
    public boolean armStay(String pin, boolean wait) {
        // Save old states
        boolean wasStay = isSystemArmedStay;
        boolean wasInstant = isSystemArmedInstant;
        Log.print(Level.INFO, "Arming in stay mode...");
        boolean success = false;

        if (pin == null) {
            success = serialHandler.sendMessageRaw(
                    0x7E, 0x03, 0x3D, 0x03, 0x01, 0x44, 0xCA);
            // or serialHandler.SendCommand(0x3D, 0x03, 0x01);
        } else if (isNumeric(pin)) {
            int[] pinb = pinToByes(pin);
            if (pinb == null) {
                return false;
            }
            success = serialHandler.sendMessage(
                    0x3c, pinb[0], pinb[1], pinb[2], 0x03, 0x01);
        }

        if (success && wait) {

            System.out.println("Waiting for panel to update...");

            // Wait for panel to update status
            long startTime = System.currentTimeMillis();

            while (true) {
                if (isSystemArmedStay) {

                    // done if changed from not-armed to armed-stay
                    if (wasStay == false) {
                        System.out.println("Arm success");
                        break;
                    }

                    // done if was armed-stay already and instant mode toggled
                    if (isSystemArmedInstant != wasInstant) {
                        System.out.println("Arm toggle success");
                        break;
                    }
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    success = false;
                    break;
                }

                if (System.currentTimeMillis() - startTime > ARM_DISARM_TIMEOUT) {
                    System.out.println("Timed-out waiting for arm-stay!");
                    success = false;
                    break;
                }
            }
        }

        // for debug
        if (success) System.out.println("Returning success");
        else System.out.println("Returning fail");

        return success;
    }
    //</editor-fold>

    /**
     * Auto arm
     * @return true = success, false = failure
     */
    public boolean autoArm() {
        // Uses panel primary command 5
        Log.print(Level.INFO, "Auto arming...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3D, 0x05, 0x01, 0x46, 0xCE);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3D, 0x05, 0x01);
    }

    /**
     * Auto arm with pin
     * @param pin
     * @return true = success, false = failure
     */
    public boolean autoArm(String pin) {
        // Uses panel primary command 5
        Log.print(Level.INFO, "Auto arming with pin...");
        int[] pinb = pinToByes(pin);
        if (pinb == null) {
            return false;
        }
        return serialHandler.sendMessage(
                0x3c, pinb[0], pinb[1], pinb[2], 0x05, 0x01);
    }

    /**
     * Arm stay button toggle (I think this will change toggle instant vs
     * delayed mode) but sending ArmStay again does the same thing, so probably
     * don't need this
     * @return true = success, false = failure
     */
    public boolean armStayToggle() {
        // Uses panel secondary command 0
        Log.print(Level.INFO, "Arm away button toggle...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x00, 0x01, 0x42, 0xC7);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x00, 0x01);
    }

    /**
     * Arm away button toggle (I think this will change toggle instant vs
     * delayed mode) but sending ArmAway again does the same thing, so probably
     * don't need this
     * @return true = success, false = failure
     */
    public boolean armAwayToggle() {
        // Uses panel secondary command 2
        Log.print(Level.INFO, "Arm away button toggle...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x02, 0x01, 0x44, 0xCB);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x02, 0x01);
    }

    /**
     * Bypass interiors
     * @return true = success, false = failure
     */
    public boolean bypassInteriors() {
        // Uses panel secondary command 3
        Log.print(Level.INFO, "Bypassing interiors...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x03, 0x01, 0x45, 0xCD);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x03, 0x01);
    }

    /**
     * Panic fire
     * @return true = success, false = failure
     */
    public boolean panicFire() {
        // Uses panel secondary command 4
        Log.print(Level.INFO, "Panic fire...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x04, 0x01, 0x46, 0xCF);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x04, 0x01);
    }

    /**
     * Panic medical
     * @return true = success, false = failure
     */
    public boolean panicMedical() {
        // Uses panel secondary command 5
        Log.print(Level.INFO, "Panic medical...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x05, 0x01, 0x47, 0xD1);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x05, 0x01);
    }

    /**
     * Panic police
     * @param wait Wait for panel state change?
     * @return true = success, false = failure
     */
    public boolean panicPolice(boolean wait) {
        // Uses panel secondary command 6
        Log.print(Level.INFO, "Panic police...");
        boolean success = serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x06, 0x01, 0x48, 0xD3);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x06, 0x01);

        if (success && wait) {
            // Wait for panel to update status
            long startTime = System.currentTimeMillis();
            while (isSystemBurglaryAlarmOn == false) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    success = false;
                    break;
                }
                if (System.currentTimeMillis() - startTime > ARM_DISARM_TIMEOUT) {
                    success = false;
                    break;
                }
            }
        }
        return success;
    }

    /**
     * Smoke reset (same as *7 on keypad)
     * @return true = success, false = failure
     */
    public boolean smokeReset() {
        // Uses panel secondary command 7
        Log.print(Level.INFO, "Smoke reset...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x07, 0x01, 0x49, 0xD5);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x07, 0x01);
    }

    /**
     * Test
     * @return true = success, false = failure
     */
    public boolean test() {
        // Uses panel secondary command 11 (0x0b)
        Log.print(Level.INFO, "Test...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x0B, 0x01, 0x4D, 0xDD);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x0B, 0x01);
    }

    /**
     * Bypass group
     * @return true = success, false = failure
     */
    public boolean bypassGroup() {
        // Uses panel secondary command 12 (0x0c)
        Log.print(Level.INFO, "Bypass group...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x0C, 0x01, 0x4E, 0xDF);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x0C, 0x01);
    }

    /**
     * Aux function 1
     * @return true = success, false = failure
     */
    public boolean auxFunction1() {
        // Uses panel secondary command 13 (0x0d)
        Log.print(Level.INFO, "Aux function 1...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x0D, 0x01, 0x4F, 0xE1);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x0D, 0x01);
    }

    /**
     * Aux function 2
     * @return true = success, false = failure
     */
    public boolean auxFunction2() {
        // Uses panel secondary command 14 (0x0e)
        Log.print(Level.INFO, "Aux function 2...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x0E, 0x01, 0x50, 0xE3);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x0E, 0x01);
    }

    /**
     * Start keypad sounder
     * @return true = success, false = failure
     */
    public boolean startKeypadSounder() {
        // Uses panel secondary command 15 (0x0f)
        Log.print(Level.INFO, "Starting keypad sounder...");
        return serialHandler.sendMessageRaw(0x7E, 0x03, 0x3E, 0x0F, 0x01, 0x51, 0xE5);
        // Above cmd is more efficient but equivalent to: serialHandler.SendCommand(0x3E, 0x0F, 0x01);
    }

    /**
     * Displays a string up to 32 character in length on the LCD The string is
     * displayed left-justified (you may left-pad with spaces if needed)
     * @param keypadAddress
     * @param message
     * @param seconds
     * @return
     */
    public boolean sendKeypadText(int keypadAddress, String message, int seconds) {
        // Only one keypad should be in terminal mode at-a-time
        // Send the message and panel stores it, then can put in terminal mode to display it
        // Or could send message while already in terminal mode
        // Panel only accepts 8 characters in each message, so this routine sends multiple messages
        boolean result = false;

        int[] cmd = new int[12];
        cmd[0] = 0x2b;
        cmd[1] = keypadAddress;
        cmd[2] = 0x00;

        Log.print(Level.INFO, "Sending keypad text: " + message + "...");
        message = String.format("%1$-" + 32 + "s", message);  // right pad to 32 chars

        for (int i = 0; i < 31; i += 8) {
            cmd[3] = i;
            for (int b = 0; b < 8; b++) {
                cmd[b + 4] = message.charAt(b);
            }
            result = serialHandler.sendMessage(cmd);
            if (!result) {
                break;
            }
        }

        // If success then send terminal mode request to show message
        if (result) {
            result = keypadTerminalModeRequest(keypadAddress, seconds);
        }

        return result;
    }

    /**
     * Put keypad into terminal mode
     * @param keypadAddress
     * @param seconds
     * @return true = success, false = failure
     */
    public boolean keypadTerminalModeRequest(int keypadAddress, int seconds) {
        // note, only one keypad should be in terminal mode at-a-time
        Log.print(Level.INFO, "Keypad terminal mode request, keypad "
                + keypadAddress + ", seconds " + seconds + "...");
        return serialHandler.sendMessage(0x2c, keypadAddress, seconds);
    }

    /**
     * Bypass zone toggle
     * @param zone Zone number (1-64)
     * @return true = success, false = failure
     */
    public boolean bypassZoneToggle(int zone) {
        Log.print(Level.INFO, "Bypass toggle, zone " + zone + "...");
        return serialHandler.sendMessage(0x3f, zone - 1);
    }

    /**
     * Validates numeric pin string and returns the 3 merged bytes
     * as expected by the panel for commands that use a pin
     * @param pin pin string
     * @return byte[3] or null if error
     */
    private static int[] pinToByes(String pin) {

        int[] pinbytes = new int[3];

        // Validate pin
        if (((pin.length() != 4) && (pin.length() != 6))
            || (isNumeric(pin) == false)) {
            return null;
        }

        pinbytes[0] = ((pin.charAt(0) - '0') | ((pin.charAt(1) - '0') << 4));
        pinbytes[1] = ((pin.charAt(2) - '0') | ((pin.charAt(3) - '0') << 4));
        pinbytes[2] = 0;

        if (pin.length() == 6) {
            pinbytes[2] = ((pin.charAt(4) - '0') | ((pin.charAt(5) - '0') << 4));
        }

        return pinbytes;
    }

    // Panel Commands - End
    //</editor-fold>
}
