package edu.rutgers.cs.cs352.bt;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
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
import edu.rutgers.cs.cs352.bt.Message.RequestMessage;
import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;
import edu.rutgers.cs.cs352.bt.util.Utility;

/**
 * Main class for RUBTClient. After starting, spends its time listening on the
 * incoming message queue
 * in order to decide what to do.
 * 
 * @author Robert Moore
 */

public class RUBTClient extends Thread {

	private final static Logger LOGGER = Logger.getLogger(RUBTClient.class.getName());

	public static void main(String[] args) {

		// Check number/type of arguments
		if (args.length != 2) {
			LOGGER.log(Level.SEVERE,"Two arguments required");
			System.exit(1);
		}

		byte[] metaBytes = null;
		try {
			File metaFile = new File(args[0]);
			DataInputStream metaIn = new DataInputStream(
					new FileInputStream(metaFile));
			metaBytes = new byte[(int) metaFile.length()];
			metaIn.readFully(metaBytes);
			metaIn.close();
		} catch (FileNotFoundException fnfe) {
			LOGGER.log(Level.SEVERE, "File not found exception encountered for file with filename \"" + args[0] + "\"", fnfe);
			System.exit(1);
		} catch (IOException ioe) {
			LOGGER.log(Level.SEVERE, "I/O exception encountered for file with filename \"" + args[0] + "\"", ioe);
			System.exit(1);
		}

		// Null check on metaBytes
		if (metaBytes == null) {
			LOGGER.log(Level.SEVERE, "Corrupt torrent metainfo file.");
			System.exit(1);
		}

		TorrentInfo tInfo = null;
		try {
			tInfo = new TorrentInfo(metaBytes);
		} catch (BencodingException be) {
			LOGGER.log(Level.WARNING, "Bencoding exception encountered", be);
		}

		RUBTClient client;
		try {
			client = new RUBTClient(tInfo, args[1]);

			// Launches the client as a thread
			client.start();
			client.join();
		} catch (InterruptedException e) {
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
	private final byte[] peerId = generateMyPeerId();

	/**
	 * Hard code the first 4 bytes of our client's peer ID.
	 */
	private static final byte[] BYTES_GROUP = { 'G', 'P', '1', '6' };

	private int port = 6881;


	private byte[] bitField;

	private int downloaded;
	private int uploaded;
	private int left;
	/**
	 * @return the downloaded
	 */
	public int getDownloaded() {
		return downloaded;
	}

	/**
	 * @param downloaded the downloaded to set
	 */
	public void setDownloaded(int downloaded) {
		this.downloaded = downloaded;
	}

	/**
	 * @return the uploaded
	 */
	public int getUploaded() {
		return uploaded;
	}

	/**
	 * @param uploaded the uploaded to set
	 */
	public void setUploaded(int uploaded) {
		this.uploaded = uploaded;
	}

	/**
	 * @return the left
	 */
	public int getLeft() {
		return left;
	}

	/**
	 * @param left the left to set
	 */
	public synchronized void setLeft(int left) {
		this.left = left;
	}

	/**
	 * List of peers currently connected to the client.
	 */
	private final List<Peer> peers = Collections.synchronizedList(new LinkedList<Peer>());

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
				peers = this.client.tracker.announce(client.getDownloaded(), client.getUploaded(), client.getLeft(), "");

				if (peers != null && !peers.isEmpty()) {
					this.client.addPeers(peers);
				}

				this.client.trackerTimer.schedule(this,
						this.client.tracker.getInterval() * 1000);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BencodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalStateException ise) {

			}			
		}
	}

	public RUBTClient(final TorrentInfo tInfo, final String outFile){
		this.tInfo = tInfo;
		this.outFileName = outFile;
		this.tracker = new Tracker(this.peerId, this.tInfo.info_hash.array(),
				this.tInfo.announce_url.toString(), this.port);

		this.downloaded = 0;
		this.uploaded = 0;
		this.left = this.tInfo.file_length;

		this.totalPieces = this.tInfo.piece_hashes.length;
		this.fileLength = this.tInfo.file_length;
		this.pieceLength = this.tInfo.piece_length;

		LOGGER.log(Level.INFO,"Total pieces: " + this.totalPieces);
		LOGGER.log(Level.INFO,"File length: " + this.fileLength);
		LOGGER.log(Level.INFO,"Piece length: " + this.pieceLength);
	}

	@Override
	public void run() {

		try {
			this.outFile = new RandomAccessFile(this.outFileName, "rw");
		} catch (FileNotFoundException fnfe) {
			LOGGER.log(Level.SEVERE,"Unable to open output file for writing!",fnfe);
			// Exit right now, since nothing else was started yet
			return;
		}

		//TODO update from output file
		int bytes = (int) Math.ceil((double)this.totalPieces/8);
		
		// Set client bit field
		this.bitField = new byte[bytes];

		// Send "started" announce and attempt to connect through ports 6881 - 6889
		List<Peer> peers = null;
		int announcePortIncrement;
		boolean trackerFailure = true;
		for(announcePortIncrement = 0; announcePortIncrement < 9 && trackerFailure == true; announcePortIncrement++) {
			if(announcePortIncrement != 0){
				LOGGER.log(Level.WARNING,"Retrying on a new port");
			}
			try {
				peers = this.tracker.announce(this.getDownloaded(), this.getUploaded(), this.getLeft(), "started");
				trackerFailure = false;
				LOGGER.log(Level.INFO,"Connected to tracker on port " + this.tracker.getPort());
			} catch (IOException ioe) {
				this.tracker.setPort(this.tracker.getPort() + 1);
				LOGGER.log(Level.WARNING, "I/O exception encountered and communication with tracker failed", ioe);
				trackerFailure = true;
			} catch (BencodingException be) {
				this.tracker.setPort(this.tracker.getPort() + 1);
				LOGGER.log(Level.WARNING,"Tracker response invalid.",be);
				trackerFailure = true;
			}
		}

		this.addPeers(peers);
		{
			// Schedule the first "regular" announce - the rest are schedule by the
			// task itself
			int interval = this.tracker.getInterval();
			this.trackerTimer.schedule(new TrackerAnnounceTask(this), interval * 1000);
		}

		// Start new thread to listen for "quit" from user
		Thread userInput = new Thread()
		{	@Override
			public void run() {
			final BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			String line = null;
			try {
				line = br.readLine();
				while (!line.equals("quit")) {
				}
				shutdown();
			} catch (IOException ioe) {
				// TODO Auto-generated catch block
				ioe.printStackTrace();
			}
		}
		};
		userInput.start();

		// Main loop:
		while (this.keepRunning) {
			try {
				MessageTask task = this.tasks.take();
				// Process the task
				Message msg = task.getMessage();
				Peer peer = task.getPeer();

				LOGGER.log(Level.INFO,"Processing message: " + msg);

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
					BitFieldMessage bitFieldMsg = (BitFieldMessage) msg;
					peer.setBitField(bitFieldMsg.getBitField());					

					// Inspect bit field
					peer.setLocalInterested(amInterested(peer.getBitField()));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					} else if (peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
					}
					break;
				case Message.ID_HAVE:
					HaveMessage haveMsg = (HaveMessage) msg;

					if (peer.getBitField() == null) {
						peer.initializeBitField(this.totalPieces);
					}
					peer.setBitField(haveMsg.getPieceIndex());

					peer.setLocalInterested(amInterested(peer.getBitField()));
					if (!peer.amChoked() && peer.amInterested()) {
						peer.sendMessage(Message.INTERESTED);
						peer.setLocalInterested(true);
					}
					break;
				case Message.ID_REQUEST:
					RequestMessage requestMsg = (RequestMessage) msg;

					//TODO process request
					break;
				case Message.ID_PIECE:
					PieceMessage pieceMsg = (PieceMessage) msg;

					// Updated downloaded
					this.downloaded = this.downloaded + pieceMsg.getBlock().length;

					// Verify piece
					if (verifyPiece(pieceMsg.getPieceIndex(), pieceMsg.getBlock())) {
						LOGGER.log(Level.INFO,"Writing to file: last piece.");
						// Write piece
						outFile.seek(pieceMsg.getPieceIndex() * this.pieceLength);
						outFile.write(pieceMsg.getBlock());
					} else {
						// Do nothing and drop piece
					}

					if (!peer.amChoked() && peer.amInterested()) {
						this.chooseAndRequestPiece(peer);
					}
					break;
				default:
					LOGGER.log(Level.WARNING,"Could not process message of unknown type: " + msg.getId());
					break;
				}					
			} catch (InterruptedException ie) {
				// This can happen either "randomly" or due to a shutdown - just
				// continue the loop.
				continue;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NullPointerException npe) {
				// TODO Auto-generated catch block
				npe.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Checks which new peers are not already connected (by peer ID) and tries to connect to those.
	 * @param newPeers the list of potentially new peers from the tracker
	 */
	void addPeers(final List<Peer> newPeers) {
		// Filter by IP address

		if (newPeers == null) {
			LOGGER.log(Level.WARNING, "No new peers to start.");
		} else {
			for (Peer newPeer : newPeers) {
				if (newPeer != null && (newPeer.getIp().equals("128.6.171.130") || newPeer.getIp().equals("128.6.171.131")) && !this.peers.contains(newPeer)) {
					this.peers.add(newPeer);
					LOGGER.log(Level.INFO,"Connecting to new peer: " + newPeer);
					newPeer.setTasks(tasks);
					newPeer.start();
				}
			}
		}
	}

	/**
	 * Determines which piece to request from the remote peer, and tells the peer to "download" it.
	 * @param peer
	 * @throws IOException
	 */
	private void chooseAndRequestPiece(final Peer peer) throws IOException {

		// Inspect bit fields and choose piece
		byte[] peerBitField = peer.getBitField();
		// Check pieces from rangeMin=0 to rangeMax=totalPieces
		// rangeMin = pieceIndex
		// rangeMax = totalPieces
		int pieceIndex = 0;
		for ( ; pieceIndex < this.totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex) && Utility.isSetBit(peerBitField, pieceIndex)) {
				break;
			}
		}

		int pieceLength = 0;
		// Check if requesting last piece
		if (pieceIndex == totalPieces - 1) {
			// Last piece is irregularly-sized
			pieceLength = this.fileLength % this.pieceLength;
		} else {
			pieceLength = this.pieceLength;
		}

		peer.requestPiece(pieceIndex, pieceLength);
	}

	/**
	 * Gracefully shuts down the client;
	 */
	void shutdown() {
		LOGGER.log(Level.INFO,"Shutting down client.");
		this.keepRunning = false;

		// Cancel any upcoming tracker announces
		this.trackerTimer.cancel();
		// Disconnect all peers
		if (!this.peers.isEmpty()) {
			for(Peer peer : this.peers){
				peer.disconnect();
			}
		}


		try {
			this.tracker.announce(this.getDownloaded(), this.getUploaded(), this.getLeft(), "stopped");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BencodingException e) {
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
		byte[] peerId = new byte[20];

		// Hard code the first four bytes for easy identification
		System.arraycopy(BYTES_GROUP, 0, peerId, 0, BYTES_GROUP.length);

		// Randomly generate remaining 16 bytes
		byte[] random = new byte[16];
		new Random().nextBytes(random);

		System.arraycopy(random, 0, peerId, 4, random.length);

		return peerId;
	}

	/**
	 * Determines whether the client is interested in downloading from the remote peer.
	 * @param peerBitField
	 * @return
	 */
	private boolean amInterested(byte[] peerBitField) {
		if (this.left == 0) {
			LOGGER.log(Level.INFO,"Nothing left!");
			return false;
		}

		// Inspect bit field
		for (int pieceIndex = 0; pieceIndex < totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex) && Utility.isSetBit(peerBitField, pieceIndex)) {
				return true;
			}
		}
		LOGGER.log(Level.INFO,"Not interested!");
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
	public boolean verifyPiece(int pieceIndex, byte[] block)
			throws IOException, NoSuchAlgorithmException {

		byte[] piece = new byte[this.pieceLength];
		System.arraycopy(block, 0, piece, 0, block.length);

		byte[] hash = null;

		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		hash = sha.digest(piece);

		if (Arrays.equals(this.tInfo.piece_hashes[pieceIndex].array(),
				hash)) {
			LOGGER.log(Level.INFO,"Piece verified.");
			return true;
		}
		LOGGER.log(Level.WARNING,"Piece does not match.");
		return false;
	}

}
