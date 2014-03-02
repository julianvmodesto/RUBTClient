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
	static final byte TYPE_CHOKE = 0;
	static final byte TYPE_UNCHOKE = 1;
	static final byte TYPE_INTERESTED = 2;
	static final byte TYPE_UNINTERESTED = 3;
	static final byte TYPE_HAVE = 4;
	static final byte TYPE_BITFIELD = 5;
	static final byte TYPE_REQUEST = 6;
	static final byte TYPE_PIECE = 7;
	static final byte TYPE_CANCEL = 8;
	
	private static final String[] TYPE_NAMES = {"choke","unchoke","interested","uninterested","have",
		"bitField","request","piece","cancel"};
	
	// Messages
	private static final PeerMessage MESSAGE_CHOKE = new PeerMessage(1, TYPE_CHOKE);
	private static final PeerMessage MESSAGE_UNCHOKE = new PeerMessage(1, TYPE_UNCHOKE);
	private static final PeerMessage MESSAGE_INTERESTED = new PeerMessage(1, TYPE_INTERESTED);
	private static final PeerMessage MESSAGE_UNINTERESTED = new PeerMessage(1, TYPE_UNINTERESTED);
	//private static final PeerMessage MESSAGE_HAVE = new PeerMessage(5, TYPE_HAVE);
	//private static final PeerMessage MESSAGE_REQUEST = new PeerMessage(13, TYPE_REQUEST);
	//private static final PeerMessage MESSAGE_PIECE = new PeerMessage(9, TYPE_PIECE);
	
	
	public static PeerMessage read(InputStream is) throws IOException {
		DataInputStream dis = new DataInputStream(is);
		
		int length = dis.readInt();
		
		if (length == 0) {
			// MESSAGE_KEEP_ALIVE
		}
		
		byte type = dis.readByte();
		
		int pieceIndex;
		int blockOffset;
		int blockLength;
		
		switch (type) {
		case TYPE_CHOKE:
			return MESSAGE_CHOKE;
		case TYPE_UNCHOKE:
			return MESSAGE_UNCHOKE;
		case TYPE_INTERESTED:
			return MESSAGE_INTERESTED;
		case TYPE_UNINTERESTED:
			return MESSAGE_UNINTERESTED;
		case TYPE_HAVE:
			pieceIndex = dis.readInt();
			return new HaveMessage(pieceIndex);
		case TYPE_BITFIELD:
			byte[] bitField = new byte[length - 1];
			dis.readFully(bitField);
			return new BitFieldMessage(length, bitField);
		case TYPE_REQUEST:
			pieceIndex = dis.readInt();
			blockOffset = dis.readInt();
			blockLength = dis.readInt();
			return new RequestMessage(pieceIndex, blockOffset, blockLength);
		case TYPE_PIECE:
			break;
		}
		
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
	
	public static class HaveMessage extends PeerMessage {
		private final int pieceIndex;
		
		public HaveMessage(int pieceIndex) {
			super(5, TYPE_HAVE);
			this.pieceIndex = pieceIndex;
		}
		
		public int getPieceIndex() {
			return this.pieceIndex;
		}
	}
	
	public static class BitFieldMessage extends PeerMessage {
		private final byte[] bitField;
		
		public BitFieldMessage(int length, byte[] bitField) {
			super(1 + length, TYPE_BITFIELD);
			this.bitField = bitField;
		}
		
		public byte[] getBitField() {
			return this.bitField;
		}
	}
	
	public static class RequestMessage extends PeerMessage {
		private final int pieceIndex;
		private final int blockOffset;
		private final int blockLength;
		
		public RequestMessage(int pieceIndex, int blockOffset, int blockLength) {
			super(13, TYPE_REQUEST);
			this.pieceIndex = pieceIndex;
			this.blockOffset = blockOffset;
			this.blockLength = blockLength;
		}
		
		public int getPieceIndex() {
			return this.pieceIndex;
		}
		
		public int getBlockOffset() {
			return this.blockOffset;
		}
		
		public int getBlockLength() {
			return this.blockLength;
		}
		
		public void writePayload(OutputStream os) throws IOException {
			DataOutputStream dos = new DataOutputStream(os);
			dos.writeInt(this.pieceIndex);
			dos.writeInt(this.blockOffset);
			dos.writeInt(this.blockLength);
		}
		
	}
}
