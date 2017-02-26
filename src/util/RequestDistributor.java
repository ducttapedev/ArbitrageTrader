package util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import exchange.Exchange;

public class RequestDistributor {
	
	private List<Request> requestList = new ArrayList<Request>();
	private final long maxWait;
	
	/**
	 * Maps exchange IDs to last api call times
	 *
	private Map<Integer, SlaveTime> lastAPICallList;
	*/
	
	
	private SlaveThreadSpawner spawner;
	
	public List<Request> getRequestList() {
		return requestList;
	}
	
	
	public RequestDistributor(SlaveThreadSpawner spawner, long maxWait) {
		this.spawner = spawner;
		this.maxWait = maxWait;
	}
	
	/**
	 * @param index
	 * @return the request at the given index
	 */
	public Request get(int index) {
		return requestList.get(index);
	}
	
	public void add(String url, int exchangeID, String userData) {
		requestList.add(new Request(url, exchangeID, userData));
	}
	
	public void add(Request r) {
		requestList.add(r);
	}
	
	public void addAll(List<Request> requestList) {
		for(Request r : requestList)
			add(r);
	}
	
	public boolean processRequests() {
		List<SlaveMeta> slaveList = spawner.slaveList;
		
		// list of requests that have yet to be processed because of API frequency restrictions
		List<Request> unprocessedRequests = new ArrayList<Request>(requestList);
		long start = System.currentTimeMillis();
		
		while(unprocessedRequests.size() > 0 && System.currentTimeMillis() - start < maxWait) {
		
			// set of all dead slaves to remove
			List<SlaveMeta> deadSlaveList = new ArrayList<SlaveMeta>();
			
			// reset all slaves, checking whether they are still alive
			for(int i = 0; i < slaveList.size(); i++) {
				SlaveMeta slaveMeta = slaveList.get(i);
				
				// reset and check if alive
				if(!slaveMeta.reset(maxWait)) {
					// if dead, add slave's ip to dead list
					deadSlaveList.add(slaveMeta);
				}
			}
			// remove dead slaves
			for(int i = 0; i < deadSlaveList.size(); i++) {
				SlaveMeta deadSlave = deadSlaveList.get(i);
				System.out.print("Removing dead slave (" + deadSlave.getIP() + ")...");
				System.out.println("Done");
				slaveList.remove(deadSlave);
			}
			
			
			
			/*
			 *  assign requests to slaves
			 */
			// create a list of all the slave metas
			List<SlaveMeta> slaveMetaList = new ArrayList<SlaveMeta>();
			slaveMetaList.addAll(slaveList);
			
			// assign each request
			for(Request request : unprocessedRequests) {
				
				// create a random priority for the slaves
				List<Integer> indexList = new ArrayList<Integer>();
				for(int i = 0; i < slaveMetaList.size(); i++) {
					indexList.add(i);
				}
				Collections.shuffle(indexList);
				
				// find a suitable slave
				for(int i = 0; i < indexList.size(); i++) {
					SlaveMeta slaveMeta = slaveMetaList.get( indexList.get(i) );
					
					// try to add the request to this slave, if successful break
					if(slaveMeta.addRequest(request)) {
						//requestAdded = true;
						break;
					}
				}
				
			}
			
			System.out.println("Starting threads");
			
			// starts and joins all the slave threads
			for(int i = 0; i < slaveMetaList.size(); i++) {
				slaveMetaList.get(i).start();
			}
			
			System.out.println("Joining threads");
			
			for(int i = 0; i < slaveMetaList.size(); i++) {
				SlaveMeta slaveMeta = slaveMetaList.get(i);
				
				// waited too long
				if(!slaveMeta.join(maxWait))
					return false;
			}
			
			// find all unprocessed requests
			List<Request> newUnprocessedRequests = new ArrayList<Request>();
			for(Request request : unprocessedRequests) {
				if(!request.hasResponded) {
					newUnprocessedRequests.add(request);
					System.out.println("No response for " + request);
				}
			}
			
			
			unprocessedRequests = newUnprocessedRequests;
			
		}
		
		return true;
		
	}
	
	
	public static void main(String[] args) {
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 1);
		
		System.out.println("numSlaves = " + sts.numSlaves());
		
		for(int i = 0; i < 3; i++) {
		
			RequestDistributor rd = new RequestDistributor(sts, 5000);
			rd.add("http://phlandis.com/", 0, null);
			rd.add("http://phlandis.com/doge/", 0, null);
			System.out.println("successfully processed requests = " + rd.processRequests());
			
			for(Request request : rd.requestList) {
				System.out.println(request.url);
				System.out.println(Exchange.getExchangeName(request.exchangeID));
				System.out.println(request.response);
				System.out.println();
			}
		}
	}
	
}
