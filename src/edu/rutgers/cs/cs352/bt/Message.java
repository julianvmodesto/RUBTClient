
package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a Peer Wire Protocol message, specifically those
 * after the handshake. Also contains methods for encoding and decoding
 * the messages according to the protocol specs.
 * 
 * @author Robert Moore
 * @author Julian Modesto
 */
public class Message {

	/**
	 * Faux message id value for Keep-Alive messages.
	 */
	public static final byte ID_KEEP_ALIVE = 9;
	/**
	 * Message id value for Choke messages.
	 */
	public static final byte ID_CHOKE = 0;
	/**
	 * Message id value for Unchoke messages.
	 */
	public static final byte ID_UNCHOKE = 1;
	/**
	 * Message id value for Interested messages.
	 */
	public static final byte ID_INTERESTED = 2;
	/**
	 * Message id value for Uninterested messages.
	 */
	public static final byte ID_UNINTERESTED = 3;
	/**
	 * Message id value for Have messages.
	 */
	public static final byte ID_HAVE = 4;
	/**
	 * Message id value for Bit-Field messages.
	 */
	public static final byte ID_BIT_FIELD = 5;
	/**
	 * Message id value for Request messages.
	 */
	public static final byte ID_REQUEST = 6;
	/**
	 * Message id value for Piece messages.
	 */
	public static final byte ID_PIECE = 7;
	/**
	 * Message id value for Cancel messages.
	 */
	public static final byte ID_CANCEL = 8;

	/**
	 * A keep-alive message.
	 */
	public static final Message KEEP_ALIVE = new Message(ID_KEEP_ALIVE, (byte) 0);
	/**
	 * A choke message.
	 */
	public static final Message CHOKE = new Message(1, ID_CHOKE);
	/**
	 * An unchoke message.
	 */
	public static final Message UNCHOKE = new Message(1, ID_UNCHOKE);
	/**
	 * An interested message.
	 */
	public static final Message INTERESTED = new Message(1, ID_INTERESTED);
	/**
	 * An uninterested message.
	 */
	public static final Message UNINTERESTED = new Message(1, ID_UNINTERESTED);

	public static final String[] ID_NAMES = { "choke", "unchoke", "interested",
		"uninterested", "have", "bit field", "request", "piece", "cancel",
	"keep alive" };


	private final int length;

	private final byte id;

	protected Message(int length, byte id) {
		this.length = length;
		this.id = id;
	}

	/**
	 * The superclass Message doesn't have any payload to write, so nothing will be written.
	 * @param dos
	 * @throws IOException
	 */
	public void writePayload(DataOutputStream dos) throws IOException {
		// Nothing here
	}

	public byte getId() {
		return this.id;
	}

	/**
	 * Reads the next Peer message from the provided DataInputStream.
	 * 
	 * @param din
	 *          the input stream to read
	 * @return the decoded message to {@code null} if an
	 */
	public static Message read(final DataInputStream din) throws IOException {
		int length = din.readInt();
		if (length == 0) {
			return KEEP_ALIVE;
		}
		// Read type
		byte type = din.readByte();

		int pieceIndex;
		int blockOffset;
		int blockLength;
		byte[] block;

		switch (type) {
		case ID_CHOKE:
			return CHOKE;
		case ID_UNCHOKE:
			return UNCHOKE;
		case ID_INTERESTED:
			return INTERESTED;
		case ID_UNINTERESTED:
			return UNINTERESTED;
		case ID_HAVE:
			pieceIndex = din.readInt();
			return new HaveMessage(pieceIndex);
		case ID_BIT_FIELD:
			byte[] bitField = new byte[length - 1];
			din.readFully(bitField);
			return new BitFieldMessage(length, bitField);
		case ID_REQUEST:
			pieceIndex = din.readInt();
			blockOffset = din.readInt();
			blockLength = din.readInt();
			return new RequestMessage(pieceIndex, blockOffset, blockLength);
		case ID_PIECE:
			pieceIndex = din.readInt();
			blockOffset = din.readInt();
			block = new byte[length - 9];
			din.readFully(block);
			return new PieceMessage(pieceIndex, blockOffset, block);
		}
		
		return null;
	}

	/**
	 * Writes this message to the provided DataOutputStream.
	 * 
	 * @param dout
	 *          the output stream to write.
	 * @throws IOException
	 *           if an IOException occurs.
	 */
	public void write(final DataOutputStream dout) throws IOException {
		dout.writeInt(this.length);
		if (this.length > 0) {
			dout.writeByte(this.id);
			// Write payload for Have, Bitfield, Request, Piece, Cancel
			// messages
			this.writePayload(dout);
		}

		dout.flush();
	}
	
	/**
	 * A have message.
	 *
	 */
	public static class HaveMessage extends Message {
		private final int pieceIndex;

		public HaveMessage(int pieceIndex) {
			super(5, ID_HAVE);
			this.pieceIndex = pieceIndex;
		}

		public int getPieceIndex() {
			return this.pieceIndex;
		}

		@Override
		public void writePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.pieceIndex);
		}
	}

	/**
	 * A bit field message.
	 *
	 */
	public static class BitFieldMessage extends Message {
		private final byte[] bitField;

		public BitFieldMessage(int length, byte[] bitField) {
			super(1 + length, ID_BIT_FIELD);
			this.bitField = bitField;
		}

		public byte[] getBitField() {
			return this.bitField;
		}

		@Override
		public void writePayload(DataOutputStream dos) throws IOException {
			dos.write(this.bitField);
		}
	}

	/**
	 * A request message.
	 *
	 */
	public static class RequestMessage extends Message {
		/**
		 * The integer specifying the zero-based piece index.
		 */
		private final int pieceIndex;
		/**
		 * The integer specifying the zero-based byte offset within the piece.
		 */
		private final int blockOffset;
		/**
		 * The integer specifying the requested length.
		 */
		private final int blockLength;

		public RequestMessage(int pieceIndex, int blockOffset, int blockLength) {
			super(13, ID_REQUEST);
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

		@Override
		public void writePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.pieceIndex);
			dos.writeInt(this.blockOffset);
			dos.writeInt(this.blockLength);
		}
	}

	/**
	 * A piece message.
	 *
	 */
	public static class PieceMessage extends Message {
		/**
		 * The integer specifying the zero-based piece index.
		 */
		private final int pieceIndex;
		/**
		 * The integer specifying the zero-based byte offset within the piece.
		 */
		private final int blockOffset;
		/**
		 * The block of data, which is a subset of the piece specified by index.
		 */
		private final byte[] block;

		public PieceMessage(int pieceIndex, int blockOffset, byte block[]) {
			super(9 + block.length, ID_PIECE);
			this.pieceIndex = pieceIndex;
			this.blockOffset = blockOffset;
			this.block = block;
		}

		public int getPieceIndex() {
			return this.pieceIndex;
		}

		public int getBlockOffset() {
			return this.blockOffset;
		}

		public byte[] getBlock() {
			return this.block;
		}

		@Override
		public void writePayload(DataOutputStream dos) throws IOException {
			dos.writeInt(this.pieceIndex);
			dos.writeInt(this.blockOffset);
			dos.write(this.block);
		}
	}
}
