package market;

import exchange.Exchange;

public class Order implements Comparable<Order> {
	public double price, volume, volBTC;
	public int exchangeID;
	
	public Order(Order o) {
		price = o.price;
		volume = o.volume;
		exchangeID = o.exchangeID;
	}
	
	/**
	 * @param price
	 * @param volume
	 */
	public Order(double price, double volume, int exchangeID) {
		this.price = price;
		this.volume = volume;
		this.exchangeID = exchangeID;
	}

	@Override
	public int compareTo(Order o) {
		// TODO Auto-generated method stub 
		if(price > o.price)
			return 1;
		if(price < o.price)
			return -1;
		return 0;
	}
	
	@Override
	public String toString() {
		return String.format("%20.10f    (%20.8f = %20.8f BTC) at %s", price, volume, volBTC, Exchange.getExchangeName(exchangeID));
	}
}
