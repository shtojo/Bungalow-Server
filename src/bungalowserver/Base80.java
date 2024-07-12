package bungalowserver;

import java.time.LocalDateTime;

/**
 * Provides custom Base80 encoding
 * @author SJOHNS
 */
public class Base80 {

    // Base 80 character set represeting values 0-79
    private static final String B80L = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-<>(){}[]#&%@!*^/";
    private static final char[] B80A = B80L.toCharArray();

    /**
     * Decodes a base80 character
     * @param c character to decode
     * @return returns the numeric value or -1
     */
    public static int decode(char c) {
        return B80L.indexOf(c);
    }

    /**
     * Encodes one or more values to base80 string
     * @param values int values
     * @return base80 encoded string
     */
    public static String encode(int... values) {

        int len = values.length;
        char[] chars = new char[len];

        for (int i = 0; i < len; i++) {
            if (values[i] < 0 || values[i] > 79) {
                throw new IllegalArgumentException("Value out of range in B80.Encode (" + values[i] + ")");
            }
            // note: wihtout above line it will throw: IndexOutOfRangeException: Index was outside the bounds of the array.
            chars[i] = B80A[values[i]];
        }
        return new String(chars);
    }

    /**
     * Encode current date & time as wmdhms
     * Where w=weekday (0=sun), m=month (1-12), d=date (1-31),
     * h=hour (0-23), m=minute (0-59), s=second (0-59)
     * @return String representing date & time encoded to six base80 characters
     */
    public static String encodeDateTime() {

        LocalDateTime time = LocalDateTime.now();

        // Translate java 1=monday to 0=sunday
        int dayofweek = time.getDayOfWeek().getValue();
        if (dayofweek > 6) {
            dayofweek = 0;
        }

        return encode(dayofweek, time.getMonthValue(), time.getDayOfMonth(),
                time.getHour(), time.getMinute(), time.getSecond());
    }
}
