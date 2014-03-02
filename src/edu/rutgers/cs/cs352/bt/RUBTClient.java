package edu.rutgers.cs.cs352.bt;

/* @author Jeffrey Rocha */

import java.net.*;
import java.util.logging.Logger;
import java.io.*;

import edu.rutgers.cs.cs352.bt.exceptions.BencodingException;

public class RUBTClient {
	
	private final static Logger LOGGER = 
			Logger.getLogger(RUBTClient.class.getName());
	
	private static TorrentInfo torrent_info;

	public static void main(String[] args) throws Exception {
		
		if (args.length != 2) {
			System.err.println("Error: two arguments required");
			System.exit(1);
		}
		
		String file_name = args[0];
		File torrent_file = new File(file_name);
		byte[] torrent_bytes = getFileInBytes(torrent_file, file_name);
		
		torrent_info = new TorrentInfo(torrent_bytes);
		
		RUBTClient client = new RUBTClient();
		client.getRequest();
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
			
			if (!torrent_file.exists()) //Check to see if the file exists
				System.err.println("The file \"" + file_name + "\" does not exist. Please make sure you have the correct path to the file.");
			
			if (!torrent_file.canRead()) //Check to see if the file is readable
				System.err.println("Cannot read from \"" + file_name + "\". Please make sure the file permissions are set correctly.");
			
			int file_offset = 0;
			int bytes_read = 0;

			// Read from the file
			while (file_offset < torrent_bytes.length && (bytes_read = file_stream.read(torrent_bytes, file_offset, torrent_bytes.length - file_offset)) >= 0)
				file_offset += bytes_read;

			if (file_offset < torrent_bytes.length)
				throw new IOException("Could not completely read file \"" + torrent_file.getName() + "\".");
		
			file_stream.close();
			
		}
		catch (FileNotFoundException fnfe)
		{
			System.err.println("The file \"" + file_name + "\" does not exist. Please make sure you have the correct path to the file.");
		}
		catch (IOException ioe)
		{
			System.err.println("There was an I/O error when reading \"" + file_name + "\".");
			System.err.println(ioe.getMessage());
		}
		return torrent_bytes;
	}
	
	/* Sends HTTP Get Request to 
	 * the tracker
	 */
	private void getRequest() throws Exception{
		
		String urlName = torrent_info.announce_url.toString();
		URL obj = new URL(urlName);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		con.setRequestMethod("GET");
 
		int responseCode = con.getResponseCode();
		System.out.println("\nSending 'GET' request to URL : " + urlName);
		System.out.println("Response Code : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		System.out.println(response.toString());
	}
}
