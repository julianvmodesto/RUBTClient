In Main:
	Parse Command Line Args
	Create TorrentInfo Object (Parses torrent file)
Send an HTTP GET request to the tracker as denoted by the torrentFile object
	Call the T_Communicator class from Main
		Capture the response from the tracker, get the list of peers, filter by RUBT11
			Pass the list of peers to a Parser class
Open a TCP socket and contact the peer, request a piece
Download the piece and verify the SHA-1 hash against the hash in the torrent file.
After it's done and verified, notify the peer that you've completed the piece.
Repeat Step6 and 7 until the rest of the file is done.
Send the completed event to the Tracker
Save the file to hard disk.


Classes
Main
TorrentFile
T_Communicator
P_Communicator 
T_Parser (Codec for Tracker Communicator [Announcing to the tracker, receiving intervals and peers])
	T_Parser uses Bencoder2 to work with messages
P_Parser (Codec for Peer Communicator [Handshaking, verifying infohash, interested, receiving unchokes, requesting pieces]
	P_Parser uses Bencoder2 to work with messages.
