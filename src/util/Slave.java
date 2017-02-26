package util;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import exchange.Exchange;

public class Slave extends Thread {
	

    private static final int PORT = SlaveThreadSpawner.PORT;
	private String hostname;
	private PrintWriter logWriter;
	
	private Socket socket;
	private ObjectInputStream in;
    private ObjectOutputStream out;
    
    private int timeout = 15;
    
	
	public Slave(String hostname, File logfile) throws FileNotFoundException {
		super();
		logWriter = new PrintWriter(logfile);
		this.hostname = hostname;
		
	}
	
	public void writeError(Exception e) {
		e.printStackTrace(logWriter);
		logWriter.write("\n");
		logWriter.flush();
	}
	
	public void write(String s) {
		logWriter.write(s);
		logWriter.flush();
	}
	
	@Override
	public void run() {
		
		long start = System.currentTimeMillis();
		
		// initialize socket
		while(true) {
			System.out.println( ">>>>" + (System.currentTimeMillis() - start)*1e-3 );
			
			try {
				System.out.print("Connecting to host...");
				socket = new Socket(hostname, PORT);
				socket.setKeepAlive(true);
				socket.setSoTimeout(timeout*1000);
				
				
				out = new ObjectOutputStream(socket.getOutputStream());
				in = new ObjectInputStream(socket.getInputStream());
				System.out.println("Done");
				break;
			} catch (IOException e) {
				System.out.println("Error");
				
			}
		}
		
		while(true) {
			System.out.println( ">>>>" + (System.currentTimeMillis() - start)*1e-3 );
			
			try {
				sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				writeError(e);
			}
			
			
			// initialize the socket and connect with the host
			if(socket.isClosed()) {
				try {
					System.out.print("Connecting to host...");
					socket = new Socket(hostname, PORT);
					socket.setKeepAlive(true);
					socket.setSoTimeout(timeout*1000);
					
					out = new ObjectOutputStream(socket.getOutputStream());
					in = new ObjectInputStream(socket.getInputStream());
					System.out.println("Done");
				} catch (IOException e) {
					System.out.println("Error");
					//writeError(e);
					continue;
				}
			}
			
			// if there are requests, process them
			try {
				
				//if(in.available() > 0) {
					// read in the max wait time and the requests
					long maxWait = in.readLong();
					List<Request> requests = (List<Request>) in.readObject();
					
					List<Thread> threadList = new ArrayList<Thread>();
					
					// spawn a separate thread for each request
					for(Request request : requests) {
						if(request == null)
							continue;
						
						final Request requestCopy = request;
						
						// create a new thread for each API request and start it
						Thread t = new Thread() {
							public void run() {
								processRequest(requestCopy);
							}
						};
						threadList.add(t);
						t.start();
					}
					
					
					
					// wait for each request to complete
					for(Thread thread : threadList) {
						thread.join(maxWait);
					}
					
					for(Request r : requests)
						write( r.toString() + "\n\n\n" );
					
					// send the request with responses back
					out.writeObject(requests);
				//}
			} catch(SocketTimeoutException e) {
				try {
					System.out.print("No requests for " + timeout + " seconds, closing connection...");
					socket.close();
					System.out.println("Done");
				} catch (IOException e1) {
					System.out.println("Error");
					writeError(e1);
				}
				continue;
			} catch (ClassNotFoundException | IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				try {
					System.out.print("Error, closing connection...");
					socket.close();
					System.out.println("Done");
				} catch (IOException e1) {
					System.out.println("Error");
					writeError(e1);
				}
				continue;
			} catch(Exception e) {
				writeError(e);
			}
			
	
		}
	}
	
	
	/**
	 * Makes the API call and stores the result in r.response
	 * @param r
	 */
	public void processRequest(Request r) {
		System.out.println("Processing request for exchange " + Exchange.getExchangeName(r.exchangeID) + ": " + r.url);
		try {
			r.response = Website.getHTML(r.url);
			r.hasResponded = true;
		} catch(Exception e) {
			writeError(e);
			r.response = null;
		}
		
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		Slave s = new Slave("graceyunchao.mooo.com", new File("slave_log_" + General.getDate() + ".txt"));
		//Slave s = new Slave("http://192.168.1.200:" + PORT + "/");
		s.start();
	}
	
}
