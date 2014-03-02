package edu.rutgers.cs.cs352.bt;

/* @author Jeffrey Rocha */

import java.net.*;
import java.io.*;


public class RUBTClient {
	// Hard code the first 4 bytes of the peer ID
	private static final byte[] GROUP = {'G','P','1','6'};
	
	public enum Event {
		STARTED, COMPLETED, STOPPED
	};
	private static TorrentInfo torrent_info; //TorrentInfo object used for the program, created in main
	public static int uploaded = 0;
	public static int downloaded = 0;
	public static int left = 0;
	
	public static void main(String[] args) throws Exception {
		
		if (args.length != 2) { // error for incorrect amount of args
			System.err.println("Error: two arguments required");
			System.exit(1);
		}
		
		String torrent_file_name = args[0];
		File torrent_file = new File(torrent_file_name); //creates file for torrent
		String download_file_name = args[1];
		File download_file = new File(download_file_name); //creates file to save data to
		
		byte[] torrent_bytes = getFileInBytes(torrent_file, torrent_file_name); //calls getFileInBytes to change the torrent file into a byte array
		
		torrent_info = new TorrentInfo(torrent_bytes); //initializes the torrentInfo 
				
		getRequest();
	}
	
	/* Changes the torrent file into a byte array
	 * used to create the TorrentInfo object
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
	
	/* Sends HTTP Get Request to 
	 * the tracker
	 */
	public static void getRequest() throws Exception{
		
		String urlName = torrent_info.announce_url.toString(); //makes the URL of the torrentInfo object into a string
		URL url = new URL(urlName); // new URL object made from the string announce_url
		HttpURLConnection con = (HttpURLConnection) url.openConnection(); //open an HTTP connection
 
		con.setRequestMethod("GET");
 
		//response code used to find if connection was success or failure (and reason for failure)
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + urlName);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null)
			response.append(inputLine);
		
		in.close();
 
		System.out.println(response.toString());
	}
}
