package exchange;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import market.CoinPair;
import market.Ticker;
import market.Trade;
import market.Transfer;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import util.General;
import util.HttpUtil;
import util.Request;
import util.RequestDistributor;
import util.SlaveThreadSpawner;
import util.Website;

public class Bter extends Exchange {
	private static final File PROPERTIES_FILE = new File("bter.prop");


	public Map<CoinPair, Ticker> tickerMap = new HashMap<CoinPair, Ticker>();
	
	public Bter() throws IOException {
		super(Exchange.BTER, PROPERTIES_FILE);
	}

	/**
	 * 0.2% fee with 40% discount (10% direct discount, 30% credited to referring account)
	 */
	private static final double FEE_FRACTION = 0.002*0.6;
	
	
	public double applyFee(CoinPair pair, double principal) {
		// trading BTC, LTC, and DOGE with CNY is free
		// TODO: some trades with CNY have the standard fee
		if(pair.getCoin1().equals("cny") || pair.getCoin2().equals("cny"))
			return principal;
		
		return principal*(1-FEE_FRACTION);
	}
	
	
	public Request updateTickersRequest() {
		return new Request( prop.getProperty("tickersUrl"), exchangeID, null );
	}
	
	/**
	 * Updates tickers
	 * @return true if at least one ticker updated successfully
	 */
	public boolean updateTickers(Request request) {
		try {
			// return true if we update at least one ticker successfully
			boolean result = false;
			
			// get the JSON for the list of tickers
			JSONObject allTickerObj = new JSONObject(request.getResponse());
			
			for(String name : JSONObject.getNames(allTickerObj)) {
				CoinPair key = new CoinPair(name);
				try {
					Ticker ticker = new Ticker( allTickerObj.getJSONObject(key.toString()), exchangeID, key );
					tickerMap.put(key, ticker);
					result = true;
				} catch(Exception e) {
					// if we don't find the trading pair
					e.printStackTrace(out);
					out.write("\n");
				}
			}
			
			return result;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	
	/**
	 * 
	 * @return true if all volumes were succesfully converted to BTC
	 */
	public boolean computeTickerVolBTC() {
		Iterator<CoinPair> keyIterator = tickerMap.keySet().iterator();
		boolean result = true;
		
		while(keyIterator.hasNext()) {
			CoinPair key = keyIterator.next();
			Ticker ticker = tickerMap.get(key);
			
			// if either of the coins is BTC, just use its volume
			String coin1 = key.getCoin1();
			String coin2 = key.getCoin2();
			if(coin1.equals("btc")) {
				ticker.volBTC = ticker.vol1;
			} 
			else if(coin2.equals("btc")) {
				ticker.volBTC = ticker.vol2;
			}
			// otherwise, we look for a pair that converts to BTC
			else {
				// (coin1, btc)
				CoinPair btcKey = new CoinPair(coin1, "btc");
				//CoinPairData btcData = pairs.get(btcKey);
				Ticker btcTicker = tickerMap.get(btcKey);
				if(btcTicker != null) {
					ticker.volBTC = ticker.vol1*btcTicker.avg;
					continue;
				}
				
				// (btc, coin1)
				btcKey = new CoinPair("btc", coin1);
				btcTicker = tickerMap.get(btcKey);
				if(btcTicker != null) {
					ticker.volBTC = ticker.vol1/btcTicker.avg;
					continue;
				}
				
				//System.out.println("coin1=" + coin1 + "\tcoin2=" + coin2 + "\texchange=" + this);
				
				// (coin2, btc)
				btcKey = new CoinPair(coin2, "btc");
				btcTicker = tickerMap.get(btcKey);
				if(btcTicker != null) {
					ticker.volBTC = ticker.vol2*btcTicker.avg;
					continue;
				}
				
				// (btc, coin2)
				btcKey = new CoinPair("btc", coin2);
				btcTicker = tickerMap.get(btcKey);
				if(btcTicker != null) {
					ticker.volBTC = ticker.vol2/btcTicker.avg;
					continue;
				}
				
				result = false;
				
			}
			
		}
		return result;
	}
	
	
	/**
	 * @param pairList list of pairs that we want to update the depths for
	 * @return list of requests for updating depths
	 */
	public List<Request> updateDepthRequest(List<CoinPair> pairList) {
		List<Request> result = new ArrayList<Request>();
		
		for(CoinPair pair : pairList) {
			result.add(updateDepthRequest(pair));
		}
		
		return result;
	}
	
	/**
	 * Updates depths with the given request responses
	 * @param requestList list of requests with corresponding responses
	 * @return true if successful
	 */
	public boolean updateDepth(List<Request> requestList) {
		boolean result = true;
		
		for(Request r : requestList) {
			if(!updateDepth(r))
				result = false;
		}
		
		return result;
	}
	
	/**
	 * @param key coin pair that we want to update depth for
	 * @return request for updating depth
	 */
	public Request updateDepthRequest(CoinPair key) {
		return new Request(prop.getProperty("depthUrlPrefix") + key, exchangeID, key.toString());
	}
	
	/**
	 * Updates depth for the given request response
	 * @param request request with corresponding response
	 * @return true if successful
	 */
	public boolean updateDepth(Request request) {
		try {
			JSONObject depthObj = new JSONObject(request.getResponse());
			CoinPair key = new CoinPair(request.userData);
			
			//depthMap.put(pair, new Depth(depthObj, exchangeID));
			Transfer.updateTransfers(this, key, depthObj);
			return true;
		} catch(Exception e) {
			write(request.toString() + "\n");
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	/**
	 * @param validCoinList
	 * @return all pairs in which both coins are in <code>validCoins</code>
	 */
	public List<CoinPair> getValidPairs(List<String> validCoinList) {
		List<CoinPair> validPairs = new ArrayList<CoinPair>();
		
		for(String coin1 : validCoinList) {
			for(String coin2 : validCoinList) {
				// verify coins are different
				if(coin1.equals(coin2))
					continue;
				
				// see if pair exists
				CoinPair key = new CoinPair(coin1, coin2);
				Ticker t = tickerMap.get(key);
				if(t != null)
					validPairs.add(key);
				
			}
		}
		
		return validPairs;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	/*
	 * 
	 * Private methods after this
	 * 
	 */
	
	
	/**
	 * Specialized post method for Bter
	 * @param params
	 * @param method
	 * @param url
	 * @return post response
	 */
	private JSONObject post(List<NameValuePair> params, String method, String url) {
		String postResponse = null;
		
		try {
	
			// add essential params
			if(params == null)
				params = new ArrayList<NameValuePair>();
			
			params.add(new BasicNameValuePair("method", method));
			params.add(new BasicNameValuePair("nonce", Long.toString(System.nanoTime()) ));
	
			// post and return the result
			HttpPost post = new HttpPost(url);

	        String postData = HttpUtil.getStringFromPostParams(params);
	        
	        post.addHeader("Key", prop.getProperty("key"));
	        post.addHeader("Sign", HttpUtil.encryptWithHmacSha512(postData, prop.getProperty("secret")));

	        UrlEncodedFormEntity requestBody = new UrlEncodedFormEntity(params, Consts.UTF_8);
	        post.setEntity(requestBody);

	        postResponse = Website.executeRequest(post);
			//System.out.println(postResponse);
			JSONObject obj = new JSONObject(postResponse);
			
			if(DEBUG)
				System.out.println(obj.toString(4));
			
			if(!obj.getBoolean("result"))
				return null;
			
			return obj;
			
		} catch(Exception e) {
			write(postResponse + "\n");
			e.printStackTrace(out);
			return null;
		}
		
	}
	
	
	/**
	 * Updates the amount of each currency available on this exchange
	 */
	@Override
	public boolean updateFunds() {
		try {
			JSONObject obj = post(null, "getfunds", prop.getProperty("urlPrefix") + "getfunds").getJSONObject("available_funds");
			
			// update all currencies
			funds.clear();
			String[] names = JSONObject.getNames(obj);
			for(int i = 0; i < names.length; i++) {
				funds.put(names[i].toLowerCase(), obj.getDouble(names[i]));
			}
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}

	/**
	 * @see exchange.Exchange#placeOrder(market.CoinPair, double, double)
	 * @return the wrong order_id
	 */
	public String placeOrder(CoinPair pair, double rate, double amount) {
		try {
			int type;
			Collection<CoinPair> pairList = tickerMap.keySet();
			
			if(pairList.contains(pair))
				type = Trade.SELL;
			else if(pairList.contains(pair.swap())) {
				pair = pair.swap();
				type = Trade.BUY;
				
				// correct amount and rate
				amount *= rate;
				rate = 1/rate;
			}
			else return null;
			
			
			// generate function parameters
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair( "pair", pair.toString() ));
			params.add(new BasicNameValuePair( "type", ""+Trade.typeToString(type) ));
			params.add(new BasicNameValuePair( "rate", ""+rate ));
			params.add(new BasicNameValuePair( "amount", ""+amount ));
			
			//System.exit(0);
			
			// post and detect failure
			JSONObject responseObj = post(params, "placeorder", prop.getProperty("urlPrefix") + "placeorder");
			write(responseObj.toString(4));
			
			if(responseObj == null)
				return null;
			
			if(!responseObj.getBoolean("result"))
				return null;
			
			return ""+responseObj.getLong("order_id");
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return null;
		}
		
	}
	
	
	public boolean cancelOrder(String orderID) {
		try {
			// generate function parameters
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair( "order_id", orderID ));
			
			// post and return null if it fails
			JSONObject responseObj = post(params, "cancelorder", prop.getProperty("urlPrefix") + "cancelorder");
			System.out.println(responseObj.toString(4));
			return responseObj.getBoolean("result");
			
		} catch(Exception e) {
			return false;
		}
	}
	
	
	/**
	 * @return a list of all open orders
	 */
	@Override
	public List<String> getOpenOrders() {
		JSONArray orderArr = getOpenOrderList();
		List<String> orderList = new ArrayList<String>();
		
		for(int i = 0; i < orderArr.length(); i++) {
			orderList.add( orderArr.getJSONObject(i).getInt("id") + "" );
		}
		
		return orderList;
	}

	private JSONArray getOpenOrderList() {
		return post(null, "orderlist", prop.getProperty("urlPrefix") + "orderlist").getJSONArray("orders");
	}
	
	
	public static void main(String[] args) throws IOException {
		Bter bter = testSlaves();
		bter.placeOrder(new CoinPair("doge_btc"), 8000.45464864864864891856620*SATOSHI, 150.9848641564547777636666);
		System.exit(0);
		/*
		 * 
		 */
		// place order
		//bter.cancelOrder(8308972);
		//bter.getOpenOrders();
	}


	private static Bter testSlaves() throws IOException {
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 2);
		
		String[] validCoins = {"btc", "ltc", "cny", "doge"};
		List<String> validCoinList = General.arrayToList(validCoins);
		Bter bter = new Bter();
		
		// add requests
		RequestDistributor rd = new RequestDistributor(sts, 15000);
		rd.add(bter.updateTickersRequest());
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
		
		bter.updateTickers(rd.get(0));
		
		// compute BTC volume
		bter.computeTickerVolBTC();
		
		// find all markets with volume above 10 BTC
		Map<CoinPair, Ticker> tickerMap = bter.tickerMap;
		
		List<Ticker> tickerList = new ArrayList<Ticker>(tickerMap.values());
		Collections.sort(tickerList);
		
		for(Ticker ticker : tickerList) {
			double volBTC = ticker.volBTC;
			if(volBTC >= 0.01)
				System.out.format("%-10s%10s%10.5f\n", bter.toString(), ticker.pair.toString(), volBTC);
		}
		
		
		
		/*
		 * depth
		 */
		
		// form request list
		List<CoinPair> validPairs = bter.getValidPairs(validCoinList);
		rd = new RequestDistributor(sts, 15000);
		for(CoinPair key : validPairs) {
			rd.add( bter.updateDepthRequest(key) );
		}
		
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
				
		
		// update depth
		for(Request request : rd.getRequestList())
			bter.updateDepth(request);
		
		// compute BTC volumes
		bter.computeVolBTC();
		System.out.println("Computed btc volumes");
		System.out.println(bter.transferMap.get(new CoinPair("btc", "ltc")));
		System.out.println(bter.transferMap.get(new CoinPair("ltc", "btc")));
		
		return bter;
	}
	

}
