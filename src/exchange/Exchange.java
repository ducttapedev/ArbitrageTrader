package exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import arbitrage.ArbitrageFinder;
import market.CoinPair;
import market.SingleExchangeTransfer;
import market.Transfer;
import util.General;
import util.LogWriter;
import util.Request;

public abstract class Exchange {
	public static final int BTER = 0;
	public static final int BTCE = 1;
	public static final int VIRCUREX = 2;
	public static final int KRAKEN = 3;
	public static final int CRYPTSY = 4;
	public static final int BTC_CHINA = 5;
	
	public static final boolean DEBUG = false;
	public static final double SATOSHI = 1e-8;
	public static final long[] minDelay = {100,100,5500,5500,0,3500};
	
	/**
	 * Bter, BTC-E, Vircurex
	 */
	public static final int NUM_EXCHANGES = minDelay.length;
	public final int exchangeID;
	
	protected LogWriter out;
	protected Map<String, Double> funds = new HashMap<String, Double>();
	protected Map<CoinPair, SingleExchangeTransfer> transferMap = new HashMap<CoinPair, SingleExchangeTransfer>();
	protected final Properties prop;
	public boolean isLagCorrected;
	public double apiSpeed = 0.86, orderSpeed = 0.86;
	
	
	public Exchange(int exchangeID, File propertiesFile) throws IOException {
		this.exchangeID = exchangeID;
		
		// create error log
		//String formattedDate = General.getDate();
		out = new LogWriter( ArbitrageFinder.logFolder + ArbitrageFinder.date + "_" + toString() + ".log");
		
		// load properties file
		prop = new Properties();
		try {
			FileInputStream inStream = new FileInputStream(propertiesFile);
			prop.load(inStream);
			inStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println("Could not load properties file: " + propertiesFile);
		}
	}
	
	public void postIteration(int count) {
		out.postIteration(count);
	}
	
	public void writeln(String s) {
		write(s + "\n");
	}
	
	public void write(String s) {
		out.write(s);
		out.flush();
	}
	

	public boolean error(Exception e) {
		e.printStackTrace(out);
		write("\n");
		return false;
	}
	
	/**
	 * @param coin
	 * @return the minimum order amount required of the coin
	 */
	public double getMinimumOrder(String coin) {
		try {
			String property = prop.getProperty(coin);
			double amount = Double.parseDouble(property);
			return amount;
		
		// if property == null or we cannot parse it to a double, assume no minimum order
		} catch(Exception e) {
			return SATOSHI;
		}
	}

	
	public void finalize() {
		out.close();
	}
	
	public Map<CoinPair, SingleExchangeTransfer> getTransferMap() {
		return transferMap;
	}
	
	public Transfer getTransfer(CoinPair key) {
		return transferMap.get(key);
	}
	
	public double getFunds(String coin) {
		Double result = funds.get(coin);
		if(result == null)
			return 0;
		else return result;
	}
	
	/**
	 * @param coin
	 * @param amount
	 * @return <code>
	 */
	public double convertToBTC(String coin, double amount) {
		Transfer transfer;
		
		if(coin.equals("btc"))
			return amount;
		
		transfer = transferMap.get(new CoinPair(coin, "btc"));
		if(transfer != null) {
			return amount *= transfer.getConversionRate();
		}
		transfer = transferMap.get(new CoinPair("btc", coin));
		if(transfer != null) {
			return amount /= transfer.getConversionRate();
		}
		write("ERROR: No conversion from " + coin + " to BTC.");
		return 0;
		
	}
	
	/**
	 * Computes the volume in BTC of each order in each transfer
	 * @return true if successful
	 */
	public boolean computeVolBTC() {
		boolean result = true;
		
		for(CoinPair key : transferMap.keySet()) {
			Transfer transfer = transferMap.get(key);
			String coin1 = key.getCoin1();
			String coin2 = key.getCoin2();
			
			// if either coin equals BTC, we don't need any other conversions 
			if(coin1.equals("btc")) {
				transfer.computeVolBTC(1.0);
				continue;
			}
			if(coin2.equals("btc")) {
				transfer.computeVolBTC(transfer.getConversionRate());
				continue;
			}
			
			// try to convert coin1 to BTC
			CoinPair btcKey = new CoinPair(coin1, "btc");
			Transfer btcTransfer = transferMap.get(btcKey);
			if(btcTransfer != null) {
				transfer.computeVolBTC(btcTransfer.getConversionRate());
				continue;
			}
			
			// try to convert coin2 to BTC
			btcKey = new CoinPair(coin2, "btc");
			btcTransfer = transferMap.get(btcKey);
			if(btcTransfer != null) {
				transfer.computeVolBTC(btcTransfer.getConversionRate());
				continue;
			}
			
			result = false;
			String errorMessage = "Could not compute BTC volumes for " + key + " in exchange " + toString() + "!\n";
			System.err.print(errorMessage);
			write(errorMessage);
			
		}
		
		return result;
		
	}
	
