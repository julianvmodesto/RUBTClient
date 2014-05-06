package edu.rutgers.cs.cs352.bt;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
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

import edu.rutgers.cs.cs352.bt.Message.BitFieldMessage;
import edu.rutgers.cs.cs352.bt.Message.HaveMessage;
import edu.rutgers.cs.cs352.bt.Message.PieceMessage;
import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * Main class for RUBTClient. After starting, spends its time listening on the
 * incoming message queue in order to decide what to do.
 * 
 * @author Robert Moore
 */

public class RUBTClient extends Thread {

	private final static Logger LOGGER = Logger.getLogger(RUBTClient.class
			.getName());

	public static void main(final String[] args) {

		// Check number/type of arguments
		if (args.length != 2) {
			RUBTClient.LOGGER.log(Level.SEVERE, "Two arguments required");
			System.exit(1);
		}

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

	private final TorrentInfo tInfo;
	private final int totalPieces;
	private final int fileLength;
	private final int pieceLength;
	private final String outFileName;
	private RandomAccessFile outFile;
	private final LinkedBlockingQueue<MessageTask> tasks = new LinkedBlockingQueue<MessageTask>();

	// Generate a random peer ID value
	private final byte[] peerId = RUBTClient.generateMyPeerId();

	/**
	 * Hard code the first 4 bytes of our client's peer ID.
	 */
	private static final byte[] BYTES_GROUP = { 'G', 'P', '1', '6' };

	private final int port = 6881;

	private byte[] bitField;

	private int downloaded;
	private int uploaded;
	private int left;

	/**
	 * @return the downloaded
	 */
	public int getDownloaded() {
		return this.downloaded;
	}

	/**
	 * @param downloaded
	 *            the downloaded to set
	 */
	public void setDownloaded(final int downloaded) {
		this.downloaded = downloaded;
	}

	/**
	 * @return the uploaded
	 */
	public int getUploaded() {
		return this.uploaded;
	}

	/**
	 * @param uploaded
	 *            the uploaded to set
	 */
	public void setUploaded(final int uploaded) {
		this.uploaded = uploaded;
	}

	/**
	 * @return the left
	 */
	public int getLeft() {
		return this.left;
	}

	/**
	 * @param left
	 *            the left to set
	 */
	public synchronized void setLeft(final int left) {
		this.left = left;
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

	private static class TrackerAnnounceTask extends TimerTask {
		private final RUBTClient client;

		public TrackerAnnounceTask(final RUBTClient client) {
			this.client = client;
		}

		@Override
		public void run() {
			List<Peer> peers = null;
			try {
				peers = this.client.tracker.announce(
						this.client.getDownloaded(), this.client.getUploaded(),
						this.client.getLeft(), "");

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

		RUBTClient.LOGGER.log(Level.INFO, "Total pieces: " + this.totalPieces);
		RUBTClient.LOGGER.log(Level.INFO, "File length: " + this.fileLength);
		RUBTClient.LOGGER.log(Level.INFO, "Piece length: " + this.pieceLength);
	}

	@Override
	public void run() {

		try {
			this.outFile = new RandomAccessFile(this.outFileName, "rw");
		} catch (final FileNotFoundException fnfe) {
			RUBTClient.LOGGER.log(Level.SEVERE,
					"Unable to open output file for writing!", fnfe);
			// Exit right now, since nothing else was started yet
			return;
		}

		// TODO update from output file
		final int bytes = (int) Math.ceil((double) this.totalPieces / 8);

		// Set client bit field
		this.bitField = new byte[bytes];

		// Send "started" announce and attempt to connect through ports 6881 -
		// 6889
		List<Peer> peers = null;
		int announcePortIncrement;
		boolean trackerFailure = true;
		for (announcePortIncrement = 0; (announcePortIncrement < 9)
				&& (trackerFailure == true); announcePortIncrement++) {
			if (announcePortIncrement != 0) {
				RUBTClient.LOGGER.log(Level.WARNING, "Retrying on a new port");
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

		// Start new thread to listen for "quit" from user
		final Thread userInput = new Thread() {
			@Override
			public void run() {
				final BufferedReader br = new BufferedReader(
						new InputStreamReader(System.in));
				String line = null;
				try {
					line = br.readLine();
					while (!line.equals("quit")) {
					}
					RUBTClient.this.shutdown();
				} catch (final IOException ioe) {
					// TODO Auto-generated catch block
					ioe.printStackTrace();
				}
			}
		};
		userInput.start();

		// Main loop:
		while (this.keepRunning) {
			try {
				final MessageTask task = this.tasks.take();
				// Process the task
				final Message msg = task.getMessage();
				final Peer peer = task.getPeer();

				RUBTClient.LOGGER.log(Level.INFO, peer + " sent " + msg);

				switch (msg.getId()) {
				case Message.ID_KEEP_ALIVE:
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
					break;
				case Message.ID_BIT_FIELD:
					// Set peer bit field
					final BitFieldMessage bitFieldMsg = (BitFieldMessage) msg;
					peer.setBitField(bitFieldMsg.getBitField());

					// Inspect bit field
					peer.setLocalInterested(this.amInterested(peer
							.getBitField()));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					} else if (peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					}
					break;
				case Message.ID_HAVE:
					final HaveMessage haveMsg = (HaveMessage) msg;

					if (peer.getBitField() == null) {
						peer.initializeBitField(this.totalPieces);
					}
					peer.setBitFieldBit(haveMsg.getPieceIndex());

					peer.setLocalInterested(this.amInterested(peer
							.getBitField()));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
						peer.setLocalInterested(true);
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
						this.setBitFieldBit(pieceMsg.getPieceIndex());
					} else {
						// Drop piece
						RUBTClient.LOGGER.warning("Dropping piece [pieceIndex="
								+ pieceMsg.getPieceIndex() + "]");
						this.resetBitFieldBit(pieceMsg.getPieceIndex());
					}
					RUBTClient.LOGGER.info("Updated my bit field: "
							+ this.getBitFieldString());

					if (!peer.amChoked() && peer.amInterested()) {
						this.chooseAndRequestPiece(peer);
					}
					break;
				default:
					RUBTClient.LOGGER.log(
							Level.WARNING,
							"Could not process message of unknown type: "
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
			} catch (final NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
		// Filter by IP address

		if (newPeers == null) {
			RUBTClient.LOGGER.log(Level.WARNING, "No new peers to start.");
		} else {
			for (final Peer newPeer : newPeers) {
				if ((newPeer != null)
						&& (newPeer.getIp().equals("128.6.171.130") || newPeer
								.getIp().equals("128.6.171.131"))
						&& !this.peers.contains(newPeer)) {
					this.peers.add(newPeer);
					RUBTClient.LOGGER.log(Level.INFO,
							"Connecting to new peer: " + newPeer);
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

		// Inspect bit fields and choose piece
		final byte[] peerBitField = peer.getBitField();
		// Check pieces from rangeMin=0 to rangeMax=totalPieces
		// rangeMin = pieceIndex
		// rangeMax = totalPieces
		int pieceIndex;
		for (pieceIndex = 0; pieceIndex < (this.totalPieces - 1); pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex)
					&& Utility.isSetBit(peerBitField, pieceIndex)) {
				break;
			}
		}
		this.setBitFieldBit(pieceIndex);

		int requestedPieceLength = 0;
		// Check if requesting last piece
		if (pieceIndex == (this.totalPieces - 2)) {
			// Last piece is irregularly-sized
			requestedPieceLength = this.fileLength % this.pieceLength;
		} else {
			requestedPieceLength = this.pieceLength;
		}

		peer.requestPiece(pieceIndex, requestedPieceLength);
	}

	/**
	 * Gracefully shuts down the client;
	 */
	void shutdown() {
		RUBTClient.LOGGER.log(Level.INFO, "Shutting down client.");
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
		// TODO: make sure all data is written to disk, all threads done

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
	 * @param peerBitField
	 * @return
	 */
	private boolean amInterested(final byte[] peerBitField) {
		if (this.left == 0) {
			RUBTClient.LOGGER.log(Level.INFO, "Nothing left!");
			return false;
		}

		// Inspect bit field
		for (int pieceIndex = 0; pieceIndex < this.totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex)
					&& Utility.isSetBit(peerBitField, pieceIndex)) {
				return true;
			}
		}
		RUBTClient.LOGGER.log(Level.INFO, "Not interested!");
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
	public boolean verifyPiece(final int pieceIndex, final byte[] block)
			throws IOException, NoSuchAlgorithmException {

		final byte[] piece = new byte[this.pieceLength];
		System.arraycopy(block, 0, piece, 0, block.length);

		byte[] hash = null;

		final MessageDigest sha = MessageDigest.getInstance("SHA-1");
		hash = sha.digest(piece);

		if (Arrays.equals(this.tInfo.piece_hashes[pieceIndex].array(), hash)) {
			RUBTClient.LOGGER.log(Level.INFO, "Piece verified.");
			return true;
		}
		RUBTClient.LOGGER.log(Level.WARNING, "Piece does not match.");
		return false;
	}

	/**
	 * @param bit
	 *            the bit to set
	 */
	private void setBitFieldBit(final int bit) {
		byte[] tempBitField = this.getBitField();
		tempBitField = Utility.setBit(tempBitField, bit);
		this.setBitField(tempBitField);
	}

	/**
	 * @param bit
	 *            the bit to reset
	 */
	private void resetBitFieldBit(final int bit) {
		byte[] tempBitField = this.getBitField();
		tempBitField = Utility.resetBit(tempBitField, bit);
		this.setBitField(tempBitField);
	}

	/**
	 * 
	 * @param bitField
	 *            the bitField to set
	 */
	private void setBitField(final byte[] bitField) {
		this.bitField = bitField;
	}

	/**
	 * @return the bitField
	 */
	private byte[] getBitField() {
		return this.bitField;
	}

	private String getBitFieldString() {
		final StringBuilder builder = new StringBuilder();
		if (this.bitField != null) {
			builder.append("bitField=");
			for (final byte b : this.bitField) {
				// Add 0x100 then skip char(0) to left-pad bits with zeros
				builder.append(Integer.toBinaryString(0x100 + b).substring(1));
			}
		}
		return builder.toString();
	}
}
