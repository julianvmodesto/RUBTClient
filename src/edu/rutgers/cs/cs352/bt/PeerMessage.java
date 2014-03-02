/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Julian Modesto
 *
 */
public class PeerMessage {
	
	private int length;
	private byte type;
	
	public PeerMessage(int length, byte type) {
		this.length = length;
		this.type = type;
	}
	
	public int getLength() {
		return this.length;
	}
	
	public byte getType() {
		return this.type;
	}
		
	// Types
	private static final byte TYPE_CHOKE = 0;
	private static final byte TYPE_UNCHOKE = 1;
	private static final byte TYPE_INTERESTED = 2;
	private static final byte TYPE_UNINTERESTED = 3;
	private static final byte TYPE_HAVE = 4;
	private static final byte TYPE_REQUEST = 6;
	private static final byte TYPE_PIECE = 7;
	
	private static final String[] TYPE_NAMES = {"choke","unchoke","interested","uninterested","have",
		null,"request","piece"};
	
	// Messages
	private static final PeerMessage MESSAGE_CHOKE = new PeerMessage(1, TYPE_CHOKE);
	private static final PeerMessage MESSAGE_UNCHOKE = new PeerMessage(1, TYPE_UNCHOKE);
	private static final PeerMessage MESSAGE_INTERESTED = new PeerMessage(1, TYPE_INTERESTED);
	private static final PeerMessage MESSAGE_UNINTERESTEd = new PeerMessage(1, TYPE_UNINTERESTED);
	private static final PeerMessage MESSAGE_HAVE = new PeerMessage(5, TYPE_HAVE);
	private static final PeerMessage MESSAGE_REQUEST = new PeerMessage(13, TYPE_REQUEST);
	private static final PeerMessage MESSAGE_PIECE = new PeerMessage(9, TYPE_PIECE);
	
	public static PeerMessage read(InputStream is) throws IOException {
		DataInputStream dis = new DataInputStream(is);
		byte[] message = new byte[1];
		dis.readFully(message);
		
		if (message[0] == TYPE_CHOKE) {
			
		}
		if (message[0] == TYPE_UNCHOKE) {
			
		}
		if (message[0] == TYPE_INTERESTED) {
			
		}
		if (message[0] == TYPE_UNINTERESTED) {
			
		}
		if (message[0] == TYPE_HAVE) {
			
		}
		if (message[0] == TYPE_REQUEST) {
			
		}
		if (message[0] == TYPE_PIECE) {
			
		}
		//return new BitFieldMessage(bits);
		return null;
	}
	
	public void write(OutputStream os) throws IOException {
		DataOutputStream dos = new DataOutputStream(os);
		dos.writeInt(this.length);
		if (this.length == 0) {
			//writeByte
		} else if (this.length > 1) {
			//writePayload
		}
	}
	
	private class BitFieldMessage {
		
	}
	
	public class RequestMessage extends PeerMessage {
		private final int pieceIndex;
		private final int blockOffset;
		private final int blockLength;
		
		public RequestMessage(int pieceIndex, int blockOffset, int blockLength) {
			super(13, TYPE_REQUEST);
			this.pieceIndex = pieceIndex;
			this.blockOffset = blockOffset;
			this.blockLength = blockLength;
		}
		
		protected void writePayload(OutputStream os) throws IOException {
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(this.pieceIndex);
			dos.writeInt(this.blockOffset);
			dos.writeInt(this.blockLength);
			
		}
		
	}
}
