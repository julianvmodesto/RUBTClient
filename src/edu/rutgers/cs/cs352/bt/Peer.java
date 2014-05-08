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
 * @author Julian Modesto
 * 
 */
public class Peer extends Thread {

	private final static Logger LOGGER = Logger.getLogger(Peer.class.getName());

	private static final byte[] BYTES_PROTOCOL = { 'B', 'i', 't', 'T', 'o',
			'r', 'r', 'e', 'n', 't', ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o',
			'l' };

	private final byte[] peerId;
	private final byte[] infoHash;
	private final byte[] clientId;

	/**
	 * @return the peer ID
	 */
	byte[] getPeerId() {
		return this.peerId;
	}

	private final String ip;

	/**
	 * @return the peer IP
	 */
	String getIp() {
		return this.ip;
	}

	private final int port;
	private Socket socket;

	private LinkedBlockingQueue<MessageTask> tasks;
	private RUBTClient client;

	private byte[] bitfield;

	/**
	 * The block size that will be requested, 16K.
	 */
	private static final int BLOCK_LENGTH = 16384; // = 16Kb
	// We should be requesting 16K blocks, while pieces are 32 blocks

	private byte[] piece;
	private int pieceLength;
	private int pieceIndex;
	private int blockOffset;
	private int lastBlockLength;

	/**
	 * @return the bitfield
	 */
	synchronized byte[] getBitfield() {
		return this.bitfield;
	}

	/**
	 * @param bit
	 *            the bit to set
	 */
	void setBitfieldBit(final int bit) {
		byte[] tempBitfield = this.getBitfield();
		tempBitfield = Utility.setBit(tempBitfield, bit);
		this.setBitfield(tempBitfield);
	}

	/**
	 * 
	 * @param bitfield
	 *            the bitfield to set
	 */
	synchronized void setBitfield(final byte[] bitfield) {
		this.bitfield = bitfield;
	}

	void initializeBitfield(final int totalPieces) {
		final int bytes = (int) Math.ceil((double) totalPieces / 8);
		final byte[] tempBitfield = new byte[bytes];

		this.setBitfield(tempBitfield);
	}

