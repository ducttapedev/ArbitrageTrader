package exchange;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import market.CoinPair;
import market.Trade;
import market.Transfer;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import util.General;
import util.HttpUtil;
import util.Request;
import util.RequestDistributor;
import util.SlaveThreadSpawner;
import util.Website;

public class Btce extends Exchange {
	private static final File PROPERTIES_FILE = new File("btce.prop");
	List<CoinPair> pairList = new ArrayList<CoinPair>();
	
	public Btce() throws IOException {
		super(Exchange.BTCE, PROPERTIES_FILE);
		
	}
	
	HashMap<CoinPair, Double> tradingFees = new HashMap<CoinPair, Double>();
	
	/**
	 * @return requests needed to call <code>updateTrandingFees</code>
	 */
	public List<Request> updateTrandingFeesRequest() {
		List<Request> requestList = new ArrayList<Request>();
		
		for(CoinPair pair : pairList) {
			requestList.add(new Request( prop.getProperty("getPrefix") + pair.toString() + prop.getProperty("feeURLSuffix"), exchangeID, pair.toString() ));
		}
		
		return requestList;
	}
	
	/**
	 * Updates the trading fees for all currency pairs
	 * @return
	 */
	public boolean updateTradingFees(List<Request> requestList) {
		try {
			for(Request request : requestList) {
				JSONObject obj = new JSONObject( request.getResponse() );
				CoinPair pair = new CoinPair(request.userData);
				
				tradingFees.put(pair, obj.getDouble("trade")*0.01);
				System.out.println(pair + "\t" + tradingFees.get(pair));
			}
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	/**
	 * Set all fees to the default 0.2%
	 */
	public void useDefaultFees() {
		for(CoinPair pair : pairList)
			tradingFees.put(pair, 0.002);
	}
	
	/**
	 * @param pair
	 * @param principal
	 * @return trading fee
	 */
	public double applyFee(CoinPair pair, double principal) {
		write("PAIR=" + pair + "\n");
		return principal*(1-tradingFees.get(pair));
	}
	
	public Request updateTradingPairsRequest() {
		String url = prop.getProperty("pairsUrl");
		return new Request(url, exchangeID, null);
	}
	
	/**
	 * @return list of trading pairs
	 */
	public boolean updateTradingPairs(Request request) {
		System.out.println("PAIRS");
		try {
			String text = request.getResponse();
			
			String[] splitText = text.split("https://btc-e.com/api/2/");
			for(int i = 0; i < splitText.length; i++) {
				int index = splitText[i].indexOf("/ticker");
				
				if(index != -1) {
					CoinPair pair = new CoinPair( splitText[i].substring(0, index) );
					addPair(pair);
				}
			}
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}

	public void addPair(CoinPair pair) {
		pairList.add(pair);
	}
	
	private final List<String> bannedCoins = General.arrayToList( new String[]{"usd", "rur", "eur"} );
	
	public List<Request> updateDepthRequest(List<CoinPair> pairList) {
		List<Request> result = new ArrayList<Request>();
		for(CoinPair key : pairList) {
			// skip banned coins
			if(bannedCoins.contains(key.getCoin1()) || bannedCoins.contains(key.getCoin2()))
				continue;
			result.add(updateDepthRequest(key));
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
	
	public Request updateDepthRequest(CoinPair key) {
		String url = prop.getProperty("getUrlPrefix") + key.toString() + prop.getProperty("depthURLSuffix");
		return new Request( url, exchangeID, key.toString() );
	}
	
	public boolean updateDepth(Request request) {
		try {
			JSONObject obj = new JSONObject(request.getResponse());
			CoinPair key = new CoinPair(request.userData);
			
			//depthMap.put(key, new Depth(obj, exchangeID));
			Transfer.updateTransfers(this, key, obj);
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	// TODO: private methods
	
	
	/**
	 * Specialized post method for Btce
	 * @param params
	 * @param method
	 * @param url
	 * @return post response
	 */
	private JSONObject post(List<NameValuePair> params, String method, String url) {
		
		String postResponse = null;

		// add essential params
		if(params == null)
			params = new ArrayList<NameValuePair>();
		
		params.add(new BasicNameValuePair("method", method));
		String nonceString = "" + (long)((System.currentTimeMillis() - 1392342253417L)/100 + 1978698486L);
		//System.out.println(nonceString);
		params.add(new BasicNameValuePair("nonce", nonceString ));

		// post and return the result
		try {
			HttpPost post = new HttpPost(url);
			
	        String postData = HttpUtil.getStringFromPostParams(params);
	        post.addHeader("Key", prop.getProperty("key"));
	        post.addHeader("Sign", HttpUtil.encryptWithHmacSha512(postData, prop.getProperty("secret")));
	        
	        UrlEncodedFormEntity requestBody = new UrlEncodedFormEntity(params, Consts.UTF_8);
	        post.setEntity(requestBody);
	        
	        postResponse = Website.executeRequest(post);
		} catch (IOException e) {
			e.printStackTrace(out);
			write("\n");
			return null;
		}
		
		//String s = Website.post(params, method, url, prop.getProperty("key"), prop.getProperty("secret"));
		if(DEBUG)
			System.out.println(postResponse);
		try {
			JSONObject obj = new JSONObject(postResponse);
			
			if(DEBUG)
				System.out.println(obj.toString(4));
			
			// failure
			if(obj.getInt("success") != 1)
				return null;
			
			// success
			return obj.getJSONObject("return");
		} catch(JSONException e) {
			e.printStackTrace(out);
			write("\n");
			return null;
		}
	}
	
	
	/**
	 * @return the amount of each currency available on this exchange
	 */
	public boolean updateFunds() {
		try {
			JSONObject obj = post(null, "getInfo", prop.getProperty("postPrefix") + "getInfo");
			obj = obj.getJSONObject("funds");
		
			// update all currencies
			funds.clear();
			String[] names = JSONObject.getNames(obj);
			for(int i = 0; i < names.length; i++) {
				funds.put(names[i], obj.getDouble(names[i]));
			}
			return true;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	
	@Override
	public String placeOrder(CoinPair pair, double rate, double amount) {
		// TODO Auto-generated method stub
		try {
		
			// see whether to buy or sell
			int type;
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
			
			String format = String.format("%.5f",rate);
			String format2 = String.format("%.5f", amount);
			System.out.println("==================");
			System.out.println(format);
			System.out.println(format2);
			//System.exit(0);
			// generate function parameters
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair( "pair", pair.toString() ));
			params.add(new BasicNameValuePair( "type", Trade.typeToString(type).toLowerCase() ));
			params.add(new BasicNameValuePair( "rate", format ));
			params.add(new BasicNameValuePair( "amount", format2 ));
			
			for(NameValuePair param : params)
				System.out.println(param.getName() + "\t" + param.getValue());
			
			// post and detect failure
			JSONObject responseObj = post(params, "Trade", prop.getProperty("postPrefix") + "Trade");
			write(responseObj.toString(4));
			
			if(responseObj == null)
				return null;
			
			return ""+responseObj.getLong("order_id");
		
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return null;
		}
	}



	@Override
	public boolean cancelOrder(String orderID) {
		try {
			// TODO Auto-generated method stub
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair( "order_id", orderID ));
			
			// post and detect failure
			JSONObject responseObj = post(params, "CancelOrder", prop.getProperty("postPrefix") + "CancelOrder");
			
			if(responseObj != null)
				return true;
		} catch(Exception e) {
			return false;
		}
		
		return false;
	}
	
	@Override
	public List<String> getOpenOrders() {
		// TODO Auto-generated method stub
		
		try {
			// send post request
			JSONObject obj = post(null, "ActiveOrders", prop.getProperty("postPrefix") + "ActiveOrders");
			
			// create list of orders
			List<String> orderList = new ArrayList<String>();
			for(String orderID : JSONObject.getNames(obj)) {
				orderList.add(orderID);
			}
		
			return orderList;
		} catch(Exception e) {
			//e.printStackTrace(out);
			//write("\n");
			return null;
		}
	}


	
	
	
	public static void main(String[] args) throws IOException {
		System.out.println((System.currentTimeMillis() - 1392342253417L)/100 + 1978698486L);
		System.out.println(System.currentTimeMillis());
		//System.exit(0);
		
		
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 2);
		
		Btce btce = new Btce();
		
		// add the request for trading pairs
		RequestDistributor rd = new RequestDistributor(sts, 15000);
		
		/*
		rd.add(btce.updateTradingPairsRequest());
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
		
		// update trading pairs
		btce.updateTradingPairs(rd.get(0));
		*/
		
		// only used for btc/ltc trading
		btce.pairList.add(new CoinPair("ltc_btc"));
		btce.useDefaultFees();
		
		String orderID = btce.placeOrder(new CoinPair("ltc_btc"), 1.6093459480967549786985, 0.21308849900432604);
		System.out.println(orderID);
		
		//btce.cancelAllOrders();
		System.exit(0);
		
		
		/*
		 * trade testing
		 *
		//btce.updateFunds();
		//btce.cancelOrder(orderID);
		
		List<OurOrder> orderList = btce.getOpenOrders();
		for(OurOrder order : orderList) {
			System.out.println(order);
		}
		
		
		
		System.exit(0);
		
		/*
		 * 
		 */
		// add the requests for depths
		rd = new RequestDistributor(sts, 15000);
		for(CoinPair pair : btce.pairList) {
			rd.add(btce.updateDepthRequest(pair));
		}
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
		
		// update depths
		for(Request r : rd.getRequestList()) {
			btce.updateDepth(r);
		}
		
		btce.computeVolBTC();
		System.out.println("Computed btc volumes");
		System.out.println(btce.transferMap.get(new CoinPair("btc", "ltc")));
		System.out.println(btce.transferMap.get(new CoinPair("ltc", "btc")));
		
		for(CoinPair key : btce.transferMap.keySet()) {
			System.out.println(key);
		}
		
		
	}
	
	
}
