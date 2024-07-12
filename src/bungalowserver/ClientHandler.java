package bungalowserver;

import bungalowserver.Log.Level;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Thread to handle a single client connection
 */
public class ClientHandler implements Runnable {

    /**
     * Authentication timeout: After a client connects, it must send an
     * authentication request within this time or server will disconnect.
     * Timeout is in milliseconds.
     */
    private static final int AUTH_TIMEOUT_MS = 1500;

    /**
     * Idle timeout: If a client does not send any requests for this time
     * the server will disconnect.
     * Timeout is in seconds.
     */
    private static final int IDLE_TIMEOUT = 90;  // 90 seconds

    private Socket clientSocket = null;
    private BufferedReader reader = null;
    private PrintWriter writer = null;

    /**
     * Constructor
     * @param clientSocket client socket
     */
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    /**
     * Sets the client socket timeout
     * @param milliseconds time in milliseconds
     * @return true if success
     */
    private boolean setSocketTimeout(int milliseconds) {
        try {
            clientSocket.setSoTimeout(milliseconds);
        } catch (SocketException ex) {
            Log.print(Level.WARN, "Error setting socket timeout! " + ex.getMessage());
            return false;
        }
        return true;
    }

    private String getClientIp() {
        return clientSocket.getInetAddress().toString().replace('/', ' ').trim();
        //clientSocket.getRemoteSocketAddress()
    }

    private boolean authenticateClient() {
        Log.print(Level.INFO, "Connection from " + getClientIp() + ", authenticating...");

        // Give the client AUTH_TIMEOUT_MS milliseconds to provide the authentication request
        if (!setSocketTimeout(AUTH_TIMEOUT_MS)) {
            return false;
        }

        String message;
        try {
            // readline() waits for data from the client (blocking).
            // Blocks for soTimeout milliseconds (or indefinitely if soTimeout=0)
            // Returns null if the client closed its output stream
            // Throws IOExeption if this server reader is closed
            // Throws SocketTimeoutException if timeout expired with no data.
            message = reader.readLine();
        } catch (SocketTimeoutException ex) {
            Log.print(Level.WARN, "Client did not send authentication request within the allowed time! " + ex.getMessage());
            return false;
        } catch (IOException ex) {
            Log.print(Level.WARN, "Error receiving data from client, reader closed! " + ex.getMessage());
            return false;
        }

        if (message == null) {
            Log.print(Level.WARN, "Error receiving data from client, client closed the connection!");
            return false;
        }

        // Decrypt message (returns null if client sends invalid data)
        message = Crypto.decrypt(message);
        if (message == null) {
            Log.print(Level.WARN, "Client failed authentication, invalid message!");
            return false;
        }

        if (message.equals("AuthRequest") == false) {
            Log.print(Level.WARN, "Client failed authentication! Client sent: " + message);
            return false;
        }

        writer.println(Crypto.encrypt("AuthGrant"));

        // Set timeout to disconnect the client if it sends
        // no requests for IDLE_TIMEOUT milliseconds
        if (!setSocketTimeout(IDLE_TIMEOUT * 1000)) {
            return false;
        }

        Log.print(Level.INFO, "Client " + getClientIp() + " is authenticated.");

        //Speaker.getInstance().speak(31);  // speak client authenticated
        return true;
    }

    /**
     * Close the socket and streams
     */
    private void close() {
        Log.print(Level.INFO, "Closing client connection...");
        //Speaker.getInstance().speak(30);
        try {
            reader.close();
            writer.close();
            clientSocket.close();
        } catch (IOException ex) {
            Log.print(Level.WARN, "Exception on socket close: " + ex.toString(), false);
        }
    }

    /**
     * Client handler, runs on separate thread
     */
    @Override
    public void run() {
        String encryptedData, decryptedData;

        // New client connected, create the reader/writer streams
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException ex) {
            Log.print(Level.ERROR, "Failed socket stream creation! " + ex.getMessage());
            close();
            return;
        }

        // Authenticate client, if fails then this thread will end
        if (authenticateClient() == false) {
            close();
            return;
        }

