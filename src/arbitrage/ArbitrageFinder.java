package arbitrage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import market.CoinPair;
import util.General;
import util.LogWriter;
import util.Request;
import util.RequestDistributor;
import util.SlaveThreadSpawner;
import exchange.BtcChina;
import exchange.Btce;
import exchange.Bter;
import exchange.Exchange;
import exchange.Kraken;
import exchange.Vircurex;

public class ArbitrageFinder {

	
	

	private static final long MAX_WAIT = 20*1000;
	
	public static LogWriter errorWriter, graphWriter, arbitrageWriter, lagWriter, fundsWriter;
	
	private static void writeln(String s) {
		write(s + "\n");
	}
	
	private static void write(String s) {
		errorWriter.write(s);
		graphWriter.write(s);
		arbitrageWriter.write(s);
		lagWriter.write(s);
		fundsWriter.write(s);
		
		System.out.print(s);
		
	}
	
	/*
	public static void gwriteln(String s) {
		gwrite(s + "\n");
	}
	
	public static void gwrite(String s) {
		graphWriter.write(s);
	}
	

	public static void awriteln(String s) {
		awrite(s + "\n");
	}
	
	public static void awrite(String s) {
		arbitrageWriter.write(s);
	}
	

	public static void lwriteln(String s) {
		lwrite(s + "\n");
	}
	
	public static void lwrite(String s) {
		lagWriter.write(s);
	}
	
	public static void fwriteln(String s) {
		fwrite(s + "\n");
	}
	
	public static void fwrite(String s) {
		fundsWriter.write(s);
	}
	*/
	
	
	private static boolean error(Exception e) {
		e.printStackTrace(errorWriter);
		errorWriter.write("\n");
		return false;
	}
	
	private static final long DEPTH_MAX_WAIT = 8*1000;
	
	//private static final String LOG_FOLDER = "D:\\Flanscam\\ARBITRAGE\\detailed_logs\\";
	public static String logFolder = "D:\\Flanscam\\ARBITRAGE\\logs\\";
	public static String date;
	
