import java.net.Socket;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class DownloadWorker implements Runnable {

    private String clientAddress;
    private String filename;
    private long offset;
    private long length;
    private String outputFile;
    private boolean success = false;

    public DownloadWorker(String clientAddress, String filename,
                          long offset, long length, String outputFile) {
        this.clientAddress = clientAddress;
        this.filename = filename;
        this.offset = offset;
        this.length = length;
        this.outputFile = outputFile;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = connectToClient();
            requestFragment(socket);
            receiveFragment(socket);
            success = true;
            System.out.println("Fragment downloaded from " + clientAddress+ " Fragment downloaded: offset=" + offset + " length=" + length);
        } catch (Exception e) {
            System.out.println("Worker failed from " + clientAddress);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private Socket connectToClient() throws Exception {
        String[] parts = clientAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        return new Socket(host, port);
    }

    private void requestFragment(Socket socket) throws Exception {
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        String request = "GET " + filename + " " + offset + " " + length;

        out.println(request);
    }

    private void receiveFragment(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        RandomAccessFile file = new RandomAccessFile(outputFile, "rw");

        try {
            file.seek(offset);

            byte[] buffer = new byte[65536]; // 64KB chunks
            long remaining = length;

            while (remaining > 0) {
                int bytesRead = in.read(
                        buffer,
                        0,
                        (int) Math.min(buffer.length, remaining)
                );
                if (bytesRead == -1) {
                    throw new IOException("Daemon disconnected");
             }
                file.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            if (remaining > 0) {
                throw new IOException("Fragment incomplete");
        }
        } finally {
            file.close(); // always close, even if an exception occurs
        }
    }
}