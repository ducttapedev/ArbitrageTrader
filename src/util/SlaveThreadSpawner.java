package util;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * After calling <code>start()</code>, listens on a separate thread for slaves and attempts to connect with them.
 * @author Mike
 *
 */
public class SlaveThreadSpawner extends Thread {

    public static final int PORT = 60001;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket = null;
    
    //private List<Socket> socketList = Collections.synchronizedList( new ArrayList<Socket>() );
    
    /**
     * Maps IP address to slave data (socket, api call times)
     */
    //public Map<String, SlaveMeta> slaveMap = Collections.synchronizedMap( new HashMap<String, SlaveMeta>() );
    List<SlaveMeta> slaveList = new CopyOnWriteArrayList<SlaveMeta>();
    
    public void run() {
        
        //Socket socket = null;

        try {
            serverSocket = new ServerSocket(PORT);
            //serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            e.printStackTrace();

        }
        while (isRunning) {
        	// listen for a new socket
        	try {
        		// create the socket
            	Socket socket = serverSocket.accept();
            	
                slaveList.add(new SlaveMeta(socket));
                
            } catch (IOException e) {
            	e.printStackTrace();
            }
        }
        
        
    }
    
    public void kill() {
    	isRunning = false;
    	try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public int numSlaves() {
    	return slaveList.size();
    }
    
}