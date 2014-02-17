1.) In Main:
		Parse Command Line Args
		Create TorrentInfo Object (Parses torrent file)
2.) Send an HTTP GET request to the tracker as denoted by the torrentFile object
		Call the T_Communicator class from Main
			Capture the response from the tracker, get the list of peers, filter by RUBT11
				Pass the list of peers to a Parser class
3.) Open a TCP socket and contact the peer, request a piece
		Future: figure out what pieces we have already and request not those. 
4.) Download the piece and verify the SHA-1 hash against the hash in the torrent file.
5.) After it's done and verified, notify the peer that you've completed the piece.
		Repeat Steps 4 and 5 until the rest of the file is done.
6.) Send the completed event to the Tracker
7.) Save the file to hard disk.


Classes

Main
	Inputs: Torrent file, save location
	Outputs: Success or Failure, downloaded data
TorrentInfo
	Inputs: Torrent file byte array from file
	Outputs: No output; TorrentInfo class leaves all data in public fields.
T_Communicator
	Methods: AnnounceToTracker, getInterval, getPeerlist
	
	T_Parser (Codec for T_Communicator [Announcing to the tracker, receiving intervals and peers])
		T_Parser uses Bencoder2 to work with messages
P_Communicator
	Methods: Handshake, getAvailablePieces, sendInterestedInPiece, RequestPiece
	
	P_Parser (Codec for Peer Communicator [Handshaking, interested, receiving unchokes, requesting pieces]
		P_Parser uses Bencoder2 to work with messages.