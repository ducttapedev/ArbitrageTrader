package arbitrage;

import java.util.List;

import market.SingleExchangeTransfer;
import market.Transfer;
import util.General;
import exchange.Exchange;

public class ArbitrageConfig implements Comparable<ArbitrageConfig> {
	private final List<SingleExchangeTransfer> transferList;
	private final int[] exchangeIDs, startIndices;
	private final int bottleneckIndex;
	private final double startVolume, volume, profit, profitPercent;
	//int exchangeID, startIndex;
	
	/*
	public ArbitrageConfig(int exchangeID, int startIndex) {
		this.exchangeID = exchangeID;
		this.startIndex = startIndex;
	}
	*/
	
	public double getProfit() {
		return profit;
	}
	
	public static final boolean DEBUG = false;
	
	public static final double MINIMUM_PROFIT = 0.1;
	
	/**
	 * @param transferList
	 * @param exchanges
	 * @param volume the approximate amount of BTC moved per transfer (only used to calculate profitability, since it is an estimate)
	 * @param bottleneckIndex the index of <code>transferList</code> that is our bottleneck (not accounting for funds)
	 */
	public ArbitrageConfig(List<SingleExchangeTransfer> transferList, Exchange[] exchanges, double volume, int bottleneckIndex) {
		this.bottleneckIndex = bottleneckIndex;
		double startVolume = transferList.get(bottleneckIndex).arbitrageVol();
		double profit = 0;
		// find the overall arbitrage profit
		boolean sufficientFunds = false;
		
		// keep reducing the volume until we have enough funds
		while(!sufficientFunds) {
			sufficientFunds = true;
		
			double currentVolume = startVolume;
	
			for(int i = bottleneckIndex; i < bottleneckIndex + transferList.size(); i++) {
				
				SingleExchangeTransfer t = transferList.get(i%transferList.size());
				
				// get the funds for the coin we want to spend
				double funds = exchanges[t.exchangeID].getFunds(t.key.getCoin1());
				
				// no funds, arbitrage cannot be processed
				if(funds < 1e-6) {
					if(DEBUG)
						ArbitrageFinder.graphWriter.writeln("Zero funds for " + t.key.getCoin1() + " at " + Exchange.getExchangeName(t.exchangeID) + " for config: ");
						ArbitrageFinder.graphWriter.writeln(toString());
					throw new RuntimeException("Zero funds!");
				}
				// insufficient funds, scale down the volume and retry
				else if(funds <= currentVolume - General.EPSILON) {
					double scalingFactor = funds*0.99/currentVolume;
					volume *= scalingFactor;
					startVolume *= scalingFactor;
					sufficientFunds = false;
					break;
				}
				

				/*
				 *  TODO: if there is no lag, it shouldn't make a difference whether or not this is here
				 *  Since there is lag, we have two outcomes if an order gets filled before we place our order:
				 *  (compute new start index) - our order remains unfilled
				 *  (don't compute new start index) - our order may get filled but at a less desirable rate (though still profitable)
				 */
				//t.computeStartIndex(currentVolume);
				currentVolume = t.convert(currentVolume);
			}

			profit = ((currentVolume-startVolume)/startVolume)*volume;
			
		}
		
		if(startVolume == 0 || profit == 0)
			throw new RuntimeException("Zero volume");
		
				
		this.profit = profit;

		// define and initialize some members
		this.startVolume = startVolume;
		this.volume = volume;
		profitPercent = profit/volume*100;
		
		// ignore arbitrage with less than 0.3% profit
		if(profitPercent < 0.1) {
			if(DEBUG) {
				ArbitrageFinder.graphWriter.writeln("Profit under 0.1% for ");
				ArbitrageFinder.graphWriter.writeln(toString());
			}
			throw new RuntimeException("Profit under 0.1%");
		}
		
		this.transferList = transferList;
		int numTransfers = transferList.size();
		exchangeIDs = new int[numTransfers];
		startIndices = new int[numTransfers];
		
		// set the per-edge configuration
		for(int i = 0; i < transferList.size(); i++) {
			SingleExchangeTransfer t = transferList.get(i);
			set( i, t.exchangeID, t.startIndex );
		}
		
		
		// final validation, see if we meet the minimum order requirements
		double currentVolume = startVolume;
		double previousVolume;
		
		for(int i = bottleneckIndex; i < bottleneckIndex + transferList.size(); i++) {
			SingleExchangeTransfer t = transferList.get(i%transferList.size());
			previousVolume = currentVolume;
			currentVolume = t.convert(currentVolume);
			
			// get the minimums
			String coin1 = t.key.getCoin1();
			String coin2 = t.key.getCoin2();
			Exchange ex = exchanges[t.exchangeID];
			double min1 = ex.getMinimumOrder(coin1);
			double min2 = ex.getMinimumOrder(coin2);
			
			/*
			System.out.println(ex);
			System.out.println(previousVolume + " > " + min1 + " " + coin1);
			System.out.println(currentVolume + " > " + min2 + " " + coin2);
			System.out.println();
			*/
			// if we don't meet the minimum (20% boost for error), this is invalid 
			if(previousVolume <= min1*1.2 || currentVolume < min2*1.2) {
				if(DEBUG) {
					ArbitrageFinder.graphWriter.writeln("minimum volume not met for ");
					ArbitrageFinder.graphWriter.writeln(toString());
					ArbitrageFinder.graphWriter.writeln(previousVolume + " < " + min1 + " " + coin1);
					ArbitrageFinder.graphWriter.writeln(currentVolume + " < " + min2 + " " + coin2);
				}
				throw new RuntimeException("minimum not met");
			}
		}
		
		ArbitrageFinder.graphWriter.writeln("successfully added config: ");
		ArbitrageFinder.graphWriter.writeln(toString());
		
		
	}
	
