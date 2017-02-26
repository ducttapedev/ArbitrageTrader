package market;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import arbitrage.ArbitrageFinder;
import exchange.Exchange;



public class Transfer implements Comparable<Transfer> {
	
	/**
	 * How much people are willing to sell for (larger)
	 * How much we can buy for
	 */
	//private List<Order> askList = new ArrayList<Order>();
	/**
	 * How much people are willing to pay (smaller)
	 * How much we can sell for
	 */
	//private List<Order> bidList = new ArrayList<Order>();
	
	//private Order lowestAsk;
	//private Order highestBid;
	
	protected List<Order> orderList = new ArrayList<Order>();
	public final CoinPair key;
	
	/**
	 * Conversion rate from 1st coin to 2nd coin, averaged between lowestAsk and highestBid
	 */
	protected final double conversionRate;
	
	
	public Transfer(CoinPair key) {
		this.key = key;
		conversionRate = -1;
	}
	

	/**
	 * Creates the <code>Transfer</code> object, which holds all the orders available to transfer from one currency to another
	 * @param orderList
	 * @param conversionRate
	 */
	protected Transfer(List<Order> orderList, double conversionRate, CoinPair key) {
		this.key = key;
		this.orderList = orderList;
		this.conversionRate = conversionRate;
	}

	protected Transfer(List<Order> orderList, CoinPair key) {
		this.key = key;
		this.orderList = orderList;
		conversionRate = orderList.get(orderList.size() - 1).price;
	}

	
	
	/**
	 * @return the conversion rate from coin1 to coin2
	 */
	public double getConversionRate() {
		if(conversionRate < 0)
			System.err.println("Undefined conversion rate");
		return conversionRate;
	}
	
	/**
	 * @return the number of orders
	 */
	public int numOrders() {
		return orderList.size();
	}
	
	/**
	 * @return the list of all orders
	 */
	public List<Order> getOrderList() {
		return orderList;
	}
	
	/**
	 * @param index
	 * @return the order at <code>index</code>
	 */
	public Order getOrder(int index) {
		return orderList.get(index);
	}
	
	/**
	 * @return the best order (as in, the highest return)
	 */
	public Order getBestOrder() {
		return orderList.get(orderList.size() - 1);
	}
	
	public double getTransferRate() {
		return orderList.get(orderList.size() - 1).price;
	}
	
	/**
	 * Computes the BTC volume of each order by using the conversion rate specified to convert coin1 to BTC
	 * @param conversionToBTC
	 */
	public void computeVolBTC(double conversionToBTC) {
		for(Order order : orderList) {
			order.volBTC = order.volume*conversionToBTC;
		}
	}
	
	/**
	 * Update the <code>transferMap</code> with the Depth of the <code>key</code> market in the <code>exchangeID</code>.
	 * The depth is represented by <code>depthObj</code>
	 * @param transferMap
	 * @param key
	 * @param depthObj
	 * @param exchangeID
	 */
	//public static boolean updateTransfers(Map<CoinPair, Transfer> transferMap, CoinPair key, JSONObject depthObj, int exchangeID) {
	public static boolean updateTransfers(Exchange ex, CoinPair key, JSONObject depthObj) {
		//int exchangeID = ex.exchangeID;
		Map<CoinPair, SingleExchangeTransfer> transferMap = ex.getTransferMap();
		
		// compute the depth
		Depth d = getDepth(ex, depthObj, key);
		if(d == null) {
			System.err.println("no bids or no asks for " + key + " on " + ex);
			return false;
		}
		// apply fees
		for(Order order : d.askList)
			order.price = ex.applyFee(key, order.price);
		
		for(Order order : d.bidList)
			order.price = ex.applyFee(key, order.price);
		
		// add to transfer map in both directions
		transferMap.put(key.swap(), new SingleExchangeTransfer(d.askList, 1/d.conversionRate, key.swap(), ex.exchangeID));
		transferMap.put(key, new SingleExchangeTransfer(d.bidList, d.conversionRate, key, ex.exchangeID));
		return true;
	}
	
