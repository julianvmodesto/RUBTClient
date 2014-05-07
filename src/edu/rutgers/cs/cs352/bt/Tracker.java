package edu.rutgers.cs.cs352.bt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Bencoder2;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * @author Robert Moore
 * @author Gaurav Kumar
 * @author Julian Modesto
 */
public class Tracker {

	/**
	 * Logger for this class.
	 */
	private final static Logger LOGGER = Logger.getLogger(Tracker.class
			.getName());

	/**
	 * Key used to retrieve the request error message.
	 */
	private static final ByteBuffer KEY_FAILURE_REASON = ByteBuffer
			.wrap(new byte[] { 'f', 'a', 'i', 'l', 'u', 'r', 'e', ' ', 'r',
					'e', 'a', 's', 'o', 'n' });
	/**
	 * Key used to retrieve the optional response warning message.
	 */
	private static final ByteBuffer KEY_WARNING_MESSAGE = ByteBuffer
			.wrap(new byte[] { 'w', 'a', 'r', 'n', 'i', 'n', 'g', ' ', 'm',
					'e', 's', 's', 'a', 'g', 'e' });

	/**
	 * Key to retrieve the interval that the client should wait to send requests
	 * to the tracker.
	 */
	private static final ByteBuffer KEY_INTERVAL = ByteBuffer.wrap(new byte[] {
			'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });

	/**
	 * Key to retrieve the minimum announce interval.
	 */
	private static final ByteBuffer KEY_MIN_INTERVAL = ByteBuffer
			.wrap(new byte[] { 'm', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r',
					'v', 'a', 'l' });

	/**
	 * Key to the tracker ID to be sent back on the next announcements.
	 */
	private static final ByteBuffer KEY_TRACKER_ID = ByteBuffer
			.wrap(new byte[] { 't', 'r', 'a', 'c', 'k', 'e', 'r', ' ', 'i', 'd' });

	/**
	 * Key for the number of peers, or seeders, with the complete file in the
	 * torrent.
	 */
	private static final ByteBuffer KEY_COMPLETE = ByteBuffer.wrap(new byte[] {
			'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });

	/**
	 * Key for the number of non-seeder peers or leechers in the torrent.
	 */
	private static final ByteBuffer KEY_INCOMPLETE = ByteBuffer
			.wrap(new byte[] { 'i', 'n', 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });

	/**
	 * Key to the peers in the torrent.
	 */
	private static final ByteBuffer KEY_PEERS = ByteBuffer.wrap(new byte[] {
			'p', 'e', 'e', 'r', 's' });

	/**
	 * Key to the peer's IP address.
	 */
	private static final ByteBuffer KEY_IP = ByteBuffer.wrap(new byte[] { 'i',
			'p' });

	/**
	 * Key to the peer's self-selected ID.
	 */
	private static final ByteBuffer KEY_PEER_ID = ByteBuffer.wrap(new byte[] {
			'p', 'e', 'e', 'r', ' ', 'i', 'd' });

	/**
	 * Key to the peer's port number
	 */
	private static final ByteBuffer KEY_PORT = ByteBuffer.wrap(new byte[] {
			'p', 'o', 'r', 't' });

	/**
	 * The infohash that identifies the torrent.
	 */
	private final byte[] infoHash;

	/**
	 * The unique ID to identify the local BT client.
	 */
	private final byte[] clientId;

	/**
	 * The announce URL of the tracker.
	 */
	private final String announceUrl;

	/**
	 * The port number that the client is listening on.
	 */
	private int port;

	/**
	 * Retrieves the port that the client is currently listening on.
	 * 
	 * @return the port
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Updates the port that the client is listening on.
	 * 
	 * @param port
	 *            the port to set
	 */
	public void setPort(final int port) {
		this.port = port;
	}

	private int interval = 60;

	/**
	 * Creates a new Tracker interface object.
	 * 
	 * @param clientId
	 *            the local client's peer id
	 * @param infoHash
	 *            the torrent's info hash
	 * @param announceUrl
	 *            the announce URL of the tracker
	 * @param port
	 *            the listen port for the local client.
	 */
	public Tracker(final byte[] clientId, final byte[] infoHash,
			final String announceUrl, final int port) {
		this.infoHash = infoHash;
		this.clientId = clientId;
		this.announceUrl = announceUrl;
		this.port = port;
	}

	/**
	 * Perform a tracker announce with the provided parameters
	 * 
	 * @param downloaded
	 *            the number of bytes downloaded in this torrent
	 * @param uploaded
	 *            the number of bytes uploaded in this torrent
	 * @param left
	 *            the number of bytes remaining in the file
	 * @param event
	 *            the announce event (optional)
	 * @return the returned list of peers, or {@code null} if an error occurred.
	 * @throws BencodingException
	 * @throws IOException
	 */
	public List<Peer> announce(final int downloaded, final int uploaded,
			final int left, final String event) throws BencodingException,
			IOException {
		// Build HTTP GET request from the announce URL from the metainfo
		final StringBuffer request = new StringBuffer();
		request.append(this.announceUrl);
		request.append("?info_hash=");
		request.append(Utility.bytesToURL(this.infoHash));
		request.append("&peer_id=");
		request.append(Utility.bytesToURL(this.clientId));
		request.append("&port=");
		request.append(this.port);
		request.append("&downloaded=");
		request.append(downloaded);
		request.append("&left=");
		request.append(left);
		if ((event != null) && !event.isEmpty()) {
			request.append("&event=");
			request.append(event);
		}

		URL url;
		try {
			url = new URL(request.toString());
		} catch (final MalformedURLException murle) {
			throw new MalformedURLException(
					"A malformed URL exception was encountered.");
		}

		Tracker.LOGGER.info("Announcing: " + request.toString());

		HttpURLConnection httpConnection;
		try {
			httpConnection = (HttpURLConnection) url.openConnection();
		} catch (final IOException ioe) {
			throw new IOException(
					"An I/O exception occurred when openning the HTTP connection");
		}
		try {
			httpConnection.setRequestMethod("GET");
		} catch (final ProtocolException e) {
			throw new ProtocolException("A protocol exception was encountered.");
		}

		// Response code used to find if connection was success or failure (and
		// reason for failure)
		int responseCode;
		try {
			responseCode = httpConnection.getResponseCode();
		} catch (final IOException e) {
			throw new IOException(
					"An I/O exception occurred when getting the HTTP response code");
		}
		Tracker.LOGGER.info("Response Code: " + responseCode);

		// Receive the response
		// Read each byte from input stream and write to an output stream
		final InputStream is = httpConnection.getInputStream();
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int dataIn;
		final byte[] data = new byte[16384];
		while ((dataIn = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, dataIn);
		}
		buffer.flush();
		buffer.close();
		is.close();
		final byte[] response = buffer.toByteArray();

		// Decode the Bencoded response
		HashMap<ByteBuffer, Object> responseMap;
		try {
			responseMap = (HashMap<ByteBuffer, Object>) Bencoder2
					.decode(response);
		} catch (final BencodingException be) {
			throw new BencodingException(
					"A bencoding exception occurred when decoding tracker response.");
		}

		// Catch warning message
		String warningMessage = null;
		if (responseMap.containsKey(Tracker.KEY_WARNING_MESSAGE)
				&& responseMap.get(Tracker.KEY_WARNING_MESSAGE) != null) {
			warningMessage = (String) responseMap
					.get(Tracker.KEY_WARNING_MESSAGE);
			Tracker.LOGGER.log(Level.WARNING, "Warning:");
			Tracker.LOGGER.log(Level.WARNING, warningMessage);
		}

		// Set the interval
		if (responseMap.containsKey(Tracker.KEY_INTERVAL)) {
			this.interval = ((Integer) responseMap.get(Tracker.KEY_INTERVAL))
					.intValue();
		} else {
			Tracker.LOGGER.log(Level.WARNING,
					"No interval specified in torrent info.");
		}

		// Set min interval
		if (responseMap.containsKey(Tracker.KEY_MIN_INTERVAL)) {
			this.interval = ((Integer) responseMap
					.get(Tracker.KEY_MIN_INTERVAL)).intValue();
			Tracker.LOGGER.info("Minimal interval specified in torrent info.");
		} else {
			this.interval = (int) (this.interval / 2.0);
			Tracker.LOGGER
					.info("No minimal interval specified in torrent info.");
		}
		Tracker.LOGGER.info("Minimal interval for announce = " + this.interval
				+ " seconds");

		// Decode list of bencodeded dictionaries corresponding to peers
		ArrayList<HashMap<ByteBuffer, Object>> encodedPeerList = null;
		if (responseMap.containsKey(Tracker.KEY_PEERS)) {
			encodedPeerList = (ArrayList<HashMap<ByteBuffer, Object>>) responseMap
					.get(Tracker.KEY_PEERS);
		} else {
			Tracker.LOGGER.log(Level.WARNING,
					"No peer list given by tracker response.");
			return null;
		}

		// Iterate through the peers and build peer list
		final LinkedList<Peer> peerList = new LinkedList<Peer>();
		for (final HashMap<ByteBuffer, Object> peerMap : encodedPeerList) {

			// Print the map
			// ToolKit.print(peerMap);

			// Get peer IP
			String peerIP = null;
			if (peerMap.containsKey(Tracker.KEY_IP)) {
				final ByteBuffer peerIPBB = (ByteBuffer) peerMap
						.get(Tracker.KEY_IP);
				peerIP = new String(peerIPBB.array(), "UTF-8");
			}

			// Get peer ID
			ByteBuffer peerIdBB;
			byte[] peerId = null;
			if (peerMap.containsKey(Tracker.KEY_PEER_ID)) {
				peerIdBB = (ByteBuffer) peerMap.get(Tracker.KEY_PEER_ID);
				peerId = peerIdBB.array();
			}

			// Get peer port
			Integer peerPort = -1;
			if (peerMap.containsKey(Tracker.KEY_PORT)) {
				peerPort = (Integer) peerMap.get(Tracker.KEY_PORT);
			}

			// Add new peer
			final Peer peer = new Peer(peerId, peerIP, peerPort, this.infoHash,
					this.clientId);
			peerList.add(peer);

			Tracker.LOGGER.info("Peer in torrent: " + peer);
		}

		return peerList;
	}

	/**
	 * Get the latest "interval" value from the tracker.
	 * 
	 * @return the latest returned "interval" value
	 */
	public int getInterval() {
		return this.interval;
	}

}
