package market;

import java.util.List;

public class SingleExchangeTransfer extends Transfer {

	public final int exchangeID;
	
	private SingleExchangeTransfer(CoinPair key, int exchangeID) {
		super(key);
		this.exchangeID = exchangeID;
		
	}

	public SingleExchangeTransfer(List<Order> askList, double d, CoinPair swap, int exchangeID) {
		// TODO Auto-generated constructor stub
		super(askList, d, swap);
		this.exchangeID = exchangeID;
	}

}
