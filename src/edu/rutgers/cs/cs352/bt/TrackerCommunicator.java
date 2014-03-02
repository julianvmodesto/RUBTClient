/**
 * 
 */
package edu.rutgers.cs.cs352.bt;

import java.util.logging.Logger;
import java.net.URI;
import java.net.URISyntaxException;
/**
 * @author Gaurav Kumar
 *
 */
public class TrackerCommunicator {
	private final static Logger LOGGER = Logger.getLogger(TrackerCommunicator.class.getName());
	private static final byte[] GROUP = {'G','P','O','1','6'};
	
	public static void AnnounceStartToTracker(TorrentInfo torrent_info, ){
		try {
			URI uri = new URI(TorrentInfo.);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