	public static void main(String[] args) throws IOException {
		Scanner in = new Scanner(System.in);
		date = General.getDate();
		logFolder += date + "\\";
		
		// create folder for logs
		boolean success = new File(logFolder).mkdir();
		if(!success) {
			System.err.println("Could not create log folder!");
			System.exit(0);
		}
		
		// logwriters
		errorWriter = new LogWriter(logFolder + date + "_other.log");
		graphWriter = new LogWriter(logFolder + date + "_graph.log");
		arbitrageWriter = new LogWriter(logFolder + date + "_arbitrage.log");
		lagWriter = new LogWriter(logFolder + date + "_lag.log");
		fundsWriter = new LogWriter(logFolder + date + "_funds.log");
		
		
		// slave thread listens for slaves
		SlaveThreadSpawner sts = new SlaveThreadSpawner();
		sts.start();
		while(sts.numSlaves() < 3);
		
		// list of all exchanges
		Exchange[] exchanges = new Exchange[Exchange.NUM_EXCHANGES];
		Btce btce = new Btce();
		Bter bter = new Bter();
		Vircurex vircurex = new Vircurex();
		Kraken kraken = new Kraken();
		BtcChina btcChina = new BtcChina();
		exchanges[bter.exchangeID] = bter;
		exchanges[btce.exchangeID] = btce;
		exchanges[vircurex.exchangeID] = vircurex;
		exchanges[kraken.exchangeID] = kraken;
		exchanges[btcChina.exchangeID] = btcChina;;
		
		/*
		 * pairs and tickers
		 */
		init(sts, btce, bter, vircurex, kraken);
		
		/*
		 *  exchange-specific coin info
		 */
		
		//String[] vircurexCoins = {"btc", "ltc", "doge"};
		String[] vircurexCoins = {"btc"};
		String[] bterCoins = {"btc", "ltc", "cny", "doge"};
		List<String> vircurexCoinList = General.arrayToList(vircurexCoins);
		List<String> bterCoinList = General.arrayToList(bterCoins);
		
		// only trading with btc, ltc, and doge on kraken
		List<CoinPair> krakenPairList = new ArrayList<CoinPair>();
		krakenPairList.add(new CoinPair("btc_ltc"));
		krakenPairList.add(new CoinPair("btc_doge"));
		krakenPairList.add(new CoinPair("ltc_doge"));
		
		// only trading with btc, ltc on btce
		List<CoinPair> btcePairList = new ArrayList<CoinPair>();
		btcePairList.add(new CoinPair("ltc_btc"));
		
		// get all bter pairs where both coins are in bterCoinList
		List<CoinPair> bterPairList = bter.getValidPairs(bterCoinList);
		
		// all btc china pairs
		List<CoinPair> btcChinaPairList = new ArrayList<CoinPair>();
		btcChinaPairList.add(new CoinPair("btc_cny"));
		btcChinaPairList.add(new CoinPair("ltc_cny"));
		btcChinaPairList.add(new CoinPair("ltc_btc"));
		
		//printDailyVolumes(bter, vircurex);
		
		/*
		 * list of coins we have in each exchange 
		 */
		/*
		List<String>[] coinsAvailable = new ArrayList[Exchange.NUM_EXCHANGES];
		for(int i = 0; i < coinsAvailable.length; i++) {
			coinsAvailable[i] = new ArrayList<String>();
		}
		
		coinsAvailable[Exchange.BTCE].add("btc");
		coinsAvailable[Exchange.BTCE].add("ltc");
		
		coinsAvailable[Exchange.BTER].add("btc");
		coinsAvailable[Exchange.BTER].add("ltc");
		coinsAvailable[Exchange.BTER].add("doge");
		coinsAvailable[Exchange.BTER].add("cny");
		
		coinsAvailable[Exchange.VIRCUREX].add("btc");
		coinsAvailable[Exchange.VIRCUREX].add("ltc");
		coinsAvailable[Exchange.VIRCUREX].add("doge");
		
		coinsAvailable[Exchange.KRAKEN].add("btc");
		coinsAvailable[Exchange.KRAKEN].add("ltc");
		coinsAvailable[Exchange.KRAKEN].add("doge");
		*/
		
		double totalProfit = 0;
		
		
		writeln("MAX_WAIT = " + + MAX_WAIT);
		writeln("DEPTH_MAX_WAIT = " + DEPTH_MAX_WAIT);
		writeln("ArbitrageConfig.MINIMUM_PROFIT = " + ArbitrageConfig.MINIMUM_PROFIT);
		writeln("\n\n");
		
		int numIterations = 0;
		int numArbitrages = 0;
		int totalNumCancelledOrders = 0;
		
		
		
		/*
		 * transfers
		 */
		for(int n = 0; n < 10000; n++) {
			boolean hasExecutedArbitrage = false;
			
			try {
				
				sts.kill();
				sts.join();
				sts = new SlaveThreadSpawner();
				sts.start();
				while(sts.numSlaves() == 0);
				Thread.sleep(5*1000);
				
				// update transfers (depth)
				updateAll(sts, btcePairList, bterPairList, vircurexCoinList, krakenPairList, btcChinaPairList, btce, bter, vircurex, kraken, btcChina);
				
				// create a graph for the transfers
				Graph graph = new Graph();
				
				double grandTotalFunds = 0;
				
				/*
				 * process each exchange
				 */
				for(int i = 0; i < exchanges.length; i++) {
					Exchange ex = exchanges[i];
					
					// this exchange doesn't exist
					if(ex == null)
						continue;
					
					// every 20 iterations, write the current funds and keep track of the grand total
					if(numIterations%20 == 0) {
						ex.writeln(ex.fundsToString());
						fundsWriter.writeln(ex.toString());
						fundsWriter.writeln(ex.fundsToString());
						grandTotalFunds += ex.getTotalFunds();
					}
					
					
					
					/*
					 * Accounting for lag
					 *  
					 */
					
					ex.apiSpeed *= 0.8;
					
					if(ex.isUpdated()) {
						// increase the API speed if the exchange updated successfully
						ex.apiSpeed += 0.2;
						
						// only add the exchange if it meets the minimum lag requirements
						if(ex.apiSpeed > 0.80 && ex.orderSpeed > 0.90) {
							graph.addExchange(ex);
						}
					}
					else {
						// failed to update
						lagWriter.writeln(ex + " failed to update!");
					}
					
					lagWriter.writeln(ex + ".apiSpeed = " + String.format("%.5f", ex.apiSpeed));
					
					if(ex.isLagCorrected) {
						lagWriter.writeln(ex + " was corrected for lag!");
					}
				} // for each exchange
				
				// every 20 iterations, write the grand total in funds
				if(numIterations%20 == 0)
					fundsWriter.writeln(String.format( "%12.8f btc (grand total)", grandTotalFunds ));
				
				
				// indicate which exchanges were used
				/*
				graphWriter.write("Exchanges used = ");
				lagWriter.write("Exchanges used = ");
				for(Exchange ex : exchanges) {
					if(ex != null) {
						graphWriter.write(String.format("%-10s ", ex));
						lagWriter.write(String.format("%-10s ", ex));
					}
				}
				graphWriter.writeln();
				lagWriter.writeln();
				
				graphWriter.writeln(graph.toString());
				*/
				//writeln(graph.toString());
				
				// find arbitrage
				System.out.print("Finding arbitrage...");
				Set<ArbitrageLoop> arbitrageSet = graph.findArbitrage();
				System.out.println("Done");
				
				
				// list of all arbitrage configs
				List<ArbitrageConfig> configList = new ArrayList<ArbitrageConfig>();
				
				// find all arbitrage configs
				// print out each arbitrageloop and all its configs
				for(ArbitrageLoop arbitrage : arbitrageSet) {
					List<ArbitrageConfig> tempConfigList = arbitrage.getArbitrageConfigList(exchanges);
					//printArbitrageLoop(arbitrage, tempConfigList);
					
					configList.addAll(tempConfigList);
				}
				
				// execute arbitrage
				if(configList.size() > 0) {
					double profit = executeArbitrage(in, exchanges, configList);
					
					// if profit was 0, then there wasn't really an arbitrage opportunity
					// if profit is positive, update some stats
					if(profit > 0) {
						numArbitrages++;
						hasExecutedArbitrage = true;
						totalProfit += profit;
						writeln("TOTAL PROFIT = " + totalProfit + " BTC");
					}
				}
				
				//printArbitrageConfig(configList);
				
				
			} catch(Exception e) {
				error(e);
			}
			
			// delay 20 seconds
			try {
				System.out.print("Sleeping...");
				Thread.sleep(100*1000);
				System.out.println("Done!");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				System.out.println("Failed to sleep!");
				error(e);
				break;
			}
			
			// cancel all pending orders
			System.out.println("Cancelling all pending orders");
			for(int i = 0; i < exchanges.length; i++) {
				Exchange ex = exchanges[i];
				
				// this exchange doesn't exist
				if(ex == null)
					continue;
				
				ex.orderSpeed *= 0.8;
				
				if(ex.isUpdated()) {
					
					// if there was lag correction, then it's understandable if our orders didn't go through yet, so don't cancel them yet
					if(ex.isLagCorrected) {
						// do not modify order speed (undo multiply by 0.8)
						ex.orderSpeed /= 0.8;
					}
					else {
					
						int numCancelledOrders = ex.cancelAllOrders();
						totalNumCancelledOrders += numCancelledOrders;
						
						if(numCancelledOrders > 0) {
							lagWriter.writeln("Cancelled " + numCancelledOrders + " orders from " + ex);
							arbitrageWriter.writeln("Cancelled " + numCancelledOrders + " orders from " + ex);
							ex.write("Cancelled " + numCancelledOrders + " orders from " + ex + "\n");
						}
						else ex.orderSpeed += 0.2;
					}
				}
				lagWriter.writeln(ex + ".orderSpeed = " + String.format("%.5f", ex.orderSpeed));
				
			}
			System.out.println("Done cancelling orders");
			
			// if we executed arbitrage, write the cancel/arbitrage ratio to the arbitrageWriter
			if(hasExecutedArbitrage) {
				String result = "Cancellations per arbitrage = ";
				result += String.format("%6d / %-6d = %.4f", totalNumCancelledOrders, numArbitrages, totalNumCancelledOrders*1.0/numArbitrages);
				arbitrageWriter.write(result);
			}
			
			// let the logwriters know we are done with this iteration
			numIterations++;
			errorWriter.postIteration(numIterations);
			graphWriter.postIteration(numIterations);
			arbitrageWriter.postIteration(numIterations);
			lagWriter.postIteration(numIterations);
			fundsWriter.postIteration(numIterations);
			for(Exchange ex : exchanges)
				if(ex != null)
					ex.postIteration(numIterations);
		}
		
		
		
		for(Exchange ex : exchanges) {
			if(ex != null)
				ex.finalize();
		}
		errorWriter.close();
		
		System.out.println("TERMINATE");
		System.exit(0);
	}