	public void execute(Exchange[] exchanges) {
		// TODO: executes the arbitrage
		//Thread[] tradingThreads = new Thread[transferList.size()];
		
		double currentVolume = startVolume;
		
		// place each order
		for(int i = bottleneckIndex; i < bottleneckIndex + transferList.size(); i++) {
			SingleExchangeTransfer t = transferList.get(i%transferList.size());
			String id = exchanges[t.exchangeID].placeOrder(t.key, t.getWorstOrder().price, currentVolume);
			System.out.println("order id = " + id);
			System.out.println("placing order:");
			System.out.println("market = " + t.key);
			System.out.println("price = " + t.getWorstOrder().price);
			System.out.println("volume = " + currentVolume);
			System.out.println();
			currentVolume = t.convert(currentVolume);
		}
	}
	
	/**
	 * Sets the configuration for edge # <code>transferIndex</code>
	 * @param transferIndex
	 * @param exchangeID
	 * @param transferStartIndex
	 */
	private void set(int transferIndex, int exchangeID, int transferStartIndex) {
		exchangeIDs[transferIndex] = exchangeID;
		startIndices[transferIndex] = transferStartIndex;
	}
	
	/**
	 * Called whenever the transferList is used.
	 * Updates the startIndices of the transferList
	 */
	private void updateTransferList() {
		for(int i = 0; i < transferList.size(); i++) {
			transferList.get(i).startIndex = startIndices[i];
		}
	}
	
	public String toString(int maxOrders) {
		updateTransferList();
		
		// header
		String result = "";
		
		// general info
		result += "volume = " + volume + " BTC\n";
		result += "profit = " + String.format("%4.8f", profit) + " BTC\n";
		result += "profit (%) = " + profitPercent + "\n";
		
		// loop info
		//double startVolume = transferList.get(bottleneckIndex).arbitrageVol();
		double currentVolume = startVolume;
		
		for(int i = bottleneckIndex; i < bottleneckIndex + transferList.size(); i++) {
			SingleExchangeTransfer t = transferList.get(i%transferList.size());
			result += String.format("%10.8f", currentVolume) + " " + t.key.getCoin1();
			currentVolume = t.convert(currentVolume);
			result += " to " + String.format("%10.8f", currentVolume) + " " + t.key.getCoin2();
			result += " at " + Exchange.getExchangeName(t.exchangeID) + "\n";
			
			if(maxOrders >= 0) {
				int numOrders = Math.min(t.numOrders() - t.startIndex, maxOrders);
				result += t.toString(numOrders) + "\n";
			}
		}
		
		return result;
	}
	
	@Override
	public String toString() {
		return toString(Integer.MAX_VALUE);
	}

	public static double profitPower = 1;
	public static double numExchangePower = 0.25;
	
	@Override
	public int compareTo(ArbitrageConfig config) {
		double value1 = getValue();
		double value2 = config.getValue();
		
		if(value1 > value2)
			return 1;
		
		if(value1 < value2)
			return -1;
		
		return 0;
	}

	public double getValue() {
		return volume*Math.pow(profitPercent, profitPower)/Math.pow(transferList.size(), numExchangePower);
	}
}
	