package util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import exchange.Exchange;

public class SlaveThread extends Thread {
    //private List<Request> requestList = new ArrayList<Request>();
	private List<Request> requests = new ArrayList<Request>();
	
	/**
	 * True if there is at least one request for this exchange
	 */
	private boolean[] exchangeRequested = new boolean[Exchange.NUM_EXCHANGES];
	
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private long maxWait;
    
    private boolean errorDetected = false;
    
    /*
    public SlaveThread(SlaveMeta slaveMeta) {
    	this.in = slaveMeta.in;
    	this.out = slaveMeta.out;
    }
	*/
    public SlaveThread(ObjectInputStream in, ObjectOutputStream out, long maxWait) {
    	this.in = in;
    	this.out = out;
    	this.maxWait = maxWait;
    }
    /*
    public void addRequest(Request request) {
    	requestList.add(request);
    }
	*/
    public boolean addRequest(Request request) {
    	int exchangeID = request.exchangeID;
		
    	// if exchange doesn't exist, fail
    	if(exchangeID >= exchangeRequested.length)
    		return false;
    	
    	// if a request already exists and the delay is positive, fail
    	if(Exchange.minDelay[exchangeID] > 0 && exchangeRequested[exchangeID])
    		return false;
    	
    	// add request
    	requests.add( request );
    	exchangeRequested[exchangeID] = true;
    	
    	return true;
    }
    
    public void run() {
    	
    	try {
			// send the request
    		out.writeLong(maxWait);
    		out.writeObject(requests);
    		
    		// read the response and write the result
			List<Request> responses = (List<Request>) in.readObject();
			assert requests.size() == responses.size();
			
			for(int i = 0; i < requests.size(); i++) {
				Request request = requests.get(i);
				Request response = responses.get(i);
				
				// must copy fields because we want to affect each request, not just the array
				if(request != null) {
					request.response = response.response;
					request.hasResponded = response.hasResponded;
					
					if(request.hasResponded && request.response == null) {
						System.out.println("No response, setting hasResponded false for: " + request);
						request.hasResponded = false;
					}
						
				}
			}
			
			// TODO: process responses
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			errorDetected = true;
			e.printStackTrace();
		}
    }
    
    
    public boolean errorDetected() {
    	return errorDetected;
    }
    
}