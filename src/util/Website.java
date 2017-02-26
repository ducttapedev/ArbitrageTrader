package util;


import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.apache.http.Consts;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Website {
    public static String getHTML(String urlName) throws IOException {
    	String result = "";
        
    	System.out.println("GET: " + urlName);
    	
        URL url = new URL(urlName);
        URLConnection urlc = url.openConnection();
        urlc.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:6.0a2) Gecko/20110613 Firefox/6.0a2");
        /*
        urlc.addRequestProperty("Accept", "text/html, *(removeThis)/*, q=0.01");
        urlc.addRequestProperty("Accept-Language", "en-US,en;q=0.5");
        urlc.addRequestProperty("Accept-Encoding", "gzip, deflate");
        urlc.addRequestProperty("X-Requested-With", "XMLHttpRequest");
        urlc.addRequestProperty("Referer", "https://coinedup.com/OrderBook");
        */
        
        Scanner in = new Scanner(urlc.getInputStream());
        
        while(in.hasNextLine()){
            result += in.nextLine();
        }
        in.close();
            
        
        return result;
    }
    
    
  
    
    
    public static String executeRequest(HttpUriRequest request) throws IOException {
    	System.out.println("REQUEST " + request.getURI().toURL().toString());
    	
        CloseableHttpResponse resp = null;
        CloseableHttpClient client = HttpClients.createDefault();
        //DefaultHttpClient client = new DefaultHttpClient();
        
        resp = client.execute(request);

        String respString = EntityUtils.toString(resp.getEntity(), Consts.UTF_8);
        return respString;
    }
                
    
    public static void main(String[] args) {
    	String hostname = "graceyunchao.mooo.com";
    	try {
	      InetAddress ipaddress = InetAddress.getByName(hostname);
	      System.out.println("IP address: " + ipaddress.getHostAddress());
	    } catch ( UnknownHostException e ) {
	      System.out.println("Could not find IP address for: " + hostname);
	      e.printStackTrace();
	    }
    }
    
 

}
