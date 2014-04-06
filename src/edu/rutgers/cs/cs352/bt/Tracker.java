package edu.rutgers.cs.cs352.bt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.TimerTask;

import edu.rutgers.cs.cs352.bt.PeerMessage.KeepAliveMessage;
import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;
import edu.rutgers.cs.cs352.bt.util.ToolKit;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * 
 * @author Gaurav Kumar
 * @author Julian Modesto
 * @author Jeffrey Rocha
 */
public class Tracker extends Thread {

	/**
	 * Hard code the first 4 bytes of our client's peer ID.
	 */
	private static final byte[] BYTES_GROUP = { 'G', 'P', '1', '6' };

	private static final byte[] BYTES_FAILURE_REASON = { 'f', 'a', 'i', 'l',
			'u', 'r', 'e', ' ', 'r', 'e', 'a', 's', 'o', 'n' };
	private static final byte[] BYTES_WARNING_MESSAGE = { 'w', 'a', 'r', 'n',
			'i', 'n', 'g', ' ', 'm', 'e', 's', 's', 'a', 'g', 'e' };
	private static final byte[] BYTES_INTERVAL = { 'i', 'n', 't', 'e', 'r',
			'v', 'a', 'l' };
	private static final byte[] BYTES_MIN_INTERVAL = { 'm', 'i', 'n', ' ', 'i',
			'n', 't', 'e', 'r', 'v', 'a', 'l' };
	private static final byte[] BYTES_TRACKER_ID = { 't', 'r', 'a', 'c', 'k',
			'e', 'r', ' ', 'i', 'd' };
	private static final byte[] BYTES_COMPLETE = { 'c', 'o', 'm', 'p', 'l',
			'e', 't', 'e' };
	private static final byte[] BYTES_INCOMPLETE = { 'i', 'n', 'c', 'o', 'm',
			'p', 'l', 'e', 't', 'e' };
	private static final byte[] BYTES_PEERS = { 'p', 'e', 'e', 'r', 's' };
	private static final byte[] BYTES_IP = { 'i', 'p' };
	private static final byte[] BYTES_PEER_ID = { 'p', 'e', 'e', 'r', ' ', 'i',
			'd' };
	private static final byte[] BYTES_PORT = { 'p', 'o', 'r', 't' };
	private static final ByteBuffer KEY_FAILURE_REASON = ByteBuffer
			.wrap(BYTES_FAILURE_REASON);
	private static final ByteBuffer KEY_WARNING_MESSAGE = ByteBuffer
			.wrap(BYTES_WARNING_MESSAGE);
	private static final ByteBuffer KEY_INTERVAL = ByteBuffer
			.wrap(BYTES_INTERVAL);
	private static final ByteBuffer KEY_MIN_INTERVAL = ByteBuffer
			.wrap(BYTES_MIN_INTERVAL);
	private static final ByteBuffer KEY_TRACKER_ID = ByteBuffer
			.wrap(BYTES_TRACKER_ID);
	private static final ByteBuffer KEY_COMPLETE = ByteBuffer
			.wrap(BYTES_COMPLETE);
	private static final ByteBuffer KEY_INCOMPLETE = ByteBuffer
			.wrap(BYTES_INCOMPLETE);
	private static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(BYTES_PEERS);
	private static final ByteBuffer KEY_IP = ByteBuffer.wrap(BYTES_IP);
	private static final ByteBuffer KEY_PEER_ID = ByteBuffer
			.wrap(BYTES_PEER_ID);
	private static final ByteBuffer KEY_PORT = ByteBuffer.wrap(BYTES_PORT);

	private String torrentFileName;
	private String downloadFileName;
	private RandomAccessFile torrentFile;
	RandomAccessFile downloadFile;

	private TorrentInfo torrentInfo;
	private byte[] infoHash;
	
	private byte[] myPeerId;
	private int myPort;

	private int downloaded;
	private int left;
	
	private byte[] myBitField;
		
	private int interval;
	private String trackerId;
	private ArrayList<HashMap<ByteBuffer, Object>> peerList;
	private ArrayList<Peer> peers;

