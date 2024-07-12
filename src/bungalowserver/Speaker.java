package bungalowserver;

import bungalowserver.Log.Level;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import static javax.sound.sampled.AudioSystem.getAudioInputStream;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/*  SPEAK PHRASE KEY:
 *   0 = none (reserved)
 *   1 = good morning
 *   2 = good afternoon
 *   3 = good evening
 *   4 = it is
 *   5 = and your
 *   6 = and I would just like to remind you that your
 *   7 = warning
 *   8 = burglary
 *   9 = fire
 *  10 = system is on-line
 *  11 = system error
 *  12 = power has gone out
 *  13 = power has been restored
 *  14 = smoke detectors have been reset
 *  15 = system battery is okay
 *  16 = system battery is low
 *  17 = instant mode on
 *  18 = instant mode off
 *  19 = siren is off
 *  20 = alarm countdown has begun
 *  21 = system is armed
 *  22 = system is disarmed
 *  23 = is experiencing sensor trouble
 *  24 = is now working
 *  26 = I am having trouble sending email
 *  27 = alert
 *  28 = initializing system
 *  30 = alert, a remote connected attempt has failed
 *  31 = a remote client has connected
 *  32 = the alarm was activated remotely
 *  33 = system is restarting
 *  40 = front door                                 41 = front door is open
 *  42 = mud room door                              43 = mud room door is open
 *  44 = garage double door                         45 = garage double door is opn
 *  46 = garage single door                         47 = garage single door is open
 *  48 = garage side door                           49 = garage side door is open
 *  50 = deck door                                  51 = deck door is open
 *  52 = downstairs patio door                      53 = downstairs patio door is open
 *  54 = foyer motion                               55 = motion detected in foyer
 *  56 = garage motion                              57 = motion detected in garage
 *  58 = downstairs family room motion              59 = motion detected in downstairs family room
 *  60 = kitchen glass break sensor                 61 = glass break detected in kitchen
 *  62 = downstairs family room glass break sensor  63 = glass break detected in downstairs family room
 *  64 = downstairs bedroom glass break sensor      65 = glass break detected in downstairs bedroom
 *  66 = main floor office glass break sensor       67 = glass break detected in main floor office
 *  68 = downstairs smoke sensor                    69 = smoke detected downstairs
 *  70 = furnace room heat sensor                   71 = excessive heat detected in furnace room
 *  72 = garage heat sensor                         73 = excessive heat detected in garage
 *  74 = upstairs smoke sensor                      75 = smoke detected upstairs
 *  76 = freeze sensor                              77 = freeze alert
 *  78 = doorbell                                   79 = someone is at the door
 *  80 = keyfob                                     81 = keyfob button pressed
 *  100 = 12 AM    112 = 12 PM    124 = 12:30 AM    136 = 12:30 PM
 *  101 = 1 AM     113 = 1 PM     125 = 1:30 AM     137 = 1:30 PM
 *  102 = 2 AM     114 = 2 PM     126 = 2:30 AM     138 = 2:30 PM
 *  103 = 3 AM     115 = 3 PM     127 = 3:30 AM     139 = 3:30 PM
 *  104 = 4 AM     116 = 4 PM     128 = 4:30 AM     140 = 4:30 PM
 *  105 = 5 AM     117 = 5 PM     129 = 5:30 AM     141 = 5:30 PM
 *  106 = 6 AM     118 = 6 PM     130 = 6:30 AM     142 = 6:30 PM
 *  107 = 7 AM     119 = 7 PM     131 = 7:30 AM     143 = 7:30 PM
 *  108 = 8 AM     120 = 8 PM     132 = 8:30 AM     144 = 8:30 PM
 *  109 = 9 AM     121 = 9 PM     133 = 9:30 AM     145 = 9:30 PM
 *  110 = 10 AM    122 = 10 PM    134 = 10:30 AM    146 = 10:30 PM
 *  111 = 11 AM    123 = 11 PM    135 = 11:30 AM    147 = 11:30 PM
 *  198 = speaks the appropriate "good morning/afternoon/evening"
 *  199 = speaks the current time (gets translated to 100-147)
 *
 *  These are messages that the client can request to be spoken
 *  200 = Alert, please check your phone (spoken twice)
 *  201 = Alert, please call father (spoken twice)
 *  202 = Alert, please call mother (spoken twice)
 */

/**
 * Speaker
 * @author Shawn Johnston
 */
public class Speaker {

