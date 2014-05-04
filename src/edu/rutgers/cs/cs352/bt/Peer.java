package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.rutgers.cs.cs352.bt.Message.PieceMessage;
import edu.rutgers.cs.cs352.bt.Message.RequestMessage;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * @author Robert Moore
 *
 */
public class Peer extends Thread {
	
	private final static Logger LOGGER = Logger.getLogger(Peer.class.getName());

	private static final byte[] BYTES_PROTOCOL = { 'B', 'i', 't', 'T', 'o', 'r', 'r',
		'e', 'n', 't', ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l' };

	private byte[] peerId;
	private final byte[] infoHash;
	private final byte[] clientId;

	/**
	 * @return the peer ID
	 */
	public byte[] getPeerId() {
		return this.peerId;
	}

	private String ip;
	/**
	 * @return the peer IP
	 */
	public String getIp() {
		return this.ip;
	}

	private int port;
	private Socket socket;
	
	private LinkedBlockingQueue<MessageTask> tasks;

	private byte[] bitField;
	

	/**
	 * The block size that will be requested, 16K.
	 */
	public static final int BLOCK_LENGTH = 16384; // = 16Kb
	// We should be requesting 16K blocks, while pieces are 32 blocks
	
	private byte[] piece;
	private int pieceLength;
	private int pieceIndex;
	private int blockOffset;
	private int lastBlockLength;

	/**
	 * @return the bitField
	 */
	public synchronized byte[] getBitField() {
		return this.bitField;
	}

	/**
	 * @param bit the bit to set
	 */
	public void setBitField(int bit) {
		byte[] tempBitField = getBitField();
		tempBitField = Utility.setBit(tempBitField, bit);
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
		
		setBitField(tempBitField);
	}
	
	public Peer(byte[] peerId, String ip, Integer port, byte[] infoHash, byte[] clientId) {
		this.peerId = peerId;
		this.ip = ip;
		this.port = port == null ? -1 : port.intValue();
		this.infoHash = infoHash;
		this.clientId = clientId;
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

		// Update time stamp for keep-alive message timer
		this.lastMessageTime = System.currentTimeMillis();

		LOGGER.info("Sent " + msg + " to " + this);
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
				@Override
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
			this.in.readFully(peerHandshake);

			// Validate handshake
			if (!validateHandshake(peerHandshake)) {
				LOGGER.log(Level.WARNING,"Handshake is incorrect.");
				this.disconnect();
			} else {
				while (this.keepRunning) {
					// read message from socket
					try {
						Message msg = Message.read(this.in);
						LOGGER.info("Decoded " + msg);
						if (msg.getId() == Message.ID_PIECE) {
							buildPiece(msg);
						} else {
							LOGGER.info("Queued message: " + msg);
							this.tasks.put(new MessageTask(this,msg));
						}
					} catch (IOException ioe) {
						// TODO Auto-generated catch block
						ioe.printStackTrace();
						//TODO remove break and handle I/O Exception properly
						break;
					} catch (InterruptedException ie) {
						// TODO Auto-generated catch block
						ie.printStackTrace();
					}
				}
			}

		} catch (IOException ioe) {
			// TODO Auto-generated catch block
			ioe.printStackTrace();
		}
	}