	/**
	 * The constructor for the Tracker object sets the torrent file and download
	 * file information
	 * 
	 * @param torrentFileName
	 * @param downloadFileName
	 * @throws FileNotFoundException
	 */
	public Tracker(String torrentFileName, String downloadFileName)
			throws FileNotFoundException {
		this.torrentFileName = torrentFileName;
		this.downloadFileName = downloadFileName;

		this.torrentFile = new RandomAccessFile(torrentFileName, "r");
		this.downloadFile = new RandomAccessFile(downloadFileName, "rws");

		this.peers = new ArrayList<Peer>();
	}

	public void run() {
		try {
			// Open the .torrent file and parse the data inside
			byte[] torrentBytes = Utility.getFileInBytes(this.torrentFile);
			this.torrentInfo = new TorrentInfo(torrentBytes);

			this.myPeerId = generateMyPeerId();
			this.myPort = 6881;
			this.infoHash = this.torrentInfo.info_hash.array();
			this.left = this.torrentInfo.torrent_file_bytes.length;
			this.downloaded = 0;
			
			boolean isStarting = true;
			
			String httpGETRequestString;
			byte[] response; 
			
			while (true) {
				
				if (isStarting) {
					// Build HTTP GET request as a string
					httpGETRequestString = getHTTPGETRequest("started");
					
					isStarting = false;
				} else if (this.left == 0) {
					httpGETRequestString = getHTTPGETRequest("completed");
					System.out.println("GET Request: " + httpGETRequestString);
					
					// Send HTTP GET request and get tracker response
					response = getHTTPGETRequestResponse(httpGETRequestString);
					
					this.shutdown();
					break;
			    } else {
			    	// Build HTTP GET request as a string
					httpGETRequestString = getHTTPGETRequest("");			
			    }
				
				System.out.println("GET Request: " + httpGETRequestString);
				
				// Send HTTP GET request and get tracker response
				response = getHTTPGETRequestResponse(httpGETRequestString);
				
				// Get peer list
				decodeTrackerResponse(response);

				// Connect to peers
				selectPeers(this.peerList);
				
				System.out.println("Wait " + this.interval + " seconds to send announce");
				sleep(this.interval*1000);
			}

		} catch (IOException ioe) {
			System.err.println("Error: encountered I/O exception");
			System.err.println(ioe.getMessage());
		} catch (BencodingException be) {
			System.err.println("Error: encountered bencoding exception");
			System.err.println(be.getMessage());
		} catch (InterruptedException ie) {
			System.err.println("Error: encountered interrupted exception");
			System.err.println(ie.getMessage());
		}

	}
	
	/**
	 * Shutsdown the tracker by sending a STOPPED tracker announce
	 * @throws IOException
	 */
	private void shutdown() throws IOException {
		// Build HTTP GET request as a string
		String httpGETRequestString = getHTTPGETRequest("stopped");
		System.out.println("GET Request: " + httpGETRequestString);
		
		// Send HTTP GET request and get tracker response
		byte[] response = getHTTPGETRequestResponse(httpGETRequestString);
	}

	/**
	 * Generates the randomized peer ID with the first four bytes hard-coded
	 * with our group ID
	 * 
	 * @author Julian Modesto
	 * @return the generated ID
	 */
	private static byte[] generateMyPeerId() {
		byte[] peerId = new byte[20];

		// Hard code the first four bytes for easy identification
		System.arraycopy(BYTES_GROUP, 0, peerId, 0, BYTES_GROUP.length);

		// Randomly generate remaining 16 bytes
		byte[] random = new byte[16];
		new Random().nextBytes(random);

		System.arraycopy(random, 0, peerId, 4, random.length);

		return peerId;
	}

	private String getHTTPGETRequest(String event) {
		String request = this.torrentInfo.announce_url.toString();

		request += "?info_hash=";
		request += Utility.bytesToURL(this.infoHash);
		request += "&peer_id=";
		request += Utility.bytesToURL(this.myPeerId);
		request += "&port=";
		request += this.myPort;
		request += "&downloaded=";
		request += this.downloaded;
		request += "&left=";
		request += this.left;
		if (!event.isEmpty()) {
			request += "&event=";
			request += event;
		}

		return request;
	}

