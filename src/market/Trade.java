package market;

import org.json.JSONObject;

import exchange.Exchange;

public class Trade implements Comparable<Trade> {
	protected long date;
	protected int tid, type;
	protected double price, amount;
	
	public static final int BUY = 0;
	public static final int SELL = 1;
	public static final int UNKNOWN = 2;
	
	public static String typeToString(int type) {
		switch(type) {
		case BUY: return "BUY";
		case SELL: return "SELL";
		default: return null;
		}
		
	}
	
	public Trade() {}
	
	public Trade(JSONObject obj, int exchangeID) {
		switch(exchangeID) {
		case Exchange.BTER:
			date = obj.getLong("date");
			tid = obj.getInt("tid");
			price = obj.getDouble("price");
			amount = obj.getDouble("amount");
			type = obj.getString("type").equals("buy") ? BUY:SELL;
			break;
			
		case Exchange.BTCE:
			date = obj.getLong("date");
			tid = obj.getInt("tid");
			price = obj.getDouble("price");
			amount = obj.getDouble("amount");
			type = obj.getString("trade_type").equals("bid") ? BUY:SELL;
			break;
			
		case Exchange.VIRCUREX:
			date = obj.getLong("date");
			tid = obj.getInt("tid");
			price = obj.getDouble("price");
			amount = obj.getDouble("amount");
			type = UNKNOWN;
			break;
		
		default: throw new IllegalArgumentException("unrecognized exchange");
			
		}
		
	}
	
	@Override
	public boolean equals(Object o) {
		Trade t = (Trade)o;
		return tid == t.tid; 
	}

	@Override
	public int compareTo(Trade t) {
		return tid - t.tid;
	}
	
	public int hashCode() {
		return tid;
	}
}
