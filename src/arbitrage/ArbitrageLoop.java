package arbitrage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import exchange.Exchange;
import market.Order;
import market.SingleExchangeTransfer;
import market.Transfer;

public class ArbitrageLoop implements Comparable<ArbitrageLoop> {
	public double factor;
	public List<Edge> tradingList;
	
	public ArbitrageLoop(List<Edge> tradingList) {
		
		// compute the factor change (>1 means arbitrage)
		factor = 1;
		
		for(int i = 0; i < tradingList.size(); i++) {
			Edge edge = tradingList.get(i);
			factor *= edge.compositeTransfer.getBestOrder().price;
		}
		
		// find the edge with the smallest origin
		int minIndex = 0;
		String minObject = tradingList.get(0).origin.coin;
		
		for(int i = 1; i < tradingList.size(); i++) {
			String object = tradingList.get(i).origin.coin;
			
			if(minObject.compareTo(object) < 0) {
				minIndex = i;
				minObject = object;
			}
		}
		
		// rotate so that the edge with the smallest origin is first
		Collections.rotate(tradingList, tradingList.size() - minIndex);
		
		this.tradingList = tradingList;
		
	}
	
	
	/**
	 * Measures the maximum value of arbitrage with the given choice of exchanges (specified by <code>transferList</code>)
	 * @param transferList
	 * @param exchanges
	 * @return the optimal arbitrage configuration
	 */
	public ArbitrageConfig getArbitrageConfig(List<SingleExchangeTransfer> transferList, Exchange[] exchanges) {
		return getArbitrageConfig(transferList, exchanges, Double.POSITIVE_INFINITY);
	}
	
	/**
	 * Measures the maximum value of arbitrage not exceeding <code>maxVolume</code> in volume
	 * with the given choice of exchanges (specified by <code>transferList</code>)
	 * @param transferList
	 * @param exchanges
	 * @param maxVolume
	 * @return the optimal arbitrage configuration
	 */
	public ArbitrageConfig getArbitrageConfig(List<SingleExchangeTransfer> transferList, Exchange[] exchanges, double maxVolume) {
		
		// set all transfers to start with just the best order
		// also set their positions
		for(int i = 0; i < transferList.size(); i++) {
			Transfer t = transferList.get(i);
			t.startIndex = t.numOrders() - 1;
			t.arbitrageIndex = i;
		}
		
		// transfers sorted by arbitrage volume, so the bottleneck is always at index 0
		List<Transfer> transfersByVolume = new ArrayList<Transfer>();
		for(Transfer t : transferList) {
			transfersByVolume.add(t);
		}
		
		// if there is no profitable config, return null
		if(getWorstFactor(transfersByVolume) <= 1)
			return null;
		
		
		
		// while the arbitrage is still profitable, try to relax each bottleneck
		boolean isProfitable = true;
		double bottleneck = 0, nextBottleneck;
		int bottleneckIndex = -1;
		
		while(isProfitable) {
			//System.out.println("a");
			Collections.sort(transfersByVolume);
			Transfer t = transfersByVolume.get(0);
			bottleneck = t.arbitrageVolBTC();
			bottleneckIndex = t.arbitrageIndex;
			nextBottleneck = transfersByVolume.get(1).arbitrageVolBTC();
			
			// relax until this is no longer the bottleneck
			while(bottleneck <= nextBottleneck) {
				
				//System.out.println(bottleneck + ", " + nextBottleneck);
				//System.out.println(t.getWorstOrder());
				// no more orders
				if(t.startIndex == 0) {
					isProfitable = false;
					break;
				}
				
				t.startIndex--;
				
				bottleneck = t.arbitrageVolBTC();
				// if not profitable or exceeds max volume, undo and break
				if(getWorstFactor(transfersByVolume) < 1 || bottleneck > maxVolume) {
					t.startIndex++;
					isProfitable = false;
					
					// recalculate bottleneck
					bottleneck = t.arbitrageVolBTC();
					break;
				}
			}
		}
		// sanity check, the worst order should break even or be profitable
		assert getWorstFactor(transfersByVolume) >= 1;
		
		try {
			ArbitrageConfig result = new ArbitrageConfig(transferList, exchanges, bottleneck, bottleneckIndex);
			
			if(ArbitrageConfig.DEBUG)
				ArbitrageFinder.graphWriter.writeln("\n\n");
			
			return result;
		} catch(RuntimeException e) {
			//e.printStackTrace();
			return null;
		}
				
		
		
		
	}

