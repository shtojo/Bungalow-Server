package bungalowserver;

import bungalowserver.Log.Level;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Rules class
 */
public class Rules {

    private static final ArrayList<Rule> RULES = new ArrayList<>();

    /**
     * Get the number of defined rules
     * @return
     */
    public static int getCount() {
        return RULES.size();
    }

    /**
     * Gets the settings string to save to the settings file
     * @return String of settings to save to file or null if error
     */
    public static String getSettingsString() {
        String ls = System.lineSeparator();
        StringBuilder sb = new StringBuilder("RULE_BEGIN" + ls);

        for (Rule rule : RULES) {

            sb.append("  RULE Const.WHEN_");
            switch (rule.whenEvent) {
                case Const.WHEN_ZONE_READY:
                    sb.append("ZONE_READY ").append(rule.whenZone);
                    break;
                case Const.WHEN_ZONE_FAULTED:
                    sb.append("ZONE_FAULTED ").append(rule.whenZone);
                    break;
                case Const.WHEN_ARMED:
                    sb.append("ARMED ");
                    break;
                case Const.WHEN_ARMED_STAY:
                    sb.append("ARMED_STAY");
                    break;
                case Const.WHEN_ARMED_AWAY:
                    sb.append("ARMED_AWAY");
                    break;
                case Const.WHEN_DISARMED:
                    sb.append("DISARMED");
                    break;
                case Const.WHEN_BURGLARY:
                    sb.append("BURGLARY");
                    break;
                case Const.WHEN_FIRE:
                    sb.append("FIRE");
                    break;
                case Const.WHEN_DAILY:
                    sb.append("DAILY ").append(rule.whenHour).append(":").append(rule.whenMinute);
                    break;
                case Const.WHEN_WEEKLY:
                    sb.append("WEEKLY ").append(rule.whenDayOfWeek).append(" ").append(rule.whenHour).append(" ").append(rule.whenMinute);
                    break;
                case Const.WHEN_MONTHLY:
                    sb.append("MONTHLY ").append(rule.whenDate).append(" ").append(rule.whenHour).append(" ").append(rule.whenMinute);
                    break;
                default:
                    Log.print(Level.ERROR, "Rule error in saveSettings: " + getRuleString(rule));
                    return null;
            }

            sb.append(" IF_");
            switch (rule.ifCondition) {
                case Const.IF_ALWAYS:
                    sb.append("ALWAYS ");
                    break;
                case Const.IF_ARMED:
                    sb.append("ARMED ");
                    break;
                case Const.IF_ARMED_AWAY:
                    sb.append("ARMED_AWAY ");
                    break;
                case Const.IF_ARMED_STAY:
                    sb.append("ARMED_STAY ");
                    break;
                case Const.IF_DISARMED:
                    sb.append("DISARMED ");
                    break;
                case Const.IF_ZONE_READY:
                    sb.append("ZONE_READY ").append(rule.ifZone);
                    break;
                case Const.IF_ZONE_FAULTED:
                    sb.append("ZONE_FAULTED  ").append(rule.ifZone);
                    break;
                default:
                    Log.print(Level.ERROR, "Rule error in saveSettings: " + getRuleString(rule));
                    return null;
            }

            sb.append(" DO_");
            switch (rule.doTask) {
                case Const.DO_EMAIL:
                    sb.append("EMAIL ")
                            .append(rule.doEmailAddress)
                            .append(" \"").append(rule.doEmailSubject).append("\" ")
                            .append("\"").append(rule.doEmailBody).append("\"");
                    break;
                case Const.DO_SPEAK:
                    sb.append("SPEAK ").append(rule.doDeviceOrSpeak);
                    break;
                case Const.DO_TURN_ON:
                    sb.append("TURN_ON ").append(rule.doDeviceOrSpeak);
                    break;
                case Const.DO_TURN_OFF:
                    sb.append("TURN_OFF ").append(rule.doDeviceOrSpeak);
                    break;
                default:
                    Log.print(Level.ERROR, "Rule error in saveSettings: " + getRuleString(rule));
                    return null;
            }
            sb.append(ls);
        }
        sb.append("RULE_END").append(ls);
        return sb.toString();
    }

    /**
     * Clears all rules
     */
    public static void clear() {
        RULES.clear();
    }

    /**
     * Gets a string of all rules to send to the client
     * @return rules string
     */
    public static String getRulesForClient() {
        StringBuilder sb = new StringBuilder();
        boolean sep = false;
        for (Rule rule : RULES) {
            if (sep) {
                sb.append('|');
            } else {
                sep = true;
            }
            sb.append(getRuleString(rule));
        }
        return sb.toString();
    }

    /**
     * Gets a single rule string to send to the client
     * @param rule
     * @return rule string
     */
    private static String getRuleString(Rule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append(Base80.encode(rule.whenEvent, rule.whenZone, rule.whenHour,
                rule.whenMinute, rule.whenDayOfWeek, rule.whenDate,
                rule.ifCondition, rule.ifZone, rule.doTask,
                rule.doDeviceOrSpeak));
        sb.append(',');
        sb.append(rule.doEmailAddress).append(',');
        sb.append(rule.doEmailSubject).append(',');
        sb.append(rule.doEmailBody);
        return sb.toString();
    }

