package util;


import org.apache.commons.codec.binary.Hex;
import org.apache.http.NameValuePair;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 1/7/14
 * Time: 6:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class HttpUtil {

	public static String hashWithSha256(String preimage) {
		try {
		    MessageDigest md = MessageDigest.getInstance("SHA-256");
	
	        byte[] hash = md.digest(preimage.getBytes());
	
	        StringBuffer sb = new StringBuffer();
	        for(byte b : hash) {
	            sb.append(String.format("%02x", b));
	        }
	        return sb.toString();
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
    public static String encryptWithHmacSha512(String data, String secret) {

        Mac mac;
        SecretKeySpec key;

        // Create a new secret key
        try {
            key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA512" );
        } catch(UnsupportedEncodingException uee) {
            System.err.println("Unsupported encoding exception: " + uee.toString());
            return null;
        }

        // Create a new mac
        try {
            mac = Mac.getInstance("HmacSHA512");
        } catch(NoSuchAlgorithmException nsae) {
            System.err.println("No such algorithm exception: " + nsae.toString());
            return null;
        }

        // Init mac with key.
        try {
            mac.init(key);
        } catch(InvalidKeyException ike) {
            System.err.println("Invalid key exception: " + ike.toString());
            return null;
        }

        try {
            return Hex.encodeHexString(mac.doFinal(data.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            System.err.println("Cannot get bytes from string");
            return null;
        }


    }

    public static String getStringFromPostParams(List<NameValuePair> params) {
        StringBuilder postData = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            NameValuePair param = params.get(i);
            if (i > 0)
                postData.append("&");
            postData.append(param.getName()).append("=").append(param.getValue());
        }
        return postData.toString();
    }
    
    
}
