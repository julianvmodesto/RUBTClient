package edu.rutgers.cs.cs352.bt;

/* @author Jeffrey Rocha */

import java.net.*;
import java.nio.charset.Charset;
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
	public static int port = 6881;
	
	public static byte[] myPeerId;
	
	public static void main(String[] args) throws Exception {
		
		if (args.length != 2) { // error for incorrect amount of args
			System.err.println("Error: two arguments required");
			System.exit(1);
		}
		
		myPeerId = generatePeerId();
		
		String torrent_file_name = args[0];
		File torrent_file = new File(torrent_file_name); //creates file for torrent
		String download_file_name = args[1];
		File download_file = new File(download_file_name); //creates file to save data to
		
		byte[] torrent_bytes = getFileInBytes(torrent_file, torrent_file_name); //calls getFileInBytes to change the torrent file into a byte array
		
		torrent_info = new TorrentInfo(torrent_bytes); //initializes the torrentInfo
		left = torrent_info.torrent_file_bytes.length;
		
		while (left > 0 && port <= 6889)		
			getRequest();
		if (port > 6889)
			System.err.println("There are no further ports available for use. Sorry.");
	}
	
	/**
	 * Changes the torrent file into a byte array
	 * used to create the TorrentInfo object
	 * @author Robert Moore
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
	 * Sends HTTP Get Request to 
	 * the tracker
	 * @author Jeffrey Rocha
	 */
	private static void getRequest() throws Exception{
		
		String torrent_hash = new String(torrent_info.info_hash.array(), Charset.forName("UTF-8"));
		String torrent_peerId = new String(myPeerId, Charset.forName("UTF-8"));
		String urlName = torrent_info.announce_url.toString();
		
		URL url; //Sets up the URL connection whether starting, completing, stopping, or going through the download
		if (left == 0)
			url = new URL(urlName + torrent_hash + torrent_peerId + port + uploaded + downloaded + left + Event.COMPLETED);
		else if (uploaded == 0)
			url = new URL(urlName + torrent_hash + torrent_peerId + port + uploaded + downloaded + left + Event.STARTED);
		else if (downloaded == 0)
			url = new URL(urlName + torrent_hash + torrent_peerId + port + uploaded + downloaded + left + Event.STOPPED);
		else
			url = new URL(urlName + torrent_hash + torrent_peerId + port + uploaded + downloaded + left);
		
		HttpURLConnection con = (HttpURLConnection) url.openConnection(); //open an HTTP connection
 
		con.setRequestMethod("GET");
 
		//response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + urlName);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null)
			response.append(inputLine);
		
		in.close();
		System.out.println(response.toString());
		
		if (responseCode == 404)
			port++;
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
		new Random().nextBytes(peerId);
		
		return peerId;
	}
}
