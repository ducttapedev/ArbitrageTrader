package util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import exchange.Exchange;

public class SlaveMeta {
	 private Socket socket;
	 private long[] lastCallTime = new long[Exchange.NUM_EXCHANGES];
	 
	 private ObjectInputStream in;
	 private ObjectOutputStream out;
	 
	 private SlaveThread slaveThread;
	 private String ip;
	 
	 public SlaveMeta(Socket socket) throws IOException {
		 this.socket = socket;
		 
		 // initialize com streams
		 out = new ObjectOutputStream(socket.getOutputStream());
		 in = new ObjectInputStream(socket.getInputStream());
		 
		 ip = socket.getInetAddress().toString();
		 // initialize call times
		 // default to 0
	 }
	 
	 /**
	 * @param exchangeID
	 * @return true if the slave associated with this object is ready to send an API request to <code>exchangeID</code>
	 * (equivalently, if (current time - last call time for this exchange) > minimum delay between calls for this exchange 
	 */
	private boolean isAvailable(int exchangeID) {
		 return (System.currentTimeMillis() - lastCallTime[exchangeID]) > Exchange.minDelay[exchangeID];
	}
	
	public boolean addRequest(Request request) {
		if(!isAvailable(request.exchangeID))
			return false;
		
		return slaveThread.addRequest(request);
	}
	
	/**
	 * Resets the slave thread so we can process more requests
	 * @param maxWait
	 * @return true if the connection is still valid
	 */
	public boolean reset(long maxWait) {
		// detect socket problems
		if( !socket.isBound() || !socket.isConnected() || socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown() )
			return false;
		
		// detect other slave thread problems
		if(slaveThread != null && slaveThread.errorDetected())
			return false;
		
		slaveThread = new SlaveThread(in, out, maxWait);
		return true;
			
	}
	
	public void start() {
		slaveThread.start();
	}
	
	/**
	 * @param millis
	 * @return true if the slave thread successfully completed
	 */
	public boolean join(long millis) {
		try {
			slaveThread.join(millis);
		} catch (InterruptedException e) {
			return false;
		}
		
		if(slaveThread.isAlive())
			return false;
		
		return true;
	}
	
	public String getIP() {
		return ip;
	}
	
	@Override
	public boolean equals(Object o) {
		SlaveMeta sm = (SlaveMeta)o;
		return ip.equals(sm.ip);
	}

}
