package market;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiExchangeTransfer extends Transfer {
	
	/**
	 * Converts a <code>Transfer</code> to a <code>MultiExchangeTransfer</code>
	 * The original Transfer will not be modified
	 * @param t
	 */
	public MultiExchangeTransfer(Transfer t) {
		super(new CoinPair(t.key.toString()));
		orderList = new ArrayList<Order>(t.orderList);
		
	}
	
	public MultiExchangeTransfer(CoinPair key) {
		super(key);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param transferList
	 * @return a <code>Transfer</code> object with all transfers in <code>transferList</code> merged
	 */
	public static MultiExchangeTransfer mergeTransfers(List<Transfer> transferList) {
		MultiExchangeTransfer mergedTransfer = new MultiExchangeTransfer(transferList.get(0).key);
		// add all the orders to the new order list
		for(Transfer transfer : transferList) {
			mergedTransfer.orderList.addAll(transfer.orderList);
		}
		// sort the new order list
		Collections.sort(mergedTransfer.orderList);
		
		return mergedTransfer;
	}
	
	/**
	 * Adds a new transfer (usually from a different exchange)
	 * @param newTransfer
	 */
	public void addTransfer(Transfer newTransfer) {
		orderList.addAll(newTransfer.orderList);
		Collections.sort(orderList);
	}
}
