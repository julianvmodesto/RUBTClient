/**
 * Authors: Gaurav Kumar, Julian Modesto, Jeffrey Rocha
 */
package edu.rutgers.cs.cs352.bt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
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
	private static final byte[] BYTES_INTERVAL = {'i','n','t','e','r','v','a','l'};
	private static final ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(BYTES_INTERVAL);
	private static final byte[] BYTES_PEERS = {'p','e','e','r','s'};
	private static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(BYTES_PEERS);
	private static final byte[] BYTES_IP = {'i','p'};
	private static final ByteBuffer KEY_IP = ByteBuffer.wrap(BYTES_IP);
	private static final byte[] BYTES_PEER_ID = {'p','e','e','r',' ','i','d'};
	private static final ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(BYTES_PEER_ID);
	private static final byte[] BYTES_PORT = {'p','o','r','t'};
	private static final ByteBuffer KEY_PORT = ByteBuffer.wrap(BYTES_PORT);
	
	private static final byte[] RUBT = {'R','U','B','T'};
	
	/**
	 * @author Jeffrey Rocha, Gaurav Kumar
	 * @throws Exception
	 */
	public static ArrayList getRequest(int port, String event) throws Exception{

		String eventString = event;
		String request = RUBTClient.torrent_info.announce_url.toString();
		
		request += "?info_hash=";
		request += bytesToURL(RUBTClient.torrent_info.info_hash.array());
		request += "&peer_id=";
		request += bytesToURL(RUBTClient.myPeerId);
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
		
		
		// Response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();

		BufferedReader in = new BufferedReader(
				new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		
		// Decode bencoded dictionary from the tracker
		byte[] response_bytes = response.toString().getBytes();
		HashMap response_map = (HashMap) Bencoder2.decode(response_bytes);
		
		// Get interval
		RUBTClient.interval = (Integer) response_map.get(KEY_INTERVAL);
		
		
		// Decode list of bencodeded dictionaries corresponding to peers
		ArrayList<HashMap> peer_list = (ArrayList<HashMap>) response_map.get(KEY_PEERS);
		// Search the peers for the peer ID beginning with RUBT bytes
		for (HashMap peer_map : peer_list) {
			
			ToolKit.print(peer_map);
			
			ByteBuffer peer_id_byte_buffer = (ByteBuffer) peer_map.get(KEY_PEER_ID);
			byte[] peer_id = peer_id_byte_buffer.array();
			
			byte[] header = new byte[4];
			
			System.arraycopy(peer_id, 0, header, 0, 4);
			
			// Check if peer ID begins with RUBT bytes
			if (Arrays.equals(header, RUBT)) {
				// Found correct peer_id
				
				// Save peer's IP address
				ByteBuffer ip_byte_buffer = (ByteBuffer) peer_map.get(KEY_IP);
				String ip = new String(ip_byte_buffer.array(),"UTF-8");
				RUBTClient.ip = ip;
				
				// Save port
				RUBTClient.peerPort = (Integer) peer_map.get(KEY_PORT);
				
				// Save peer_id
				RUBTClient.peerId = peer_id;
				// Convert peer_id to string for printing
				String peer_id_str = bytesToHexStr(peer_id); //TODO comment out
				
				System.out.println("---------------------------------------------------------------");
				System.out.println("Found the peer to download from");
				System.out.println("Peer ID in hex: " + peer_id_str);
				System.out.println("IP: " + ip);
				System.out.println("Port: " + RUBTClient.peerPort);
				System.out.println("---------------------------------------------------------------");
				
			}
		}
		
		RUBTClient.left = 0;
		return peer_list;
		
	}
	
	private final static char[] HEX_CHARS = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	
	private static String bytesToHexStr(byte[] byteArr) {
		
		char[] charArr = new char[byteArr.length*2];
		for(int i=0; i<byteArr.length; i++){
			final int val = (byteArr[i]&0xFF);
			final int charLoc=i<<1;
			charArr[charLoc]=HEX_CHARS[val>>>4];
			charArr[charLoc+1]=HEX_CHARS[val&0x0F];
		}
		String hexString = new String(charArr);
		
		return hexString;
	}
		
	private static String bytesToURL(byte[] byteArr) {

		String hexString = bytesToHexStr(byteArr);
				
		int len = hexString.length();
		char[] charArr = new char[len+len/2];
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
