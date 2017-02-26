package util;


import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;

/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 1/7/14
 * Time: 6:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class HttpUtilKraken {
	
	public static Charset[] charsets = {
			StandardCharsets.ISO_8859_1, 
			StandardCharsets.US_ASCII,
			StandardCharsets.UTF_8,
			StandardCharsets.UTF_16,
			StandardCharsets.UTF_16BE,
			StandardCharsets.UTF_16LE,
			};

	public static String hashWithSha256(String preimage) {
		System.out.println("preimage=" + preimage);
		try {
		    MessageDigest md = MessageDigest.getInstance("SHA-256");
	
	        byte[] hash;
			try {
				hash = md.digest(preimage.getBytes());
				System.out.format("%-20s hash = %s\n", "default", Arrays.toString(hash));
				for(Charset charset : charsets) {
					hash = md.digest(preimage.getBytes(charset.toString()));
					System.out.format("%-20s hash = %s\n", charset, Arrays.toString(hash));
				}
				
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
	
	        StringBuffer sb = new StringBuffer();
	        for(byte b : hash) {
	            sb.append(String.format("%02x", b));
	        }
	        return new String(hash);
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
    public static String encryptWithHmacSha512(String data, String secret) {

        Mac mac;
        SecretKeySpec key;
        
        byte[] bytes;
		try {
			//bytes = secret.getBytes("UTF-8");
			//bytes = DatatypeConverter.parseBase64Binary(secret);
			//byte[] decodedSecret = Base64.decodeBase64(secret);
			bytes = Base64.decodeBase64(secret);
			key = new SecretKeySpec(bytes, "HmacSHA512" );
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
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
            byte[] doFinal = mac.doFinal(data.getBytes());
            //return Hex.encodeHexString(doFinal);
        	//return DatatypeConverter.printBase64Binary(doFinal);
            return Base64.encodeBase64String(doFinal);
        } catch (Exception e) {
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
