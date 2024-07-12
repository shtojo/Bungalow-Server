package bungalowserver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    public enum Level { INFO, WARN, ERROR }

    // When log file Log.txt reaches max size it is renamed to LogOld.txt and a
    // new Log.txt file is started. This is more efficient than pruning one file.
    private static final int MAX_FILE_SIZE = 1024 * 1024;  // 1 MB
    private static final File LOG_FILE = new File("Log.txt");
    private static final File LOG_FILE_OLD = new File("LogOld.txt");
    private static boolean DEBUG = false;  // prints all messages to console
    private static BufferedWriter writer = null;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("MMddHHmmss");

    public static void setDebugMode(boolean debug) {
        DEBUG = debug;
    }

    public static void print(Level level, String message) {
        print(level, message, true);
    }

    /**
     * Log message and optionally print to console
     * @param level message severity
     * @param message message string
     * @param console true = always print to console, false = only prints to
     * console if debug is turned on in this class.
     */
    public static void print(Level level, String message, boolean console) {

        if (console) {
            System.out.println(message);
        }

        StringBuilder sb = new StringBuilder(SDF.format(new Date()));

        // If writer is null then initialize
        if (writer == null) {
            try {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(LOG_FILE, true), StandardCharsets.UTF_8));
            } catch (FileNotFoundException ex) {
                throw new RuntimeException(ex.getMessage());
            }
        }

        // Log the severity
        switch (level) {
            case WARN:
                sb.append(" WRN ");
                break;
            case ERROR:
                sb.append(" ERR ");
                break;
            default:
                sb.append(" INF ");
                break;
        }

        message = sb.append(message).toString();

        if (DEBUG) {
            System.out.println("----->" + message);
        }

        // Print message to log file (with tiemstamp and severity)
        try {
            writer.write(message);
            writer.newLine();
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        if (LOG_FILE.length() > MAX_FILE_SIZE) {
            try {
                System.out.println("Starting new log file...");
                writer.close();
                writer = null;  // must set to null so it is initialized on next call
                Files.move(LOG_FILE.toPath(), LOG_FILE_OLD.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
