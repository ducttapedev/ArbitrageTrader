package exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import market.CoinPair;
import market.Trade;
import market.Transfer;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import util.Request;
import util.RequestDistributor;
import util.SlaveThreadSpawner;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class Kraken extends Exchange {
	private static final File PROPERTIES_FILE = new File("kraken.prop");
	List<CoinPair> pairList = new ArrayList<CoinPair>();
	BiMap<String, String> coinTranslations = getCoinTranslations();
	BiMap<String, String> longCoinTranslations = getLongCoinTranslations();
	
	/**
	 * @return mapping from Kraken coin abbreviations to general coin abbreviation
	 */
	private static BiMap<String, String> getCoinTranslations() {
		BiMap<String, String> map = HashBiMap.create();

		map.put("LTC", "ltc");
		map.put("NMC", "nmc");
		map.put("XBT", "btc");
		map.put("XDG", "doge");
		map.put("XRP", "xrp");
		map.put("XVN", "ven");
		map.put("EUR", "eur");
		map.put("GBP", "gbp");
		map.put("KRW", "krw");
		map.put("USD", "usd");
		
		return map;
	}
	
	
	
	/**
	 * @return mapping from Kraken coin abbreviations to general coin abbreviation
	 */
	private static BiMap<String, String> getLongCoinTranslations() {
		BiMap<String, String> map = HashBiMap.create();
		
		map.put("XLTC", "ltc");
		map.put("XNMC", "nmc");
		map.put("XXBT", "btc");
		map.put("XXDG", "doge");
		map.put("XXRP", "xrp");
		map.put("XXVN", "ven");
		map.put("ZEUR", "eur");
		map.put("ZGBP", "gbp");
		map.put("ZKRW", "krw");
		map.put("ZUSD", "usd");
				
		return map;
	}
	
	public Kraken() throws IOException {
		super(Exchange.KRAKEN, PROPERTIES_FILE);
		
	}
	
	
	/**
	 * @return GET Request to update the list of trading pairs
	 */
	public Request updateTradingPairsRequest() {
		String url = prop.getProperty("pairsUrl");
		return new Request(url, exchangeID, null);
	}
	
	/**
	 * @return true if trading pairs were successfully updated
	 */
	public boolean updateTradingPairs(Request request) {
		try {
			JSONObject obj = new JSONObject(request.getResponse());
			
			// if the error is not empty, return false
			if( obj.getJSONArray("error").length() != 0 )
				return false;
			
			obj = obj.getJSONObject("result");
			
			for(String krakenPairName8 : JSONObject.getNames(obj)) {
				String krakenPairName6 = obj.getJSONObject(krakenPairName8).getString("altname");
				
				String coin1 = coinTranslations.get( krakenPairName6.substring(0, 3) );
				String coin2 = coinTranslations.get( krakenPairName6.substring(3, 6) );
				
				CoinPair pair = new CoinPair(coin1, coin2);
				System.out.println(pair);
				pairList.add(pair);
			}
			
			
			return true;
		} catch(Exception e) {
			return error(e);
		}
	}

	/**
	 * @param pairList
	 * @return list of requests for updating the depth
	 */
	public List<Request> updateDepthRequest(List<CoinPair> pairList) {
		List<Request> result = new ArrayList<Request>();
		
		for(CoinPair pair : pairList) {
			result.add(updateDepthRequest(pair));
		}
		
		return result;
	}
	
	private Request updateDepthRequest(CoinPair normalPair) {
		// get the normal coin names
		BiMap<String, String> map = coinTranslations.inverse();
		String coin1 = normalPair.getCoin1();
		String coin2 = normalPair.getCoin2();
		
		// use the translator to generate the kraken pair
		String krakenPairString = map.get(coin1) + map.get(coin2);
		return new Request(prop.getProperty("depthUrlPrefix") + krakenPairString, exchangeID, krakenPairString);
	}

	/**
	 * @param requestList
	 * @return true if depths were succesfully updated
	 */
	public boolean updateDepth(List<Request> requestList) {
		boolean result = true;
		
		for(Request r : requestList) {
			if(!updateDepth(r))
				result = false;
		}
		
		return result;
	}
	
	public boolean updateDepth(Request request) {
		try {
			// grab the actual depth object
			JSONObject depthObj = new JSONObject(request.getResponse());
			if(DEBUG) {
				//System.out.println(depthObj.toString(4));
			}
			
			// use the long kraken name (e.g. XXBTXLTC) to get the bid/ask object
			depthObj = depthObj.getJSONObject("result");
			String longName = JSONObject.getNames(depthObj)[0];
			//System.out.println(longName);
			depthObj = depthObj.getJSONObject(longName);
			
			
			/*
			 * Convert kraken pair back to normal pair
			 */
			String krakenPairString = request.userData;
			
			// get the normal coin names
			BiMap<String, String> map = coinTranslations;
			String coin1 = krakenPairString.substring( 0, krakenPairString.length()/2 );
			String coin2 = krakenPairString.substring( krakenPairString.length()/2, krakenPairString.length() );
			
			// use the translator to generate the kraken pair
			String normalString = map.get(coin1) + "_" + map.get(coin2);
			
			
			System.out.println(normalString);
			CoinPair key = new CoinPair( normalString );
			
			
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

	private JSONObject post(List<NameValuePair> params, String method) {
		try {
			if(DEBUG && params != null) {
				for(NameValuePair nvp : params) {
					System.out.println(nvp.getName() + "\t" + nvp.getValue());
				}
			}
			
			
			// prevents null pointer exception if there are no params
			if(params == null)
				params = new ArrayList<NameValuePair>();
			
			PrintWriter writer = new PrintWriter(PYTHON_PATH + "kraken_input.txt");
			
			// write method
			writer.write(method + "\n");
			
			// write num of params
			writer.write(params.size() + "\n");
			
			// write each name,value pair
			for(NameValuePair param : params) {
				writer.write(param.getName() + " " + param.getValue() + "\n");
			}
			
			// ensure we are done writing the file
			writer.close();
			
			// call python to send POST request to kraken with the specified inputs
			String text = pythonPost();
			
			// convert to JSON and return
			JSONObject obj = new JSONObject(text);
			if(DEBUG)
				System.out.println(obj.toString(4));
			return obj;
		} catch(Exception e) {
			e.printStackTrace(out);
			write("\n");
			return null;
		}
	}
	
	public void print(InputStream in) {
		String result = "";
		
		Scanner scan = new Scanner(in);
		while(scan.hasNextLine()) {
			System.out.println(scan.nextLine());
		}
		
	}
	
	@Override
	public double applyFee(CoinPair key, double principal) {
		// TODO Auto-generated method stub
		return principal*(1 - 0.2/100);
	}

	@Override
	public boolean updateFunds() {
		try {
			// TODO Auto-generated method stub 
			JSONObject obj = post(null, "Balance").getJSONObject("result");
			funds.clear();
			
			for(String coin : JSONObject.getNames(obj)) {
				String key = longCoinTranslations.get(coin);
				double value = obj.getDouble(coin);
				funds.put( key, value );
				
				if(DEBUG)
					System.out.println(key + ", " + value);
				
			}
			
			return true;
			
		} catch(Exception e) {
			return error(e);
		}
	}

	public String placeMarketOrder(CoinPair pair, double rate, double amount) {
		try {
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
			
			// translate from other exchanges to kraken (e.g. btc to XXBT)
			BiMap<String, String> map = coinTranslations.inverse();
			String coin1 = map.get(pair.getCoin1());
			String coin2 = map.get(pair.getCoin2());
			
			
			
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("pair", coin1 + coin2));
			params.add(new BasicNameValuePair( "type", Trade.typeToString(type).toLowerCase() ));
			params.add(new BasicNameValuePair("ordertype", "market"));
			params.add(new BasicNameValuePair("volume", ""+amount));
			//params.add(new BasicNameValuePair("validate", "true"));
			
			
			JSONObject obj = post(params, "AddOrder");
			return obj.getString("txid");
			
		} catch(Exception e) {
			error(e);
			return null;
		}
	}

	@Override
	public String placeOrder(CoinPair pair, double rate, double amount) {
		
		
		try {
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
			
			// translate from other exchanges to kraken (e.g. btc to XBT)
			BiMap<String, String> map = coinTranslations.inverse();
			String coin1 = map.get(pair.getCoin1());
			String coin2 = map.get(pair.getCoin2());
			
			
			
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("pair", coin1 + coin2));
			params.add(new BasicNameValuePair( "type", Trade.typeToString(type).toLowerCase() ));
			
			// TODO: ordertype should be "market" for arbitrage purposes  
			params.add(new BasicNameValuePair("ordertype", "limit"));
			params.add(new BasicNameValuePair("price", "" + rate));
			params.add(new BasicNameValuePair("volume", ""+amount));
			//params.add(new BasicNameValuePair("validate", "true"));
			
			
			JSONObject obj = post(params, "AddOrder");
			write(obj.toString(4));
			
			
			return obj.getJSONObject("result").getJSONArray("txid").getString(0);
			
		} catch(Exception e) {
			error(e);
			return null;
		}
	}

	@Override
	public boolean cancelOrder(String orderID) {
		try {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("txid", orderID));
			//params.add(new BasicNameValuePair("validate", "true"));
			
			JSONObject obj = post(params, "CancelOrder");
			
			// return true if at least one order was cancelled
			return obj.getInt("count") > 0;
			
		} catch(Exception e) {
			error(e);
			return false;
		}
	}

	@Override
	public List<String> getOpenOrders() {
		//throw new RuntimeException("Don't get fucking open orders from Kraken");
		
		// TODO Auto-generated method stub
		try {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("trades", "true"));
			
			// post and grab the json object of all orders
			JSONObject obj = post(params, "OpenOrders");
			obj = obj.getJSONObject("result").getJSONObject("open");
			
			// build list of all orders
			List<String> orderList = new ArrayList<String>();
			
			for(String orderID : JSONObject.getNames(obj)) {
				orderList.add(orderID);
			}
			
			return orderList;
			
		} catch(Exception e) {
			//error(e);
			return null;
		}
		
	}
	
	
	public static void main(String[] args) throws IOException {
		/*
		String url = "https://api.kraken.com/0/public/AssetPairs";
		String text = Website.getHTML(url);
		
		Request r = new Request(null, Exchange.KRAKEN, null);
		r.response = text;
		*/
		/*
		Kraken kraken = new Kraken();
		Request r = new Request(null, Exchange.KRAKEN, null);
		kraken.updateTradingPairs(r);
		kraken.placeOrder(new CoinPair("btc", "ltc"), 38, 0.01);
		*/
		
		
		
		
		
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 2);
		
		Kraken kraken = new Kraken();
		
		
		// add requests
		RequestDistributor rd = new RequestDistributor(sts, 15000);
		//rd.add(kraken.updateDepthRequest( new CoinPair("btc", "doge") ));
		rd.add(kraken.updateTradingPairsRequest());
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
		//kraken.updateDepth(rd.get(0));
		kraken.updateTradingPairs(rd.get(0));
		
		
		//kraken.placeOrder(new CoinPair("btc", "ltc"), 38, 0.01);
		System.out.println("===");
		System.out.println(kraken.placeOrder(new CoinPair("doge", "btc"), 140.56878569678959448799361*SATOSHI, 8001.48979789486415641599879));
		System.out.println("===");
		
		List<String> orderList = kraken.getOpenOrders();
		for(String o : orderList)
			System.out.println( o.toString() );
		
		
		System.exit(0);
		
	}

	private static String PYTHON_PATH = "krakenex-master\\examples\\";
	

	private static String pythonPost() throws IOException {
		ProcessBuilder builder = new ProcessBuilder(
	            "cmd.exe", "/c", "cd " + PYTHON_PATH + " && python conditional-close.py");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = r.readLine();
        if(DEBUG)
        	System.out.println(line);
		return line;
	}
	
}