	public Peer(final byte[] peerId, final String ip, final Integer port,
			final byte[] infoHash, final byte[] clientId) {
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
	private boolean remoteChoked = true;

	private DataInputStream in = null;
	private DataOutputStream out = null;

	/**
	 * Returns the current Interested state of the LOCAL CLIENT.
	 * 
	 * @return {@code true} if the LOCAL CLIENT is interested in the REMOTE
	 *         PEER's pieces
	 */
	boolean amInterested() {
		return this.localInterested;
	}

	/**
	 * Returns the current Choked state of the LOCAL CLIENT.
	 * 
	 * @return {@code true} if the LOCAL CLIENT is choked by the REMOTE PEER.
	 */
	boolean amChoked() {
		return this.localChoked;
	}

	/**
	 * Returns the current Interested state of the REMOTE CLIENT.
	 * 
	 * @return {@code true} if the REMOTE CLIENT is interested in the LOCAL
	 *         PEER's pieces
	 */
	boolean remoteInterested() {
		return this.localInterested;
	}

	/**
	 * Returns the current Choked state of the REMOTE CLIENT.
	 * 
	 * @return {@code true} if the REMOTE CLIENT is choked by the LOCAL PEER.
	 */
	boolean remoteChoked() {
		return this.localChoked;
	}

	/**
	 * @param localInterested
	 *            the localInterested to set
	 */
	synchronized void setLocalInterested(final boolean localInterested) {
		this.localInterested = localInterested;
	}

	/**
	 * @param remoteInterested
	 *            the remoteInterested to set
	 */
	synchronized void setRemoteInterested(final boolean remoteInterested) {
		this.remoteInterested = remoteInterested;
	}

	/**
	 * @param localChoked
	 *            the localChoked to set
	 */
	synchronized void setLocalChoked(final boolean localChoked) {
		this.localChoked = localChoked;
	}

	/**
	 * @param remoteChoked
	 *            the remoteChoked to set
	 */
	synchronized void setRemoteChoked(final boolean remoteChoked) {
		this.remoteChoked = remoteChoked;
	}

	// Set up timer
	private static final long KEEP_ALIVE_TIMEOUT = 120000;
	private final Timer keepAliveTimer = new Timer();
	private long lastMessageTime = System.currentTimeMillis();

	/**
	 * Sends the provided message to this remote peer.
	 * 
	 * @param msg
	 *            the Peer message to send
	 * @throws IOException
	 *             if an Exception is thrown by the underlying write operation.
	 */
	synchronized void sendMessage(final Message msg) throws IOException {
		if (this.out == null) {
			throw new IOException(
					"Output stream is null, cannot write message to " + this);
		}

		msg.write(this.out);

		// Update time stamp for keep-alive message timer
		this.lastMessageTime = System.currentTimeMillis();

		Peer.LOGGER.info("Sent " + msg + " to " + this);
	}

	/**
	 * Flag to keep the main loop running. Once false, the peer *should* exit.
	 */
	private volatile boolean keepRunning = true;

	@Override
	public void run() {
		try {
			// Connect
			this.connect();

			/*
			 * Schedules a new anonymous implementation of a TimerTask that will
			 * start now and execute every 10 seconds afterward.
			 */
			this.keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					// Let the peer figure out how/when to send a keep-alive
					try {
						Peer.this.checkAndSendKeepAlive();
					} catch (final Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}, new Date(), 10000);

			// Send handshake
			final byte[] myHandshake = this.getHandshake();
			this.out.write(myHandshake);
			this.out.flush();

			// Read response
			final byte[] peerHandshake = new byte[68];
			this.in.readFully(peerHandshake);

			// Validate handshake
			if (!this.validateHandshake(peerHandshake)) {
				Peer.LOGGER.warning("Handshake is incorrect.");
				this.disconnect();
			} else {
				this.sendMessage(new Message.BitfieldMessage(this.client.getBitfield().length, this.client.getBitfield()));
				while (this.keepRunning) {
					// read message from socket
					try {
						final Message msg = Message.read(this.in);
						Peer.LOGGER.info("Decoded " + msg);
						// Handle the message received
						if (msg.getId() == Message.ID_PIECE) {
							// Take the Piece Message and build blocks into a
							// piece
							this.buildPiece(msg);
						} else {
							// Queue Message as a MessageTask with the local
							// client
							Peer.LOGGER.info("Queued message: " + msg);
							this.tasks.put(new MessageTask(this, msg));
						}
					} catch (final IOException ioe) {
						// TODO Auto-generated catch block
						ioe.printStackTrace();
						// TODO remove break and handle I/O Exception properly
						break;
					} catch (final InterruptedException ie) {
						// TODO Auto-generated catch block
						ie.printStackTrace();
					}
				}
			}

		} catch (final IOException ioe) {
			// TODO Auto-generated catch block
			ioe.printStackTrace();
		}
	}

	/**
	 * Connects this peer.
	 * 
	 * @throws IOException
	 */
	void connect() throws IOException {
		// Create socket
		this.socket = null;
		try {
			this.socket = new Socket(this.ip, this.port);
		} catch (final UnknownHostException uhe) {
			Peer.LOGGER.log(Level.WARNING,
					"The IP address of the host could not be determined from "
							+ this.ip + ".", uhe);
		} catch (final IOException ioe) {
			Peer.LOGGER.log(Level.WARNING, "An I/O error occurred.", ioe);
		}

		// Check if connected once but not closed
		if ((this.socket == null) && !this.socket.isClosed()) {
			Peer.LOGGER.log(Level.WARNING,
					"Socket connected once but not closed.");
		}

		// Open IO streams
		this.in = new DataInputStream(this.socket.getInputStream());
		this.out = new DataOutputStream(this.socket.getOutputStream());
	}