	/**
	 * Finds the tickers and list of all market pairs for all exchanges
	 * except bter, since we need the same # of API calls for ticker and depth anyway
	 * @param sts
	 * @param btce
	 * @param bter
	 * @param vircurex
	 */
	private static void init(SlaveThreadSpawner sts, Btce btce, Bter bter, Vircurex vircurex, Kraken kraken) {
		boolean success = true;
		RequestDistributor rd;
		// add requests 
		rd = new RequestDistributor(sts, MAX_WAIT);
		rd.add(btce.updateTradingPairsRequest());
		rd.add(bter.updateTickersRequest());
		rd.add(vircurex.updateTickersRequest());
		rd.add(kraken.updateTradingPairsRequest());
		
		// send out requests
		boolean processRequests = rd.processRequests();
		System.out.println("successfully processed requests = " + processRequests);
		success = processRequests;
		List<Request> requestList = rd.getRequestList();
		
		/*
		// update btce pairs and use default fees
		success &= btce.updateTradingPairs(requestList.get(0));
		btce.useDefaultFees();
		*/
		
		// TODO: currently we hard-code BTC-e to use only ltc_btc
		btce.addPair(new CoinPair("ltc_btc"));
		btce.useDefaultFees();
		
		// update bter and vircurex tickers
		success &= bter.updateTickers(requestList.get(1));
		success &= bter.computeTickerVolBTC();
		success &= vircurex.updateTickers(requestList.get(2));
		success &= vircurex.computeTickerVolBTC();
		
		// update kraken pairs
		success &= kraken.updateTradingPairs(requestList.get(3));
		
		// btc-china pairs are hard-coded
		if(!success) {
			System.err.println("Failed to update tickers and coin pairs!");
			System.exit(0);
		}
	}