        // Keep handling client requests until client disconnects or idle timeout
        while (true) {
            try {
                // readline() waits for data from the client (blocking).
                // Blocks for soTimeout milliseconds (or indefinitely if soTimeout=0)
                // Returns null if the client closed its output stream
                // Throws IOExeption if this server reader is closed
                // Throws SocketTimeoutException if timeout expired with no data.
                encryptedData = reader.readLine();
            } catch (java.net.SocketTimeoutException ex) {
                // If client has been idle for IDLE_TIMEOUT then disconnect
                Log.print(Level.INFO, "Disconnecting idle client...");
                break;
            } catch (IOException ex) {
                Log.print(Level.WARN, "Error receiving message from client, disconnecting... " + ex.getMessage());
                break;
            }

            if (encryptedData == null) {
                Log.print(Level.WARN, "Client closed the connection, disconnecting...");
                break;
            }

            // Decrypt message (returns null if client sends invalid data)
            decryptedData = Crypto.decrypt(encryptedData);
            if (decryptedData == null) {
                // Invalid data received from client
                // Client is not sending Base64 data or is sending data that is too short to contain IV, etc
                // So discard the data and disconnect the misbehaving client
                Log.print(Level.WARN, "Received bad message from client, disconnecting...");
                return;
            }

            Log.print(Level.INFO, "Received message from client: " + decryptedData);

            if (processReceivedData(decryptedData) == false) {
                Log.print(Level.WARN, "Failed sending message to client, disconnecting...");
                break;
            }
        }

