/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import edu.rutgers.cs.cs352.bt.RUBTClient;
import edu.rutgers.cs.cs352.bt.RUBTClient.Event;
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
	public static byte[] getRequest(int port, Event event) throws Exception{

		String urlName = RUBTClient.torrent_info.announce_url.toString(); //makes the URL of the torrentInfo object into a string
		String peerId = RUBTClient.myPeerId.toString();
		String ip = InetAddress.getLocalHost().toString();
		String downloaded = Integer.toString(RUBTClient.downloaded);
		String left = Integer.toString(RUBTClient.left);
		String eventString = "";
		switch(event){
		case STARTED:
			eventString = "started";
			break;
		case COMPLETED:
			eventString = "completed";
			break;
		case STOPPED:
			eventString = "stopped";
			break;
		default:
			break;
		}
		String query = "info_hash=";
		query += RUBTClient.torrent_info.info_hash.toString();
		query += "&peer_id=";
		query += RUBTClient.myPeerId;
		query += "&ip=";
		query += ip;
		query += "&port=";
		query += port;
		query += "&downloaded=";
		query += RUBTClient.downloaded;
		query += "&left=";
		query += RUBTClient.left;
		query += "&event=";
		query += eventString;
		
		URI uri = new URI("http", null, urlName, port, eventString, eventString, eventString);
		URL url = new URL(urlName); // new URL object made from the string announce_url
		HttpURLConnection con = (HttpURLConnection) url.openConnection(); //open an HTTP connection
		con.setRequestMethod("GET");
		//response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + urlName);
		System.out.println("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(
				new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		//print result
		System.out.println(response.toString());
	}
}