	private static void updateAll(SlaveThreadSpawner sts,
			List<CoinPair> btcePairs, List<CoinPair> bterPairs, List<String> vircurexCoins, List<CoinPair> krakenPairs, List<CoinPair> btcChinaPairs,
			Btce btce,	Bter bter, Vircurex vircurex, Kraken kraken, BtcChina btcChina) {
		RequestDistributor rd;
		// get request lists for each depth
		List<Request> btceRequestList = btce.updateDepthRequest( btcePairs );
		List<Request> bterRequestList = bter.updateDepthRequest( bterPairs );
		List<Request> vircurexRequestList = vircurex.updateDepthRequest(vircurexCoins);
		List<Request> krakenRequestList = kraken.updateDepthRequest(krakenPairs);
		List<Request> btcChinaRequestList = btcChina.updateDepthRequest(btcChinaPairs);
		
		// add depth requests
		rd = new RequestDistributor(sts, DEPTH_MAX_WAIT);
		rd.addAll(btceRequestList);
		rd.addAll(bterRequestList);
		rd.addAll(vircurexRequestList);
		rd.addAll(krakenRequestList);
		rd.addAll(btcChinaRequestList);
		
		// send out requests
		System.out.println("successfully processed requests = " + rd.processRequests());
				
		// update depths
		btce.updateAll(btceRequestList);
		bter.updateAll(bterRequestList);
		vircurex.updateAll(vircurexRequestList);
		kraken.updateAll(krakenRequestList);
		btcChina.updateAll(btcChinaRequestList);
		
		// compute BTC volume equivalents
		btce.computeVolBTC();
		bter.computeVolBTC();
		vircurex.computeVolBTC();
		kraken.computeVolBTC();
		btcChina.computeVolBTC();
	}

	/**
	 * @param in
	 * @param exchanges
	 * @param configList
	 * @return profit in BTC of this arbitrage
	 */
	private static double executeArbitrage(Scanner in, Exchange[] exchanges,
			List<ArbitrageConfig> configList) {
		// find the best arbitrage
		ArbitrageConfig.profitPower = 1.75;
		Collections.sort(configList);
		if(configList.size() == 0)
			return 0;
		ArbitrageConfig config = configList.get(configList.size() - 1);
		
		// execute arbitrage
		config.execute(exchanges);
		
		// write the config for this arbitrage
		arbitrageWriter.writeln(config.toString());
		
		return config.getProfit();
	}
}
