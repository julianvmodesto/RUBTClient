/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * @author Julian Modesto
 *
 */
public class PeerCommunicator {
	
	private final static Logger LOGGER = Logger.getLogger(PeerCommunicator.class.getName());
	
	private static final byte[] GROUP = {'G','P','O','1','6'};
	
	// Message ID
	private static final int MSG_CHOKE = 0;
	private static final int MSG_UNCHOKE = 1;
	private static final int MSG_INTERESTED = 2;
	private static final int MSG_UNINTERESTED = 3;
	private static final int MSG_HAVE = 4;
	private static final int MSG_REQUEST = 6;
	private static final int MSG_PIECE = 7;
	
	/**
	 * Generates the handshake from the client to the peer
	 * 
	 * @author Julian Modesto
	 * @param infohash the 20-byte SHA-1 hash of the bencoded form of the info value from the metainfo (.torrent) file
	 * @param peerID the peer id generated by the client
	 * @return the handshake byte array
	 */
	public static byte[] getHandshake(byte[] infohash, byte[] peerID) {
		byte[] handshake = new byte[68];
		int position = 0;
		
		// Header 19:BitTorrent protocol
		// Begin with byte 19
		handshake[0] = 19;
		
		// Add "BitTorrent protocol"
		byte[] protocol = {'B','i','t','T','o','r','r','e','n','t',' ','p','r','o','t','o','c','o','l'};
		System.arraycopy(protocol, 0, handshake, 1, protocol.length);
		
		// 8 reserved bytes 20-27 are already initialized to 0; skip + omit commented-out code below
//		// Add 8 reserved bytes which are set to 0
//		byte[] reserved = new byte[8]; // Initialized to 0
//		System.arraycopy(reserved, 0, handshake, 20, reserved.length);
		
		// Add infohash
		System.arraycopy(infohash, 0, handshake, 28, infohash.length);
		
		// Add peer id
		System.arraycopy(peerID, 0, handshake, 48, peerID.length);	
		
		return handshake;
	}
	
	/**
	 * 
	 */
	public static void getConnection(String address, int port) {
		
		// Check that port number is within standard TCP range i.e. max port number is an unsigned, 16-bit short = 2^16 - 1 = 65535
		if (port <= 0 | port >= 65535) {
			LOGGER.warning("Error: port number" + port + "is out of bounds");
			return;
		}
		
		Socket socket = null;
		try {
			socket = new Socket(address, port);
		} catch (UnknownHostException uhe) {
			LOGGER.warning("Error: the IP address of the host could not be determined from " + address);
			uhe.printStackTrace();
			return;
		} catch (IOException ioe) {
			LOGGER.warning("Error: an I/O error occurred");
			LOGGER.warning(ioe.getMessage());
			ioe.printStackTrace();
			return;
		}
	}
	
	/**
	 * 
	 */
	public void getAvailablePieces() {
		
	}
	
	/**
	 * 
	 */
	public void sendInterestedInPiece() {
		
	}
	
	/**
	 * 
	 */
	public void requestPiece() {
		
	}
}
