package bungalowserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Settings
 */
public class Settings {

    private static String filePath = null;

    private enum InBlock {
        SPEAKER, EMAIL, RULE, SERIAL, ZONE, NONE
    }

    /**
     * Must call this first to set the file
     * @param path
     */
    public static void setFile(String path) {
        // Get directory and translate slashes if necessary
        // or:  new java.io.File("").getAbsolutePath();
        filePath = path;
    }

    /**
     * Saves settings to file
     * @return true on success else false (see log for more info)
     */
    public static boolean save() {
        String ls = System.lineSeparator();
        Log.print(Log.Level.INFO, "Saving settings...");

        StringBuilder sb = new StringBuilder(getSettingsMessage());
        sb.append(SerialHandler.getSettingsString()).append(ls);
        sb.append(Emailer.getSettingsString()).append(ls);
        sb.append(Speaker.getSettingsString()).append(ls);
        sb.append(Zones.getSettingsString()).append(ls);
        sb.append(Rules.getSettingsString());

        /* Another option:
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
            new FileOutputStream(filename, true), StandardCharsets.UTF_8))) {
            pw.println(someContent);
        } */

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(filePath, false), StandardCharsets.UTF_8))) {
            writer.write(sb.toString());
        } catch (IOException ex) {
            Log.print(Log.Level.ERROR, "Error saving settings to file! " + ex.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Restore settings from file
     * @return true if success else false (see log for more info)
     */
    public static boolean restore() {
        Log.print(Log.Level.INFO, "Restoring settings...");
        String line;
        int lineNum = 0;

        // If file does not exist then create it with defaults and stop
        File file = new File(filePath);
        if(!file.exists() || file.isDirectory()) {
            save();
            Log.print(Log.Level.INFO, "Default settings created, please update " +
                    "the SERIAL, SPEAKER and EMAIL sections then restart the application.");
            return false;
        }

        boolean foundSerialBlock = false;
        boolean foundEmailBlock = false;
        boolean foundSpeakerBlock = false;
        boolean foundRuleBlock = false;
        boolean foundZoneBlock = false;
        InBlock inBlock = InBlock.NONE;

        // Call this once at start, sets mixer volume, does not affect slider volume
        // This might not be needed
        Speaker.setMixerVolumes(100);

        Zones.clear();
        Rules.clear();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();

                // Ignore empty lines and commented lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Ignore all lines unless inside EMAIL_BEGIN/END block
                switch (line) {
                    case "SERIAL_BEGIN":
                        inBlock = InBlock.SERIAL;
                        foundSerialBlock = true;
                        continue;
                    case "SPEAKER_BEGIN":
                        inBlock = InBlock.SPEAKER;
                        foundSpeakerBlock = true;
                        continue;
                    case "EMAIL_BEGIN":
                        inBlock = InBlock.EMAIL;
                        foundEmailBlock = true;
                        continue;
                    case "ZONE_BEGIN":
                        inBlock = InBlock.ZONE;
                        foundZoneBlock = true;
                        continue;
                    case "RULE_BEGIN":
                        inBlock = InBlock.RULE;
                        foundRuleBlock = true;
                        continue;
                    case "SPEAKER_END":
                    case "EMAIL_END":
                    case "RULE_END":
                    case "SERIAL_END":
                    case "ZONE_END":
                        inBlock = InBlock.NONE;
                }

                if (inBlock == InBlock.NONE) {
                    continue;
                }

                boolean success = false;
                switch (inBlock) {
                    case SERIAL:
                        success = handleSerialLine(line);
                        break;
                    case EMAIL:
                        success = handleEmailLine(line);
                        break;
                    case SPEAKER:
                        success = handleSpeakerLine(line);
                        break;
                    case ZONE:
                        success = handlerZoneLine(line);
                        break;
                    case RULE:
                        success = handleRuleLine(line);
                        break;
                    default:
                        break;
                }
                if (!success) {
                    Log.print(Log.Level.ERROR, "Error in settings file in "
                            + inBlock + " block, line " + lineNum + "!");
                    return false;
                }
            }

            if (Emailer.server.isEmpty() || Emailer.username.isEmpty() ||
                    Emailer.password.isEmpty() || Emailer.contacts.isEmpty()) {
                Log.print(Log.Level.ERROR, "Settings file is missing email settings!");
                return false;
            }

        } catch (IOException ex) { // catches error reading file and file does not exist
            // If here then file does not exist, so just continue on and default file will be created.
            Log.print(Log.Level.ERROR, "Error accessing settings file! " + ex.getMessage());
            return false;
        }

        // If file is missing any sections then stop
        if (foundEmailBlock && foundRuleBlock && foundSerialBlock && foundSpeakerBlock && foundZoneBlock) {
            return true;
        }

        Log.print(Log.Level.INFO, "Settings file is missing sections, please fix and restart.");
        return false;
    }

    /**
     * Gets the settings header to write to the settings file
     * @return
     */
    private static String getSettingsMessage() {
        String ls = System.lineSeparator();

        String message =
            "##### SETTINGS FILE #####" + ls +
            "# You may make changes to this file to be picked up the next time the application is started." + ls +
            "#" + ls +
            "##### SERIAL #####" + ls +
            "# PORT <port name>" + ls +
            "# BAUD <baud>  (valid bauds = 9600, 19200, 38400, 57600, 115200)" + ls +
            "#" + ls +
            "##### EMAIL #####" + ls +
            "# CONTACT <email address> (can use multiple EMAIL_CONTACT lines with one email per line)" + ls +
            "# USERNAME <from address>" + ls +
            "# SERVER <server address>" + ls +
            "# SECURITY <TLS or SSL or NONE>" + ls +
            "# PASSWORD <password>" + ls +
            "#" + ls +
            "##### SPEAKER #####" + ls +
            "# VOLUME <volume>  (where <volume> is in percent (0-100)" + ls +
            "#" + ls +
            "##### ZONE #####" + ls +
            "# ZONE zone type speak name" + ls +
            "#    where <zone> is the zone number" + ls +
            "#    and <type> is one of the following:" + ls +
            "#       UNKNOWN DOOR WINDOW MOTION GLASS FIRE FREEZE DOORBELL KEYFOB INFO" + ls +
            "#    and <faulted> <ready> are the IDs of the text to speak for this zone open/closed" + ls +
            "#    and <name> is the zone name (not limited to 16 chars like panel zone name" + ls +
            "#    Example: ZONE 1 DOOR 11 12 garage double door" + ls +
            "#" + ls +
            "##### RULE #####" + ls +
            "# RULE <when> <if> <do>" + ls +
            "#   where <when> is one of these:" + ls +
            "#     WHEN_ZONE_READY <zone number>, WHEN_ZONE_FAULTED <zone number>" + ls +
            "#     WHEN_ARMED, WHEN_ARMED_STAY, WHEN_ARMED_AWAY, WHEN_DISARMED, WHEN_BURGLARY, WHEN_FIRE" + ls +
            "#     WHEN_DAILY <hour> <minute>" + ls +
            "#     WHEN_WEEKLY <SUN|MON|TUE|WED|THU|FRI> <hour> <min>" + ls +
            "#     WHEN_MONTHLY <1-31> <hour> <min>" + ls +
            "#   and <if> is one of these:" + ls +
            "#     THEN, AND_IF_ARMED, AND_IF_ARMED_STAY, AND_IF_ARMED_AWAY, AND_IF_DISARMED" + ls +
            "#     AND_IF_ZONE_READY <zone number>, AND_IF_ZONE_FAULTED <zone number>" + ls +
            "#   and <do> is one of these:" + ls +
            "#     EMAIL <email address> \"<subject>\" \"<message>\"" + ls +
            "#     SPEAK \"<message>\"" + ls +
            "#     TURN_ON <device number>" + ls +
            "#     TURN_OFF <device number>" + ls + ls;

        return message;
    }

    /**
     * Handles a single line in the SPEAKER block
     * @param line
     * @return true on success else false (caller will display the failing line)
     */
    private static boolean handleSpeakerLine(String line) {

        // Split string on whitespace
        String[] tokens = line.split("\\s+", 3);

        if (tokens.length != 2) {
            return false;
        }

        if (tokens[0].equalsIgnoreCase("VOLUME") == false) {
            return false;
        }

        int vol = Integer.parseInt(tokens[1]);
        if ((vol < 0) || (vol > 100)) {
            return false;
        }
        Speaker.setVolume(vol);
        return true;
    }

    /**
     * Handles a single line in the ZONE block
     * @param line
     * @return true on success else false (caller will display the failing line)
     */
    private static boolean handlerZoneLine(String line) {

        // Split string on whitespace
        String[] tokens = line.split("\\s+", 6);

        if (tokens.length != 6) {
            return false;
        }

        // Zone keyword
        if (tokens[0].equalsIgnoreCase("ZONE") == false) {
            return false;
        }

        // Zone number
        int zoneNum;
        try {
            zoneNum = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException ex) {
            zoneNum = 0;  // will cause fail below
        }
        if ((zoneNum < 1) || (zoneNum > 64)) {
            return false;
        }

        // Zone type name
        int zoneType = -1;
        int pos = 0;
        for (String ztn : Const.ZONE_TYPE_NAMES) {
            if (tokens[2].equalsIgnoreCase(ztn)) {
                zoneType = pos;
                break;
            }
            pos++;
        }
        if (zoneType == -1) {
            Log.print(Log.Level.ERROR, "Invalid zone type!");
            return false;
        }

        // Speak number open/closed
        int zoneSpeakName = Integer.parseInt(tokens[3]);
        int zoneSpeakFault = Integer.parseInt(tokens[4]);

        // Note: Full zone name is in tokens[5]
        Zones.addZone(zoneNum, zoneType, zoneSpeakName, zoneSpeakFault, tokens[5]);
        return true;
    }

    /**
     * Handles a single line in the EMAIL block
     * @param line
     * @return true on success else false (caller will display the failing line)
     */
    private static boolean handleEmailLine(String line) {

        // Split string on whitespace
        String[] tokens = line.split("\\s+", 3);

        if (tokens.length != 2) {
            return false;
        }

        switch (tokens[0].toUpperCase(Locale.US)) {
            case "CONTACT":
                if (Emailer.contacts.isEmpty() == false) {
                    Emailer.contacts += ",";
                }
                Emailer.contacts += tokens[1];
                break;
            case "USERNAME":
                Emailer.username = tokens[1];
                break;
            case "SERVER":
                Emailer.server = tokens[1];
                break;
            case "SECURITY":
                switch (tokens[1]) {
                    case "TLS":
                        Emailer.security = Emailer.Security.TLS;
                        break;
                    case "SSL":
                        Emailer.security = Emailer.Security.SSL;
                        break;
                    case "NONE":
                        Emailer.security = Emailer.Security.NONE;
                        break;
                    default:
                        return false;
                }
                break;
            case "PASSWORD":
                Emailer.password = tokens[1];
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Handles a single line in the SERIAL block
     * @param line
     * @return true on success else false (caller will display the failing line)
     */
    private static boolean handleSerialLine(String line) {

        // Split string on whitespace
        String[] tokens = line.split("\\s+", 3);

        if (tokens.length != 2) {
            return false;
        }

        switch (tokens[0].toUpperCase(Locale.US)) {
            case "PORT":
                SerialHandler.serialPortName = tokens[1];
                break;
            case "BAUD":
                SerialHandler.serialBaudRate = Integer.parseInt(tokens[1]);
                if (SerialHandler.serialBaudRate != 9600 &&
                        SerialHandler.serialBaudRate != 19200 &&
                        SerialHandler.serialBaudRate != 38400 &&
                        SerialHandler.serialBaudRate != 57600 &&
                        SerialHandler.serialBaudRate != 115200) {
                    Log.print(Log.Level.ERROR, "Invalid baud rate!");
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Handles a single line in the RULE block
     * @param line
     * @return true on success else false (caller will display the failing line)
     */
    private static boolean handleRuleLine(String line) {

        // Split string on whitespace
        String[] tokens = line.split("\\s+");

        Rule rule = new Rule();
        int idx = 0;

        try {
            // Process WHEN
            switch (tokens[idx++].toUpperCase(Locale.US)) {
                case "WHEN_ZONE_READY":
                    rule.whenEvent = Const.WHEN_ZONE_READY;
                    break;
                case "WHEN_ZONE_FAULTED":
                    rule.whenEvent = Const.WHEN_ZONE_FAULTED;
                    break;
                case "WHEN_ARMED":
                    rule.whenEvent = Const.WHEN_ARMED;
                    break;
                case "WHEN_ARMED_STAY":
                    rule.whenEvent = Const.WHEN_ARMED_STAY;
                    break;
                case "WHEN_ARMED_AWAY":
                    rule.whenEvent = Const.WHEN_ARMED_AWAY;
                    break;
                case "WHEN_DISARMED":
                    rule.whenEvent = Const.WHEN_DISARMED;
                    break;
                case "WHEN_BURGLARY":
                    rule.whenEvent = Const.WHEN_BURGLARY;
                    break;
                case "WHEN_FIRE":
                    rule.whenEvent = Const.WHEN_FIRE;
                    break;
                case "WHEN_DAILY":
                    rule.whenEvent = Const.WHEN_DAILY;
                    break;
                case "WHEN_WEEKLY":
                    rule.whenEvent = Const.WHEN_WEEKLY;
                    switch (tokens[idx++].toUpperCase(Locale.US)) {
                        case "SUNDAY":
                            rule.whenDayOfWeek = Const.SUNDAY;
                            break;
                        case "MONDAY":
                            rule.whenDayOfWeek = Const.MONDAY;
                            break;
                        case "TUESDAY":
                            rule.whenDayOfWeek = Const.TUESDAY;
                            break;
                        case "WEDNESDAY":
                            rule.whenDayOfWeek = Const.WEDNESDAY;
                            break;
                        case "THURSDAY":
                            rule.whenDayOfWeek = Const.THURSDAY;
                            break;
                        case "FRIDAY":
                            rule.whenDayOfWeek = Const.FRIDAY;
                            break;
                        case "SATURDAY":
                            rule.whenDayOfWeek = Const.SATURDAY;
                            break;
                        default:
                            return false;
                    }
                    break;
                case "WHEN_MONTHLY":
                    rule.whenEvent = Const.WHEN_MONTHLY;
                    rule.whenDate = Integer.parseInt(tokens[idx++]);
                    if ((rule.whenDate < 0) || (rule.whenDate > 31)) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }

            // Get zone number
            if ((rule.whenEvent == Const.WHEN_ZONE_READY)
                    || (rule.whenEvent == Const.WHEN_ZONE_FAULTED)) {
                rule.whenZone = Integer.parseInt(tokens[idx++]);
                if (rule.whenZone < 1 || rule.whenZone > 64) {
                    return false;
                }
            }

            // Get time (hh mm)
            if (rule.whenEvent == Const.WHEN_DAILY
                    || rule.whenEvent == Const.WHEN_WEEKLY
                    || rule.whenEvent == Const.WHEN_MONTHLY) {
                rule.whenHour = Integer.parseInt(tokens[idx++]);
                rule.whenMinute = Integer.parseInt(tokens[idx++]);
                if ((rule.whenHour < 0) || (rule.whenHour > 23)) {
                    return false;
                }
            }

            // Process IF
            switch (tokens[idx++].toUpperCase(Locale.US)) {
                case "THEN":
                    rule.ifCondition = Const.IF_ALWAYS;
                    break;
                case "AND_IF_ZONE_READY":
                    rule.ifCondition = Const.IF_ZONE_READY;
                    rule.ifZone = Integer.parseInt(tokens[idx++]); // get zone number
                    break;
                case "AND_IF_ZONE_FAULTED":
                    rule.ifCondition = Const.IF_ZONE_FAULTED;
                    rule.ifZone = Integer.parseInt(tokens[idx++]); // get zone number
                    break;
                case "AND_IF_ARMED":
                    rule.ifCondition = Const.IF_ARMED;
                    break;
                case "AND_IF_ARMED_STAY":
                    rule.ifCondition = Const.IF_ARMED_STAY;
                    break;
                case "AND_IF_ARMED_AWAY":
                    rule.ifCondition = Const.IF_ARMED_AWAY;
                    break;
                case "AND_IF_DISARMED":
                    rule.ifCondition = Const.IF_DISARMED;
                    break;
                default:
                    return false;
            }

            // Process DO
            int strStart;
            int strEnd;
            switch (tokens[idx++].toUpperCase(Locale.US)) {
                case "SPEAK":
                    rule.doTask = Const.DO_SPEAK;
                    rule.doDeviceOrSpeak = Integer.parseInt(tokens[idx++]);
                    break;
                case "EMAIL":
                    rule.doTask = Const.DO_EMAIL;
                    rule.doEmailAddress = tokens[idx++];
                    strStart = line.indexOf('"') + 1;
                    strEnd = line.indexOf('"', strStart);
                    rule.doEmailSubject = line.substring(strStart, strEnd).trim();
                    strStart = line.indexOf('"', strEnd + 1);
                    strEnd = line.indexOf('"', strStart);
                    rule.doEmailBody = line.substring(strStart, strEnd).trim();
                    break;
                case "TURN_ON":
                    rule.doTask = Const.DO_TURN_ON;
                    rule.doDeviceOrSpeak = Integer.parseInt(tokens[idx++]);
                    break;
                case "TURN_OFF":
                    rule.doTask = Const.DO_TURN_OFF;
                    rule.doDeviceOrSpeak = Integer.parseInt(tokens[idx++]);
                    break;
                default:
                    return false;
            }
        } catch (IndexOutOfBoundsException ex) {
            return false;
        }

        Rules.addRule(rule);
        return true;
    }
}
