package bungalowserver;

import java.util.LinkedList;

/**
 * The event log that is sent to the client upon request. This log is stored in
 * memory (on application restart, the log will be empty)
 * @author Shawn Johnston
 */
public class EventLog {

    private static boolean eventLoggingEnabled = false;
    private static int maxEvents = 100;
    private static int repeat = 0;
    private static String prevEv = "";
    private static final LinkedList<String> EVENT_LOG = new LinkedList<>();

    /**
     * Enables or disables logging
     * @param enable
     */
    public static void enableEventLogging(boolean enable) {
        eventLoggingEnabled = enable;
    }

    /**
     * Sets the maximum number of events to hold in the log
     * @param eventMax
     */
    public static void setMaxEvents(int eventMax) {
        maxEvents = eventMax;
    }

    /**
     * Gets the entire event logs as a string to send to the client
     * @return entire event log as string
     */
    public static String getLogForClient() {
        StringBuilder sb = new StringBuilder();
        for (String str : EVENT_LOG) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Log an event not associated with a zone
     * @param event
     */
    public static void logEvent(int event) {
        logEvent(event, 0);
    }

    /**
     * Log and event associated with a zone
     * @param event Event
     * @param zone Zone number 1.. (ignored for events not related to a zone)
     */
    public static void logEvent(int event, int zone) {
        if (!eventLoggingEnabled) {
            return;  // Can use this to avoid logging until system is fully up
        }

        // Note: zone is unused for non-zone events (like system armed)
        if (zone < 1 || zone > 64) {
            zone = 1;
        }

        // When armed in stay mode the panel sends events ARMED then ARMED-STAY
        // So if this event is ARMED-STAY, remove previous event from the log
        // so only the single ARMED-STAY event is logged.
        if (event == Const.EVENT_ARMED_STAY) {
            if (EVENT_LOG.isEmpty() == false) {
                EVENT_LOG.removeFirst();
            }
        }

        String ev = Base80.encode(event, zone-1);

        // Start a new event if repeat count reaches 79 (limit of base80 char)
        if (ev.equals(prevEv) && repeat < 79) {
            repeat++;
            EVENT_LOG.removeFirst(); // Remove most recently added item so it can be replaced by this
        } else {
            repeat = 0;
            prevEv = ev;
        }

        // Add the log entry
        EVENT_LOG.addFirst(Base80.encodeDateTime() + ev + Base80.encode(repeat));

        // Prune the event log
        while (EVENT_LOG.size() >= maxEvents) {
            EVENT_LOG.removeLast();
        }
    }
}
