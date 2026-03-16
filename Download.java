import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Download {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Download <filename> [directory_host]");
            System.out.println("  directory_host defaults to localhost");
            return;
        }

        String filename = args[0];
        String directoryHost = (args.length > 1) ? args[1] : "localhost";

        try {
            long startTime = System.currentTimeMillis(); 
            DirectoryInterface directory = connectToDirectory(directoryHost);

            List<String> clients = getFileSources(directory, filename);

            if (clients.isEmpty()) {
                System.out.println("No sources found.");
                return;
            }

            prepareDownloadFolder();

            long totalFileSize = getFileSize(clients.get(0), filename);
            if (totalFileSize <= 0) {
                System.out.println("Could not get file size from daemon. File may not exist.");
                return;
            }

            System.out.println("File size: " + totalFileSize + " bytes");
            String outputPath = "downloads/" + filename;

            createOutputFile(outputPath, totalFileSize);
            startParallelDownload(clients, filename, totalFileSize, outputPath);

            long endTime = System.currentTimeMillis(); 
            System.out.println("Download complete.");
            System.out.println("Download time: " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static DirectoryInterface connectToDirectory(String host) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host);

        return (DirectoryInterface) registry.lookup("DirectoryService");
    }

    private static List<String> getFileSources(DirectoryInterface directory, String filename)
            throws Exception {
        List<String> clients = directory.getFileLocations(filename);
        System.out.println("Sources found: " + clients);

        return clients;
    }

    private static void prepareDownloadFolder() {
        File folder = new File("downloads");

        if (!folder.exists()) {
            folder.mkdir();
        }
    }

    private static long getFileSize(String clientAddress, String filename) {
        // Ask a Daemon via TCP: "SIZE filename" → returns the size as a number
        try {
            String[] parts = clientAddress.split(":");
            Socket socket = new Socket(parts[0], Integer.parseInt(parts[1]));

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("SIZE " + filename);
            String response = in.readLine();
            socket.close();

            return Long.parseLong(response.trim());
        } catch (Exception e) {
            System.err.println("Failed to get file size: " + e.getMessage());
            return -1;
        }
    }

    private static void createOutputFile(String path, long size) throws Exception {
        RandomAccessFile file = new RandomAccessFile(path, "rw");
        file.setLength(size);

        file.close();
    }

    private static void startParallelDownload(
            List<String> clients,
            String filename,
            long totalFileSize,
            String outputPath
    ) throws Exception {
        int sources = clients.size();
        long fragmentSize = totalFileSize / sources;

        ExecutorService pool = Executors.newFixedThreadPool(sources);
        Collections.shuffle(clients);

for (int i = 0; i < sources; i++) {

    long offset = i * fragmentSize;

    long length = (i == sources - 1)
            ? totalFileSize - offset
            : fragmentSize;

    String assignedClient = clients.get(i);  

    pool.execute(() -> {

        boolean success = false;

        List<String> retryList = new java.util.ArrayList<>(clients);

        retryList.remove(assignedClient);
        retryList.add(0, assignedClient);  // try assigned first

        for (String client : retryList) {

            DownloadWorker worker = new DownloadWorker(
                    client,
                    filename,
                    offset,
                    length,
                    outputPath
            );

            worker.run();

            if (worker.isSuccess()) {
                success = true;
                break;
            }

            System.out.println("Retrying fragment from another daemon...");
        }

        if (!success) {
            System.out.println("Fragment failed: offset=" + offset);
        }

    });
}
        pool.shutdown();
        while (!pool.isTerminated()) {
            Thread.sleep(500);
        }
    }
}