	private static class Depth {
		List<Order> askList, bidList;
		double conversionRate; 
		
		public Depth(List<Order> askList, List<Order> bidList, double conversionRate) {
			this.askList = askList;
			this.bidList = bidList;
			this.conversionRate = conversionRate;
					
		}
	}
	
	/**
	 * Returns an instance of Depth based on the supplied JSON object
	 * @param depthObj
	 */
	private static Depth getDepth(Exchange ex, JSONObject depthObj, CoinPair key) {
		int exchangeID = ex.exchangeID;
		
		List<Order> askList = new ArrayList<Order>();
		List<Order> bidList = new ArrayList<Order>();
		
		JSONArray askArray = depthObj.getJSONArray("asks");
		JSONArray bidArray = depthObj.getJSONArray("bids");
		
		switch(exchangeID) {
		case Exchange.BTER:
		case Exchange.BTCE:
		case Exchange.VIRCUREX:
		case Exchange.KRAKEN:
		case Exchange.BTC_CHINA:
			
			// [orderIndex][0=price,1=volume]
			
			// buying is priced at the 2nd coin and we want it to be for the 1st coin (we must correct lag before we correct this)
			for(int i = 0; i < askArray.length(); i++) {
				JSONArray orderArray = askArray.getJSONArray(i);
				askList.add(new Order( orderArray.getDouble(0), orderArray.getDouble(1), exchangeID ));
			}
			
			// selling is price at the 2nd coin, which is what we want
			for(int i = 0; i < bidArray.length(); i++) {
				JSONArray orderArray = bidArray.getJSONArray(i);
				bidList.add(new Order( orderArray.getDouble(0), orderArray.getDouble(1), exchangeID ));
			}
			break;
			
		default: throw new IllegalArgumentException("unsupported exchange: " + exchangeID);
			
		}
		
		// organize asks and bids
		Collections.sort(askList);
		Collections.sort(bidList);
		/*
		System.out.println("Asks:");
		System.out.println(Arrays.toString(askList.toArray()));
		
		System.out.println("Bids:");
		System.out.println(Arrays.toString(bidList.toArray()));
		*/
		
		// detect lag
		// TODO: sometimes one way trading can be profitable
		if(askList.size() == 0 || bidList.size() == 0)
			return null;
		Order lowestAsk = askList.get(0);
		Order highestBid = bidList.get(bidList.size()-1);
		
		ex.isLagCorrected = false;
		// if ask <= bid, then the order should have been carried out, so there is lag we must compensate for
		while(lowestAsk.price <= highestBid.price) {
			ex.isLagCorrected = true;
			ArbitrageFinder.lagWriter.writeln(key.toString());
			ArbitrageFinder.lagWriter.writeln("lowestAsk  = " + lowestAsk);
			ArbitrageFinder.lagWriter.writeln("highestBid = " + highestBid);
			
			// if the lowest ask has more volume
			if(lowestAsk.volume > highestBid.volume) {
				lowestAsk.volume -= highestBid.volume;
				ArbitrageFinder.lagWriter.writeln("Removing: " + bidList.remove(bidList.size()-1));
				highestBid = bidList.get(bidList.size()-1);
			}
			// vice versa
			else {
				highestBid.volume -= lowestAsk.volume;
				ArbitrageFinder.lagWriter.writeln("Removing: " + askList.remove(0));
				lowestAsk = askList.get(0);
			}
		}
		
		
		// compute the conversion rate as the center of market price
		double conversionRate = (highestBid.price + lowestAsk.price)/2.0;
		
		// buying is priced at the 2nd coin and we want it to be for the 1st coin
		// buying volume is at 1st coin and we want it to be for the 2nd coin
		// invert the prices of the ask list
		for(Order order : askList) {
			order.price = 1.0/order.price;
			order.volume = order.volume*conversionRate;
		}
		
		Collections.sort(askList);
		
		
		return new Depth(askList, bidList, conversionRate);
		
	}
	
