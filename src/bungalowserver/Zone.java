package bungalowserver;

/**
 * Represents a single zone
 */
public class Zone {

    //<editor-fold defaultstate="collapsed" desc="Zone variables">

    // Zone number (1-64)
    public int number = 1;

    // Numeric speak-code for this zone
    public int speakName = 0;

    // Numeric speak-code for this zone when faulted
    public int speakFault = 0;

    // Zone type
    public int type = 0;

    // Zone faulted?
    public boolean isFaulted = false;

    // Zone has error?
    public boolean isError = false;

    // Zone is bypassed?
    public boolean isBypassed = false;

    // Zone is force-armed?
    public boolean isForceArmed = false;

    // Was this the zone that triggered the most recent alarm?
    public boolean isAlarmMemory = false;

    // Zone name read from the panel (16 chars max)
    // Note: not currently used, but keep anyway
    public String panelName = "";   // name from panel 16 chars max

    // Zone name read from the settings file (more descriptive, no length limit)
    public String name = "";    // name from settings file

    //</editor-fold>

    /**
     * Constructor
     * @param zoneNumber
     */
    public Zone(int zoneNumber) {
        this.number = zoneNumber;
    }
}
