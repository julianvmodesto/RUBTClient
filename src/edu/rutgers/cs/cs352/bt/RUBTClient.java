package edu.rutgers.cs.cs352.bt;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.rutgers.cs.cs352.bt.Message.BitfieldMessage;
import edu.rutgers.cs.cs352.bt.Message.HaveMessage;
import edu.rutgers.cs.cs352.bt.Message.PieceMessage;
import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * Main class for RUBTClient. After starting, spends its time listening on the
 * incoming message queue in order to decide what to do.
 * 
 * @author Robert Moore
 * @author Julian Modesto
 * @author Gaur
 */

public class RUBTClient extends Thread {

	/**
	 * Logger for the local client.
	 */
	private final static Logger LOGGER = Logger.getLogger(RUBTClient.class
			.getName());

	public static void main(final String[] args) {

		// Check number/type of arguments
		if (args.length != 2) {
			RUBTClient.LOGGER.severe("Two arguments required");
			System.exit(1);
		}

		// Open torrent file
		byte[] metaBytes = null;
		try {
			final File metaFile = new File(args[0]);
			final DataInputStream metaIn = new DataInputStream(
					new FileInputStream(metaFile));
			metaBytes = new byte[(int) metaFile.length()];
			metaIn.readFully(metaBytes);
			metaIn.close();
		} catch (final FileNotFoundException fnfe) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"File not found exception encountered for file with filename \""
							+ args[0] + "\"", fnfe);
			System.exit(1);
		} catch (final IOException ioe) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"I/O exception encountered for file with filename \""
							+ args[0] + "\"", ioe);
			System.exit(1);
		}

		// Null check on metaBytes
		if (metaBytes == null) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"Corrupt torrent metainfo file.");
			System.exit(1);
		}

		// Decode torrent file
		TorrentInfo tInfo = null;
		try {
			tInfo = new TorrentInfo(metaBytes);
		} catch (final BencodingException be) {
			RUBTClient.LOGGER.log(Level.WARNING,
					"Bencoding exception encountered", be);
		}

		RUBTClient client;
		try {
			client = new RUBTClient(tInfo, args[1]);

			// Launches the client as a thread
			client.start();
			client.join();
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * The TorrentInfo object containing information for the torrent metainfo
	 * file.
	 */
	private final TorrentInfo tInfo;
	/**
	 * The number of total pieces for the file.
	 */
	private final int totalPieces;
	/**
	 * The total length of the file.
	 */
	private final int fileLength;
	/**
	 * The length of each piece of the file.
	 */
	private final int pieceLength;
	/**
	 * The name to save the file as.
	 */
	private final String outFileName;
	/**
	 * The file to write to.
	 */
	private RandomAccessFile outFile;
	/**
	 * A queue to process message tasks as received and queued from each peer.
	 */
	private final LinkedBlockingQueue<MessageTask> tasks = new LinkedBlockingQueue<MessageTask>();

	/**
	 * Generate a random peer ID value to identify the local client.
	 */
	private final byte[] peerId = RUBTClient.generateMyPeerId();

	/**
	 * Hard code the first 4 bytes of our client's peer ID.
	 */
	private static final byte[] BYTES_GROUP = { 'G', 'P', '1', '6' };

	/**
	 * The initial port for the local client to listen on.
	 */
	private final int port = 6881;

	/**
	 * The local client's bitfield.
	 */
	private byte[] bitfield;

	/**
	 * The amount of bytes downloaded by the client from peers.
	 */
	private int downloaded;
	/**
	 * The amount of bytes uploaded by the client to peers.
	 */
	private int uploaded;
	/**
	 * The amount of bytes required to download.
	 */
	private int left;

	/**
	 * Retrieve the amount of bytes the client has downladed.
	 * 
	 * @return the amount of bytes downloaded
	 */
	private int getDownloaded() {
		return this.downloaded;
	}

	/**
	 * Add to the amount of bytes the client has downloaded.
	 * 
	 * @param downloaded
	 */
	synchronized void addDownloaded(int downloaded) {
		RUBTClient.LOGGER.info("Amount downloaded = " + this.downloaded);
		this.downloaded += downloaded;
	}

	/**
	 * @return the uploaded
	 */
	private int getUploaded() {
		return this.uploaded;
	}

	/**
	 * @return the left
	 */
	private int getLeft() {
		return this.left;
	}

	/**
	 * List of peers currently connected to the client.
	 */
	private final List<Peer> peers = Collections
			.synchronizedList(new LinkedList<Peer>());

	/**
	 * A timer for scheduling tracker announces.
	 */
	final Timer trackerTimer = new Timer();

	/**
	 * Tracker interface.
	 */
	final Tracker tracker;
	/**
	 * Flag to keep the main loop running. Once false, the client *should* exit.
	 */
	private volatile boolean keepRunning = true;

	/**
	 * Define the timed task to announce to the tracker.
	 * 
	 */
	private static class TrackerAnnounceTask extends TimerTask {
		private final RUBTClient client;

		public TrackerAnnounceTask(final RUBTClient client) {
			this.client = client;
		}

		@Override
		public void run() {
			List<Peer> peers = null;
			try {
				// Get peers
				peers = this.client.tracker.announce(
						this.client.getDownloaded(), this.client.getUploaded(),
						this.client.getLeft(), "");

				// Add new peers
				if ((peers != null) && !peers.isEmpty()) {
					this.client.addPeers(peers);
				}

				this.client.trackerTimer.schedule(this,
						this.client.tracker.getInterval() * 1000);

			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final BencodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final IllegalStateException ise) {

			}
		}
	}

	/**
	 * Constructor for a new RUBTClient that downloads a file as from the
	 * specification in the torrent file of interest.
	 * 
	 * @param tInfo
	 *            the TorrentInfo object containing torrent metadata
	 * @param outFile
	 *            the file to write the download to
	 */
	public RUBTClient(final TorrentInfo tInfo, final String outFile) {
		this.tInfo = tInfo;
		this.outFileName = outFile;
		try {
			RUBTClient.LOGGER.info("Starting with peer id \""
					+ new String(this.peerId, "US-ASCII") + "\"");
		} catch (final UnsupportedEncodingException uee) {
			// Nope, can't happen
		}
		this.tracker = new Tracker(this.peerId, this.tInfo.info_hash.array(),
				this.tInfo.announce_url.toString(), this.port);

		this.downloaded = 0;
		this.uploaded = 0;
		this.left = this.tInfo.file_length;

		this.totalPieces = this.tInfo.piece_hashes.length;
		this.fileLength = this.tInfo.file_length;
		this.pieceLength = this.tInfo.piece_length;

		RUBTClient.LOGGER.info("Total pieces: " + this.totalPieces);
		RUBTClient.LOGGER.info("File length: " + this.fileLength);
		RUBTClient.LOGGER.info("Piece length: " + this.pieceLength);
		RUBTClient.LOGGER.info("Last piece length: " + (this.fileLength % this.pieceLength));
	}

	@Override
	public void run() {

		try {
			this.outFile = new RandomAccessFile(this.outFileName, "rw");

			// Allocate the total file
			if (this.outFile.length() != this.fileLength) {
				this.outFile.setLength(this.fileLength);
			}
			
			// Set client bitfield
			this.setBitfield();
			
			RUBTClient.LOGGER.info("Starting bitfield: "
					+ this.getBitfieldString());

		} catch (final FileNotFoundException fnfe) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"Unable to open output file for writing!", fnfe);
			// Exit right now, since nothing else was started yet
			return;
		} catch (IOException ioe) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"I/O exception encountered when accessing output file!",
					ioe);
			// Exit right now, since nothing else was started yet
			return;
		}

		// Send "started" announce and attempt to connect through ports 6881 -
		// 6889
		List<Peer> peers = null;
		int announcePortIncrement;
		boolean trackerFailure = true;
		for (announcePortIncrement = 0; (announcePortIncrement < 9)
				&& (trackerFailure == true); announcePortIncrement++) {
			if (announcePortIncrement != 0) {
				RUBTClient.LOGGER.warning("Retrying on a new port");
			}
			try {
				peers = this.tracker.announce(this.getDownloaded(),
						this.getUploaded(), this.getLeft(), "started");
				trackerFailure = false;
				RUBTClient.LOGGER.log(
						Level.INFO,
						"Connected to tracker on port "
								+ this.tracker.getPort());
			} catch (final IOException ioe) {
				this.tracker.setPort(this.tracker.getPort() + 1);
				RUBTClient.LOGGER
						.log(Level.WARNING,
								"I/O exception encountered and communication with tracker failed",
								ioe);
				trackerFailure = true;
			} catch (final BencodingException be) {
				this.tracker.setPort(this.tracker.getPort() + 1);
				RUBTClient.LOGGER.log(Level.WARNING,
						"Tracker response invalid.", be);
				trackerFailure = true;
			}
		}

		this.addPeers(peers);
		{
			// Schedule the first "regular" announce - the rest are schedule by
			// the
			// task itself
			final int interval = this.tracker.getInterval();
			this.trackerTimer.schedule(new TrackerAnnounceTask(this),
					interval * 1000);
		}

		// Main loop:
		while (this.keepRunning) {
			try {
				final MessageTask task = this.tasks.take();
				// Process the task
				final Message msg = task.getMessage();
				final Peer peer = task.getPeer();

				RUBTClient.LOGGER.info(peer + " sent " + msg);

				switch (msg.getId()) {
				case Message.ID_KEEP_ALIVE:
					peer.sendMessage(Message.KEEP_ALIVE);
					break;
				case Message.ID_CHOKE:
					// Update internal state
					peer.setLocalChoked(true);
					break;
				case Message.ID_UNCHOKE:
					// Update internal state
					peer.setLocalChoked(false);

					if (!peer.amChoked() && peer.amInterested()) {
						this.chooseAndRequestPiece(peer);
					} else {
						peer.sendMessage(Message.KEEP_ALIVE);
					}
					break;
				case Message.ID_INTERESTED:
					// Update internal state
					peer.setRemoteInterested(true);

					// Only send unchoke if not downloading
					if (peer.amInterested()) {
						peer.sendMessage(Message.CHOKE);
						peer.setRemoteChoked(true);
					} else {
						peer.sendMessage(Message.UNCHOKE);
						peer.setRemoteChoked(false);
					}
					break;
				case Message.ID_UNINTERESTED:
					// Update internal state
					peer.setRemoteInterested(false);
					peer.sendMessage(Message.KEEP_ALIVE);
					break;
				case Message.ID_BITFIELD:
					// Set peer bitfield
					final BitfieldMessage bitfieldMsg = (BitfieldMessage) msg;
					peer.setBitfield(bitfieldMsg.getBitfield());

					// Inspect bitfield
					peer.setLocalInterested(this.amInterested(peer));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					} else if (peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					} else {
						peer.sendMessage(Message.KEEP_ALIVE);
					}
					break;
				case Message.ID_HAVE:
					final HaveMessage haveMsg = (HaveMessage) msg;

					if (peer.getBitfield() == null) {
						peer.initializeBitfield(this.totalPieces);
					}
					peer.setBitfieldBit(haveMsg.getPieceIndex());

					peer.setLocalInterested(this.amInterested(peer));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
						peer.setLocalInterested(true);
					} else {
						peer.sendMessage(Message.KEEP_ALIVE);
					}
					break;
				case Message.ID_REQUEST:
					// TODO process request
					break;
				case Message.ID_PIECE:
					final PieceMessage pieceMsg = (PieceMessage) msg;

					// Updated downloaded
					this.downloaded = this.downloaded
							+ pieceMsg.getBlock().length;

					// Verify piece
					if (this.verifyPiece(pieceMsg.getPieceIndex(),
							pieceMsg.getBlock())) {
						// Write piece
						RUBTClient.LOGGER.info("Writing piece [pieceIndex="
								+ pieceMsg.getPieceIndex() + "] to file");

						this.outFile.seek(pieceMsg.getPieceIndex()
								* this.pieceLength);
						this.outFile.write(pieceMsg.getBlock());
						this.setBitfieldBit(pieceMsg.getPieceIndex());

						// Recalculate amount left to download
						this.left = this.left - pieceMsg.getBlock().length;
						RUBTClient.LOGGER.info("Amount left = " + this.left);

						// Notify peers that the piece is complete
						this.notifyPeers(pieceMsg.getPieceIndex());
					} else {
						// Drop piece
						RUBTClient.LOGGER.warning("Dropping piece [pieceIndex="
								+ pieceMsg.getPieceIndex() + "]");
						this.resetBitfieldBit(pieceMsg.getPieceIndex());
					}
					RUBTClient.LOGGER.info("Updated my bitfield: "
							+ this.getBitfieldString());

					if (!peer.amChoked() && peer.amInterested()) {
						this.chooseAndRequestPiece(peer);
					} else {
						peer.sendMessage(Message.KEEP_ALIVE);
					}
					break;
				default:
					RUBTClient.LOGGER
							.warning("Could not process message of unknown type: "
									+ msg.getId());
					break;
				}
			} catch (final InterruptedException ie) {
				// This can happen either "randomly" or due to a shutdown - just
				// continue the loop.
				continue;
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final NullPointerException npe) {
				// TODO Auto-generated catch block
				npe.printStackTrace();
			}
		}
	}

	/**
	 * Checks which new peers are not already connected (by peer ID) and tries
	 * to connect to those.
	 * 
	 * @param newPeers
	 *            the list of potentially new peers from the tracker
	 */
	void addPeers(final List<Peer> newPeers) {

		if (newPeers == null) {
			RUBTClient.LOGGER.log(Level.WARNING, "No new peers to start.");
		} else {
			for (final Peer newPeer : newPeers) {
				// Filter peers by IP address
				if ((newPeer != null)
						&& (newPeer.getIp().equals("128.6.171.130") || newPeer
								.getIp().equals("128.6.171.131"))
						&& !this.peers.contains(newPeer)) {
					this.peers.add(newPeer);
					RUBTClient.LOGGER
							.info("Connecting to new peer: " + newPeer);
					newPeer.setClient(this);
					newPeer.setTasks(this.tasks);
					newPeer.start();
				}
			}
		}
	}

	/**
	 * Determines which piece to request from the remote peer, and tells the
	 * peer to "download" it.
	 * 
	 * @param peer
	 * @throws IOException
	 */
	private void chooseAndRequestPiece(final Peer peer) throws IOException {

		// Inspect bitfields and choose piece
		final byte[] peerBitfield = peer.getBitfield();
		// Check pieces from rangeMin=0 to rangeMax=totalPieces
		// rangeMin = pieceIndex
		// rangeMax = totalPieces
		int pieceIndex;
		for (pieceIndex = 0; pieceIndex < this.totalPieces ; pieceIndex++) {
			if (!Utility.isSetBit(this.bitfield, pieceIndex)
					&& Utility.isSetBit(peerBitfield, pieceIndex)) {
				this.setBitfieldBit(pieceIndex);

				int requestedPieceLength = 0;
				// Check if requesting last piece
				if (pieceIndex == (this.totalPieces - 1)) {
					// Last piece is irregularly-sized
					requestedPieceLength = this.fileLength % this.pieceLength;
				} else {
					requestedPieceLength = this.pieceLength;
				}

				peer.requestPiece(pieceIndex, requestedPieceLength);				
				
				break;
			}
		}
	}

	/**
	 * Gracefully shuts down the client; make sure all data is written to disk
	 * and all threads are done.
	 */
	void shutdown() {
		RUBTClient.LOGGER.info("Shutting down client.");
		this.keepRunning = false;

		// Cancel any upcoming tracker announces
		this.trackerTimer.cancel();
		// Disconnect all peers
		if (!this.peers.isEmpty()) {
			for (final Peer peer : this.peers) {
				peer.disconnect();
			}
		}

		try {
			this.tracker.announce(this.getDownloaded(), this.getUploaded(),
					this.getLeft(), "stopped");
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final BencodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return;
	}

	/**
	 * Generates the randomized peer ID with the first four bytes hard-coded
	 * with our group ID
	 * 
	 * @author Julian Modesto
	 * @return the generated ID
	 */
	private static byte[] generateMyPeerId() {
		final byte[] peerId = new byte[20];

		// Hard code the first four bytes for easy identification
		System.arraycopy(RUBTClient.BYTES_GROUP, 0, peerId, 0,
				RUBTClient.BYTES_GROUP.length);

		// Randomly generate remaining 16 bytes
		final byte[] random = new byte[16];
		new Random().nextBytes(random);

		System.arraycopy(random, 0, peerId, 4, random.length);

		return peerId;
	}

	/**
	 * Determines whether the client is interested in downloading from the
	 * remote peer.
	 * 
	 * @param peerBitfield
	 * @return
	 */
	private boolean amInterested(Peer peer) {
		if (this.left == 0) {
			RUBTClient.LOGGER.info("Nothing left, not interested in pieces from " + peer);
			return false;
		}

		// Inspect bitfield
		for (int pieceIndex = 0; pieceIndex < this.totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitfield, pieceIndex)
					&& Utility.isSetBit(peer.getBitfield(), pieceIndex)) {
				RUBTClient.LOGGER.info("Still interested in pieces from " + peer);
				return true;
			}
		}
		RUBTClient.LOGGER.info("Not interested in pieces from " + peer);
		return false;
	}

	/**
	 * Verify a piece by checking that its corresponding SHA-1 hash of the data
	 * matches that in the torrent metadata file.
	 * 
	 * @author Julian Modesto
	 * @param pieceIndex
	 *            the zero-based index of the piece
	 * @param block
	 *            the block of data
	 * @return true if the data is verifiably part of the file
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * 
	 */
	private boolean verifyPiece(final int pieceIndex, final byte[] block)
			throws IOException {

		final byte[] piece = new byte[block.length];
		System.arraycopy(block, 0, piece, 0, block.length);

		byte[] hash = null;

		MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-1");
			hash = sha.digest(piece);
		} catch (NoSuchAlgorithmException nsae) {
			// Won't happen!
		}
		
		if (Arrays.equals(this.tInfo.piece_hashes[pieceIndex].array(), hash)) {
			RUBTClient.LOGGER.info("Piece [pieceIndex=" + pieceIndex + "] verified.");
			return true;
		}
		RUBTClient.LOGGER.warning("Piece [pieceIndex=" + pieceIndex + "] doesn't match.");
		return false;
	}

	private void notifyPeers(final int pieceIndex) {
		for (Peer p : this.peers) {
			try {
				p.sendMessage(new Message.HaveMessage(pieceIndex));
			} catch (IOException e) {
				RUBTClient.LOGGER
						.warning("I/O exception encountered when notifying peer "
								+ p
								+ "about piece [pieceIndex="
								+ pieceIndex
								+ "]");
			}
		}
	}
	
	/**
	 * Updates the bitfield according to the existing output file.
	 * @throws IOException 
	 */
	private void setBitfield() throws IOException {
		final int bytes = (int) Math.ceil(this.totalPieces / 8.0);
		this.bitfield = new byte[bytes];
		
		for (int pieceIndex = 0; pieceIndex < this.totalPieces; pieceIndex++) {
			byte[] temp;
			if (pieceIndex == this.totalPieces - 1) {
				// Last piece
				temp = new byte[this.fileLength % this.pieceLength];
			} else {
				temp = new byte[this.pieceLength];
			}
			this.outFile.read(temp);
			if (this.verifyPiece(pieceIndex, temp)) {
				this.setBitfieldBit(pieceIndex);
				this.left = this.left - temp.length;
			} else {
				this.resetBitfieldBit(pieceIndex);
			}
		}
	}

	/**
	 * Sets a specific bit in the bitfield to 1.
	 * 
	 * @param bit
	 *            the bit to set
	 */
	private void setBitfieldBit(final int bit) {
		byte[] tempBitfield = this.getBitfield();
		tempBitfield = Utility.setBit(tempBitfield, bit);
		this.setBitfield(tempBitfield);
	}

	/**
	 * Resets a specific bit in the bitfield to 0.
	 * 
	 * @param bit
	 *            the bit to reset
	 */
	private void resetBitfieldBit(final int bit) {
		byte[] tempBitfield = this.getBitfield();
		tempBitfield = Utility.resetBit(tempBitfield, bit);
		this.setBitfield(tempBitfield);
	}

	/**
	 * Sets the byte array as the updated client bitfield.
	 * 
	 * @param bitfield
	 *            the bitfield to set
	 */
	private void setBitfield(final byte[] bitfield) {
		this.bitfield = bitfield;
	}

	/**
	 * Returns the current local bitfield.
	 * 
	 * @return the bitfield
	 */
	byte[] getBitfield() {
		return this.bitfield;
	}

	/**
	 * Returns the bitfield as 0s and 1s.
	 * 
	 * @return the string representation of the bitfield
	 */
	private String getBitfieldString() {
		final StringBuilder builder = new StringBuilder();
		if (this.bitfield != null) {
			builder.append("bitfield=");
			for (final byte b : this.bitfield) {
				// Add 0x100 then skip char(0) to left-pad bits with zeros
				builder.append(Integer.toBinaryString(0x100 + b).substring(1));
			}
		}
		return builder.toString();
	}
}
