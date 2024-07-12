package bungalowserver;

import bungalowserver.Log.Level;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetServer class providing network server functions.
 */
public class ClientListener implements Runnable {

    // Server port
    private static int serverPort = 11000;

    // Server socket
    private static ServerSocket serverSocket = null;

    // Holds the current client listener thread state
    private static boolean isRunning = false;

    // Using threadpool
    // Client threads run on a thread pool with max of 4 concurrent threads
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(4);

    /**
     * Constructor, initializes the NetServer
     * @param port Port to listen on
     * @throws java.lang.RuntimeException
     */
    public ClientListener(int port){
        // I generate the key once here in the client listener thread
        // Rather than doing it every time in the client handler thread
        // that this thread starts every time a client connects.
        serverPort = port;
    }

    /**
     * Is the client listener thread running?
     * @return True if running else false
     */
    //private synchronized boolean isRunning() {
    //    return isRunning;
    //}

    /**
     * Stops the client listener thread
     */
    public synchronized void stop(){
        isRunning = false;
        try {
            serverSocket.close();  // causes handled exception in listener thread
        } catch (IOException ex) {
            throw new RuntimeException("Error stopping server", ex);
        }
    }

    /**
     * Starts the client listener thread
     */
    public synchronized void start() {
        if (isRunning == false) {
            isRunning = true;
            new Thread(this, "NetServer").start();
        }
    }

    /**
     * This thread listens for client connections then
     * starts another thread to handle each client.
     */
    @Override
    public void run() {
        //synchronized(this){
        //    runningThread = Thread.currentThread();
        //}

        //Thread.currentThread().getId();

        // Begin listening for clients
        try {
            serverSocket = new ServerSocket(serverPort);
        } catch (IOException ex) {
            String errMsg = "Error listening on port " + serverPort + "! " + ex.getMessage();
            Log.print(Level.ERROR, errMsg);
            throw new RuntimeException(errMsg, ex);
        }
        Log.print(Level.INFO, "Listening on port " + serverPort + "...");

        while (isRunning) {
            Socket clientSocket = null;
            try {
                /**
                 * Wait here for a remote client to connect. accept() waits for
                 * a connection (blocking, queues up to 50 requests) When a
                 * connection is established, accept() returns a new
                 * communications socket.
                 */
                clientSocket = serverSocket.accept();  // Blocking
            } catch (IOException ex) {
                // If isRunning flag is false then the thread was stopped intentionally.
                // Otherwise the exception was due to an error.
                if (isRunning == false) {
                    continue;  // user called stop()
                }
                String errMsg = "Error accepting client connection! " + ex.getMessage();
                Log.print(Level.ERROR, errMsg);
                throw new RuntimeException(errMsg, ex);
            }

            // Start the client handler thread then go back to listening for new clients
            //new Thread(new NetHandler(clientSocket), "NetHandler").start(); // Use thread
            THREAD_POOL.execute(new ClientHandler(clientSocket));  // Use threadpool
        }
        // Stop all client threads then exit this listener thread
        THREAD_POOL.shutdownNow();  // Use threadpool
        Log.print(Level.WARN, "Client listener and all handler threads stopped");
    }
}