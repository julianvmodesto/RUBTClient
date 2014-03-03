/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;

import edu.rutgers.cs.cs352.bt.RUBTClient;
import edu.rutgers.cs.cs352.bt.RUBTClient.Event;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;
import edu.rutgers.cs.cs352.bt.util.ToolKit;
/**
 * @author Gaurav Kumar
 *
 */
public class TrackerCommunicator {
	private final static Logger LOGGER = Logger.getLogger(TrackerCommunicator.class.getName());
	private static final byte[] GROUP = {'G','P','O','1','6'};

	/**
	 * @author Jeffrey Rocha, Gaurav Kumar
	 * @throws Exception
	 */
	public static Map getRequest(int port, String event) throws Exception{

		String eventString = event;
		String request = RUBTClient.torrent_info.announce_url.toString();
		
		request += "?info_hash=";
		request += byteToURL(RUBTClient.torrent_info.info_hash.array());
		request += "&peer_id=";
		request += byteToURL(RUBTClient.myPeerId);
		request += "&port=";
		request += port;
		request += "&downloaded=";
		request += RUBTClient.downloaded;
		request += "&left=";
		request += RUBTClient.left;
		request += "&event=";
		request += eventString;
		System.out.println("GET Request: "+request);
		URL url = new URL(request);
		HttpURLConnection con = (HttpURLConnection) url.openConnection(); 
		con.setRequestMethod("GET");
		//response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();

		BufferedReader in = new BufferedReader(
				new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		byte[] response_bytes = response.toString().getBytes();
		ToolKit.printMap(((Map)Bencoder2.decode(response_bytes)), 1);
		RUBTClient.left = 0;
		return (Map) Bencoder2.decode(response_bytes);
	}
	
	private final static char[] HEX_CHARS = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	
	private static String byteToURL(byte[] byteArr) {
		
		char[] charArr = new char[byteArr.length*2];
		for(int i=0; i<byteArr.length; i++){
			final int val = (byteArr[i]&0xFF);
			final int charLoc=i<<1;
			charArr[charLoc]=HEX_CHARS[val>>>4];
			charArr[charLoc+1]=HEX_CHARS[val&0x0F];
		}
		String hexString = new String(charArr);
		int len = hexString.length();
		charArr = new char[len+len/2];
		int i=0;
		int j=0;
		while(i<len){
			charArr[j++]='%';
			charArr[j++]=hexString.charAt(i++);
			charArr[j++]=hexString.charAt(i++);
		}
		return new String(charArr);
	}
}
