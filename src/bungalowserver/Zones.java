package bungalowserver;

import bungalowserver.Log.Level;
import java.util.Locale;

/**
 * Zones
 */
public class Zones {

    private static final Zone[] ZONES = new Zone[64];

    /**
     * Clears all zones
     */
    public static void clear() {
        for (int i = 0; i < 64; i++) {
            ZONES[i] = null;
        }
    }

    /**
     * Checks if a zone exists
     * @param zone zone number (1-64)
     * @return true if zone exists else false
     */
    public static boolean zoneExists(int zone) {
        if (zone < 1 || zone > 64) {
            return false;
        }
        return ZONES[zone - 1] != null;
    }

    /**
     * Sets the zone name (name from panel)
     * @param zone zone number (1-64)
     * @param name zone name
     * @return true if success else false
     */
    public static boolean setZonePanelName(int zone, String name) {
        if (zoneExists(zone)) {
            if (name.isEmpty() == false) {
                ZONES[zone - 1].panelName = name;
            }
            return true;
        }
        return false;
    }

    /**
     * Sets the zone name (name from settings file)
     * @param zone zone number (1-64)
     * @param name zone name
     * @return true if success else false
     */
    public static boolean setZoneName(int zone, String name) {
        if (zoneExists(zone)) {
            if (name.isEmpty() == false) {
                ZONES[zone - 1].name = name;
            }
            return true;
        }
        return false;
    }

    /**
     * Gets a Zone
     * @param zone zone number (1-64)
     * @return Zone item, or null if zone does not exist
     */
    public static Zone getZone(int zone) {
        if (zoneExists(zone)) {
            return ZONES[zone - 1];
        }
        return null;
    }

    /**
     * Gets zone name
     * @param zone zone number (1-64)
     * @return Returns zone full-name if exists else panel-name
     * Returns empty string if no name or if zone does not exist
     */
    public static String getZoneName(int zone) {
        if (zoneExists(zone)) {
            return (ZONES[zone - 1].name.isEmpty())? ZONES[zone - 1].panelName : ZONES[zone - 1].name;
        }
        return "";
    }

    /**
     * Call this when adding zones defined in the panel
     * If zone does not exist, it will be created and named
     * If zone does exist, the name will be updated if not set
     * @param zone Zone number (1-64)
     * @return true if success, false if fail
     */
    public static boolean addZone(int zone) {
        if (zone < 1 || zone > 64) {
            return false;
        }
        Log.print(Level.INFO, "Adding/updating zone " + Integer.toString(zone), false);

        // Create the zone if it doesn't exist
        if (ZONES[zone - 1] == null) {
            ZONES[zone - 1] = new Zone(zone);
            ZONES[zone - 1].number = zone;
        }

        ZONES[zone - 1].panelName = "Zone " + Integer.toString(zone);
        return true;
    }

    /**
     * Call this when restoring zone info from the settings file
     * Adds/updates a zone (1-64) with type/speakname/speakfault/name
     * If zone does not exist, it will be created
     * @param zone Zone number (1-64)
     * @param type Zone type
     * @param speakName
     * @param speakFault
     * @param name Zone name
     * @return true if success, false if fail
     */
    public static boolean addZone(int zone, int type, int speakName, int speakFault, String name) {
        if (zone < 1 || zone > 64) {
            return false;
        }
        Log.print(Level.INFO, "Adding/updating zone " + Integer.toString(zone), false);

        // Create the zone if it doesn't exist
        if (ZONES[zone - 1] == null) {
            ZONES[zone - 1] = new Zone(zone);
            ZONES[zone - 1].number = zone;
        }

        // Set the zone variables
        ZONES[zone - 1].name = name;
        ZONES[zone - 1].type = type;
        ZONES[zone - 1].speakName = speakName;
        ZONES[zone - 1].speakFault = speakFault;
        return true;
    }

    /**
     * Looks for zones that were defined in the settings file (zone.name not empty)
     * that were not not defined in the panel (zone.panelName empty).
     * In this case, the user needs to remove zones from the settings file.
     * and
     * Looks for zones that were defined in the panel (zone.panelName not empty)
     * that were not defined in the settings file (zone.name empty).
     * In this case, the user needs to add zones to the settings file.
     * @return true on success else false
     */
    public static boolean checkBuiltZones() {
        for (int i = 0; i < 64; i++) {
            if (ZONES[i] == null) {
                continue;
            }
            if (ZONES[i].name.isEmpty() || ZONES[i].panelName.isEmpty()) {
                Log.print(Level.ERROR, "The zones in the settings file do "
                        + "not match the actual zones defined in the panel."
                        + System.lineSeparator()
                        + "Please make changes to the settings file then "
                        + "restart the application."
                        + System.lineSeparator()
                        + "Zone " + i);
                return false;
            }
        }
        return true;
    }

