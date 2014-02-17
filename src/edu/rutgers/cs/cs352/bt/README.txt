Main
Parse Torrent File
Send an HTTP GET request as denoted by the torrentFile object
Capture the response, get the list of peers, filter by RUBT11
Open a TCP socket and contact the peer, request a piece
Download the piece and verify the SHA-1 hash against the hash in the torrent file.
After it's done and verified, notify the peer that you've completed the piece.
Repeat Step6 and 7 until the rest of the file is done.
Send the completed event to the Tracker
Save the file to hard disk.