	public String toString(int numOrders) {
		String result = "";
		result += key + "\n";
		
		// print the conversion rate if applicable
		result += conversionRate();
		
		// print the best orders
		int startIndex = Math.max(orderList.size() - numOrders, 0);
		for(int i = startIndex; i < orderList.size(); i++) {
			Order order = orderList.get(i);
			result += order + "\n";
		}
		return result;
	}


	@Override
	public String toString() {
		String result = "";
		result += key + "\n";
		
		result += conversionRate();
		
		for(Order order : orderList) {
			result += order + "\n";
		}
		return result;
	}
	
	private String conversionRate() {
		String result = "";
		
		if(this instanceof MultiExchangeTransfer)
			result += "MultiExchangeTransfer\n";
		else {
			if(this instanceof SingleExchangeTransfer)
				result += "SingleExchangeTransfer\n";
			else result += "Transfer\n";
			result += "Conversion Rate = " + conversionRate + "\n";
		}
		return result;
	}


	@Override
	public int hashCode() {
		return orderList.hashCode();
	}
	
	/**
	 * Used for calculating arbitrage.
	 * Indicates that arbitrage will be carried out for <code>orderList</code> indices <code>startIndex</code> ~ <code>orderList.size() - 1</code>
	 */
	public int startIndex;
	
	
	/**
	 * The position in the arbitrage loop
	 */
	public int arbitrageIndex;
	
	/**
	 * @param volume
	 * @return <code>amount</code> converted from coin1 to coin2 using the best orders possible
	 */
	public double convert(double volume) {
		double result = 0;
		
		for(int i = orderList.size()-1; i >= 0; i--) {
			Order order = orderList.get(i);
			
			// last order to process, will only be partially filled
			if(order.volume > volume) {
				result += order.price*volume;
				break;
			}
			result += order.price*order.volume;
			volume -= order.volume;
		}
		
		return result;
	}
	
	/**
	 * Computes a maximal <code>startIndex</code> that can accomodate <code>volume</code>
	 * @param volume
	 */
	public void computeStartIndex(double volume) {
		double result = 0;
		
		for(int i = orderList.size()-1; i >= 0; i--) {
			Order order = orderList.get(i);
			
			// last order to process, will only be partially filled
			if(order.volume > volume) {
				result += order.price*volume;
				// set this as the start index
				startIndex = i;
				break;
			}
			result += order.price*order.volume;
			volume -= order.volume;
		}
		
	}

	/**
	 * @return volume of arbitrage in the first coin
	 */
	public double arbitrageVolBTC() {
		double result = 0;
		
		for(int i = startIndex; i < orderList.size(); i++) {
			result += orderList.get(i).volBTC;
		}
		
		return result;
	}

	/**
	 * @return volume of arbitrage in the first coin
	 */
	public double arbitrageVol() {
		double result = 0;
		
		for(int i = startIndex; i < orderList.size(); i++) {
			result += orderList.get(i).volume;
		}
		
		return result;
	}
	
	/**
	 * @return the worst <code>Order</code> (lowest price) in our current arbitrage configuration 
	 */
	public Order getWorstOrder() {
		return orderList.get(startIndex);
	}
	
	/**
	 * @return the next worst <code>Order</code> (lowest price) after our current worst order in our current arbitrage configuration 
	 */
	public Order getNextWorstOrder() {
		return orderList.get(startIndex-1);
	}


	@Override
	public int compareTo(Transfer t) {
		double volume1 = arbitrageVolBTC();
		double volume2 = t.arbitrageVolBTC();
		
		if( volume1 > volume2 )
			return 1;
		if ( volume1 < volume2 )
			return -1;
		return 0;
	}
	
}
