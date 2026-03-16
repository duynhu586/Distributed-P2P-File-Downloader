import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Directory {

    // Default RMI port is 1099
    private static final int RMI_PORT = 1099;

    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("   SongSong - Directory RMI Server");
        System.out.println("===========================================");

        try {
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            System.out.println("[Directory] RMI Registry started on port " + RMI_PORT);

            DirectoryImpl directoryImpl = new DirectoryImpl();

            registry.rebind("DirectoryService", directoryImpl);

            System.out.println("[Directory] Service \"DirectoryService\" registered.");
            System.out.println("[Directory] Server is running. Press Ctrl+C to stop.");
            System.out.println("===========================================");

            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[Directory] ERROR starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