    /**
     * Gets a status message covering all zones
     * For emailing current status of all zones
     * @return String containing readable status of all zones
     */
    public static String getZoneStatusMessage() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 64; i++) {

            Zone zone = ZONES[i];
            if (zone == null) {
                continue;
            }

            sb.append("Zone ").append(i + 1).append(" ");
            sb.append(getZoneName(i+1)).append(" ");

            if (zone.isFaulted) {
                sb.append("is faulted");
            } else {
                sb.append("is okay");
            }

            if (zone.isError) {
                sb.append(" but has an error");
            }

            if (zone.isAlarmMemory) {
                sb.append(" and triggered last alarm");
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Builds a message for all open doors, windows, etc (not motion, glass,
     * etc) This is intended to be called on the hour of half-hour. Time is
     * rounded to the nearest half-hour since the voice currently supports
     * speaking half-hour times.
     * @return Returns array of phrases or an empty array if nothing to say
     */
    public static int[] getUnsecuredZoneSpeak() {

        int[] speak = new int[32];
        int words = 0;

        for (int i = 0; i < 64; i++) {

            Zone zone = ZONES[i];
            if (zone == null) {
                continue;
            }

            if (zone.isFaulted && words < 32) {
                if (zone.type == Const.ZONE_TYPE_DOOR ||
                        zone.type == Const.ZONE_TYPE_WINDOW) {
                    if (words == 0) {
                        speak[words++] = Speaker.GREETING;
                        speak[words++] = 4;
                        speak[words++] = Speaker.TIME;
                        speak[words++] = 6;
                    } else if (words > 4) {
                        speak[words++] = 5;
                    }
                    speak[words++] = zone.speakFault;
                }
            }
        }

        // Create a new array of actual needed size and copy items to it
        int[] spk = new int[words];
        if (words > 0) {
            System.arraycopy(speak, 0, spk, 0, words);
        }
        return spk;
    }

    /**
     * Gets a zone status string to send to the client
     * @return client string
     */
    public static String getStatusForClient() {
        byte zoneStatus;
        StringBuilder sb = new StringBuilder();

        for (Zone zone : ZONES) {

            if (zone == null) {
                continue;
            }

            zoneStatus = 0;

            if (zone.isFaulted) {
                zoneStatus |= 0x01;
            }

            if (zone.isBypassed) {
                zoneStatus |= 0x02;
            }

            if (zone.isForceArmed) {
                zoneStatus |= 0x04;
            }

            if (zone.isAlarmMemory) {
                zoneStatus |= 0x08;
            }

            if (zone.isError) {
                zoneStatus |= 0x10;
            }

            sb.append(Base80.encode(zone.number - 1, zoneStatus));
        }
        return sb.toString();
    }

    /**
     * Gets a zone name string to send to the client
     * @return client string
     */
    public static String getNamesAndTypesForClient() {
        boolean sep = false;
        StringBuilder sb = new StringBuilder();

        for (Zone zone : ZONES) {
            if (zone == null) {
                continue;
            }
            if (sep) {
                sb.append('~');
            } else {
                sep = true;
            }
            sb.append(Base80.encode(zone.number - 1, zone.type));
            sb.append(zone.name);
        }
        return sb.toString();
    }

    /**
     * Accepts a zone type change string from the client
     * @param message message from client
     * @return true if success else false
     */
    public static boolean changeZoneType(String message) {

        // verify and remove header
        if (message == null) {
            message = "";
        }

        if (message.length() != 6 || message.startsWith("ZTC=") == false) {
            Log.print(Level.WARN, "Invalid message in changeZoneType! " + message);
            return false;
        }

        int zone = Base80.decode(message.charAt(4));  // 0-63
        int type = Base80.decode(message.charAt(5));  // 0-9

        if ((zoneExists(zone + 1) == false) || (zone < 0)) {
            Log.print(Level.WARN, "Invalid message in changeZoneType! " + message);
            return false;
        }

        ZONES[zone].type = type;
        return true;
    }

    /**
     * Gets the settings string to save to the settings file
     * @return String of settings to save to file
     */
    public static String getSettingsString() {
        StringBuilder sb = new StringBuilder("ZONE_BEGIN" + System.lineSeparator());
        for (int i = 0; i < 64; i++) {
            Zone zone = ZONES[i];
            if (zone == null) {
                continue;
            }

            sb.append(String.format("  ZONE %1$2d %2$-8s %3$3d %4$3d  ",
                    i+1,
                    Const.ZONE_TYPE_NAMES[zone.type].toUpperCase(Locale.US),
                    zone.speakName,
                    zone.speakFault));
            sb.append(getZoneName(i+1));
            sb.append(System.lineSeparator());
        }
        sb.append("ZONE_END").append(System.lineSeparator());
        return sb.toString();
    }
}
