/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import edu.rutgers.cs.cs352.bt.RUBTClient;
/**
 * @author Gaurav Kumar
 *
 */
public static class TrackerCommunicator {
	private final static Logger LOGGER = Logger.getLogger(TrackerCommunicator.class.getName());
	private static final byte[] GROUP = {'G','P','O','1','6'};

	/**
	 * @author Jeffrey Rocha
	 * @throws Exception
	 */
	public static  getRequest(int port) throws Exception{

		String urlName = RUBTClient.torrent_info.announce_url.toString(); //makes the URL of the torrentInfo object into a string
		String peerId = RUBTClient.myPeerId.toString();
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
