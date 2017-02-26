package market;



/**
 * @author Mike
 * holds the exchange and the trading pair
 */
public class CoinPair {
	private String coin1, coin2;
	
	/**
	 * Creates a Pair object where <code>pairString</code> is formatted [coin1]_[coin2]
	 * @param pairString
	 */
	public CoinPair(String pairString) {
		String[] coins = pairString.split("_");
		coin1 = coins[0];
		coin2 = coins[1];
		lowerCase();
	}
	
	public CoinPair(String coin1, String coin2) {
		this.coin1 = coin1;
		this.coin2 = coin2;
		lowerCase();
	}
	
	public void lowerCase() {
		coin1 = coin1.toLowerCase();
		coin2 = coin2.toLowerCase();
	}

	public String getCoin1() {return coin1;}
	public String getCoin2() {return coin2;}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return coin1 + "_" + coin2;
	}
	
	public String toBtcChinaString() {
		return coin1+coin2;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		CoinPair p = (CoinPair)o;
		return toString().equals( p.toString() );
	}
	
	/**
	 * @return a new CoinPair with <code>coin1</code> and <code>coin2</code> swapped
	 */
	public CoinPair swap() {
		return new CoinPair(coin2, coin1);
	}
	
}
