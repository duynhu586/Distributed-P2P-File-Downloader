import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface DirectoryInterface extends Remote {

    /**
     * Register the list of files that a client owns.
     *
     * @param clientAddress Address of the client (e.g. "192.168.1.5:5000")
     * @param fileNames     List of file names the client has
     */
    void registerFiles(String clientAddress, List<String> fileNames) throws RemoteException;

    /**
     * Get the list of clients that have a specific file.
     *
     * @param fileName Name of the file to look up (e.g. "movie.mp4")
     * @return List of client addresses that have the file
     */
    List<String> getFileLocations(String fileName) throws RemoteException;

    /**
     * Unregister a client (when it disconnects).
     *
     * @param clientAddress Address of the client to remove
     */
    void unregisterClient(String clientAddress) throws RemoteException;
}
