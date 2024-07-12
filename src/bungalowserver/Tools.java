package bungalowserver;

/**
 * Tools/Utility Methods
 */
public class Tools {

    /**
     * Gets a printable hex string from an array of values
     * @param data array of values
     * @return hex string
     */
    public static String toHexString(int[] data) {
        return toHexString(data, 0, data.length);
    }

    /**
     * Gets a printable hex string from an array of values
     * @param data array of values
     * @param start start index (0 = first element)
     * @param length length of data to convert (bytes to convert)
     * @return hex string
     */
    public static String toHexString(int[] data, int start, int length) {

        if (data == null) {
            return "[NULL]";
        }

        if (length == 0 || data.length == 0) {
            return "[NO DATA]";
        }

        if (data.length < (start + length)) {
            return "[ERROR]";
        }

        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = start; i < start + length; i++) {
            if (data[i] < 0 || data[i] > 0xFF) {
                sb.append(String.format("-- "));  // represents invalid data
            } else {
                sb.append("0123456789ABCDEF".charAt((data[i] & 0xF0) >> 4)).append(
                    "0123456789ABCDEF".charAt((data[i] & 0x0F))).append(" ");
                // or sb.append(String.format("%02X ", data[i]));
            }
        }
        sb.deleteCharAt(sb.length() - 1);  // remove trailing space
        return sb.toString();
    }

}
