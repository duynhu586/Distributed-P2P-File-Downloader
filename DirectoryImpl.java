import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class DirectoryImpl extends UnicastRemoteObject implements DirectoryInterface {
    private Map<String, List<String>> fileLocations;

    public DirectoryImpl() throws RemoteException {
        super();
        fileLocations = new HashMap<>();
    }

    @Override
    public synchronized void registerFiles(String clientAddress, List<String> fileNames) throws RemoteException {
        System.out.println("[Directory] Client registered: " + clientAddress);
        for (String fileName : fileNames) {
            List<String> clients = fileLocations.computeIfAbsent(fileName, k -> new ArrayList<>());
            if (!clients.contains(clientAddress)) { // avoid duplicate if Daemon restarts
                clients.add(clientAddress);
                System.out.println("  + File \"" + fileName + "\" -> " + clientAddress);
            } else {
                System.out.println("  ~ File \"" + fileName + "\" already registered for " + clientAddress + " (skipped)");
            }
        }
        printCurrentState();
    }

    @Override
    public synchronized List<String> getFileLocations(String fileName) throws RemoteException {
        List<String> locations = fileLocations.getOrDefault(fileName, new ArrayList<>());
        if (locations.isEmpty()) {
            System.out.println("[Directory] File \"" + fileName + "\" not found on any client.");
        } else {
            System.out.println("[Directory] File \"" + fileName + "\" is at: " + locations);
        }
        return new ArrayList<>(locations);
    }

    @Override
    public synchronized void unregisterClient(String clientAddress) throws RemoteException {
        System.out.println("[Directory] Client disconnected: " + clientAddress);
        for (Map.Entry<String, List<String>> entry : fileLocations.entrySet()) {
            entry.getValue().remove(clientAddress);
        }
        fileLocations.entrySet().removeIf(e -> e.getValue().isEmpty());
        System.out.println("[Directory] Removed client " + clientAddress + " from all lists.");
        printCurrentState();
    }

    private void printCurrentState() {
        System.out.println("[Directory] === Current State ===");
        if (fileLocations.isEmpty()) {
            System.out.println("  (No files registered yet)");
        } else {
            for (Map.Entry<String, List<String>> entry : fileLocations.entrySet()) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
        }
        System.out.println("[Directory] ================================");
    }
}