        close();
        // This thread will now end
    }

    /**
     * Handles data received from the client
     * @return false if a socket error occurred while sending data to the client
     * else returns true (the command can fail, for example arm-stay can fail
     * and true will still be returned as long as the response was successfully
     * sent to the client).
     * @param packet data packet from client
     */
    private boolean processReceivedData(String packet) {

        //<editor-fold defaultstate="collapsed" desc="Arm stay (STY)">
        // Client sent "STY"
        // Arms in STAY mode, if already armed in STAY then toggles between DELAYED and INSTANT modes
        if (packet.equals("STY")) {
            if (VirtualPanel.getInstance().armStay(null, true)) {
                return sendQueryResponse(false);
            }
            return netSend("STY=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Arm away (AWY)">
        // Client sent "AWY", Arms in AWAY mode
        if (packet.equals("AWY")) {
            if (VirtualPanel.getInstance().armAway(null, true)) {
                return sendQueryResponse(false);
            }
            return netSend("AWY=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Disarm (DIS=)">
        // Format = "DIS=n" where n = 4 or 6 digit disarm code
        if (packet.startsWith("DIS=")) {
            if (packet.length() > 3) {
                if (VirtualPanel.getInstance().disarm(packet.substring(4), true)) {
                    return sendQueryResponse(false);
                }
            }
            return netSend("DIS=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Email settings change (EMS=)">
        if (packet.startsWith("EMS=")) {
            if (Emailer.changeEmailSettings(packet)) {
                return sendQueryResponse(false);
            }
            return netSend("EMS=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Speak (SAY=)">
        // format: SAY=vt, where v = volume(base80 0-20), t = phrase id (base80, 0-79)
        if (packet.startsWith("SAY=")) {
            if (Speaker.speakClientPhrase(packet)) {
                return netSend("SAY=OK");
            }
            return netSend("SAY=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Activate alarm (ALM)">
        else if (packet.equals("ALM")) {
            Speaker.speak(7, 32);  // "Warning, the alarm was activated remotely."
            if (VirtualPanel.getInstance().panicPolice(true)) {
                return netSend("ALM=OK");
            }
            return netSend("ALM=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Query (QRY)">
        if (packet.equals("QRY")) {
            return sendQueryResponse(false);
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Query All (QRA)">
        if (packet.equals("QRA")) {
            return sendQueryResponse(true);
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Zone type change (ZTC=)">
        if (packet.startsWith("ZTC=")) {
            //if (Zones.getInstance().changeZoneType(packet)) {
            if (Zones.changeZoneType(packet)) {
                return sendQueryResponse(false);
            }
            return netSend("ZTC=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Email test (EMT)">
        if (packet.equals("EMT")) {
            if ((Emailer.sendEmail("BURGLARY") == false) ||
                    (Emailer.sendEmail("FIRE") == false)) {
                System.out.println("Error sending email, see the log file for more info.");
                Speaker.speak(27, 26);
                return netSend("EMT=ER");
            }
            return netSend("EMT=OK");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Reboot">
        // Server always responds with BOO=OK before rebooting
        if (packet.equals("RBT")) {
            netSend("RBT=OK");
            Speaker.speak(27, 33);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ex) {
            }
            reboot();  // does not return
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Rule add (RUA=)">
        if (packet.startsWith("RUA=")) {
            //if (Rules.getInstance().addRule(packet)) {
            if (Rules.addRule(packet)) {
                return sendQueryResponse(false);
            }
            return netSend("RUL=ER");
        } //</editor-fold>

        //<editor-fold defaultstate="collapsed" desc="Rule remove (RUR=)">
        // "RUL-n" where n = rule index to remove (0 = first rule)
        if (packet.startsWith("RUR=")) {
            //if (Rules.getInstance().removeRule(packet)) {
            if (Rules.removeRule(packet)) {
                return sendQueryResponse(false);
            }
            return netSend("RUL=ER");
        }
        //</editor-fold>

        return netSend("ERR");
    }

    /**
     * Stop application and shutdown the computer
     */
    private static void reboot() {
        String command;
        String os = System.getProperty("os.name");

        switch (os) {
            case "Linux":
            case "Mac OS X":
                command = "shutdown -r now";
                break;
            case "Windows":
                command = "shutdown.exe -r -t 0";
                break;
            default:
                throw new RuntimeException("Unsupported operating system (" + os + ")!");
        }

        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException ex) {
            throw new RuntimeException("Error shutting down system! " + ex.getMessage());
        }

        System.exit(0);
    }

    /**
     * Sends a query response to the client
     * @return true if success, false if error
     */
    private boolean sendQueryResponse(boolean isQueryAll) {
        StringBuilder sb = new StringBuilder();

        if (isQueryAll) {
            sb.append("QRA=");
            sb.append(Emailer.getSettingsForClient()).append("$");
            sb.append(Zones.getNamesAndTypesForClient()).append("$");
            sb.append(Speaker.getPhrasesForClient()).append("$");
            sb.append(Rules.getRulesForClient()).append("$");
        } else {
            sb.append("QRY=");
        }

        sb.append(VirtualPanel.getInstance().getSecurityStatusForClient()).append("$");
        sb.append(Zones.getStatusForClient()).append("$");
        sb.append(EventLog.getLogForClient());

        return netSend(sb.toString());
    }

    /**
     * Call this to send data to the client over the network
     * @param data
     * @return true if success, false if error
     */
    private boolean netSend(String message) {
        // Log unencrypted data
        Log.print(Level.INFO, "NetSend: " + message, false);

        // Begin sending the data to the client
        //writer.write(message + (char)0x0a);  // or "\n";
        writer.println(Crypto.encrypt(message));  // or "\n";

        // Check for errors
        return !writer.checkError();
    }
}

// Notes on socket streams
// Closing the input stream or outputstream or socket auto-closes the other two.
// If multiple streams are chained together then closing the outermost
// stream will close all of the underlying streams. Also, closing the
// input stream also closes the underlying socket connection.
// (so the socket close is probably not needed).
// Once a socket is closed it can not be reused, a new socket must be created.
//
// Notes:
// BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
// PrintWriter writer3b = new PrintWriter(clientSocket.getOutputStream(), true);
// PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)), true);
// BufferedWriter writer2 = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
// PrintWriter writer3 = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
//
// note: printwriter auto-flushes he output when println() is called.
// printwriter swallows exeptions, need to call checkerror()
// OutputStreamWriter writer4 = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8);
// writer4.write("hello");
