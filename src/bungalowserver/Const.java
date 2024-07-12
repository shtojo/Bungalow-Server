package bungalowserver;

/**
 * Event constants
 */
public final class Const {

    //<editor-fold defaultstate="collapsed" desc="Events">
    /*
    ZONE_READY/FAULTED      Zone ready/faulted
    ZONE_ERROR/CLEARED      Zone error (tamper/trouble/lost/low-batt) or cleared
    ZONE_FORCE_ARMED        Zone force-armed
    ARMED                   System armed in any mode (stay or away), if armed stay
                            then this msg will be followed by armed-stay. If armed
                            away then this is the only message received.
    ARMED_STAY              Armed stay
    DISARMED                Disarmed
    INSTANT_MODE_ON/OFF     When armed-stay, arm btn toggles instant mode on/off
    FIRE_ALARM              Fire alarm (siren is on)
    BURGLARY_ALARM          Burglary alarm (siren is on)
    ALARM_OFF               Alarm off (siren off due to timer expired or disarmed)
    TIMING_ENTRY            Timing entry (delayed entry zone triggered while armed away)
    SMOKE_RESET             Smoke detectors reset
    SENSOR_LOST             A wireless sensor did not report-in
    SYSTEM_BATTERY_LOW/OK   System battery is low or returned to normal
    AC_POWER_FAIL/RESTORED  Ac power lost/restored (immediate status)
    GLASS_BREAK             Glass break detected
    DOORBELL                Doorbell
    FREEZE                  Freezig temperature detected
    Note: READY_TO_ARM, READY_TO_FORCE_ARM, NOT_READY_TO_ARM  Currently unused
     */

    public static final int
        EVENT_ZONE_READY = 0,
        EVENT_ZONE_FAULTED = 1,
        EVENT_ZONE_ERROR = 2,
        EVENT_ZONE_ERROR_CLEARED = 3,
        EVENT_ZONE_FORCE_ARMED = 4,
        EVENT_ARMED = 5,
        EVENT_ARMED_STAY = 6,
        EVENT_DISARMED = 7,
        EVENT_INSTANT_MODE_ON = 8,
        EVENT_INSTANT_MODE_OFF = 9,
        EVENT_FIRE_ALARM = 10,
        EVENT_BURGLARY_ALARM = 11,
        EVENT_ALARM_OFF = 12,
        EVENT_TIMING_ENTRY = 13,
        EVENT_SMOKE_RESET = 14,
        EVENT_SENSOR_LOST = 15,
        EVENT_SYSTEM_BATTERY_LOW = 16,
        EVENT_SYSTEM_BATTERY_OK = 17,
        EVENT_AC_POWER_FAIL = 18,
        EVENT_AC_POWER_RESTORED = 19,
        EVENT_GLASS_BREAK = 20,
        EVENT_DOORBELL = 21,
        EVENT_FREEZE = 22,
        EVENT_APPLICATION_STARTED = 23;
        //EVENT_CLIENT_CONNECTED
        //EVENT_CLIENT_CONNECT_FAILED
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Security mode constants">
    // First char is one of these
    public static final char
        // First char is one of these
        SEC_UNKNOWN = '0',
        SEC_READY_TO_ARM = '1',
        SEC_NOT_READY = '2',
        SEC_READY_TO_FORCE_ARM = '3',
        SEC_ARMED_STAY = '4',
        SEC_ARMED_AWAY = '5',
        SEC_ARMED_STAY_INSTANT = '6',
        SEC_BURGLARY = '7',
        SEC_FIRE = '8',
        // Second char is one of these
        SEC2_NORMAL = '0',
        SEC2_AC_POWER_OFF = '1',
        SEC2_SYSTEM_BATTERY_LOW = '2',
        SEC2_AC_PWR_OFF_AND_SYS_BAT_LOW = '3';
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Zone types">
    // These must be sequential and start at zero
    public static final int
        ZONE_TYPE_UNKNOWN = 0,
        ZONE_TYPE_DOOR = 1,
        ZONE_TYPE_WINDOW = 2,
        ZONE_TYPE_MOTION = 3,
        ZONE_TYPE_GLASS = 4,
        ZONE_TYPE_FIRE = 5,
        ZONE_TYPE_FREEZE = 6,
        ZONE_TYPE_KEYFOB = 7,
        ZONE_TYPE_DOORBELL = 8,
        ZONE_TYPE_INFO = 9;

    // Zone type names (must match the order above)
    public static final String[] ZONE_TYPE_NAMES = {
        "Unknown",
        "Door",
        "Window",
        "Motion",
        "Glass",
        "Fire",
        "Freeze",
        "Keyfob",
        "Doorbell",
        "Info" };
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Rule constants">

    // When event
    public static final char
        WHEN_ZONE_READY = '0',
        WHEN_ZONE_FAULTED = '1',
        WHEN_ARMED = '2',
        WHEN_ARMED_STAY = '3',
        WHEN_ARMED_AWAY = '4',
        WHEN_DISARMED = '5',
        WHEN_BURGLARY = '6',
        WHEN_FIRE = '7',
        WHEN_DAILY = '8',
        WHEN_WEEKLY = '9',
        WHEN_MONTHLY = 'A';

    // If conditions
    public static final char
        IF_ALWAYS = '0',
        IF_ZONE_READY = '1',
        IF_ZONE_FAULTED = '2',
        IF_ARMED = '3',
        IF_ARMED_STAY = '4',
        IF_ARMED_AWAY = '5',
        IF_DISARMED = '6';

    // Do tasks
    public static final char
        DO_EMAIL = '0',
        DO_SPEAK = '1',
        DO_TURN_ON = '2',
        DO_TURN_OFF = '3';

    // Weekdays
    public static final int
        SUNDAY = 0,
        MONDAY = 1,
        TUESDAY = 2,
        WEDNESDAY = 3,
        THURSDAY = 4,
        FRIDAY = 5,
        SATURDAY = 6;
    //</editor-fold>

}