    // These values are translated to the appropriate phrases
    public static final int GREETING = 198, TIME = 199;
    private static final BlockingQueue<AudioInputStream> STREAM_QUEUE = new ArrayBlockingQueue<>(256);
    private static Thread thread;
    private static boolean isSpeaking = false;
    private static int volumePercent = 50;
    private static final byte[] WAV_BUFFER = new byte[4096];

    // Use System.getProperty or new java.io.File("").getAbsolutePath();
    private static final String WORD_PATH =
            System.getProperty("user.dir") + '/' + "speak" + '/';

    /**
     * Gets a string containing all phrases that can be requested to be spoken
     * by the client.
     * @return
     */
    public static String getPhrasesForClient() {
        StringBuilder sb = new StringBuilder();
        sb.append("Please check your phone").append('~');
        sb.append("Please call your father").append('~');
        sb.append("Please call your mother");
        return sb.toString();
    }

    /**
     * Client is requesting to speak a phrase from the client phrase list
     * @param message
     * @return
     */
    public static boolean speakClientPhrase(String message) {
        // format: SAY=vt, where v = volume(base80 0-20), t = phrase id (base80, 0-79)
        // add 200 since those phrases start at 200

        // verify header
        if (message == null) {
            message = "";
        }
        if ((message.length() != 6) || (message.startsWith("SAY=") == false)) {
            Log.print(Level.WARN, "Client sent invalid message: " + message);
            return false;
        }

        int volume, phrase;
        try {
            volume = Base80.decode(message.charAt(4));
            phrase = Base80.decode(message.charAt(5));
        } catch (NumberFormatException ex) {
            Log.print(Level.WARN, "Client sent invalid message: " + message);
            return false;
        }

        // Convert client level 1-10 to serveer level 0-100
        // Adjust volume to compensate for actual sound level output
        volume *= 10;
        switch (volume) {
            case 10: volume += 5;
            case 20: volume += 5;
            case 30: volume += 5;
        }

        if (volume > 0 && volume <= 100) {
            setVolume(volume, true);
            if (speak(phrase + 200)) {
                return true;
            }
        }

        // if here then error so set volume back to default
        setVolume(volumePercent);
        return false;
    }

    /**
     * Changes the volume
     * If temporary, will go back to default when done speaking
     * To change the default, modify the settings file.
     * @param percent volume percent
     * @param temporary
     */
    public static void setVolume(int percent, boolean temporary) {
        if (percent >= 0 && percent <= 100) {
            if (!temporary) {
                volumePercent = percent;
            }
            runShell("amixer -M set PCM playback " + percent + "%", true);
        }
    }

    public static void setVolume(int percent) {
        setVolume(percent, false);
    }

    /**
     * Runs a shell command Ex: To set system volume control on rpi: "amixer -M
     * set PCM playback 50%" In Windows: "cmd /C command" In Linux: "command" or
     * "sh -c command"
     * @param command
     * @param getOutput If true gets and returns command output (blocks), if
     * false then returns immediately
     * @return Command output or null
     */
    public static String runShell(String command, boolean getOutput) {

        String os = System.getProperty("os.name");

        if ("Windows".equals(os)) {
            command = "cmd /C " + command;
        }

        Process pr;
        try {
            pr = Runtime.getRuntime().exec(command);
        } catch (IOException ex) {
            return null;  // unable to run command
        }

        if (getOutput) {

            try {
                pr.waitFor();  // wait for shell command to complete
            } catch (InterruptedException ex) {
                return null;  // command interrupted
            }

            String line;
            StringBuilder sb = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    pr.getInputStream(), StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            } catch (IOException ex) {
                return null;  // io error
            }

            return sb.toString();
        }