	/**
	 * @param transferList
	 * @return the worst profit factor with this arbitrage config
	 */
	private double getWorstFactor(List<Transfer> transferList) {
		double factor = 1;
		
		for(int i = 0; i < transferList.size(); i++) {
			factor *= transferList.get(i).getWorstOrder().price;
		}
		return factor;
	}
	
	public List<ArbitrageConfig> getArbitrageConfigList(Exchange[] exchanges) {
		
		ArbitrageFinder.graphWriter.writeln("---------------------------------------------------");
		ArbitrageFinder.graphWriter.writeln("Arbitrage Loop");
		ArbitrageFinder.graphWriter.writeln("---------------------------------------------------");
		ArbitrageFinder.graphWriter.writeln(toString());
		ArbitrageFinder.graphWriter.writeln("");
		
		
		
		int[] currentIndices = new int[tradingList.size()];
		List<ArbitrageConfig> arbitrageConfigList = new ArrayList<ArbitrageConfig>();
		boolean choicesRemaining = true;
		
		while(choicesRemaining) {
			//System.out.println("b");
			// form our choice of exchanges from currentIndices
			boolean validChoice = true;
			List<SingleExchangeTransfer> choiceOfExchanges = new ArrayList<SingleExchangeTransfer>();
			
			for(int i = 0; i < tradingList.size() && validChoice; i++) {
				SingleExchangeTransfer transfer = tradingList.get(i).individualTransfers.get(currentIndices[i]);
				if(transfer == null)
					validChoice = false;
				else choiceOfExchanges.add(transfer);
			}
			
			
			
			// if it's a valid choice, get the optimal config
			if(validChoice) {
				// TODO: max 0.2 BTC volume
				ArbitrageConfig arbitrageConfig = getArbitrageConfig(choiceOfExchanges, exchanges);
				// null indicates it wasn't profitable
				if(arbitrageConfig != null)
					arbitrageConfigList.add( arbitrageConfig );
			}
			

			//System.out.println(Arrays.toString(currentIndices));
			
			// advance the indices, where a lower index is more significant
			for(int i = currentIndices.length-1; i >= 0; i--) {
				// increment the current index, and if it is still within limits, we are done 
				currentIndices[i]++;
				if(currentIndices[i] < Exchange.NUM_EXCHANGES)
					break;
				
				// if it exceeds limits, set this index to 0 and carry to the next place
				currentIndices[i] = 0;
				
				if(i == 0) {
					choicesRemaining = false;
					break;
				}
				
			}
			
		}
		
		

		ArbitrageFinder.graphWriter.writeln("---------------------------------------------------");
		ArbitrageFinder.graphWriter.writeln("---------------------------------------------------");
		
		return arbitrageConfigList;
		
	}
	
	
	
	
	public String toString() {
		String result = "";
		result += "\nPROFIT % = " + (factor-1)*100 + "\n";
		
		for(int i = 0; i < tradingList.size(); i++) {
			Edge e = tradingList.get(i);
			//List<Order> orderList = e.transfer.getOrderList();
			result += e.compositeTransfer.toString(4);
		}
		
		return result;
	}
	
	public String toSimpleString() {
		String result = "";
		
		for(int i = 0; i < tradingList.size(); i++) {
			Edge e = tradingList.get(i);
			
			if(i == 0)
				result += e.origin.coin;
			
			result += "," + e.destination.coin;
		}
		return result;
		
	}
	
	public int hashCode() {
		return tradingList.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		ArbitrageLoop a = (ArbitrageLoop)o;
		//return compareTo(a) == 0;
		return tradingList.equals(a.tradingList);
		
		
	}
	
	@Override
	public int compareTo(ArbitrageLoop o) {
		double ourValue = factor;
		double theirValue = o.factor;
		
		//if( Math.abs(ourValue - theirValue) < EPSILON )
		if(equals(o))
			return 0;
		
		if(ourValue > theirValue)
			return 1;
		if(ourValue < theirValue)
			return -1;
		return 0;
	}
	
	public static void main(String[] args) {
		// create array list object
	      List numbers = new ArrayList();
	      
	      // populate the list
	      for (int i = 0; i < 15; i++) {
	         numbers.add(i);
	      }

	      System.out.println("Before : "+Arrays.toString(numbers.toArray()));
	      
	      // rotate the list at distance 10
	      Collections.rotate(numbers, numbers.size() - 5);

	      System.out.println("After : "+Arrays.toString(numbers.toArray()));
	}
}