    /**
     * Adds a rule
     * @param rule Rule object
     */
    public static void addRule(Rule rule) {
        RULES.add(rule);
    }

    /**
     * Adds a rule
     * @param message message string
     * @return true if success
     */
    public static boolean addRule(String message) {
        Log.print(Level.INFO, "Adding rule: " + message);

        String[] tokens = message.split("~");
        if ((tokens.length != 4) || (tokens[0].length() != 10)) {
            Log.print(Level.WARN, "Invalid rule string in addRule! " + message);
            return false;
        }

        Rule rule = new Rule();

        rule.whenEvent = tokens[0].charAt(0);  // whenEvent
        rule.whenZone = Base80.decode(tokens[0].charAt(1));  // whenZone
        rule.whenHour = Base80.decode(tokens[0].charAt(2));  // whenHour
        rule.whenMinute = Base80.decode(tokens[0].charAt(3));  // whenMinute
        rule.whenDayOfWeek = Base80.decode(tokens[0].charAt(4));  // whenDayOfWeek
        rule.whenDate = Base80.decode(tokens[0].charAt(5));  // whenDate
        rule.ifCondition = tokens[0].charAt(6);  // ifCondition
        rule.ifZone = Base80.decode(tokens[0].charAt(7));  // ifZone
        rule.doTask = tokens[0].charAt(8);  // doTask
        rule.doDeviceOrSpeak = Base80.decode(tokens[0].charAt(9));  // doDevice
        rule.doEmailAddress = tokens[1];  // doEmailAddress
        rule.doEmailSubject = tokens[2];  // doEmailSubject
        rule.doEmailBody = tokens[3];  // doEmailMessageOrSpeakText

        if ((rule.whenEvent < 0 || rule.whenEvent > 10) ||
                (rule.whenZone < 0 || rule.whenZone > 64) ||
                (rule.whenHour < 0 || rule.whenHour > 23) ||
                (rule.whenMinute < 0 || rule.whenMinute > 59) ||
                (rule.whenDayOfWeek < 0 || rule.whenDayOfWeek > 6) ||
                (rule.whenDate < 1 || rule.whenDate > 31) ||
                (rule.ifCondition < 0 || rule.ifCondition > 6) ||
                (rule.ifZone < 0 || rule.ifZone > 64) ||
                (rule.doTask < 0 || rule.doTask > 3) ||
                (rule.doDeviceOrSpeak < 0)) {
            Log.print(Level.ERROR, "Invalid rule string in addRule (doDevice): " + message);
            return false;
        }

        RULES.add(rule);  // add the rule to the rule array
        return Settings.save();
    }

    /**
     * Removes a rule
     * @param message message string
     * @return true if success
     */
    public static boolean removeRule(String message) {
        Log.print(Level.INFO, "Removing rule: " + message);

        if (message.length() != 5) {
            Log.print(Level.INFO, "Invalid rule string!" + message);
            return false;
        }

        int i = Base80.decode(message.charAt(4));
        if (i < 0 || i >= RULES.size()) {
            return false;
        }
        RULES.remove(i);
        return true;
    }

    /**
     * Handles rule triggers
     */
    public static void handleRuleTriggers() {

        boolean trigger;
        LocalDateTime now = LocalDateTime.now();

        for (Rule rule : RULES) {
            trigger = false;
            switch (rule.whenEvent) {
                case Const.WHEN_DAILY:
                    if ((rule.whenHour == now.getHour())
                            && (rule.whenMinute == now.getMinute())) {
                        trigger = true;
                    }
                    break;
                case Const.WHEN_WEEKLY:
                    // convert java 1-7=mon-sun to 0-6=sun-sat
                    int dayofweek = now.getDayOfWeek().getValue();
                    if (dayofweek > 6) {
                        dayofweek--;
                    }
                    if ((rule.whenHour == now.getHour())
                            && (rule.whenMinute == now.getMinute())
                            && (rule.whenDayOfWeek == dayofweek)) {
                        trigger = true;
                    }
                    break;
                case Const.WHEN_MONTHLY:
                    if ((rule.whenHour == now.getHour())
                            && (rule.whenMinute == now.getMinute())
                            && (rule.whenDate == now.getDayOfMonth())) {
                        trigger = true;
                    }
                    break;
                default:
                    break;
            }

            if (trigger) {
                Log.print(Level.INFO, "Performing rule");

                switch (rule.doTask) {
                    case Const.DO_EMAIL:
                        if (Emailer.sendEmail(
                                rule.doEmailAddress,
                                rule.doEmailSubject,
                                rule.doEmailBody, true) == false) {
                            // Warn but continue on if error sending email
                            System.out.println("Error sending email, see the log file for more info.");
                            Speaker.speak(27, 26);
                        }
                        break;
                    case Const.DO_SPEAK:
                        Speaker.speak(rule.doDeviceOrSpeak);
                        break;
                    case Const.DO_TURN_ON:
                        // Future support for device control
                        break;
                    case Const.DO_TURN_OFF:
                        // Future support for device control
                        break;
                    default:
                        Log.print(Level.WARN, "Unhandled event in PerformRuleTask!");
                        break;
                }
            }
        }
    }
}
