package arbitrage;

import java.util.List;
import java.util.TreeMap;

import market.MultiExchangeTransfer;
import market.Order;
import market.SingleExchangeTransfer;
import market.Transfer;

public class Edge {
	
	
	public Vertex origin;
	public Vertex destination;
	public MultiExchangeTransfer compositeTransfer;
	//public Transfer[] allTransfers = new Transfer[Exchange.NUM_EXCHANGES];
	public TreeMap<Integer, SingleExchangeTransfer> individualTransfers = new TreeMap<Integer, SingleExchangeTransfer>();
	
	public Edge(Vertex origin, Vertex destination, SingleExchangeTransfer transfer) {
		this.origin = origin;
		this.destination = destination;
		this.compositeTransfer = new MultiExchangeTransfer(transfer);
		//allTransfers[transfer.getBestOrder().exchangeID] = transfer;
		individualTransfers.put(transfer.exchangeID, transfer);
	}
	
	private Edge(Vertex origin, Vertex destination, List<Transfer> transferList) {
		this.origin = origin;
		this.destination = destination;
		
		compositeTransfer = MultiExchangeTransfer.mergeTransfers(transferList);
	}
	
	/**
	 * @param minOrderIndex
	 * @return the largest volume we can arbitrage with in one exchange
	 */
	public double getHighestVolume(int minOrderIndex) {
		double result = 0;

		// use the minOrderIndex to determine the lowest price we can go
		double minPrice = compositeTransfer.getOrder(minOrderIndex).price;
		
		double maxVolume = 0;
		int bestExchange = -1;
		
		// calculate the volume of orders whose price >= minPrice for each exchange
		for(Transfer transfer : individualTransfers.values()) {
			double volume = 0;
			
			// starting with the highest priced order, work our way down
			for(int orderIndex = transfer.numOrders()-1; orderIndex >= 0 ; orderIndex--) {
				
				Order currentOrder = transfer.getOrder(orderIndex);
				// if the price is too low, stop accumulating volume
				if(currentOrder.price < minPrice)
					break;
				
				// add volume at this price to the total
				volume += currentOrder.volBTC;
			}
			
			if(volume > maxVolume) {
				//maxVolume = volume;
				//bestExchange = transfer.
			}
			
		}
		
		return result;
	}
	
	/**
	 * Adds the transfer to the per-exchange list of transfers as well as the composite transfers
	 * @param newTransfer
	 */
	public void addTransfer(SingleExchangeTransfer newTransfer) {
		compositeTransfer.addTransfer(newTransfer);
		//allTransfers[newTransfer.getBestOrder().exchangeID] = newTransfer;
		individualTransfers.put(newTransfer.exchangeID, newTransfer);
	}
	
	@Override
	public boolean equals(Object o) {
		//System.err.println("Edge.equals");
		Edge edge = (Edge)o;
		return origin.equals(edge.origin) && destination.equals(edge.destination);
	}
	
	@Override
	public int hashCode() {
		return (int) (origin.hashCode()*destination.hashCode());
	}
	
	/*
	@Override
	public String toString() {
		return String.format("%10s: %5s -> %5s @ %.8f %5s/%-5s\n", Exchange.getExchangeName(exchangeID), origin.data, destination.data, Math.exp(-weight), destination.data, origin.data);
	}
	*/
}
