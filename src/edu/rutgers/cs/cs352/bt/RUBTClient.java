/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.io.FileNotFoundException;

/**
 * The RUBT Client class implements a client that operates under the BitTorrent
 * Protocol.
 * 
 * @author Gaurav Kumar
 * @author Julian Modesto
 * @author Jeffrey Rocha
 */
public class RUBTClient {

	/**
	 * Take as a command-line argument the name of the .torrent file to be
	 * loaded and the name of the file to save the data to.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		// Check for two arguments
		if (args.length != 2) {
			System.err.println("Error: two arguments required");
			System.exit(1);
		}

		try {
			Tracker tracker = new Tracker(args[0], args[1]);
			tracker.run();
		} catch (FileNotFoundException e) {
			System.err.println("Error: encountered file not found exception");
		}
	}

}
