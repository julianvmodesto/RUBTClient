/**
 * Authors: Gaurav Kumar, Julian Modesto, Jeffrey Rocha
 */
package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import edu.rutgers.cs.cs352.bt.PeerMessage.KeepAliveMessage;
import edu.rutgers.cs.cs352.bt.PeerMessage.PieceMessage;
import edu.rutgers.cs.cs352.bt.PeerMessage.RequestMessage;
import edu.rutgers.cs.cs352.bt.PeerMessage.*;

/**
 * @author Julian Modesto
 *
 */
public class PeerCommunicator extends Thread {

	//	private final static Logger LOGGER = 
	//			Logger.getLogger(PeerCommunicator.class.getName());

	// BitTorrent protocol
	final byte[] PROTOCOL = {'B','i','t','T','o','r','r','e','n','t',' ',
			'p','r','o','t','o','c','o','l'};

	// Choked status and interested status of client and peer
	private boolean amChoking; // this client is choking the peer
	private boolean peerChoking; // peer is choking this client
	private boolean amInterested; // this client is interested in the peer
	private boolean peerInterested; // peer is interested in this client

	// Flag to keep the peer running
	private boolean keepRunning;

	private Socket socket;
	private DataInputStream dataIn;
	private DataOutputStream dataOut;
	
	// File information
	private static int pieceLength;
	private static int totalPieces;
	private static int fileLength;

	// Download information
	private int pieceIndex = 0;
	private int blockOffset;
	int blockLength = PeerMessage.BLOCK_LENGTH;
	
	private byte[] myBitField;
	private byte[] peerBitField;

	private LinkedBlockingQueue<PeerMessage> peerMessages;

	// Set up timer
	private static final long KEEP_ALIVE_TIMEOUT = 120000;
	private Timer keepAliveTimer = new Timer();
	private long lastMessageTime = System.currentTimeMillis();

	private byte[] peerId;
	private String address;
	private int port;
	private byte[] infohash;
	private byte[] myPeerId;

	public PeerCommunicator(byte[] peerId, String address, int port, byte[] infohash, byte[] myPeerId) {
		this.peerId = peerId;
		this.address = address;
		this.port = port;
		this.infohash = infohash;
		this.myPeerId = myPeerId;

		// Set default states
		this.amChoking = true;
		this.peerChoking = true;
		this.amInterested = false;
		this.peerInterested = false;
		
		// Set file information
		this.pieceLength = RUBTClient.torrent_info.piece_length;
		this.totalPieces = RUBTClient.torrent_info.piece_hashes.length;
		this.fileLength = RUBTClient.torrent_info.file_length;
		
		this.keepRunning = true;
	}
	
	private void shutdown() {
		this.keepRunning = false;
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
		System.arraycopy(PROTOCOL, 0, handshake, 1, PROTOCOL.length);

		// 8 reserved bytes 20-27 are already initialized to 0; skip + omit commented-out code below

		// Add infohash SHA-1 hash - not encoded
		System.arraycopy(infohash, 0, handshake, 28, this.infohash.length);

		// Add peer id, which should match the infohash
		System.arraycopy(this.myPeerId, 0, handshake, 48, this.myPeerId.length);	

		System.out.println("Generated handshake.");

		return handshake;
	}

	/**
	 * Validate two handshakes for equality
	 * 
	 * @param myHandshake
	 * @param otherHandshake
	 * @return the truth value for the equality of the handshakes
	 */
	private boolean validateHandshake(byte[] otherHandshake) {

		if (otherHandshake == null) {
			return false;
		}

		if (otherHandshake.length != 68) {
			return false;
		}
		
		// Check protocol
		byte[] otherProtocol = new byte[19];
		System.arraycopy(otherHandshake, 1, otherProtocol, 0, PROTOCOL.length);
		if (!Arrays.equals(otherProtocol, PROTOCOL)) {
			return false;
		}

		// Skip header and reserved bytes

		// Check info hash against info hash from .torrent file
		byte[] otherInfoHash = new byte[20];
		System.arraycopy(otherHandshake, 28, otherInfoHash, 0, 20);
		if (!Arrays.equals(otherInfoHash, this.infohash)) {
			return false;
		}
		
		// Check that peer ID is the same as from tracker
		byte[] otherPeerId = new byte[20];
		System.arraycopy(otherHandshake, 48, otherPeerId, 0, 20);
		if (!Arrays.equals(otherPeerId, this.peerId)) {
			return false;
		}

		System.out.println("Handshake validated.");

		return true;
	}

