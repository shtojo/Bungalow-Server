package bungalowserver;

import bungalowserver.Log.Level;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

/**
 * Serial handler class. Handles low-level communication to and from the panel.
 * Receives data from the panel and builds full command packets then invokes the
 * callback whenever a complete command is available. This class handles
 * communication errors and automatically acknowledges commands received from
 * the panel. It ignores invalid commands and verifies checksum, etc.
 *
 * To implement the callback: Define a class that implements the SerialCallback:
 * public class PanelComm implements SerialHandler.SerialCallBack {...} Then
 * override the SerialReceiveCallback method:
 * @Override public void SerialReceiveCallback(int[] data) {...} Then subscribe
 * to the SerialCallBack event: SerialHandler serialHandler = new
 * SerialHandler(); serialHandler.AddListener(this);
 *
 * @author Shawn Johnston
 */
public class SerialHandler {

    //<editor-fold defaultstate="collapsed" desc="Panel Request Constants">
    /**
     * Panel serial response constants
     */
    private static final int REQUEST_INTERFACE_CONFIGURATION = 0x21,
            REQUEST_ZONE_NAME = 0x23,
            REQUEST_ZONE_STATUS = 0x24,
            REQUEST_ZONE_SNAPSHOT = 0x25,
            REQUEST_PARTITION_STATUS = 0x26,
            REQUEST_PARTITION_SNAPSHOT = 0x27,
            REQUEST_SYSTEM_STATUS = 0x28,
            REQUEST_SEND_X10 = 0x29,
            REQUEST_LOG_EVENT = 0x2a,
            REQUEST_SEND_KEYPAD_TEXT = 0x2b,
            REQUEST_KEYPAD_TERMINAL_MODE = 0x2c,
            REQUEST_PROGRAM_DATA_REQUEST = 0x30,
            REQUEST_PROGRAM_DATA_COMMAND = 0x31,
            REQUEST_STORE_COMM_EVENT = 0x3a,
            REQUEST_SET_CLOCK_CALENDAR = 0x3b,
            REQUEST_PRI_KEYPAD_FUNC_WITH_PIN = 0x3c,
            REQUEST_PRI_KEYPAD_FUNC_WITHOUT_PIN = 0x3d,
            REQUEST_SECONDARY_KEYPAD_FUNCTION = 0x3e,
            REQUEST_ZONE_BYPASS_TOGGLE = 0x3f,
            REQUEST_ACKNOWLEDGE = 0x1d;
    // Note: Acknowledge is sent to the panel in response to a command that requests ACK
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Panel Response Constants">
    /**
     * Panel Response Constants
     */
    public static final int RESPONSE_INTERFACE_CONFIG_MESSAGE = 0x01,
            RESPONSE_ZONE_NAME_MESSAGE = 0x03,
            RESPONSE_ZONE_STATUS_MESSAGE = 0x04,
            RESPONSE_ZONE_SNAPSHOT_MESSAGE = 0x05,
            RESPONSE_PARTITION_STATUS_MESSAGE = 0x06,
            RESPONSE_PARTITION_SNAPSHOT_MESSAGE = 0x07,
            RESPONSE_SYSTEM_STATUS_MESSAGE = 0x08,
            RESPONSE_X10_MESSAGE = 0x09,
            RESPONSE_LOG_EVENT_MESSAGE = 0x0a,
            RESPONSE_KEYPAD_MESSAGE = 0x0b,
            RESPONSE_PROGRAM_DATA_REPLY = 0x10,
            RESPONSE_USER_INFORMATION_REPLY = 0x12,
            RESPONSE_COMMAND_FAILED = 0x1c, // panel unable to do command
            RESPONSE_COMMAND_COMPLETED = 0x1d,
            RESPONSE_COMMAND_ERROR = 0x1e, // bad command or panel busy with previous command
            RESPONSE_COMMAND_NOT_SUPPORTED = 0x1f, // message rejected (valid but not supported or disabled)
            RESPONSE_NONE = 0x3f; // expect nothing back when I send an ACK to the panel

    //</editor-fold>

