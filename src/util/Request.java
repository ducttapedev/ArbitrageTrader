package util;

import java.io.Serializable;

import exchange.Exchange;

public class Request implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 4839572470947688163L;
	/**
	 * 
	 */
	//private static final long serialVersionUID = 7924985068888885559L;
	
	String url;
	int exchangeID;
	boolean hasResponded = false;
	public String response;
	public String userData;
	//public Object userData;
	
	
	public Request(String url, int exchangeID, String userData) {
		this.url = url;
		this.exchangeID = exchangeID;
		this.userData = userData;
	}
	
	
	public String getResponse() {
		return response;
	}
	
	public String toString() {
		String result = String.format("Exchange = %-20s URL = %-20s hasResponded = %-6s", Exchange.getExchangeName(exchangeID), url, hasResponded+"");
		result += "response =\n" + response;
		return result;
	}
}


