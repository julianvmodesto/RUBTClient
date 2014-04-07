package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * @author Robert Moore
 *
 */
public class Peer extends Thread {

	private static final byte[] BYTES_PROTOCOL = { 'B', 'i', 't', 'T', 'o', 'r', 'r',
		'e', 'n', 't', ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l' };

	private byte[] peerId;
	private final byte[] infoHash;
	private final byte[] clientId;

	/**
	 * @return the peer ID
	 */
	public byte[] getPeerId() {
		return peerId;
	}

	private String ip;
	/**
	 * @return the peer IP
	 */
	public String getIp() {
		return ip;
	}

	private int port;
	private Socket socket;

	private byte[] bitField;

	/**
	 * @return the bitField
	 */
	public synchronized byte[] getBitField() {
		return bitField;
	}

	/**
	 * @param bit the bit to set
	 * @return 
	 */
	public void setBitField(int bit) {
		byte[] tempBitField = getBitField();
		Utility.setBit(tempBitField, bit);
		setBitField(tempBitField);
	}
	
	/**
	 * 
	 * @param bitField the bitField to set
	 */
	public synchronized void setBitField(byte[] bitField) {
		this.bitField = bitField;
	}

	public void initializeBitField(int totalPieces) {
		int bytes = (int) Math.ceil((double)totalPieces/8);
		byte[] tempBitField = new byte[bytes];
		
		for (int i = 0; i < tempBitField.length; i++) {
			tempBitField[i] = 0;
		}
		
		setBitField(tempBitField);
	}
	
	private final RUBTClient client;

	public Peer(byte[] peerId, String ip, Integer port, byte[] infoHash, byte[] clientId, RUBTClient client) {
		this.peerId = peerId;
		this.ip = ip;
		this.port = port;
		this.infoHash = infoHash;
		this.clientId = clientId;
		this.client = client;
	}

	// Set default states
	/**
	 * True if the LOCAL client is interested in the REMOTE peer's pieces.
	 */
	private boolean localInterested = false;
	/**
	 * True if the REMOTE peer is interested in the LOCAL client's pieces.
	 */
	private boolean remoteInterested = false;

	/**
	 * True if the LOCAL client is choked by the REMOTE peer.
	 */
	private boolean localChoked = true;

	/**
	 * True if the REMOTE peer is choked by the LOCAL client.
	 */
	private boolean remoteChoked=true;

	private DataInputStream in = null;
	private DataOutputStream out = null;

	/**
	 * Returns the current Interested state of the LOCAL CLIENT.
	 * @return {@code true} if the LOCAL CLIENT is interested in the REMOTE PEER's pieces
	 */
	public boolean amInterested(){
		return this.localInterested;
	}

	/**
	 * Returns the current Choked state of the LOCAL CLIENT.
	 * @return {@code true} if the LOCAL CLIENT is choked by the REMOTE PEER.
	 */
	public boolean amChoked(){
		return this.localChoked;
	}

	/**
	 * Returns the current Interested state of the REMOTE CLIENT.
	 * @return {@code true} if the REMOTE CLIENT is interested in the LOCAL PEER's pieces
	 */
	public boolean remoteInterested(){
		return this.localInterested;
	}

	/**
	 * Returns the current Choked state of the REMOTE CLIENT.
	 * @return {@code true} if the REMOTE CLIENT is choked by the LOCAL PEER.
	 */
	public boolean remoteChoked(){
		return this.localChoked;
	}

	/**
	 * @param localInterested the localInterested to set
	 */
	public synchronized void setLocalInterested(boolean localInterested) {
		this.localInterested = localInterested;
	}

	/**
	 * @param remoteInterested the remoteInterested to set
	 */
	public synchronized void setRemoteInterested(boolean remoteInterested) {
		this.remoteInterested = remoteInterested;
	}

	/**
	 * @param localChoked the localChoked to set
	 */
	public synchronized void setLocalChoked(boolean localChoked) {
		this.localChoked = localChoked;
	}

	/**
	 * @param remoteChoked the remoteChoked to set
	 */
	public synchronized void setRemoteChoked(boolean remoteChoked) {
		this.remoteChoked = remoteChoked;
	}

	// Set up timer
	private static final long KEEP_ALIVE_TIMEOUT = 120000;
	private Timer keepAliveTimer = new Timer();
	private long lastMessageTime = System.currentTimeMillis();

	/**
	 * Sends the provided message to this remote peer.
	 * @param msg the Peer message to send
	 * @throws IOException if an Exception is thrown by the underlying write operation.
	 */
	public synchronized void sendMessage(final Message msg) throws IOException{
		if(this.out == null){
			throw new IOException("Output stream is null, cannot write message to " + this);
		}
		
		msg.write(this.out);

		// Update timestamp for keep-alive message timer
		this.lastMessageTime = System.currentTimeMillis();

		System.out.println("Sent " + Message.ID_NAMES[msg.getId()] + " message to the peer.");
	}

	/**
	 * Flag to keep the main loop running. Once false, the peer *should* exit.
	 */
	private volatile boolean keepRunning = true;

	@Override
	public void run() {
		try {
			// Connect
			connect();

			/* Schedules a new anonymous implementation of a TimerTask that
			 * will start now and execute every 10 seconds afterward.
			 */
			this.keepAliveTimer.scheduleAtFixedRate(new TimerTask(){
				public void run(){
					// Let the peer figure out how/when to send a keep-alive
					try {
						Peer.this.checkAndSendKeepAlive();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}, new Date(), 10000);

			// Send handshake
			byte[] myHandshake = getHandshake();
			this.out.write(myHandshake);
			this.out.flush();

			// Read response
			byte[] peerHandshake = new byte[68];
			in.readFully(peerHandshake);

			// Validate handshake
			if (!validateHandshake(peerHandshake)) {
				System.err.println("Error: handshake is incorrect.");
				this.disconnect();
			} else {
				while (this.keepRunning) {
					// read message from socket
					try {
						Message msg = Message.read(this.in);
						System.out.println("Queued message: " + Message.ID_NAMES[msg.getId()]);
						this.client.putMessageTask(new MessageTask(this,msg));
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println("Peer.run()");
					}
				}
			}

		} catch (IOException ioe) {
			// TODO Auto-generated catch block
			ioe.printStackTrace();
		} finally {
			this.disconnect();
		}
	}

	/**
	 * Connects this peer.
	 * @throws IOException 
	 */
	public void connect() throws IOException {
		// Create socket
		socket = null;
		try {
			socket = new Socket(this.ip, this.port);
		} catch (UnknownHostException uhe) {
			System.err.println("Error: the IP address of the host could not be determined from " + this.ip + ".");
			System.err.println(uhe.getMessage());
		} catch (IOException ioe) {
			System.err.println("Error: an I/O error occurred.");
			System.err.println(ioe.getMessage());
		}

		// Check if connected once but not closed
		if (socket == null && !socket.isClosed()) {
			System.err.println("Error: socket connected once but not closed.");
		}

		// Open IO streams
		this.in = new DataInputStream(socket.getInputStream());
		this.out = new DataOutputStream(socket.getOutputStream());
	}

	/**
	 * Disconnects this peer.
	 */
	public void disconnect() {
		// Disconnect the socket, close data streams, catch all exceptions
		try {
			this.keepRunning = false;
			
			this.socket.close();

			this.in.close();
			
			this.out.flush();
			this.out.close();
			
			System.out.println("Disconnected peer: " + this);
		} catch (IOException e) {
			System.err.println("Error: I/O exception encountered when disconnecting peer " + this);
		}
	}

	/**
	 * Sends a keep-alive message to the remote peer if the time between now
	 * and the previous message exceeds the limit set by KEEP_ALIVE_TIMEOUT.
	 * @author Robert Moore
	 * @throws Exception 
	 */
	protected void checkAndSendKeepAlive() throws Exception{
		long now = System.currentTimeMillis();
		if(now - this.lastMessageTime > KEEP_ALIVE_TIMEOUT){
			sendMessage(Message.KEEP_ALIVE);
			// Validate that the timestamp was updated
			if(now > this.lastMessageTime){
				throw new Exception("Didn't update lastMessageTime when sending a keep-alive!");
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(peerId);
		result = prime * result + ((ip == null) ? 0 : ip.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Peer)) {
			return false;
		}
		Peer other = (Peer) obj;
		if (!Arrays.equals(peerId, other.peerId)) {
			return false;
		}
		if (ip == null) {
			if (other.ip != null) {
				return false;
			}
		} else if (!ip.equals(other.ip)) {
			return false;
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Peer [");
		if (peerId != null) {
			builder.append("peerId=");
			builder.append(Utility.bytesToHexStr(peerId));
			builder.append(", ");
		}
		if (ip != null) {
			builder.append("ip=");
			builder.append(ip);
			builder.append(", ");
		}
		builder.append("port=");
		builder.append(port);
		builder.append("]");
		return builder.toString();
	}

	/**
	 * Generates the handshake from the client to the peer.
	 * 
	 * The byte array is preallocated and then filled with System.arraycopy.
	 * 
	 * @author Julian Modesto
	 * @param infohash the 20-byte SHA-1 hash of the bencoded form of the info value from the metainfo (.torrent) file
	 * @param peerId the peer id generated by the client
	 * @return the handshake byte array
	 */
	private byte[] getHandshake() {
		// Preallocate bytes for handshake
		byte[] handshake = new byte[68];

		// Header 19:BitTorrent protocol
		// Begin with byte 19
		handshake[0] = 19;

		// Add "BitTorrent protocol"
		System.arraycopy(BYTES_PROTOCOL, 0, handshake, 1, BYTES_PROTOCOL.length);

		// 8 reserved bytes 20-27 are already initialized to 0; skip + omit commented-out code below

		// Add infohash SHA-1 hash - not encoded
		System.arraycopy(this.infoHash, 0, handshake, 28, this.infoHash.length);

		// Add peer id, which should match the infohash
		System.arraycopy(this.clientId, 0, handshake, 48, this.clientId.length);	

		System.out.println("Generated handshake for " + this);

		return handshake;
	}

	/**
	 * Validate two handshakes for equality.
	 * 
	 * @param myHandshake
	 * @param otherHandshake
	 * @return the truth value for the equality of the handshakes
	 */
	private boolean validateHandshake(byte[] otherHandshake) {

		if (otherHandshake == null) {
			return false;
		}

		// Verify the length
		if (otherHandshake.length != 68) {
			return false;
		}

		// Check protocol
		byte[] otherProtocol = new byte[19];
		System.arraycopy(otherHandshake, 1, otherProtocol, 0, BYTES_PROTOCOL.length);
		if (!Arrays.equals(otherProtocol, BYTES_PROTOCOL)) {
			return false;
		}

		// Skip reserved bytes

		// Check info hash against info hash from .torrent file
		byte[] otherInfoHash = new byte[20];
		System.arraycopy(otherHandshake, 28, otherInfoHash, 0, 20);
		if (!Arrays.equals(otherInfoHash, this.infoHash)) {
			return false;
		}

		// Check that peer ID is the same as from tracker
		byte[] otherPeerId = new byte[20];
		System.arraycopy(otherHandshake, 48, otherPeerId, 0, 20);
		if (!Arrays.equals(otherPeerId, this.peerId)) {
			return false;
		}

		System.out.println("Handshake validated for " + this);

		return true;
	}

}
