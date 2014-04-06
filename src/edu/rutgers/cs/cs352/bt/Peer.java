package edu.rutgers.cs.cs352.bt;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;

import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * @author Robert Moore
 *
 */
public class Peer {
	
	private byte[] id;
	/**
	 * @return the peer ID
	 */
	public byte[] getId() {
		return id;
	}

	private String ip;
	/**
	 * @return the peer IP
	 */
	public String getIp() {
		return ip;
	}
	
	private int port;
	
	public Peer(byte[] peerId, String ip, Integer port) {
		this.id = id;
		this.ip = ip;
		this.port = port;
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
	 * Disconnects this peer.
	 */
	public void disconnect() {
		// TODO: Disconnect the socket, catch all exceptions
		
		// Close data streams
		try {
			this.out.flush();
			this.out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		result = prime * result + Arrays.hashCode(id);
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
		if (!Arrays.equals(id, other.id)) {
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
		if (id != null) {
			builder.append("id=");
			builder.append(Utility.bytesToHexStr(id));
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
}
