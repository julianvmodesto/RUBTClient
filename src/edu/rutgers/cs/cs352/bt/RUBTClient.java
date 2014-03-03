/*
 * Authors: Gaurav Kumar, Julian Modesto, Jeffrey Rocha
 */

package edu.rutgers.cs.cs352.bt;

/* @author Jeffrey Rocha */

import java.util.Random;
import java.io.*;

public class RUBTClient {
	// Hard code the first 4 bytes of the peer ID
	private static final byte[] GROUP = {'G','P','1','6'};
	
	public enum Event {
		STARTED, COMPLETED, STOPPED
	};
	public static TorrentInfo torrent_info; //TorrentInfo object used for the program, created in main
	public static int uploaded = 0;
	public static int downloaded = 0;
	public static int left = 0;
	public static int myPort = 6881;
	public static int interval = 60;
	
	public static String ip;
	public static int peerPort;
	public static byte[] peerId;
	
	public static byte[] myPeerId;
	
	public static void main(String[] args) throws Exception {
		
		// Check correct usage
		if (args.length != 2) {
			System.err.println("Error: two arguments required");
			System.exit(1);
		}
		
		// Generate peer ID
		myPeerId = generatePeerId();
		
		String torrent_file_name = args[0];
		// Creates file for torrent
		File torrent_file = new File(torrent_file_name);
		String download_file_name = args[1];
		// Creates file to save data to
		File download_file = new File(download_file_name);
		
		// Calls getFileInBytes to change the torrent file into a byte array
		byte[] torrent_bytes = getFileInBytes(torrent_file, torrent_file_name);
		
		// Initializes the torrentInfo
		torrent_info = new TorrentInfo(torrent_bytes);
		left = torrent_info.torrent_file_bytes.length;
		String event = "started";
		TrackerCommunicator.getRequest(myPort, event);
		
		if (peerPort > 6889){
			System.err.println("There are no further ports available for use. Sorry.");
			System.exit(1);
		}
	}
	
	/**
	 * Changes the torrent file into a byte array
	 * used to create the TorrentInfo object
	 * @author Robert Moore
	 * @param torrent_file Torrent file about to be converted, 
	 * @param file_name Name of torrent file
	 * @return torrent file in byte array
	 */
	public static byte[] getFileInBytes(File torrent_file, String file_name){
		
		byte[] torrent_bytes = new byte[(int)torrent_file.length()];
		InputStream file_stream;
		
		try
		{
			file_stream = new FileInputStream(torrent_file);
			
			if (!torrent_file.exists()){ //Check to see if the file exists
				System.err.println("The file \"" + file_name + "\" does not exist. Find the correct path to the file.");
				return null;
			}
			
			if (!torrent_file.canRead()){ //Check to see if the file is readable
				System.err.println("Cannot read from \"" + file_name + "\". File permissions must be set correctly.");
				return null;
			}
			
			int file_offset = 0;
			int bytes_read = 0;

			// Read from the file
			while (file_offset < torrent_bytes.length && (bytes_read = file_stream.read(torrent_bytes, file_offset, torrent_bytes.length - file_offset)) >= 0)
				file_offset += bytes_read;

			if (file_offset < torrent_bytes.length) { //In case the whole file could not be read
				throw new IOException("Could not completely read file \"" + torrent_file.getName() + "\".");
			}
			file_stream.close();	
		}
		catch (FileNotFoundException fnfe) //file was not found
		{
			System.err.println("The file \"" + file_name + "\" does not exist. Find the correct path to the file.");
			return null;
		}
		catch (IOException ioe) //I/O error
		{
			System.err.println("There was an I/O error when reading \"" + file_name + "\".");
			System.err.println(ioe.getMessage());
		}
		return torrent_bytes;
	}
	
	/**
	 * Generates the randomized peer ID with the first four bytes hard-coded with our group ID
	 * 
	 * @author Julian
	 * @return the generated ID
	 */
	private static byte[] generatePeerId() {
		byte[] peerId = new byte[20];
		
		// Hard code the first four bytes for easy identification
		System.arraycopy(GROUP, 0, peerId, 0, GROUP.length);
		
		// Randomly generate remaining 16 bytes
		byte[] random = new byte[16];
		new Random().nextBytes(random);
		
		System.arraycopy(random, 0, peerId, 4, random.length);
		
		return peerId;
	}
}
