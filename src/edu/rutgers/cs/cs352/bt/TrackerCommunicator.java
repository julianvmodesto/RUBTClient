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

import edu.rutgers.cs.cs352.bt.RUBTClient;
import edu.rutgers.cs.cs352.bt.RUBTClient.Event;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;
/**
 * @author Gaurav Kumar
 *
 */
public class TrackerCommunicator {
	private final static Logger LOGGER = Logger.getLogger(TrackerCommunicator.class.getName());
	private static final byte[] GROUP = {'G','P','O','1','6'};

	/**
	 * @author Jeffrey Rocha
	 * @throws Exception
	 */
	public static Map getRequest(int port, String event) throws Exception{
		
		System.out.println("announce_url" + RUBTClient.torrent_info.announce_url.toString());
		String urlName = RUBTClient.torrent_info.announce_url.toString(); //makes the URL of the torrentInfo object into a string
		String peerId = RUBTClient.myPeerId.toString();
		String ip = InetAddress.getLocalHost().toString();
		String downloaded = Integer.toString(RUBTClient.downloaded);
		String left = Integer.toString(RUBTClient.left);
		String eventString = event;
		String query = "info_hash=";
		
		String request = RUBTClient.torrent_info.announce_url.toString();
		request += RUBTClient.torrent_info.info_hash.toString();
		request += "&peer_id=";
		request += RUBTClient.myPeerId;
		request += "&ip=";
		request += ip;
		request += "&port=";
		request += port;
		request += "&downloaded=";
		request += RUBTClient.downloaded;
		request += "&left=";
		request += RUBTClient.left;
		request += "&event=";
		request += eventString;
		request = java.net.URLEncoder.encode(request, "UTF-8");
		URL url = new URL(request);
		HttpURLConnection con = (HttpURLConnection) url.openConnection(); 
		con.setRequestMethod("GET");
		//response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();
		System.out.println("HTTP response code:" + responseCode);

		BufferedReader in = new BufferedReader(
				new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		byte[] response_bytes = response.toString().getBytes();
		return (Map) Bencoder2.decode(response_bytes);
	}
}