	private boolean isUpdated = false;
	
	public boolean isUpdated() {
		return isUpdated;
	}
	
	/**
	 * Updates depth and funds
	 * @param requestList
	 */
	public void updateAll(List<Request> requestList) {
		isUpdated = updateDepth(requestList) && updateFunds();
	}
	public abstract boolean updateDepth(List<Request> requestList); 
	public abstract boolean updateDepth(Request request);
	
	public abstract double applyFee(CoinPair key, double principal);

	/*
	public abstract double getTradingFee(CoinPair pair, double principal);
	
	public abstract List<Request> updateTradingFeesRequest();
	public abstract boolean updateTradingFees(String response);
	
	public List<Request> updateDepthRequest(List<String> coinList);
	public List<Request> updateDepth(List<String> responseList);
	*/
	
	
	/**
	 * Updates our funds for each coin in this exchange
	 */
	public abstract boolean updateFunds();
	
	/**
	 * Places an order for converting <code>pair.coin1</code> to <code>pair.coin2</code>.
	 * <code>amount</code> is the amount in <code>coin1</code>.
	 * <code>rate</code> scales the amount of <code>coin1</code> to an amount of <code>coin2</code>
	 * @param pair
	 * @param rate
	 * @param amount
	 * @return
	 * null if we failed to place our order
	 * the order_id otherwise
	 */
	public abstract String placeOrder(CoinPair pair, double rate, double amount);
	
	public int cancelAllOrders() {
		try {
			boolean result = true;
			
			List<String> idList = getOpenOrders();
			for(String orderID : idList) {
				try {
					System.out.println("Cancelling " + orderID);
					if(!cancelOrder(orderID))
						result = false;
				} catch(Exception e) {
					result = false;
				}
			}
			
			return idList.size();
		} catch(Exception e) {
			return 0;
		}
	}
	
	/**
	 * @param orderID
	 * @return true if the order was successfuly cancelled
	 */
	public abstract boolean cancelOrder(String orderID);
	
	public abstract List<String> getOpenOrders();
	
	
	
	/**
	 * @param exchangeID
	 * @return the human readable name for the given <code>exchangeID</code>
	 */
	public static String getExchangeName(int exchangeID) {
		switch(exchangeID) {
		case BTER: return "Bter";
		case BTCE: return "BTC-e";
		case VIRCUREX: return "Vircurex";
		case KRAKEN: return "Kraken";
		case CRYPTSY: return "Cryptsy";
		case BTC_CHINA: return "BTC China";
		default: return "Invalid Exchange";
		}
	}
	
	public String toString() {
		return getExchangeName(exchangeID);
	}
	
	/**
	 * @return total value of all assets in BTC
	 */
	public double getTotalFunds() {
		double totalBtcAmount = 0;
		
		// for each coin
		for(String coin : funds.keySet()) {
			double amount = funds.get(coin);
			
			// if we have some of this coin, convert it to BTC
			if(amount > 0) {
				double btcAmount = convertToBTC(coin, amount);
				totalBtcAmount += btcAmount;
			}
		}
		
		return totalBtcAmount;
	}
	
	public String fundsToString() {
		String result = "";
		double totalBtcAmount = 0;
		
		// for each coin
		for(String coin : funds.keySet()) {
			double amount = funds.get(coin);
			
			// if we have some of this coin, print out its amount in the coin and in BTC
			if(amount > 0) {
				double btcAmount = convertToBTC(coin, amount);
				totalBtcAmount += btcAmount;
				result += String.format("%12.8f btc = %.8f " + coin + "%n", btcAmount, amount);
			}
		}
		
		result += String.format("%12.8f btc (total)", totalBtcAmount);
		return result;
	}
	
	public void printFunds() {
		System.out.println(toString());
		for(String coin : funds.keySet()) {
			if(funds.get(coin) > 0) {
				System.out.format("%.8f " + coin + "%n", funds.get(coin));
			}
		}
		System.out.println();
	}
	
}
