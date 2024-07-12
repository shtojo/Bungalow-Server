package bungalowserver;

/**
 * Represents a single rule
 */
public class Rule {

    //<editor-fold defaultstate="collapsed" desc="comment">

    // ----- When event -----
    public char whenEvent;

    // When zone (1-63) or 0 if no zone associated with the rule
    public int whenZone;

    // When hour, 0-23, 0 = midnight, 23 = 11pm
    public int whenHour;

    // When minute (0-59)
    public int whenMinute;

    // When day of week (0-6), 0 = Sunday, 6 = Saturday
    public int whenDayOfWeek;

    // When date (1-31)
    public int whenDate;

    // ----- If condition -----
    public char ifCondition;

    // If zone (1-63) or 0 if no zone associated with the rule
    public int ifZone;

    // ----- Do task -----
    public char doTask;

    // Do speak id or device number (1-64)
    public int doDeviceOrSpeak;

    // Do email address
    public String doEmailAddress;

    // Do email subject
    public String doEmailSubject;

    // Do email body
    public String doEmailBody;
    //</editor-fold>

    public Rule() {
        whenEvent = 0;
        whenZone = 0;
        whenHour = 0;
        whenMinute = 0;
        whenDayOfWeek = 0;
        whenDate = 1;
        ifCondition = 0;
        ifZone = 0;
        doTask = 0;
        doDeviceOrSpeak = 1;
        doEmailAddress = "";
        doEmailSubject = "";
        doEmailBody = "";
    }
}
