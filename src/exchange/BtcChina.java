package exchange;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import market.CoinPair;
import market.Trade;
import market.Transfer;

import org.json.JSONArray;
import org.json.JSONObject;

import util.Request;

public class BtcChina extends Exchange {

	private static final File PROPERTIES_FILE = new File("btcchina.prop");
	private List<CoinPair> pairList = new ArrayList<CoinPair>();
	
	public BtcChina() throws IOException {
		super(Exchange.BTC_CHINA, PROPERTIES_FILE);
		pairList.add(new CoinPair("btc_cny"));
		pairList.add(new CoinPair("ltc_cny"));
		pairList.add(new CoinPair("ltc_btc"));
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
		String pairString = key.toBtcChinaString();
		return new Request(prop.getProperty("depthUrlPrefix") + pairString, exchangeID, key.toString());
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
			write("Depth error\n");
			write(request.userData + "\n");
			write(request.getResponse() + "\n");
			e.printStackTrace(out);
			write("\n");
			return false;
		}
	}
	
	
	@Override
	public double applyFee(CoinPair key, double principal) {
		// NO FEES
		return principal;
	}

	@Override
	public boolean updateFunds() {
		// TODO Auto-generated method stub
		try {
			String code = "bc.get_account_info()";
			String result = pythonPost(code);
			
			JSONObject obj = new JSONObject(result);
			obj = obj.getJSONObject("result").getJSONObject("balance");
			
			funds.clear();
			
			funds.put("cny", obj.getJSONObject("cny").getDouble("amount"));
			funds.put("ltc", obj.getJSONObject("ltc").getDouble("amount"));
			funds.put("btc", obj.getJSONObject("btc").getDouble("amount"));
			
			return true;
		} catch(Exception e) {
			return error(e);
		}
	}

	@Override
	public String placeOrder(CoinPair pair, double rate, double amount) {
		String result = "";
		
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
			
			//System.out.println(amount + "," + rate);
			//System.exit(0);
			
			// create the code to be executed in python
			String code = null;
			if(type == Trade.SELL)
				code = "bc.sell(" + rate + "," + amount + "," + "\'" + pair.toBtcChinaString() + "\')";
			else if(type == Trade.BUY)
				code = "bc.buy(" + rate + "," + amount + "," + "\'" + pair.toBtcChinaString() + "\')";
			else return null;
			
			// execute the code and return the id
			result = pythonPost(code);
			JSONObject obj = new JSONObject(result);
			
			if(DEBUG)
				System.out.println(obj.toString(4));
			
			writeln("Placed order:");
			writeln(obj.toString(4));
			
			return obj.getInt("result") + "";
		} catch(Exception e) {
			writeln("result =");
			writeln(result);
			e.printStackTrace(out);
			write("\n");
			return null;
		}
		
	}

	@Override
	public int cancelAllOrders() {
		try {
			/*
			 * get all open orders
			 */
			
			String code = "bc.get_orders(open_only=True)";
			String result = pythonPost(code);
			JSONObject obj = new JSONObject(result);
			if(DEBUG)
				System.out.println(obj.toString(4));
			
			// cycle through all markets and add all orders to the list
			obj = obj.getJSONObject("result");
			String[] keys = {"order_btccny", "order_ltccny", "order_ltcbtc"};
			String[] markets = {"BTCCNY", "LTCCNY", "LTCBTC"};
			List<String>[] orderList = new ArrayList[keys.length];
			
			for(int i = 0; i < keys.length; i++) {
				orderList[i] = new ArrayList<String>();
				JSONArray orderArr = obj.getJSONArray(keys[i]);
				
				for(int j = 0; j < orderArr.length(); j++) {
					orderList[i].add(orderArr.getJSONObject(j).getInt("id") + "");
				}
				
			}
			
			/*
			 * cancel all open orders
			 */
			int count = 0;
			
			for(int i = 0; i < orderList.length; i++) {
				for(String orderID : orderList[i]) {
					try {
						String response = pythonPost("bc.cancel(" + orderID + ",\"" + markets[i] + "\")");
						count++;
						write(new JSONObject(response).toString(4));
					} catch(Exception e) {
						System.err.println("Error cancelling order: " + orderID + " for " + markets[i] + " at " + toString());
					}
				}
			}
			
			return count;
		} catch(Exception e) {
			return 0;
		}
	}



	@Override
	public boolean cancelOrder(String orderID) {
		throw new RuntimeException("Cannot cancel orders directly - market must be specified!");
	}
	
	@Override
	public List<String> getOpenOrders() {
		try {
			
			List<String> orderList = new ArrayList<String>();
			
			String code = "bc.get_orders(open_only=True)";
			String result = pythonPost(code);
			JSONObject obj = new JSONObject(result);
			if(DEBUG)
				System.out.println(obj.toString(4));
			
			// cycle through all markets and add all orders to the list
			obj = obj.getJSONObject("result");
			String[] keys = {"order_btccny", "order_ltccny", "order_ltcbtc"};
			
			for(int i = 0; i < keys.length; i++) {
				JSONArray orderArr = obj.getJSONArray(keys[i]);
				
				for(int j = 0; j < orderArr.length(); j++) {
					orderList.add(orderArr.getJSONObject(j).getInt("id") + "");
				}
				
			}
			
			return orderList;
		} catch(Exception e) {
			return null;
		}
	}
	
	private static final String PYTHON_PATH = "btcchina-master\\";
	
	public String pythonPost(String code) {
		try {
			// write the python code to execute.py
			PrintWriter writer = new PrintWriter(PYTHON_PATH + "execute.py");
			
			writer.write("import btcchina\n");
			writer.write("access_key=\"" + prop.getProperty("access_key") + "\"\n");
			writer.write("secret_key=\"" + prop.getProperty("secret_key") + "\"\n");
			writer.write("bc = btcchina.BTCChina(access_key,secret_key)\n");
			writer.write("result = " + code + "\n");
			writer.write("print result\n");
			
			writer.close();
			
			// run execute.py and grab the output
			ProcessBuilder builder = new ProcessBuilder(
		            "cmd.exe", "/c", "cd " + PYTHON_PATH + " && python execute.py");
	        builder.redirectErrorStream(true);
	        Process p = builder.start();
	        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        String line = r.readLine();
	        if(DEBUG)
	        	System.out.println(line);
			return line;
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			error(e);
			return null;
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		BtcChina b = new BtcChina();
		b.cancelAllOrders();
		System.exit(0);
		//b.placeOrder(new CoinPair("cny_btc"), 0.0005, 0.22);
		b.updateFunds();
		List<String> orderList = b.getOpenOrders();
		for(String s : orderList)
			System.out.println(s);
		
		for(String coin : b.funds.keySet()) {
			System.out.println(b.funds.get(coin) + "\t " + coin);
		}
		
	}

}