    // These 'expect' variables are updated by the serial receive thread as responses come in from the panel, so the
    // serial send thread can see the responses as they come in while waiting for a specific response
    private static int expectResponse = RESPONSE_NONE;
    private static int expectZoneOrPartition = 0;  // 0 = none, 1.. = zone or partition number
    private static boolean receiveFlag = false;
    //private static boolean enableCallback = false;

    private static SerialReceive serialReceiveCallback = null;
    private static SerialPort serialPort = null;
    public static String serialPortName = "/dev/ttyUSB0";
    public static int serialBaudRate = 38400;

    // Panel will respond within 2.5 seconds from receiving a request, so allow 3 seconds here before retry
    private static final int SERIAL_RECEIVE_TIMEOUT = 3000;  // milliseconds

    // Serial send timeout (encounted occassional send timeout with 250ms)
    private static final int SERIAL_SEND_TIMEOUT = 750;  // milliseconds

    /**
     * Serial callback called when serial data received from panel. Note: Can
     * disable serial callback to avoid processing messages before I initialize
     * and build the zone info. When disabled, messages will still be received
     * and acknowledged as needed, but no other action will be done.
     */
    private SerialCallback callBack = null;

    /**
     * Adds a callback listener
     * @param mycallback
     */
    public void addListener(SerialCallback mycallback) {
        callBack = mycallback;
    }

    /**
     * Serial callback interface defines the callback methods. These methods
     * must be defined in the class that "implements
     * SerialHandler.SerialCallBack", except for the ones defined below with
     * default handlers, these may be redefined in the implements class.
     */
    public interface SerialCallback {

        /**
         * Callback when Interface Configuration messages received from panel
         * @param message
         */
        void processInterfaceConfiguration(int[] message);

        /**
         * Callback when Zone Name messages received from panel
         * @param message
         */
        void processZoneName(int[] message);

        /**
         * Callback when Zone Status messages received from panel
         * @param message
         */
        void processZoneStatus(int[] message);

        /**
         * Callback when Partition Status messages received from panel
         * @param message
         */
        void processPartitionStatus(int[] message);

        /**
         * Callback when System Status messages received from panel
         * @param message
         */
        void processSystemStatus(int[] message);

        /**
         * Callback when Acknowledge messages received from panel
         */
        //default void processAcknowledge() {
        //    Log.print(Level.INFO, "Received Acknowledge from panel.", false);x
        //}