        return null;
    }

    /**
     * Set the volume for all mixers supporting volume (speaker, microphone,
     * etc) This does not affect the system master volume control, it can only
     * reduce the volume To adjust the actual system volume control, use the
     * command line as follows: Command Line: amixer -M set PCM playback 50%
     * @param volume Volume (0-10)
     * @return true on success
     */
    public static boolean setMixerVolumes(int volume) {
        if (volume < 0 || volume > 100) {
            return false;
        }
        boolean success = false;
        javax.sound.sampled.Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] lineinfos = mixer.getTargetLineInfo();
            for (Line.Info lineinfo : lineinfos) {
                try {
                    Line line = mixer.getLine(lineinfo);
                    line.open();
                    if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                        FloatControl control = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                        //System.out.println("Volume " + round(control.getValue() * 100)
                        //  + " > " + volume + " : " + mixerInfo.getName());
                        control.setValue((float) volume / 100);  // 0 = 0%, 0.5 = 50%, 1.0 = 100%
                        success = true;
                    }
                } catch (LineUnavailableException e) {
                }
            }
        }
        return success;
    }

    /**
     * Gets the words value representing the current time.
     * @return The word value representing the current time.
     */
    private static int getTimeWord() {

        LocalTime time = LocalTime.now();
        int hour = time.getHour();
        int minute = time.getMinute();

        // Round minute to nearest half-hour
        if (minute < 15) {
            minute = 0;
        } else if (minute < 45) {
            minute = 30;
        } else {
            minute = 0;
            hour++;
            if (hour > 23) {
                hour = 0;
            }
        }

        // Hour
        int timeWord = hour + 100;
        if (minute == 30) {
            timeWord += 24;
        }

        return timeWord;
    }

    /**
     * Returns the appropriate "good morning/afternoon/evening"
     * @return True on success, false on fail
     */
    private static int getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return 1;
        }
        if (hour < 17) {
            return 2;
        }
        return 3;
    }

    /**
     * Accepts numeric comma-delimited string
     * @param words
     * @return
     */
    public static boolean speak(String words) {
        String[] wordStrings = words.split(words);
        int[] wordInts = new int[wordStrings.length];

        for (int i = 0; i < wordStrings.length; i++) {
            try {
                wordInts[i] = Integer.parseInt(wordStrings[i]);
            } catch (NumberFormatException ex) {
                Log.print(Level.WARN, "Error in speak, non-numeric found!");
                return false;
            }
        }
        return speak(wordInts);
    }

    /**
     * Speak the text items identified by the integer array This routine is
     * non-blocking, it queues up the words then returns while the speaking is
     * done on another thread.
     * @param words
     * @return true on success
     */
    public static boolean speak(int... words) {

        if (words.length < 1) {
            Log.print(Level.WARN, "No words to speak!");
            return false;
        }

        for (int word : words) {
            // Translate 98 to current greeting
            if (word == GREETING) {
                word = getGreeting();
            }

            // Translate 99 to the actual current time.
            if (word == TIME) {
                word = getTimeWord();
            }

            String filepath = WORD_PATH + String.format("%03d", word) + ".wav";
            System.out.println("Speaking " + filepath);

            boolean success;
            try {
                // add audio stream to queue
                System.out.println("Added to speak queue");
                success = STREAM_QUEUE.offer(getAudioInputStream(new File(filepath)));
            } catch (IOException | UnsupportedAudioFileException ex) {
                Log.print(Level.ERROR, "File " + filepath + ", " + ex.getMessage());
                return false;
            }
            if (!success) {
                Log.print(Level.WARN, "Unable to speak, queue is full!", true);
            }
        }

        if (isSpeaking == false) {
            isSpeaking = true;
            System.out.println("Starting speak thread");
            thread = new Thread(new SpeakThread(), "SpeakThread");
            thread.start();
        }
        return true;
    }

    /**
     * Gets the settings string to save to the settings file
     * @return String of settings to save to file
     */
    public static String getSettingsString() {
        String ls = System.lineSeparator();
        return "SPEAKER_BEGIN" + ls + "  VOLUME " +
                Integer.toString(volumePercent) + ls + "SPEAKER_END" + ls;
    }

    /**
     * This thread runs until the streamQueue is empty (nothing left to say)
     */
    public static class SpeakThread implements Runnable {

        /**
         * Speak thread
         */
        @Override
        public void run() {
            AudioInputStream stream = (AudioInputStream) STREAM_QUEUE.peek();
            AudioFormat format = stream.getFormat();
            SourceDataLine line;
            try {
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();
            } catch (LineUnavailableException ex) {
                Log.print(Level.ERROR, ex.getMessage());
                setVolume(volumePercent);  // set volume back to default
                return;
            }

            while (STREAM_QUEUE.isEmpty() == false) {
                try {
                    // "take" is blocking, dont need while empty, etc
                    stream = (AudioInputStream) STREAM_QUEUE.take();
                    System.out.println("Speaking item from queue");
                    while (stream.available() > 0) {
                        line.write(WAV_BUFFER, 0, stream.read(WAV_BUFFER));
                    }
                } catch (IOException | InterruptedException ex) {
                    Log.print(Level.ERROR, "Error processing audio streams! " + ex.toString());
                }
            }
            line.drain(); // wait for the buffer to empty before closing the line
            line.close();
            isSpeaking = false;
            setVolume(volumePercent);  // set volume back to default
        }
    }
}
