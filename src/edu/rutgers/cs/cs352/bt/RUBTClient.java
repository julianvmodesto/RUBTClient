package edu.rutgers.cs.cs352.bt;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
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

	public static void main(String[] args) {
		// Check number/type of arguments
		if (args.length != 2) {
			System.err.println("Error: two arguments required");
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
			System.err.println("Error: file not found exception encountered for file with filename " + args[0]);
			System.exit(1);
		} catch (IOException ie) {
			System.err.println("Error: I/O exception encountered for file with filename " + args[0]);
			System.exit(1);
		}

		// Null check on metaBytes
		if (metaBytes == null) {
			System.err.println("Error: corrupt torrent metainfo file");
			System.exit(1);
		}

		TorrentInfo tInfo = null;
		try {
			tInfo = new TorrentInfo(metaBytes);
		} catch (BencodingException be) {
			System.err.println("Error: bencoding exception encountered");
			System.err.println(be.getMessage());
		}

		RUBTClient client;
		try {
			client = new RUBTClient(tInfo, args[1]);

			// Launches the client as a thread
			client.start();
		} catch (IOException ioe) {
			System.err.println("Error: I/O exception encountered");
		}
	}

	/**
	 * The block size that will be requested, 16K.
	 */
	public static final int BLOCK_LENGTH = 2 ^ 14; // = 16Kb
	// We should be requesting 16K blocks, while pieces are 32 blocks

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

	/**
	 * An array of ByteBuffers to hold pieces assembled from blocks.
	 */
	private ByteBuffer[] pieces;

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
	private final Timer trackerTimer = new Timer();

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

		public void run() {
			List<Peer> peers = null;
			try {
				peers = this.client.tracker.announce(client.getDownloaded(), client.getUploaded(), client.getLeft(), null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BencodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (peers != null && !peers.isEmpty()) {
				this.client.addPeers(peers);
			}
			this.client.trackerTimer.schedule(this,
					this.client.tracker.getInterval() * 1000);
		}
	}

	public RUBTClient(final TorrentInfo tInfo, final String outFile) throws IOException {
		this.tInfo = tInfo;
		this.outFileName = outFile;
		this.tracker = new Tracker(this.peerId, this.tInfo.info_hash.array(),
				this.tInfo.announce_url.toString(), this.port, this);

		this.downloaded = 0;
		this.uploaded = 0;
		this.left = this.tInfo.file_length;

		this.totalPieces = this.tInfo.piece_hashes.length;
		this.fileLength = this.tInfo.file_length;
		this.pieceLength = this.tInfo.piece_length;
	}

	@Override
	public void run() {

		try {
			this.outFile = new RandomAccessFile(this.outFileName, "rw");
		} catch (FileNotFoundException e) {
			System.err.println("Unable to open output file for writing!");
			e.printStackTrace();
			// Exit right now, since nothing else was started yet
			return;
		}

		//TODO update from output file
		// Set client bit field
		int bytes = (int) Math.ceil((double)totalPieces/8);
		byte[] tempBitField = new byte[bytes];

		for (int i = 0; i < tempBitField.length; i++) {
			tempBitField[i] = 0;
		}
		this.bitField = tempBitField;

		initializePieces();

		// Send "started" announce
		List<Peer> peers = null;
		int announcePortIncrement;
		boolean trackerFailure = true;
		for(announcePortIncrement = 0; announcePortIncrement < 9 && trackerFailure == true; announcePortIncrement++) {
			if(announcePortIncrement != 0){
				System.out.println("Retrying on a new port.");
			}
			try {
				peers = this.tracker.announce(this.getDownloaded(), this.getUploaded(), this.getLeft(), "started");
				trackerFailure = false;
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				this.tracker.setPort(this.tracker.getPort() + announcePortIncrement);
				System.err.println("Communication with tracker failed.");
				trackerFailure = true;
				e1.printStackTrace();
			} catch (BencodingException e1) {
				// TODO Auto-generated catch block
				this.tracker.setPort(this.tracker.getPort() + announcePortIncrement);
				System.err.println("Tracker response invalid.");
				trackerFailure = true;
				e1.printStackTrace();
			}
		}

		this.addPeers(peers);
		{
			// Schedule the first "regular" announce - the rest are schedule by the
			// task itself
			int interval = this.tracker.getInterval();
			this.trackerTimer.schedule(new TrackerAnnounceTask(this), interval * 1000);
		}

		Thread userInput = new Thread()
		{
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
				// TODO: Process the task
				Message msg = task.getMessage();
				Peer peer = task.getPeer();

				System.out.println("Processing message: " + Message.ID_NAMES[msg.getId()]);

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
					if (!peer.amInterested() && amInterested(peer.getBitField())) {
						peer.sendMessage(Message.INTERESTED);
						peer.setLocalInterested(true);
					}
					break;
				case Message.ID_HAVE:
					HaveMessage haveMsg = (HaveMessage) msg;

					if (peer.getBitField() == null) {
						peer.initializeBitField(this.totalPieces);
					}
					peer.setBitField(haveMsg.getPieceIndex());

					if (!peer.amInterested() && amInterested(peer.getBitField())) {
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
					break;
				default:
					//TODO weird message id
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
			}
		}

		this.shutdown();
	}

	/**
	 * Checks which new peers are not already connected (by peer ID) and tries to connect to those.
	 * @param newPeers the list of potentially new peers from the tracker
	 */
	void addPeers(final List<Peer> newPeers) {
		// Filter by IP address
		for (Peer newPeer : newPeers) {
			if ((newPeer.getIp().equals("128.6.171.130") || newPeer.getIp().equals("128.6.171.131")) && !this.peers.contains(newPeer)) {
				this.peers.add(newPeer);
				System.out.println("Connecting to new peer: " + newPeer);
				newPeer.start();
			}
		}
	}

	/**
	 * Puts a new task into the tasks queue for processing.
	 * @param task the MessageTask to queue
	 */
	public void putMessageTask(MessageTask task) {
		try {
			this.tasks.put(task);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Determines which piece to request from the remote peer, and tells the peer to "download" it.
	 * @param peer
	 * @throws IOException
	 */
	private void chooseAndRequestPiece(final Peer peer) throws IOException {
		int pieceIndex;
		int blockOffset;
		int blockLength;

		// Inspect bit field and choose piece
		byte[] peerBitField = peer.getBitField();
		for (pieceIndex = 0; pieceIndex < totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex) && Utility.isSetBit(peerBitField, pieceIndex)) {
				break;
			}
		}

		blockOffset = pieceIndex * this.pieceLength;

		// Check if requesting last piece
		if (pieceIndex == totalPieces - 1) {
			// Request the last irregularly-sized piece
			blockLength = fileLength % this.pieceLength;
		} else {
			blockLength = this.pieceLength;
		}

		// Send request message
		RequestMessage msg =  new RequestMessage(pieceIndex, blockOffset, blockLength);
		peer.sendMessage(msg);
	}	

	/**
	 * Gracefully shuts down the client;
	 */
	private void shutdown() {
		System.out.println("Shutting down client.");
		this.keepRunning = false;

		// Cancel any upcoming tracker announces
		this.trackerTimer.cancel();
		// Disconnect all peers
		for(Peer peer : this.peers){
			peer.disconnect();
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
		// Inspect bit field
		for (int pieceIndex = 0; pieceIndex < totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex) && Utility.isSetBit(peerBitField, pieceIndex)) {
				return true;
			}
		}
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
		byte[] hash = null;

		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		hash = sha.digest(block);

		if (Arrays.equals(this.tInfo.piece_hashes[pieceIndex].array(),
				hash)) {
			System.out.println("Piece verified.");
			return true;
		}
		System.out.println("Piece does not match.");
		return false;
	}

	private void initializePieces() {
		this.pieces = new ByteBuffer[totalPieces];
		for (int pieceIndex = 0; pieceIndex < totalPieces; pieceIndex++) {
			if (!Utility.isSetBit(this.bitField, pieceIndex)) {
				this.pieces[pieceIndex] = ByteBuffer.allocate(pieceLength); 
			}
		}
	}
}
