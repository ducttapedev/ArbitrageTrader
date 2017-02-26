package exchange;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import market.CoinPair;
import market.Ticker;
import market.Trade;
import market.Transfer;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import util.HttpUtil;
import util.Request;
import util.RequestDistributor;
import util.SlaveThreadSpawner;
import util.Website;

public class Vircurex extends Exchange {

	private static final File PROPERTIES_FILE = new File("vircurex.prop");

	public Map<CoinPair, Ticker> tickerMap = new HashMap<CoinPair, Ticker>();
	
	public Vircurex() throws IOException {
		super(Exchange.VIRCUREX, PROPERTIES_FILE);
		
	}
	
	private double feePercent = 0.002;

	public double applyFee(CoinPair pair, double principal) {
		return principal*(1-feePercent);
	}

	public List<Request> updateTradingFeesRequest() {
		Request r = new Request(prop.getProperty("tradingFeeUrl"), exchangeID, null);
		List<Request> list = new ArrayList<Request>();
		list.add(r);
		return list;
	
	}
	
	public boolean updateTradingFees(Request request) {
		try {
			JSONObject obj = new JSONObject(request.getResponse());
			feePercent = obj.getDouble("fee");
			return true;
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
				
				System.err.println("coin1=" + coin1 + "\tcoin2=" + coin2 + "\texchange=" + this);
				result = false;
				
			}
			
		}
		return result;
	}
	
	public Request updateTickersRequest() {
		return new Request( prop.getProperty("tickersUrl"), exchangeID, null );
	}
	
	/**
	 * @return true if at least one ticker updated successfully
	 */
	public boolean updateTickers(Request request) {
		try {
			// return true if we update at least one ticker successfully
			boolean result = false;
			
			// get the JSON for the list of tickers
			JSONObject allTickerObj = new JSONObject(request.getResponse());
			JSONObject coin1Obj, tickerObj;
			
			// get the first coin
			for(String coin1 : JSONObject.getNames(allTickerObj)) {
				try {
					coin1Obj = allTickerObj.getJSONObject(coin1);
				} catch(JSONException e) {
					continue;
				}
				
				// get the second coin
				for(String coin2 : JSONObject.getNames(coin1Obj)) {
					try {
						tickerObj = coin1Obj.getJSONObject(coin2);
					} catch(JSONException e) {
						// coin pair doesn't exist
						write("Depth: " + coin1 + "_" + coin2 + " not available");
						continue;
					}
					
					CoinPair key = new CoinPair(coin1, coin2);
					try {
						Ticker ticker = new Ticker( tickerObj, exchangeID, key );
						tickerMap.put(key, ticker);
						result = true;
					} catch(Exception e) {
						// on any error with the trading pair
						write("Depth: " + coin1 + "_" + coin2 + " error decoding JSON");
						e.printStackTrace(out);
						write("\n");
					}
				}
			}
			
			return result;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	public List<Request> updateDepthRequest(List<String> coinList) {
		List<Request> result = new ArrayList<Request>();
		
		for(String coin : coinList) {
			result.add(updateDepthRequest(coin));
		}
		
		return result;
	}
	
	public boolean updateDepth(List<Request> requestList) {
		boolean result = true;
		
		for(Request r : requestList) {
			if(!updateDepth(r))
				result = false;
		}
		
		return result;
	}
	
	public Request updateDepthRequest(String coin) {
		String url = prop.getProperty("depthUrl") + "?alt=" + coin;
		return new Request(url, exchangeID, coin);
		
	}
	/**
	 * Updates the depth for all BTC related exchanges
	 * @param coinList for each <code>coin</code> in <code>coinList</code>, we update the depths of all pairs involving <code>coin</code>
	 * @return true if successful
	 */
	public boolean updateDepth(Request request) {
		boolean result = true;
			
		// try to call GET on the API
		// if anything fails, mark the result as false
		try {
			// read JSON object into memory
			JSONObject allPairObj = new JSONObject(request.getResponse());
			String coin2 = request.userData;
			
			// the coins listed are actually the first coin
			for(String coin1 : JSONObject.getNames(allPairObj)) {
				// verify valid status (=0)
				if(coin1.equals("status")) {
					//verifyStatus(allPairObj);
				}
				// process the depth for this pair
				else {
					try {
						// verify status
						JSONObject pairObj = allPairObj.getJSONObject(coin1);
						//verifyStatus(pairObj);
						
						// create depth object and add to map
						//Depth depth = new Depth(pairObj, exchangeID);
						CoinPair key = new CoinPair(coin1, coin2);
						//depthMap.put(key, depth);
						Transfer.updateTransfers(this, key, pairObj);
					} catch(JSONException e) {
						continue;
					}
				}
			}// for
			
			
		} catch (Exception e) {
			e.printStackTrace(out);;
			write("\n");
			result = false;
		}
		
		
		return result;
	}


	
	
	
	// TODO: private methods
	/**
	 * @return timestamp in Vircurex format
	 */
	public static String getTimestamp() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		String formattedDate = sdf.format(date);
		return formattedDate;
	}
	
	/**
	 * @param tokenParams 
	 * @return hashed token
	 */
	public static String getToken(List<String> tokenParams) {
		String preimage = "";
		
		// semicolon delimited list of token params
		for(int i = 0; i < tokenParams.size(); i++) {
			if(i > 0)
				preimage += ";";
			preimage += tokenParams.get(i);
		}
		
		//System.out.println("tok_preimage = " + preimage);
		return HttpUtil.hashWithSha256(preimage);
	}

	/**
	 * Specialized get method for Vircurex
	 * @param getParams list of "in" parameters not counting the 4 that are always present (account, t, id, token)
	 * @param tokenParams list of token params after "ID"
	 * @param method name of method to call
	 * @return get response
	 */
	private JSONObject get(List<NameValuePair> getParams, List<String> tokenParams, String method) {
		String result = null;

		// initialize params if they are null
		if(getParams == null)
			getParams = new ArrayList<NameValuePair>();
		if(tokenParams == null)
			tokenParams = new ArrayList<String>();
		
		// add the 3 persistent post params that also correspond with params 1-4 for the token
		getParams.add(0,new BasicNameValuePair( "account", prop.getProperty("account") ));
		String timestamp = getTimestamp();
		getParams.add(1,new BasicNameValuePair( "timestamp", timestamp ));
		String transactionID = HttpUtil.hashWithSha256(timestamp + "-" + Math.random());
		getParams.add(2,new BasicNameValuePair( "id", transactionID ));
		
		// form the token by pre-pending persistent token params 0-4
		// YourSecurityWord;YourUserName;Timestamp;ID
		
		tokenParams.add(0, prop.getProperty(method));
		for(int i = 0; i <= 2; i++)
			tokenParams.add(i+1, getParams.get(i).getValue());
		
		// compute token and add to post params
		getParams.add(3,new BasicNameValuePair( "token", getToken(tokenParams) ));
		
		// form the query
		String url = prop.getProperty("apiURL") + method + ".json?";
		for(int i = 0; i < getParams.size(); i++) {
			if(i > 0)
				url += "&";
			NameValuePair param = getParams.get(i);
			url += param.getName() + "=" + param.getValue();
		}
		
		// GET and return the result
		try {
			result = Website.getHTML(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace(out);
			write("\n");
			return null;
		}
		
		if(DEBUG)
			System.out.println(new JSONObject(result).toString(4));
		
		return new JSONObject(result);
	}
	
	
	@Override
	public boolean updateFunds() {
		try {
			List<String> tokenParams = new ArrayList<String>();
			tokenParams.add("get_balances");
			
			JSONObject obj = get(null, tokenParams, "get_balances");
			obj = obj.getJSONObject("balances");
			
			// update all currencies
			funds.clear();
			String[] names = JSONObject.getNames(obj);
			for(int i = 0; i < names.length; i++) {
				funds.put( names[i].toLowerCase(), obj.getJSONObject(names[i]).getDouble("availablebalance") );
			}
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	
	
	/**
	 * Places an order
	 * @param pair
	 * @param type
	 * @param rate
	 * @param amount
	 */
	public String placeOrder(CoinPair pair, double rate, double amount) {
		try {
			// see whether to buy or sell
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
			
			List<String> tokenParams = new ArrayList<String>();
			tokenParams.add("create_order");
			
			List<NameValuePair> getParams = new ArrayList<NameValuePair>();
			getParams.add(new BasicNameValuePair( "ordertype", Trade.typeToString(type) ));
			getParams.add(new BasicNameValuePair( "amount", ""+amount ));
			getParams.add(new BasicNameValuePair( "currency1", pair.getCoin1() ));
			getParams.add(new BasicNameValuePair( "unitprice", ""+rate ));
			getParams.add(new BasicNameValuePair( "currency2", pair.getCoin2() ));
			
			if(DEBUG) {
				for(NameValuePair nvp : getParams) {
					System.out.println(nvp.getName() + "\t" + nvp.getValue());
				}
			}
			
			for(int i = 0; i < getParams.size(); i++) {
				tokenParams.add(getParams.get(i).getValue());
			}
			
			JSONObject obj = get(getParams, tokenParams, "create_released_order");
			//JSONObject obj = get(getParams, tokenParams, "create_order");
			write(obj.toString(4));
			
			if( obj.getInt("status") == 0 )
				return ""+obj.getLong("orderid");
			
			return null;
		
		} catch(Exception e) {
			return null;
		}
		
	}
	
	/**
	 * @param orderID
	 * @return true if the order was successfully cancelled
	 */
	public boolean cancelOrder(String orderID) {
		try {
			List<String> tokenParams = new ArrayList<String>();
			tokenParams.add("delete_order");
			
			List<NameValuePair> getParams = new ArrayList<NameValuePair>();
			getParams.add(new BasicNameValuePair( "orderid", orderID ));
			getParams.add(new BasicNameValuePair( "otype", ""+1 ));
			
			for(int i = 0; i < getParams.size(); i++) {
				tokenParams.add(getParams.get(i).getValue());
			}
			
			JSONObject obj = get(getParams, tokenParams, "delete_order");
			
			return obj.getInt("status") == 0;
		} catch(Exception e) {
			return false;
		}
	}
	
	
	
	/**
	 * @return a list of all open orders
	 */
	@Override
	public List<String> getOpenOrders() {
		try {
			List<String> tokenParams = new ArrayList<String>();
			tokenParams.add("read_orders");
			
			List<NameValuePair> getParams = new ArrayList<NameValuePair>();
			getParams.add(new BasicNameValuePair( "otype", ""+1 ));
			JSONObject obj = get(getParams, tokenParams, "read_orders");
			
			List<String> orderList = new ArrayList<String>();
			
			// read orders until there are no more, which is indicated by a JSONException
			for(int i = 1; true; i++) {
				try {
					JSONObject orderObj = obj.getJSONObject("order-" + i);
					orderList.add(orderObj.getInt("orderid") + "");
					
				} catch(JSONException e) {
					break;
				}
			}
			
			return orderList;
			
		} catch(Exception e) {
			return null;
		}
	}
	
	

	public static void main(String[] args) throws IOException {
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 2);
		
		Vircurex vircurex = new Vircurex();
		//updateDepths(sts, v);
		
		RequestDistributor rd;
		// add requests 
		rd = new RequestDistributor(sts, 15*1000);
		rd.add(vircurex.updateTickersRequest());
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
		
		List<Request> requestList = rd.getRequestList();
		
		// update vircurex tickers
		vircurex.updateTickers(requestList.get(0));
		vircurex.computeTickerVolBTC();
		
		vircurex.cancelAllOrders();
		System.exit(0);;
				
		
		//vircurex.placeOrder(new CoinPair("btc_doge"), 800000, 0.0001);
		String orderID = vircurex.placeOrder(new CoinPair("btc_doge"), 2e7, 0.00000232);
		//vircurex.cancelOrder(orderID);
		//System.exit(0);
		
		/*
		List<OurOrder> orderList = vircurex.getOpenOrders();
		
		for(OurOrder order : orderList) {
			vircurex.cancelOrder(order.orderID);
			System.exit(0);
		}
		*/
	}

	private static void updateDepths(SlaveThreadSpawner sts, Vircurex v) {
		String[] vircurexCoins = {"btc", "ltc", "doge"};
		
		// add the API requests for depth
		List<Request> requestList = new ArrayList<Request>();
		for(String vircurexCoin : vircurexCoins) {
			Request request = v.updateDepthRequest(vircurexCoin);
			requestList.add( request );
		}
		
		// create request distributor
		RequestDistributor rd = new RequestDistributor(sts, 15000);
		for(Request r : requestList)
			rd.add(r);
		
		// send out requests
		System.out.println(requestList.size());
		System.out.println("successfully processed requests = " + rd.processRequests());
		
		for(Request r : requestList)
			System.out.println(r);
		
		// update depth
		for(Request request : requestList)
			v.updateDepth(request);
		
		// compute BTC volumes
		v.computeVolBTC();
		System.out.println("Computed btc volumes");
		System.out.println(v.transferMap.get(new CoinPair("btc", "doge")));
		System.out.println(v.transferMap.get(new CoinPair("doge", "btc")));
	}
	
}