	/**
	 * @throws IOException 
	 * 
	 */
	private void connect() throws IOException {

		// Check that port number is within standard TCP range i.e. max port number is an unsigned, 16-bit short = 2^16 - 1 = 65535
		if (port <= 0 | port >= 65535) {
			System.err.println("Error: port number" + port + "is out of bounds");
			System.exit(1);
		}

		// Create socket
		socket = null;
		try {
			socket = new Socket(this.address, port);
		} catch (UnknownHostException uhe) {
			System.err.println("Error: the IP address of the host could not be determined from " + this.address + ".");
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
		dataIn = new DataInputStream(socket.getInputStream());
		dataOut = new DataOutputStream(socket.getOutputStream());
	}

	/**
	 * The peer's run method - keeps reading messages 
	 * from the remote peer until disconnected.
	 * 
	 */
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
					PeerCommunicator.this.checkAndSendKeepAlive();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		      }
		    
		    }, new Date(), 10000);

			// Send handshake
			byte[] myHandshake = getHandshake();
			dataOut.write(myHandshake);
			dataOut.flush();

			// Read response
			byte[] peerHandshake = new byte[68];
			dataIn.readFully(peerHandshake);

			// Validate handshake
			if (!validateHandshake(peerHandshake)) {
				System.err.println("Error: handshake is incorrect.");
			}
			
			// Send an interested message upon validating the handshake
			this.amInterested = true;
			this.sendPeerMessage(PeerMessage.MESSAGE_INTERESTED);

			// Main loop
			while (this.keepRunning) {
				// read message from socket
				PeerMessage message = PeerMessage.read(this.dataIn);
				if (message == null) {
					System.err.println("Error: no message.");
				}
				
				System.out.println("Received message: " + PeerMessage.TYPE_NAMES[message.getType()]);

				switch (message.getType()) {
				case PeerMessage.TYPE_KEEP_ALIVE:
					break;
				case PeerMessage.TYPE_CHOKE:
					// Update internal state
					this.peerChoking = true;
					break;
				case PeerMessage.TYPE_UNCHOKE:
					// Update internal state
					this.peerChoking = false;
					
					if (this.amInterested) {
						this.sendPeerMessage(this.getRequestMessage());
					}
					
					break;
				case PeerMessage.TYPE_INTERESTED:
					// Update internal state
					this.peerInterested = true;
					
					// Only send unchoke if not downloading
					if (!amInterested) {
						// Send unchoke
						this.sendPeerMessage(PeerMessage.MESSAGE_UNCHOKE);
					}
					break;
				case PeerMessage.TYPE_UNINTERESTED:
					// Update internal state
					this.peerInterested = false;
					break;
				case PeerMessage.TYPE_BITFIELD:
					break;
				case PeerMessage.TYPE_HAVE:
					//TODO inspect bit field
					break;
				case PeerMessage.TYPE_REQUEST:
					//TODO process request
					break;
				case PeerMessage.TYPE_PIECE:
					
					this.sendPeerMessage(this.getRequestMessage());
					break;
				}
			}
			
			// The peer is done now, kill the timer
		    try { this.keepAliveTimer.cancel(); } catch(Exception e) { }

			// Close IO streams
			dataIn.close();
			dataOut.flush();
			dataOut.close();

			// Close socket
			socket.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Returns the next request message to be sent to the peer,
	 * based on the remaining pieces to be downloaded
	 * 
	 * @author Julian Modesto
	 * @return the request message
	 */
	private PeerMessage getRequestMessage() {
		PeerMessage.RequestMessage requestMessage;
		
		this.setPieceIndex();
		
		// Check if requesting last piece
		if (this.pieceIndex == this.totalPieces - 1) {
			// Request the last irregularly-sized piece
			this.blockLength = this.fileLength % this.pieceLength;
		}
		
		requestMessage = new PeerMessage.RequestMessage(this.pieceIndex, this.blockOffset, this.blockLength);
		
		this.blockOffset += this.pieceLength;
		
		return requestMessage;
	}
	
	private void setPieceIndex() {
		this.pieceIndex++;
	}

	public void verifyPiece(byte[] piece) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			byte[] hash = sha.digest(piece);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
			shutdown();
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
			// The "sendMessage" method should update lastMessageTime
			new KeepAliveMessage().write(this.dataOut);
			// Validate that the timestamp was updated
			if(now > this.lastMessageTime){
				throw new Exception("Didn't update lastMessageTime when sending a keep-alive!");
			}
			System.out.println("Sent Keep-Alive");
		}
	}
	
	private void sendPeerMessage(PeerMessage peerMessage) throws IOException {
		this.lastMessageTime = System.currentTimeMillis();
		peerMessage.write(this.dataOut);
		System.out.println("Sent " + PeerMessage.TYPE_NAMES[peerMessage.getType()] + " message to the peer.");
	}
}
