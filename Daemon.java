import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

public class Daemon {

    private static final String SHARED_FOLDER = "shared";

    private static int TCP_PORT = 5000;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("   SongSong - Daemon Server");
        System.out.println("===========================================");

      
        String directoryIP = (args.length > 0) ? args[0] : "localhost";
        System.out.println("[Daemon] Connecting to Directory at: " + directoryIP);

        try {
      
            List<String> fileNames = scanSharedFolder();
            System.out.println("[Daemon] Total files found: " + fileNames.size());

            String myIP = (args.length > 1) ? args[1] : InetAddress.getLocalHost().getHostAddress();

            if (args.length > 2) {
                TCP_PORT = Integer.parseInt(args[2]);
        }
            
            String myAddress = myIP + ":" + TCP_PORT;
            System.out.println("[Daemon] This machine's address: " + myAddress);

            registerWithDirectory(directoryIP, myAddress, fileNames);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Registry registry = LocateRegistry.getRegistry(directoryIP, 1099);
                    DirectoryInterface directory =
                            (DirectoryInterface) registry.lookup("DirectoryService");

                    directory.unregisterClient(myAddress);

                    System.out.println("[Daemon] Unregistered from Directory");

                } catch (Exception e) {
                    System.err.println("[Daemon] Failed to unregister: " + e.getMessage());
                }
            }));
            startTCPServer();

        } catch (Exception e) {
            System.err.println("[Daemon] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<String> scanSharedFolder() {
        List<String> fileNames = new ArrayList<>();
        File folder = new File(SHARED_FOLDER);

        if (!folder.exists()) {
            folder.mkdir();
            System.out.println("[Daemon] Created folder \"" + SHARED_FOLDER + "\" (no files yet).");
        }

        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) { 
                    fileNames.add(f.getName());
                    System.out.println("[Daemon] Found file: " + f.getName()
                            + " (" + f.length() + " bytes)");
                }
            }
        }

        return fileNames;
    }

    private static void registerWithDirectory(String directoryIP, String myAddress, List<String> fileNames) {
        if (fileNames.isEmpty()) {
            System.out.println("[Daemon] No files to register. Skipping registration step.");
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry(directoryIP, 1099);

            DirectoryInterface directory = (DirectoryInterface) registry.lookup("DirectoryService");

            directory.registerFiles(myAddress, fileNames);

            System.out.println("[Daemon] Successfully registered " + fileNames.size()
                    + " file(s) with Directory at " + directoryIP);

        } catch (Exception e) {
            System.err.println("[Daemon] WARNING: Could not register with Directory: " + e.getMessage());
            System.err.println("[Daemon] Daemon will still start the TCP server...");
        }
    }

    private static void startTCPServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(TCP_PORT);
        System.out.println("[Daemon] TCP Server listening on port " + TCP_PORT);
        System.out.println("[Daemon] Ready to receive download requests. Press Ctrl+C to stop.");
        System.out.println("===========================================");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            String clientInfo = clientSocket.getInetAddress().getHostAddress()
                    + ":" + clientSocket.getPort();
            System.out.println("[Daemon] New connection from: " + clientInfo);

            Thread handlerThread = new Thread(() -> handleRequest(clientSocket));
            handlerThread.setDaemon(true); 
            handlerThread.start();
        }
    }

    private static void handleRequest(Socket socket) {
        String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            OutputStream outputStream = socket.getOutputStream();

            String request = reader.readLine();
            if (request == null || request.trim().isEmpty()) {
                System.err.println("[Daemon] Received empty request from " + clientInfo);
                return;
            }

            System.out.println("[Daemon] Request from " + clientInfo + ": " + request);

            String[] parts = request.trim().split("\\s+");

            if (parts[0].equalsIgnoreCase("SIZE") && parts.length == 2) {
 
                String fileName = parts[1];
                File f = new File(SHARED_FOLDER + File.separator + fileName);
                String response;
                if (f.exists()) {
                    response = String.valueOf(f.length());
                    System.out.println("[Daemon] SIZE request for \"" + fileName + "\": " + response + " bytes");
                } else {
                    response = "-1";  // -1 means file not found
                    System.err.println("[Daemon] SIZE request failed: file not found: " + fileName);
                }
                PrintWriter pw = new PrintWriter(outputStream, true);
                pw.println(response);

            } else if (parts[0].equalsIgnoreCase("GET") && parts.length == 4) {
                String fileName = parts[1];
                long offset = Long.parseLong(parts[2]);
                long length = Long.parseLong(parts[3]);  // long, not int
                sendFileFragment(fileName, offset, length, outputStream);

            } else {
                System.err.println("[Daemon] Invalid request format: " + request);
            }

        } catch (Exception e) {
            System.err.println("[Daemon] Error handling request from " + clientInfo + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Read and send a fragment of a file.
     *
     * @param fileName     Name of the file in the shared/ folder
     * @param offset       Start position to read from (which byte)
     * @param length       Number of bytes to read and send
     * @param outputStream Stream to write data back to the Download client
     */
    private static void sendFileFragment(String fileName, long offset, long length,
            OutputStream outputStream) {
        File file = new File(SHARED_FOLDER + File.separator + fileName);

        if (!file.exists()) {
            System.err.println("[Daemon] File not found: " + fileName);
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);

            // Use a fixed 64KB buffer and loop — never allocate the whole fragment at once.
            // raf.read() is NOT guaranteed to fill the buffer in one call,
            // so we must loop until all requested bytes are sent.
            byte[] buffer = new byte[65536]; // 64 KB chunks
            long remaining = length;
            long totalSent = 0;

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = raf.read(buffer, 0, toRead);
                if (bytesRead == -1) break; // reached end of file
                outputStream.write(buffer, 0, bytesRead);
                // Thread.sleep(50);
                remaining -= bytesRead;
                totalSent += bytesRead;
            }

            outputStream.flush();
            System.out.println("[Daemon] Sent " + totalSent + " bytes of \""
                    + fileName + "\" (offset=" + offset + ")");

        } catch (Exception e) {
            System.err.println("[Daemon] Error reading/sending file " + fileName + ": " + e.getMessage());
        }
    }
}