	private byte[] getHTTPGETRequestResponse(String request) throws IOException {
		URL url = new URL(request);
		HttpURLConnection httpConnection = (HttpURLConnection) url
				.openConnection();
		httpConnection.setRequestMethod("GET");

		// Response code used to find if connection was success or failure (and
		// reason for failure)
		int responseCode = httpConnection.getResponseCode();

		System.out.println("Response Code: " + responseCode);

		BufferedReader in = new BufferedReader(new InputStreamReader(
				httpConnection.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		return response.toString().getBytes();
	}

	private void decodeTrackerResponse(byte[] response) throws BencodingException,
			UnsupportedEncodingException {
		@SuppressWarnings("unchecked")
		HashMap<ByteBuffer, Object> responseMap = (HashMap<ByteBuffer, Object>) Bencoder2
				.decode(response);

		// Catch request failure
		String errorMessage = (String) responseMap.get(KEY_FAILURE_REASON);
		if (errorMessage != null) {
			System.err.println("Error: request failed");
			System.err.println(errorMessage);
			System.exit(1);
		}

		// Catch warning message
		/*
		String warningMessage = (String) responseMap.get(KEY_WARNING_MESSAGE);
		if (warningMessage != null) {
			System.out.println("Warning:");
			System.out.println(warningMessage);
		}
		*/

		// Set interval
		this.interval = (Integer) responseMap.get(KEY_INTERVAL);

		// Set min interval
		if (responseMap.get(KEY_MIN_INTERVAL) != null) {
			this.interval = (Integer) responseMap.get(KEY_MIN_INTERVAL);
		} else {
			this.interval = this.interval / 2;
		}

		// Set tracker id
		this.trackerId = (String) responseMap.get(KEY_TRACKER_ID);

		// Decode list of bencodeded dictionaries corresponding to peers
		this.peerList = (ArrayList<HashMap<ByteBuffer, Object>>) responseMap
				.get(KEY_PEERS);
	}

	private void selectPeers(ArrayList<HashMap<ByteBuffer, Object>> peerList) throws UnsupportedEncodingException, InterruptedException {
		// Search the peers for the peer ID
		for (HashMap<ByteBuffer, Object> peerMap : peerList) {

			// ToolKit.print(peerMap); // Print the map

			// Get peer IP
			ByteBuffer peerIPBB = (ByteBuffer) peerMap.get(KEY_IP);
			String peerIP = new String(peerIPBB.array(), "UTF-8");

			// Find peers
			if (peerIP.equals("128.6.171.130")
					|| peerIP.equals("128.6.171.131")) {
				// Get peer ID
				ByteBuffer peerIdBB = (ByteBuffer) peerMap.get(KEY_PEER_ID);
				byte[] peerId = peerIdBB.array();

				// Get peer port
				Integer peerPort = (Integer) peerMap.get(KEY_PORT);

				// Start a new peer
				if (!isExistingPeer(peerId)) {
					Peer peer = new Peer(this, peerId, peerIP, peerPort);
					this.peers.add(peer);
					peer.start();

					System.out.println("Found a peer to download from");
					System.out.println("\tPeer ID in hex: "
							+ Utility.bytesToHexStr(peerId));
					System.out.println("\tIP: " + peerIP);
					System.out.println("\tPort: " + peerPort);
					break;
					//TODO remove break to start multiple peers
				}
			}

		}
	}
	
	private boolean isExistingPeer(byte[] peerId) {
		for (Peer p : this.peers) {
			if (Arrays.equals(p.getPeerId(), peerId)) {
				return true;
			}
		}
		return false;
	}
	
	public byte[] getMyPeerId() {
		return this.myPeerId;
	}
	
	public byte[] getInfoHash() {
		return this.infoHash;
	}
	
	/**
	 * @return the downloaded
	 */
	public synchronized int getDownloaded() {
		return downloaded;
	}

	/**
	 * @param downloaded the downloaded to set
	 */
	public synchronized void setDownloaded(int downloaded) {
		this.downloaded = downloaded;
	}

	/**
	 * @return the left
	 */
	public synchronized int getLeft() {
		return left;
	}

	/**
	 * @param left the left to set
	 */
	public synchronized void setLeft(int left) {
		this.left = left;
	}

	/**
	 * @return the myBitField
	 */
	public synchronized byte[] getMyBitField() {
		return myBitField;
	}

	/**
	 * @param myBitField the myBitField to set
	 */
	public synchronized void setMyBitField(byte[] myBitField) {
		this.myBitField = myBitField;
	}

}