	/**
	 * Connects this peer.
	 * @throws IOException 
	 */
	public void connect() throws IOException {
		// Create socket
		this.socket = null;
		try {
			this.socket = new Socket(this.ip, this.port);
		} catch (UnknownHostException uhe) {
			LOGGER.log(Level.WARNING,"The IP address of the host could not be determined from " + this.ip + ".", uhe);
		} catch (IOException ioe) {
			LOGGER.log(Level.WARNING,"An I/O error occurred.",ioe);
		}

		// Check if connected once but not closed
		if (this.socket == null && !this.socket.isClosed()) {
			LOGGER.log(Level.WARNING,"Socket connected once but not closed.");
		}

		// Open IO streams
		this.in = new DataInputStream(this.socket.getInputStream());
		this.out = new DataOutputStream(this.socket.getOutputStream());
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
			
			LOGGER.info("Disconnected peer: " + this);
		} catch (IOException ioe) {
			LOGGER.log(Level.WARNING,"I/O exception encountered when disconnecting peer " + this, ioe);
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
		result = prime * result + Arrays.hashCode(this.peerId);
		result = prime * result + ((this.ip == null) ? 0 : this.ip.hashCode());
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
		if (!Arrays.equals(this.peerId, other.peerId)) {
			return false;
		}
		if (this.ip == null) {
			if (other.ip != null) {
				return false;
			}
		} else if (!this.ip.equals(other.ip)) {
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
		if (this.peerId != null) {
			builder.append("peerId=");
			for(int i = 0; i < this.peerId.length; ++i){
			  if(this.peerId[i] < ' ' || this.peerId[i] > 126){
			    builder.append(String.format("_%02X",this.peerId[i]));
			  }else {
			    builder.append((char)this.peerId[i]);
			  }
			}
			builder.append(", ");
		}
		if (this.ip != null) {
			builder.append("ip=");
			builder.append(this.ip);
			builder.append(", ");
		}
		builder.append("port=");
		builder.append(this.port);
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

		LOGGER.log(Level.CONFIG,"Generated handshake for " + this);

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

		LOGGER.info("Handshake validated for " + this);

		return true;
	}

	public void setTasks(LinkedBlockingQueue<MessageTask> tasks) {
		this.tasks = tasks;
	}

	public void requestPiece(int pieceIndex, int pieceLength) throws IOException {
		this.pieceIndex = pieceIndex;
		this.pieceLength = pieceLength;
		this.piece = new byte[pieceLength];
		this.lastBlockLength = this.pieceLength % BLOCK_LENGTH;
		
		this.blockOffset = 0;
		
		RequestMessage requestMsg;
		if (this.blockOffset + this.lastBlockLength >= this.pieceLength) {
			// Request the last piece
			requestMsg = new RequestMessage(this.pieceIndex, blockOffset, this.lastBlockLength);
		} else {
			requestMsg = new RequestMessage(this.pieceIndex, blockOffset, BLOCK_LENGTH);
		}
		sendMessage(requestMsg);
	}

	
	private void buildPiece(Message msg) throws InterruptedException, IOException {
		if (msg.getId() == Message.ID_PIECE) {
			PieceMessage pieceMsg = (PieceMessage) msg;
			if (pieceMsg.getPieceIndex() != this.pieceIndex) {
				LOGGER.warning("Incorrect piece received from " + pieceMsg);
			} else if (pieceMsg.getBlockOffset() != this.blockOffset) {
				LOGGER.warning("Incorrect block offset received from " + pieceMsg);
			} else {
				if (blockOffset == this.pieceLength) {
					// Write the last block of piece
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece, this.blockOffset, this.lastBlockLength);
					// Queue the full piece
					PieceMessage returnMsg = new PieceMessage(this.pieceIndex, 0, this.piece);
					tasks.put(new MessageTask(this, returnMsg));
				} else if (blockOffset + BLOCK_LENGTH >= this.pieceLength) {
					// Write the second-to-last block in the piece
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece, this.blockOffset, BLOCK_LENGTH);

					// Request the last block in the piece
					this.blockOffset = this.blockOffset + this.lastBlockLength;
					RequestMessage requestMsg = new RequestMessage(this.pieceIndex, this.blockOffset, this.lastBlockLength);
					sendMessage(requestMsg);
				} else {
					// Request a block in the middle of the piece
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece, this.blockOffset, BLOCK_LENGTH);
					this.blockOffset = this.blockOffset + BLOCK_LENGTH;
					RequestMessage requestMsg = new RequestMessage(this.pieceIndex, this.blockOffset, BLOCK_LENGTH);
					sendMessage(requestMsg);
				}
			}
		}
	}
}
