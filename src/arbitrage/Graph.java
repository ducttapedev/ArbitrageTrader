package arbitrage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import market.CoinPair;
import market.SingleExchangeTransfer;
import exchange.Exchange;


public class Graph {
	/**
	 * List of all nodes in the graph
	 */
	private Map<String, Vertex> vertexList;
	/**
	 * List of all edges in the graph
	 */
	private Map<Pair<String,String>, Edge> edgeList;
	
	public Graph() {
		vertexList = new HashMap<String, Vertex>();
		edgeList = new HashMap<Pair<String,String>, Edge>();
	}
	
	public boolean addExchange(Exchange ex) {
		boolean result = true;
		
		// get the transfer map
		Map<CoinPair, SingleExchangeTransfer> transferMap = ex.getTransferMap();
		
		// go through every transfer
		for(CoinPair key : transferMap.keySet()) {
			// add the coins as vertices if they do not already exist
			addVertex(key.getCoin1());
			addVertex(key.getCoin2());
			
			// add the transfer as an edge
			addEdge(key.getCoin1(), key.getCoin2(), transferMap.get(key));
		}
		
		return result;
	}
	
	private boolean addVertex(String coin) {
		// verify that node does not exist
		Vertex node = new Vertex(coin);
		if(vertexList.containsValue(node))
			return false;
		
		// add node to graph
		vertexList.put(coin, node);
		return true;
		
	}

	private boolean addEdge(String origin, String destination, SingleExchangeTransfer transfer) {
		// verify that both nodes exist
		if( !vertexList.containsKey(origin) || !vertexList.containsKey(destination) )
			return false;
		
		// form the key for the edge
		Pair<String,String> key = new Pair<String,String>(origin, destination);
		
		// if the edge already exists, simply add the transfer
		Edge edge = edgeList.get(key);
		if(edge != null) {
			//edge.transfer.addTransfer(transfer);
			edge.addTransfer(transfer);
		}
		// otherwise create a new edge
		else {
			// create edge
			Vertex node1 = vertexList.get(origin);
			Vertex node2 = vertexList.get(destination);
			edge = new Edge(node1, node2, transfer);
			
			CoinPair cp1 = new CoinPair(origin, destination);
			CoinPair cp2 = transfer.key;
			
			if(!cp1.equals(cp2)) {
				System.out.println("EDGE ERROR");
				System.out.println(cp1);
				System.out.println(cp2);
				System.out.println(transfer.toString(5));
			}

			//System.out.println(edge);
			
			// add edge to graph
			edgeList.put(key, edge);
			node1.outwardList.add(edge);
		}
		
		return true;
	}
	
	public Set<ArbitrageLoop> findArbitrage() {
		Set<ArbitrageLoop> loops = new TreeSet<ArbitrageLoop>();
		
		for(Vertex root : vertexList.values()) {
			
			// set "visited" to false for all vertices
			for(Vertex v : vertexList.values()) {
				v.visited = false;
			}
			
			Stack<Edge> partialPath = new Stack<Edge>();
			
			// start DFS on each outward edge of the root
			root.visited = true;
			for(Edge e : root.outwardList) {
				//System.out.println("DONE");
				partialPath.push(e);
				arbitrageSearch(loops, partialPath, root);
				partialPath.pop();
			}
		}

		return loops;
	}
	
	private void arbitrageSearch(Set<ArbitrageLoop> arbitrageLoops, Stack<Edge> partialPath, Vertex root) {
		// find the next vertex on the path
		Vertex currentVector = partialPath.peek().destination;
		currentVector.visited = true;
		
		// find all of its outbound edges
		for(Edge e : currentVector.outwardList) {
			
			// if we haven't visited it before, visit
			if(!e.destination.visited) {
				partialPath.push(e);
				arbitrageSearch(arbitrageLoops, partialPath, root);
				partialPath.pop();
			}
			// otherwise, if we are back at the root, see if we have found arbitrage
			else if(e.destination.equals(root)) {
				// form the trading loop
				List<Edge> tradingList = new ArrayList<Edge>();
				
				// first add the already visited edges
				for(int i = 0; i < partialPath.size(); i++) {
					tradingList.add(partialPath.get(i));
				}
				// add the last edge that loops back to the root
				tradingList.add(e);
				
				// create the loop
				ArbitrageLoop potentialArbitrage = new ArbitrageLoop(tradingList);
				
				// if profitable, add to arbitrage loop
				if(potentialArbitrage.factor > 1)
					arbitrageLoops.add(potentialArbitrage);
			}
		}
		
		currentVector.visited = false;
	}
	
	public String toString() {
		String result = "";
		
		for(Edge e : edgeList.values()) {
			result += e.compositeTransfer.toString();
			result += "\n----------------------------------\n\n";
		}
		
		return result;
	}
	
}
