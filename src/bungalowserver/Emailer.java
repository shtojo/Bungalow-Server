package bungalowserver;

// Requires javax.mail from https://java.net/projects/javamail/pages/Home
import bungalowserver.Log.Level;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Email sender
 * @author Shawn Johnston
 */
public class Emailer {

    public static String server = "smtp.gmail.com";  // smtp.gmail.com
    public static String username = "";  // username-(without-@xyz.com)
    public static String password = "";
    public static Security security = Security.TLS;  // defaults to TLS
    public static String contacts = "";  // comma delimited email addresses

    public enum Security {
        TLS, SSL, NONE
    }

    /**
     * Send an email
     * @param to Email address to send to (can be multiple, comma-delimited)
     * @param subject Subject text
     * @param message Message text
     * @param highPriority true = high priority, false = normal priority
     * @return true on success else false
     */
    public static boolean sendEmail(String to, String subject,
            String message, boolean highPriority) {

        /*
        Gmail uses:
        IMAP (read mail) requires SSL: imap.gmail.com:993
        SMTP (send mail) requires TLS: smtp.gmail.com (ue port 465 or 587)
        */

        // Store email event to log
        Log.print(Level.INFO,
                "Sending email to: " + to + System.lineSeparator() +
                "   subject: " + subject + System.lineSeparator() +
                "   message: " + message + System.lineSeparator());

        Properties properties = new Properties();
        properties.put("mail.smtp.host", server);

        switch (security) {
            case TLS:
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.auth", "true");
                properties.put("mail.smtp.port", "587");
                break;
            case SSL:
                properties.put("mail.smtp.socketFactory.port", "465");
                properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                properties.put("mail.smtp.auth", "true");
                properties.put("mail.smtp.port", "465");
                break;
            default:
                properties.put("mail.smtp.auth", "false");  // ?
                properties.put("mail.smtp.port", "25");
                break;
        }

        // Session session = Session.getDefaultInstance(properties,
        Session session = Session.getInstance(properties,
                new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        Message msg = new MimeMessage(session);
        try {
            if (highPriority) {
                msg.setHeader("X-Priority", "1");  // 1 = high, 3 = normal, 5 = low
            }
            msg.setFrom(new InternetAddress(username));  // this should actually be with the @gmail.com added
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject);
            msg.setText(message);
            Transport.send(msg);
        } catch (MessagingException ex) {
            Log.print(Level.WARN, "Unable to send email message to " + to + "! " + ex.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Sends an alert email to 'contacts' list
     * @param subject Subject text
     * @param message Message text
     * @return true on success else false
     */
    public static boolean sendEmail(String subject, String message) {
        return sendEmail(contacts, subject, message, true);
    }

    /**
     * Sends an alert email to 'contacts' list
     * @param subject
     * @return
     */
    public static boolean sendEmail(String subject) {
        return sendEmail(contacts, subject, Zones.getZoneStatusMessage(), true);
    }

    /**
     * Get email settings as string to send to client
     * @return email settings string
     */
    public static String getSettingsForClient() {
        // Note: Port : 'N' = normal/25, 'S' = SSL/465, 'T' = TLS/587
        StringBuilder sb = new StringBuilder();

        sb.append(server).append('~');
        sb.append(password).append('~');

        switch (security) {
            case TLS:
                sb.append("T~");
                break;
            case SSL:
                sb.append("S~");
                break;
            default:
                sb.append("N~");
                break;
        }

        sb.append(contacts);
        return sb.toString();
    }

    /**
     * Change the email settings with string from client
     * @param message
     * @return true for success
     */
    public static boolean changeEmailSettings(String message) {

        // verify and remove header
        if (message == null) {
            message = "";
        }
        if (message.length() < 5 || message.startsWith("EMS=") == false) {
            Log.print(Level.WARN, "Invalid: " + message);
            return false;
        }
        message = message.substring(4);

        String[] words = message.split("~");

        if (words.length != 4) {
            Log.print(Level.WARN, "Invalid: " + message);
            return false;
        }

        // server
        if (words[0].length() > 0) {
            server = words[0];
        }

        // password
        if (words[1].length() > 0) {
            password = words[1];
        }

        // security type (port)
        if (words[2].length() > 0) {
            switch (words[2]) {
                case "T":
                    security = Security.TLS;
                    break;
                case "S":
                    security = Security.SSL;
                    break;
                case "N":
                    security = Security.NONE;
                    break;
                default:
                    Log.print(Level.WARN, "Invalid email settings string, bad security type, " + message);
                    return false;
            }
        }

        // alarmContacts
        if (words[3].length() > 0) {
            contacts = words[3].replaceAll(" ", "");  // remove spaces
        }
        return Settings.save();
    }

    /**
     * Gets the settings string to save to the settings file
     * @return String of settings to save to file
     */
    public static String getSettingsString() {
        String ls = System.lineSeparator();

        StringBuilder sb = new StringBuilder("EMAIL_BEGIN" + ls);
        sb.append("  USERNAME ").append(username).append(ls);
        sb.append("  SERVER ").append(server).append(ls);
        sb.append("  SECURITY ").append(security).append(ls);
        sb.append("  PASSWORD ").append(password).append(ls);

        String[] contactArray = contacts.split(",");
        for (String contact : contactArray) {
            if (contact.isEmpty() == false) {
                sb.append("  CONTACT ").append(contact).append(ls);
            }
        }
        sb.append("EMAIL_END").append(ls);
        return sb.toString();
    }
}
