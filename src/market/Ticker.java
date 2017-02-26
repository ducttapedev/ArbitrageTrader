package market;

import org.json.JSONObject;

import exchange.Exchange;

public class Ticker implements Comparable<Ticker> {
	
	public CoinPair pair;
	public double last, high, low, avg, sell, buy, vol1, vol2, volBTC;
	public long time;
	
	
	/**
	 * Constructs a ticker based on the JSONObject <code>tickerObj</code> for an exchange between <code>coin1</code> and <code>coin2</code>
	 * @param obj
	 * @param coin1
	 * @param coin2
	 */
	public Ticker(JSONObject obj, int exchange, CoinPair pair) {
		this.pair = pair; 
		
		switch(exchange) {
		case Exchange.BTER:
			last = obj.getDouble("last");
			high = obj.getDouble("high");
			low = obj.getDouble("low");
			avg = obj.getDouble("avg");
			
			vol1 = obj.getDouble("vol_" + pair.getCoin1());
			vol2 = obj.getDouble("vol_" + pair.getCoin2());
			
			// bter lists buy/sell in order's perspective
			// we switch them to our perspective
			sell = obj.getDouble("buy");
			buy = obj.getDouble("sell");
			break;
			
		case Exchange.BTCE:
			high = obj.getDouble("high");
			low = obj.getDouble("low");
			avg = obj.getDouble("avg");
			last = obj.getDouble("last");
			buy = obj.getDouble("buy");
			sell = obj.getDouble("sell");
			
			vol1 = obj.getDouble("vol_cur");
			vol2 = vol1*avg;
			
			time = obj.getLong("server_time");
			break;
			
		case Exchange.VIRCUREX:
			buy = obj.getDouble("lowest_ask");
			sell = obj.getDouble("highest_bid");
			last = obj.getDouble("last_trade");
			
			// no avg reported, so just use the last trade
			avg = last;
			
			vol1 = obj.getDouble("volume");
			vol2 = vol1*avg;
			
			break;
			
		case Exchange.KRAKEN:
			//TODO
			break;
			
		case Exchange.CRYPTSY:
			buy = obj.getJSONArray("sellorders").getJSONObject(0).getDouble("price");
			sell = obj.getJSONArray("buyorders").getJSONObject(0).getDouble("price");
			last = obj.getJSONArray("recenttrades").getJSONObject(0).getDouble("price");
			
			// no avg reported, so just use the last trade
			avg = last;
			
			vol1 = obj.getDouble("volume");
			vol2 = vol1*avg;
			
			break;
			
		default: throw new IllegalArgumentException("unrecognized exchange");
		
		}
	}


	@Override
	public int compareTo(Ticker t) {
		if(volBTC > t.volBTC)
			return 1;
		if(volBTC < t.volBTC)
			return -1;
		return 0;
	}
	
	
	
}
