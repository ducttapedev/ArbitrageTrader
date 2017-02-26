package arbitrage;

import java.util.ArrayList;
import java.util.List;

import exchange.Exchange;


public class Vertex {
	String coin;
	List<Edge> outwardList;
	boolean visited = false;
	
	/**
	 * How much of this coin we have in each exchange
	 */
	double[] funds = new double[Exchange.NUM_EXCHANGES];
	
	public Vertex(String data) {
		this.coin = data;
		outwardList = new ArrayList<Edge>();
		
		// mark funds as -1 to indicate they have not been set
		for(int i = 0; i < funds.length; i++) {
			funds[i] = -1;
		}
	}
	
	public void setFunds(double amount, int exchangeID) {
		funds[exchangeID] = amount;
	}
	
	@Override
	public boolean equals(Object o) {
		Vertex v = (Vertex)o;
		return coin.equals(v.coin);
	}
	
	@Override
	public int hashCode() {
		return coin.hashCode();
	}
}