        /**
         * Callback when Keypad messages received from panel
         * @param message
         */
        default void processKeypadMessage(int[] message) {
            // ignored
            Log.print(Level.INFO, "Received Keypad message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when Partition Snapshot messages received from panel
         * @param message
         */
        default void processPartitionSnapshot(int[] message) {
            // currently ignores this message since I process the normal partition message
            Log.print(Level.INFO, "Received Partition Snapshot message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when Zone Snapshot messages received from panel
         * @param message
         */
        default void processZoneSnapshot(int[] message) {
            // currently ignores this message since I process the normal zone message
            Log.print(Level.INFO, "Received Zone Snapshot message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when Program Data messages received from panel
         * @param message
         */
        default void processProgramData(int[] message) {
            // ignored
            Log.print(Level.INFO, "Received Program Data message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when Log Event messages received from panel
         * @param message
         */
        default void processLogEvent(int[] message) {
            Log.print(Level.INFO, "Received Log Event message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when User Information messages received from panel
         * @param message
         */
        default void processUserInformation(int[] message) {
            Log.print(Level.INFO, "Received User Information message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Callback when X10 messages received from panel
         * @param message
         */
        default void processX10Received(int[] message) {
            Log.print(Level.INFO, "Received X10 message from panel: "
                    + Tools.toHexString(message), false);
        }

        /**
         * Error
         * @param message message is one of the following:
         * RESPONSE_COMMAND_FAILED = 0x1c; // panel was unable to perform the
         * command RESPONSE_COMMAND_ERROR = 0x1e; // Bad command or panel was
         * busy with previous command RESPONSE_COMMAND_NOT_SUPPORTED = 0x1f; //
         * Message rejected (valid but not supported or disabled)* or message is
         * not recognized (entire message is returned)
         */
        default void processError(int[] message) {
            // error is: RESPONSE_COMMAND_FAILED (0x1c) or RESPONSE_COMMAND_ERROR (0x1e) or RESPONSE_COMMAND_NOT_SUPPORTED (0x1f)
            Log.print(Level.INFO, "Received Error message from panel: "
                    + Tools.toHexString(message), false);
        }
    }

    /**
     * Gets the expected reply from a given request.
     * @param request The request
     * @return The expected reply
     */
    private static int getExpectedResponse(int request) {

        // Expected requests and responses:
        // REQUEST_ACKNOWLEDGE                  RESPONSE_NONE (no response expected from panel when I send Acknowledge)
        // REQUEST_SEND_X10                     RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_SEND_KEYPAD_TEXT             RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_KEYPAD_TERMINAL_MODE         RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_PROGRAM_DATA_COMMAND         RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_STORE_COMM_EVENT             RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_SET_CLOCK_CALENDAR           RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_PRI_KEYPAD_FUNC_WITH_PIN     RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_PRI_KEYPAD_FUNC_WITHOUT_PIN  RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_SECONDARY_KEYPAD_FUNCTION    RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_ZONE_BYPASS_TOGGLE           RESPONSE_COMMAND_COMPLETED (ACK)
        // REQUEST_INTERFACE_CONFIGURATION      RESPONSE_INTERFACE_CONFIG_MESSAGE
        // REQUEST_ZONE_NAME                    RESPONSE_ZONE_NAME_MESSAGE
        // REQUEST_ZONE_STATUS                  RESPONSE_ZONE_STATUS_MESSAGE
        // REQUEST_ZONE_SNAPSHOT                RESPONSE_ZONE_SNAPSHOT_MESSAGE
        // REQUEST_PARTITION_STATUS             RESPONSE_PARTITION_STATUS_MESSAGE
        // REQUEST_PARTITION_SNAPSHOT           RESPONSE_PARTITION_SNAPSHOT_MESSAGE
        // REQUEST_SYSTEM_STATUS                RESPONSE_SYSTEM_STATUS_MESSAGE
        // REQUEST_LOG_EVENT                    RESPONSE_LOG_EVENT_MESSAGE
        // REQUEST_PROGRAM_DATA_REQUEST         RESPONSE_PROGRAM_DATA_REPLY
        // Look at the message to determine what the expected response is

        switch (request) {

            // If sending ACK to the panel, then no reponse expected
            case REQUEST_ACKNOWLEDGE:
                return RESPONSE_NONE;

            // Group of commands that get ACK/NACK response
            case REQUEST_SEND_X10:
            case REQUEST_SEND_KEYPAD_TEXT:
            case REQUEST_KEYPAD_TERMINAL_MODE:
            case REQUEST_PROGRAM_DATA_COMMAND:
            case REQUEST_STORE_COMM_EVENT:
            case REQUEST_SET_CLOCK_CALENDAR:
            case REQUEST_PRI_KEYPAD_FUNC_WITH_PIN:
            case REQUEST_PRI_KEYPAD_FUNC_WITHOUT_PIN:
            case REQUEST_SECONDARY_KEYPAD_FUNCTION:
            case REQUEST_ZONE_BYPASS_TOGGLE:
                return RESPONSE_COMMAND_COMPLETED;

            // Group of commands that get specific message responses
            case REQUEST_INTERFACE_CONFIGURATION:
                return RESPONSE_INTERFACE_CONFIG_MESSAGE;
            case REQUEST_ZONE_NAME:
                return RESPONSE_ZONE_NAME_MESSAGE;
            case REQUEST_ZONE_STATUS:
                return RESPONSE_ZONE_STATUS_MESSAGE;
            case REQUEST_ZONE_SNAPSHOT:
                return RESPONSE_ZONE_SNAPSHOT_MESSAGE;
            case REQUEST_PARTITION_STATUS:
                return RESPONSE_PARTITION_STATUS_MESSAGE;
            case REQUEST_PARTITION_SNAPSHOT:
                return RESPONSE_PARTITION_SNAPSHOT_MESSAGE;
            case REQUEST_SYSTEM_STATUS:
                return RESPONSE_SYSTEM_STATUS_MESSAGE;
            case REQUEST_LOG_EVENT:
                return RESPONSE_LOG_EVENT_MESSAGE;
            case REQUEST_PROGRAM_DATA_REQUEST:
                return RESPONSE_PROGRAM_DATA_REPLY;
            default:
                throw new IllegalArgumentException("Invalid request id in getExpectedResponse (" + request + ")!");
        }
    }

    /**
     * Opens the serial port using the settings file then starts listening
     * If this method fails, the port will be closed before returning.
     * (listener runs on separate thread).
     * @return true on success else false
     */
    public boolean start() {

        String[] portnames = SerialPortList.getPortNames();
        if (Arrays.asList(portnames).contains(serialPortName) == false) {
            Log.print(Level.ERROR, "Bad serial port (" + serialPortName
                    + "), valid ports: " + Arrays.toString(portnames));
            return false;
        }

        serialPort = new SerialPort(serialPortName);
        try {
            serialPort.openPort();
            serialPort.setParams(serialBaudRate, SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);

            // Note: The event listener runs on separate thread
            serialReceiveCallback = new SerialReceive(serialPort);
            serialPort.addEventListener(serialReceiveCallback, SerialPort.MASK_RXCHAR);

        } catch (SerialPortException ex) {
            Log.print(Level.ERROR, "Error opening serial port! " + ex.getMessage(), false);
            close();
            return false;
        }

        // Wait a bit to allow messages queued-up in the panel send buffer to be ack'd (but otherwise ignored)
        Log.print(Level.INFO, "Clearing queued messages...");
        final int IDLE_TIME = 4000;  // wait until 4 seconds with no data from panel
        final int IDLE_DELAY = 200;  // time between checks
        int idleTime = IDLE_TIME;
        receiveFlag = false;

        while (idleTime > 0) {
            try {
                Thread.sleep(IDLE_DELAY);
            } catch (InterruptedException ex) {
                throw new RuntimeException("Thread sleep exception!", ex);
            }
            idleTime -= IDLE_DELAY;
            if (receiveFlag) {
                idleTime = IDLE_TIME;
                receiveFlag = false;
            }
        }
        Log.print(Level.INFO, "All queued messages cleared, message processing enabled", false);
        serialReceiveCallback.enableCallback(true);
        return true;
    }

    /**
     * Close serial port You must close the serial port when done to stop the
     * serial thread Otherwise the main application may not end.
     * @return true on success else false
     */
    public boolean close() {
        try {
            if (serialPort != null) {
                serialPort.closePort();  // stops listeners then closes port
            }
        } catch (SerialPortException ex) {
            Log.print(Level.ERROR, "Error closing serial port! " + ex.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Build a full command packet and send to the panel (adds start, length,
     * byte-padding and checksum). Ex: 07 84 09 7E 10 58 01 00 -> 7E 07 84 09 7D
     * 5E 10 58 01 00 7C D1 Accepts int array or comma delimited integers, ex:
     * SendCommand(0xab, 0xcd);
     * @param command
     * @return True if success, False if fail
     */
    public boolean sendMessage(int... command) {
        int[] cmd = new int[command.length + 4];  // start, length, data, cs1, cs2

        cmd[0] = 0x7e;  // start byte
        cmd[1] = command.length;  // length byte
        System.arraycopy(command, 0, cmd, 2, command.length);  // data bytes

        // Calculate and append checksum bytes (exclude start byte)
        int checksum = calculateChecksum(cmd, 1, command.length + 1);
        cmd[cmd.length - 2] = checksum >> 8 & 0xff;
        cmd[cmd.length - 1] = checksum & 0xff;

        cmd = byteStuff(cmd);
        return sendMessageRaw(cmd);
    }

    /**
     * Sends a complete packet to the panel and wait for a valid response.
     * Accepts int array or comma delimited ints, ex: SendCommand(0xab, 0xcd);
     * This call blocks until success or all retries failed.
     * @param message Message to send to panel
     * @return True if success, False if fail
     */
    public boolean sendMessageRaw(int... message) {
        // Get the expected response and save in global expectResponse variable
        // When this response is received, the receive handler will clear this back to RESPONSE_NONE
        expectResponse = getExpectedResponse(message[2]);

        // If sending ACK (or anything that does not require a reply) then just send and be done
        if (expectResponse == RESPONSE_NONE) {
            serialSend(message);
            return true;
        }

        // If message has a zone or partition then set the expected zone/partition number in response
        // else set to zero if no zone or partition is associated with the message
        expectZoneOrPartition = 0;  // default to none

        String waitMessage;
        boolean isZoneOrPartitionMessage = (
                message[2] == REQUEST_ZONE_STATUS ||
                message[2] == REQUEST_ZONE_NAME ||
                message[2] == REQUEST_ZONE_SNAPSHOT ||
                message[2] == REQUEST_PARTITION_STATUS ||
                message[2] == REQUEST_PARTITION_SNAPSHOT);
        if (isZoneOrPartitionMessage) {
            expectZoneOrPartition = message[3];
            waitMessage = String.format("Waiting for panel response %02X for zone/part byte %d (%02X)", expectResponse, expectZoneOrPartition, expectZoneOrPartition);
        } else {
            waitMessage = String.format("Waiting for panel response %02X", expectResponse);
        }

        int attempts = 3;  // attempt to send up to 3 times before giving up
        while (attempts > 0) {
            attempts--;

            if (attempts == 1) {
                Log.print(Level.WARN, "Second attempt!", false);
            } else if (attempts == 0) {
                Log.print(Level.WARN, "Third (and final) attempt!", false);
            }

            serialSend(message);

            Log.print(Level.INFO, waitMessage, false);

            // Command sent, now wait for a response or timeout
            //Log.print(Level.INFO, "Waiting for panel response...", false);
            long timeout = SERIAL_RECEIVE_TIMEOUT;  // milliseconds
            long endTime = System.currentTimeMillis() + timeout;
            while (expectResponse != RESPONSE_NONE && timeout > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    throw new RuntimeException("Thread sleep exception!");
                }
                timeout = endTime - System.currentTimeMillis();
            }

            // If success then break out
            if (expectResponse == RESPONSE_NONE) {
                //Log.print(Level.INFO, "Expected message received", false);
                break;
            }

            // Take ownership of the lock object then call 'wait' to release the lock (and
            // be added to the lock objects waiting queue, then this trhead sleeps until
            // notified when the other thread gets the lock and calls 'notify' (or timeout)
            //synchronized (lock) {
            //    panelResponse = RESPONSE_NONE;
            //    while ((panelResponse != expectedResponse) && (timeout > 0)) {
            //        timeout = endTime - System.currentTimeMillis();
            //    }
            //    // while expected condition is false (required in case of spurious wakeup)
            //    while ((response == RESPONSE_NONE) && (timeout > 0)) {
            //        try {
            //            lock.wait(timeout);  // timeout in milliseconds
            //        } catch (InterruptedException ex) {
            //            throw new RuntimeException("Error waiting for response from panel! " + ex.getMessage(), ex);
            //        }
            //        timeout = endTime - System.currentTimeMillis();
            //    }
            //}
        }

        if (expectResponse != RESPONSE_NONE) {
            String errMsg = "Did not receive the expected response from the panel!";
            Log.print(Level.ERROR, errMsg);
            return false;
        }

        return true;
    }

    /**
     * Gets the settings string to save to the settings file
     * @return String of settings to save to file
     */
    public static String getSettingsString() {
        String ls = System.lineSeparator();
        return "SERIAL_BEGIN" + ls +
            "  PORT " + serialPortName + ls +
            "  BAUD " + Integer.toString(serialBaudRate) + ls +
            "SERIAL_END" + ls;
    }

    /**
     * Send the command in another thread with timeout and wait for completion
     * @param message
     */
    private void serialSend(int[] message) {

        if (serialPort == null) {
            throw new RuntimeException("Serial port not defined in serialSend!");
        }

        // Log the data sent unless ack
        if (message[2] != 0x1d) {
            Log.print(Level.INFO, "Sending message to panel: " + Tools.toHexString(message), false);
        }

        // Run the serial send on another thread allowing SERIAL_SEND_TIMEOUT milliseconds to complete
        // I've seen the serial send block forever if passed an invalid serial port for example
        // So to be safe, run the serial send on another thread with a timeout.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future future = executor.submit(() -> {
            boolean success;
            try {
                success = serialPort.writeIntArray(message);
            } catch (SerialPortException ex) {
                success = false;
            }
            if (!success) {
                // Throw an exception to be caught below in future.get
                throw new RuntimeException("Error writing to serial port!");
            }
            //return success;
        });

        // Wait for thread, check after SERIAL_SEND_TIMEOUT milliseconds, if not complete then stop it
        try {
            future.get(SERIAL_SEND_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException ex) {
            future.cancel(true);
            close();
            throw new RuntimeException("Error writing to serial port! " + ex.getMessage(), ex);
        }
        executor.shutdownNow();
    }

    /**
     * Add byte-stuffing to the array Note: Checksum must be calculated without
     * byte-stuffing
     * @param cmd
     * @return
     */
    private static int[] byteStuff(int[] cmd) {

        // Check number of byte-stuff operations are needed
        int bytestuffs = 0;
        for (int i = 1; i < cmd.length; i++) {
            if (cmd[i] == 0x7d || cmd[i] == 0x7e) {
                bytestuffs++;
            }
        }

        // If not byte-stuffs are needed then just return original array
        if (bytestuffs == 0) {
            return cmd;
        }

        // Create new array of the apropriate size and add byte-stuffing
        int[] newcmd = new int[cmd.length + bytestuffs];
        newcmd[0] = cmd[0];  // copy start byte
        int i1 = 1;
        for (int i2 = 1; i2 < cmd.length; i2++) {
            // Convert all 7D -> 7D 5D
            // Convert all 7E -> 7D 5E
            switch (cmd[i2]) {
                case 0x7d:
                    newcmd[i1++] = 0x7d;
                    newcmd[i1++] = 0x5d;
                    break;
                case 0x7e:
                    newcmd[i1++] = 0x7d;
                    newcmd[i1++] = 0x5e;
                    break;
                default:
                    newcmd[i1++] = cmd[i2];
                    break;
            }
        }
        return newcmd;
    }

    /**
     * Calculates the checksum over the specified region of the array Note:
     * Checksum must be calculated without byte-stuffing
     * To do entire array: calculateChecksum(data, 0, data.length);
     * @param data
     * @param start
     * @param length
     * @return Checksum
     */
    private static int calculateChecksum(int[] data, int start, int length) {
        int cs1 = 0, cs2 = 0;

        for (int i = start; i < (start + length); i++) {
            if (255 - cs1 < data[i]) {
                cs1 = (cs1 + 1) & 0xff;
            }
            cs1 = (cs1 + data[i]) & 0xff;
            if (cs1 == 255) {
                cs1 = 0;
            }
            if (255 - cs2 < cs1) {
                cs2 = (cs2 + 1) & 0xff;
            }
            cs2 = (cs2 + cs1) & 0xff;
            if (cs2 == 255) {
                cs2 = 0;
            }
        }
        return ((cs1 << 8) | cs2) & 0xffff;
    }

    /**
     * Serial port listener callback (runs on separate thread)
     */
    private class SerialReceive implements SerialPortEventListener {

        private SerialPort serialPort = null;

        // Longest possible message, assuming (unrealistic) 100% bytestuffing is:
        // start + length + 2*(18data + cshi + cslo) = 42 bytes
        private static final int MSG_MAX_LEN = 64;

        // Note: length excludes START, CSHI, CSSLO and bytestuffing
        private final int[] dataFromPanel = new int[MSG_MAX_LEN];
        private int dataLen = 0;

        // rawData holds the unmodified data from panel as a string for debug/logging
        private final int[] rawData = new int[MSG_MAX_LEN];
        private int rawLen = 0;

        private int expectLength = 0;
        private boolean byteStuff = false;
        private boolean callbackEnable = false;

        public SerialReceive(SerialPort port) {
            serialPort = port;
        }

        public void enableCallback(boolean enable) {
            callbackEnable = enable;
        }

        /**
         * Serial receive event
         * @param event
         */
        @Override
        public void serialEvent(SerialPortEvent event) {
            int bytesAvailable = event.getEventValue();

            // Should only be here if data is available, return if none
            if ((event.isRXCHAR() == false) || (bytesAvailable < 1)) {
                return;
            }

            int[] data;
            try {
                data = serialPort.readIntArray(bytesAvailable);
            } catch (SerialPortException ex) {
                String errMsg = "Error reading from serial port! " + ex.getMessage();
                Log.print(Level.ERROR, errMsg);
                throw new RuntimeException(errMsg);
            }

            // Set flag here to indicate data recieved
            receiveFlag = true;

            // Process one byte at-a-time since I don't know yet how many bytes are in this message
            // and there could be two messages back-to-back possibly
            for (int b : data) {
                if (processByte(b) == false) {
                    rawLen = 0;   // discard raw data
                    dataLen = 0;  // discard command data
                    byteStuff = false;  // ensure bytestuffing is off
                }
            }
        }

        /**
         * Process a single byte from the panel
         * @param b
         * @return true if success
         */
        private boolean processByte(int b) {

            // Check here to avoid data buffer overflow if panel sends too much data
            if (dataLen >= MSG_MAX_LEN || rawLen >= MSG_MAX_LEN) {
                Log.print(Level.WARN, "Panel sent too much data: " + Tools.toHexString(rawData, 0, rawLen));
                return false;
            }

            //System.out.println(String.format("RCV: %02X ", b));
            rawData[rawLen++] = b;  // store byte so I can display raw data from panel in the event of an error

            // Start byte
            if (b == 0x7e)// 0x7e = start
            {
                if (dataLen != 0) {
                    // Received unexpected start byte then log warning and discard previous data
                    Log.print(Level.WARN, "Received unexpected start: " + Tools.toHexString(rawData, 0, rawLen));
                    return false;
                }
                byteStuff = false;  // ensure bytestuffing is off
                dataFromPanel[dataLen++] = b;  // save byte and increment length
                return true;
            }

            if (dataLen == 0) // Expected start byte but received something else
            {
                Log.print(Level.WARN, "Expected start byte: " + Tools.toHexString(rawData, 0, rawLen));
                return false;
            }

            // Byte-stuffing begin (0x7D), set flag, discard byte and continue
            if ((b == 0x7d) && (byteStuff == false)) {
                byteStuff = true;
                return true;
            }

            // Remove byte-stuffing from byte
            // Note: byte-stuffing applies to all bytes including the checksum bytes, excluding start byte
            if (byteStuff == true) {
                byteStuff = false;
                switch (b) {
                    // Convert [7D 5E]=>[7E]
                    case 0x5e:
                        b = 0x7e;
                        break;
                    // Convert [7D 5D]=>[7D]
                    case 0x5d:
                        b = 0x7d;
                        break;
                    default:
                        Log.print(Level.WARN, "Byte-stuff error: " + Tools.toHexString(rawData, 0, rawLen));
                        return false;
                }
            }

            // Length byte
            // Length byte is length of all bytes that follow except byte-stuff bytes and checksum bytes
            if (dataLen == 1) {
                expectLength = b + 4;  // + start, lengthbyte, cshi, cslo
                dataFromPanel[dataLen++] = b;  // save byte and increment length
                return true;
            }

            // Note: Sequence = Start, Length, Data..., CsHi, CsLo
            // if I made it here, then this is Data or CsHi or CsLo (not Start, not Length)
            // and all byte-stuffing has already been removed
            dataFromPanel[dataLen++] = b;  // save byte and increment length

            // if not a full packet (expecting more data) then wait for more data
            if (dataLen < expectLength) {
                return true;
            }

            // Now dataFromPanel contains a complete sequence (Start, Length, Data..., CsHi, CsLo)
            // So calculate and verify the checksum (over length byte and all data bytes)
            // get checksum of response and computed checksum for validation
            int responseChecksum = dataFromPanel[dataLen - 2] << 8 | dataFromPanel[dataLen - 1];
            int computedChecksum = calculateChecksum(dataFromPanel, 1, dataLen - 3);
            if (responseChecksum != computedChecksum) {
                Log.print(Level.WARN, "Bad checksum, discarding data: " + Tools.toHexString(rawData, 0, rawLen));
                return false;
            }

            // If panel requested an acknowledge then send ACK,
            // and clear the ack-request bit from the message-number byte
            dataFromPanel[2] &= 0xbf;  // Clear unused bit 6 (should be clear anyway)
            if (dataFromPanel[2] > 0x7f) {
                dataFromPanel[2] &= 0x7f;  // Clear the 'ack request' bit from the 'message number' byte
                // Send ACK
                sendMessageRaw(0x7E, 0x01, 0x1D, 0x1E, 0x1F);
            }

            // Create a new byte array 'message' containing only data (strip START,LENGTH,CSHI,CSLO)
            dataLen -= 4;
            int[] message = new int[dataLen];
            for (int i = 0; i < dataLen; i++) {
                message[i] = dataFromPanel[i + 2];
            }

            // Invoke the callback after ensuring someone is listening
            if (callBack == null) {
                throw new RuntimeException("No callback assigned to the serial receive event!");
            }

            if (callbackEnable) {
                switch (message[0]) {
                    case RESPONSE_COMMAND_COMPLETED:
                        //callBack.processAcknowledge();
                        break;
                    case RESPONSE_INTERFACE_CONFIG_MESSAGE:
                        callBack.processInterfaceConfiguration(message);
                        break;
                    case RESPONSE_ZONE_NAME_MESSAGE:
                        callBack.processZoneName(message);
                        break;
                    case RESPONSE_ZONE_STATUS_MESSAGE:
                        callBack.processZoneStatus(message);
                        break;
                    case RESPONSE_PARTITION_STATUS_MESSAGE:
                        callBack.processPartitionStatus(message);
                        break;
                    case RESPONSE_SYSTEM_STATUS_MESSAGE:
                        callBack.processSystemStatus(message);
                        break;
                    case RESPONSE_X10_MESSAGE:
                        callBack.processX10Received(message);
                        break;
                    case RESPONSE_LOG_EVENT_MESSAGE:
                        callBack.processLogEvent(message);
                        break;
                    case RESPONSE_PARTITION_SNAPSHOT_MESSAGE:
                        callBack.processPartitionSnapshot(message);
                        break;
                    case RESPONSE_ZONE_SNAPSHOT_MESSAGE:
                        callBack.processZoneSnapshot(message);
                        break;
                    case RESPONSE_KEYPAD_MESSAGE:
                        callBack.processKeypadMessage(message);
                        break;
                    case RESPONSE_PROGRAM_DATA_REPLY:
                        callBack.processProgramData(message);
                        break;
                    case RESPONSE_USER_INFORMATION_REPLY:
                        callBack.processUserInformation(message);
                        break;
                    case RESPONSE_COMMAND_ERROR:
                    case RESPONSE_COMMAND_FAILED:
                    //case RESPONSE_COMMAND_NOT_SUPPORTED:
                    //    callBack.processError(message);
                    //    break;
                    default:
                        callBack.processError(message);
                        break;
                }

                // If the received message matches the message being watched for then signal that it has been received
                // (by clearing the 'expect' variables) then continue to process the message normaly
                if (message[0] == expectResponse) {
                    boolean isZoneOrPartitionMessage = (
                            message[0] == REQUEST_ZONE_STATUS ||
                            message[0] == REQUEST_ZONE_NAME ||
                            message[0] == REQUEST_ZONE_SNAPSHOT ||
                            message[0] == REQUEST_PARTITION_STATUS ||
                            message[0] == REQUEST_PARTITION_SNAPSHOT);

                    if (!isZoneOrPartitionMessage || (message[1] == expectZoneOrPartition)) {
                        expectZoneOrPartition = 0;
                        expectResponse = RESPONSE_NONE;
                    }
                }
            } else {
                Log.print(Level.INFO, "Discarding message from panel");
            }

            // Done, discard data
            rawLen = 0;
            dataLen = 0;
            return true;
        }
    }
}