	/**
	 * Disconnects this peer.
	 */
	void disconnect() {
		// Disconnect the socket, close data streams, catch all exceptions
		try {
			this.keepRunning = false;

			this.socket.close();

			this.in.close();

			this.out.flush();
			this.out.close();

			Peer.LOGGER.info("Disconnected peer: " + this);
		} catch (final IOException ioe) {
			Peer.LOGGER
					.log(Level.WARNING,
							"I/O exception encountered when disconnecting peer "
									+ this, ioe);
		}
	}

	/**
	 * Sends a keep-alive message to the remote peer if the time between now and
	 * the previous message exceeds the limit set by KEEP_ALIVE_TIMEOUT.
	 * 
	 * @author Robert Moore
	 * @throws Exception
	 */
	protected void checkAndSendKeepAlive() throws Exception {
		final long now = System.currentTimeMillis();
		if ((now - this.lastMessageTime) > Peer.KEEP_ALIVE_TIMEOUT) {
			this.sendMessage(Message.KEEP_ALIVE);
			// Validate that the timestamp was updated
			if (now > this.lastMessageTime) {
				throw new Exception(
						"Didn't update lastMessageTime when sending a keep-alive!");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + Arrays.hashCode(this.peerId);
		result = (prime * result)
				+ ((this.ip == null) ? 0 : this.ip.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Peer)) {
			return false;
		}
		final Peer other = (Peer) obj;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("Peer [");
		if (this.peerId != null) {
			builder.append("peerId=");
			for (final byte element : this.peerId) {
				if ((element < ' ') || (element > 126)) {
					builder.append(String.format("_%02X", element));
				} else {
					builder.append((char) element);
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
	 * @param infohash
	 *            the 20-byte SHA-1 hash of the bencoded form of the info value
	 *            from the metainfo (.torrent) file
	 * @param peerId
	 *            the peer id generated by the client
	 * @return the handshake byte array
	 */
	private byte[] getHandshake() {
		// Preallocate bytes for handshake
		final byte[] handshake = new byte[68];

		// Header 19:BitTorrent protocol
		// Begin with byte 19
		handshake[0] = 19;

		// Add "BitTorrent protocol"
		System.arraycopy(Peer.BYTES_PROTOCOL, 0, handshake, 1,
				Peer.BYTES_PROTOCOL.length);

		// 8 reserved bytes 20-27 are already initialized to 0; skip + omit
		// commented-out code below

		// Add infohash SHA-1 hash - not encoded
		System.arraycopy(this.infoHash, 0, handshake, 28, this.infoHash.length);

		// Add peer id, which should match the infohash
		System.arraycopy(this.clientId, 0, handshake, 48, this.clientId.length);

		Peer.LOGGER.info("Generated handshake for " + this);

		return handshake;
	}

	/**
	 * Validate two handshakes for equality.
	 * 
	 * @param myHandshake
	 * @param otherHandshake
	 * @return the truth value for the equality of the handshakes
	 */
	private boolean validateHandshake(final byte[] otherHandshake) {

		if (otherHandshake == null) {
			return false;
		}

		// Verify the length
		if (otherHandshake.length != 68) {
			return false;
		}

		// Check protocol
		final byte[] otherProtocol = new byte[19];
		System.arraycopy(otherHandshake, 1, otherProtocol, 0,
				Peer.BYTES_PROTOCOL.length);
		if (!Arrays.equals(otherProtocol, Peer.BYTES_PROTOCOL)) {
			return false;
		}

		// Skip reserved bytes

		// Check info hash against info hash from .torrent file
		final byte[] otherInfoHash = new byte[20];
		System.arraycopy(otherHandshake, 28, otherInfoHash, 0, 20);
		if (!Arrays.equals(otherInfoHash, this.infoHash)) {
			return false;
		}

		// Check that peer ID is the same as from tracker
		final byte[] otherPeerId = new byte[20];
		System.arraycopy(otherHandshake, 48, otherPeerId, 0, 20);
		if (!Arrays.equals(otherPeerId, this.peerId)) {
			return false;
		}

		Peer.LOGGER.info("Handshake validated for " + this);

		return true;
	}

	void setTasks(final LinkedBlockingQueue<MessageTask> tasks) {
		this.tasks = tasks;
	}

	/**
	 * @param client
	 *            the client to set
	 */
	void setClient(RUBTClient client) {
		this.client = client;
	}

	public void requestPiece(final int pieceIndex, final int pieceLength)
			throws IOException {
		this.pieceIndex = pieceIndex;
		this.pieceLength = pieceLength;
		this.piece = new byte[pieceLength];
		this.lastBlockLength = this.pieceLength % Peer.BLOCK_LENGTH;

		this.blockOffset = 0;

		RequestMessage requestMsg;
		if (this.lastBlockLength == this.pieceLength) {
			// Request the last piece
			requestMsg = new RequestMessage(this.pieceIndex, this.blockOffset,
					this.lastBlockLength);
		} else {
			requestMsg = new RequestMessage(this.pieceIndex, this.blockOffset,
					Peer.BLOCK_LENGTH);
		}
		this.sendMessage(requestMsg);
	}

	/**
	 * Builds a file piece from blocks.
	 * 
	 * @param msg
	 *            the Piece Message containing file contents
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void buildPiece(final Message msg) throws InterruptedException,
			IOException {
		// Make sure this is a Piece Message
		if (msg.getId() == Message.ID_PIECE) {
			final PieceMessage pieceMsg = (PieceMessage) msg;

			// Add to client downloaded
			this.client.addDownloaded(pieceMsg.getBlock().length);

			// Confirm that a Piece Message was received for the piece currently
			// being built by this Peer. The block offset should indicate that
			// the expected block is being worked on, as well.
			if (pieceMsg.getPieceIndex() != this.pieceIndex) {
				Peer.LOGGER
						.warning("Incorrect piece received from " + pieceMsg);
			} else if (pieceMsg.getBlockOffset() != this.blockOffset) {
				Peer.LOGGER.warning("Incorrect block offset received from "
						+ pieceMsg);
			} else {
				// The Peer received a good Piece Message
				if (pieceMsg.getBlock().length == this.lastBlockLength) {
					// Write the last block of piece
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece,
							this.blockOffset, this.lastBlockLength);
					// Queue the full piece
					final PieceMessage returnMsg = new PieceMessage(
							this.pieceIndex, 0, this.piece);
					this.tasks.put(new MessageTask(this, returnMsg));
				} else if ((pieceMsg.getBlockOffset() + BLOCK_LENGTH + this.lastBlockLength) == this.pieceLength) {
					// Write the second to last block of piece
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece,
							this.blockOffset, Peer.BLOCK_LENGTH);
					RequestMessage requestMsg;
					
					// Request another piece
					this.blockOffset = this.blockOffset + Peer.BLOCK_LENGTH;
					requestMsg = new RequestMessage(this.pieceIndex,
							this.blockOffset, this.lastBlockLength);
					this.sendMessage(requestMsg);
				} else {
					// Temporarily store the block
					System.arraycopy(pieceMsg.getBlock(), 0, this.piece,
							this.blockOffset, Peer.BLOCK_LENGTH);
					RequestMessage requestMsg;

					// Request another piece
					this.blockOffset = this.blockOffset + Peer.BLOCK_LENGTH;
					requestMsg = new RequestMessage(this.pieceIndex,
							this.blockOffset, Peer.BLOCK_LENGTH);
					this.sendMessage(requestMsg);
				}
			}
		}
	}
}
