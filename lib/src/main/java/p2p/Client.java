package p2p;

import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.DataInputStream;

// Client defines a single socket connection a client uses to connect 
// to the server, the output and input streams associated with it
class Client implements Runnable, ClientInterface {

    private Peer localPeer;
    private PeerInfo neighbor;
    private boolean handshakeReceived;
    private int downloadedPiecesSinceUnchoked;
    private Instant lastUnchokedByNeighborAt;
    private final ReadWriteLock lastDownloadRateLock = new ReentrantReadWriteLock();
    private float lastDownloadRate;
    private boolean isChoked;
    private boolean shutdown;
    private Socket socket;
    private DataOutputStream outStream;
    private LinkedBlockingQueue<Message> writeMessageQueue;
    private DataInputStream inStream;
    private Logger logger;

    public Client(Peer peer, PeerInfo neighborPeerInfo, Logger p2pLogger) {
        localPeer = peer;
        neighbor = neighborPeerInfo;
        logger = p2pLogger;
        initializeClient();
    }

    public Client(Peer peer, PeerInfo neighborPeerInfo, Logger p2pLogger, Socket sock, DataInputStream in, DataOutputStream out) {
        localPeer = peer;
        neighbor = neighborPeerInfo;
        logger = p2pLogger;
        socket = sock;
        downloadedPiecesSinceUnchoked = 0;
        lastDownloadRate = 0;
        shutdown = false;
        writeMessageQueue = new LinkedBlockingQueue<>();
        handshakeReceived = true;
        isChoked = true;
        inStream = in;
        outStream = out;
    }

    private void initializeClient() {
        downloadedPiecesSinceUnchoked = 0;
        lastDownloadRate = 0;
        handshakeReceived = false;
        isChoked = true;
        shutdown = false;
        writeMessageQueue = new LinkedBlockingQueue<>();
        try {
            socket = new Socket(neighbor.hostName, neighbor.port);
            inStream = new DataInputStream(socket.getInputStream());
            outStream = new DataOutputStream(socket.getOutputStream());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getID() {
        return neighbor.peerID;
    }

    @Override
    public void run() {
        try {
            performHandshake();
            sendBitField();

            new Thread(this::writeMessages).start();

            while (!shutdown) {
                handleActualMessage();
            }

        } catch (EOFException e) {
            handleEOF();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleEOF() {
        writeMessageQueue.add(new Message(null, null));
        logger.info("Terminating connection with Peer " + neighbor.peerID);
    }

    private void writeMessages() {
        while (true) {
            try {
                Message msg = writeMessageQueue.take();
                if (msg.type == null)
                    return;

                sendActualMessage(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendActualMessage(Message msg) throws IOException {
        int totalLength = 1;
        if (msg.body != null) {
            totalLength += msg.body.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength + 4);
        buffer.putInt(totalLength);
        buffer.put((byte) msg.type.ordinal());
        if (msg.body != null) {
            buffer.put(msg.body);
        }

        outStream.write(buffer.array());
    }


    /**
     * Sends a 'HAVE' message to the connected neighbor indicating the availability of a specific piece.
     *
     * @param pieceIndex The index of the piece available.
     */
    public void sendHave(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);
        writeMessageQueue.add(new Message(MessageType.HAVE, buffer.array()));
    }

    /**
     * Reads the input stream for an incoming message and directs it to the appropriate message type handler.
     *
     * @throws IOException If an I/O error occurs while reading the stream.
     */
    private void handleActualMessage() throws IOException {
        int messageLength = inStream.readInt();
        MessageType messageType = MessageType.getType(Byte.toUnsignedInt(inStream.readByte()));
        switch (messageType) {
            case CHOKE:
                handleChoke();
                break;
            case UNCHOKE:
                handleUnchoke();
                break;
            case INTERESTED:
                handleInterested();
                break;
            case NOT_INTERESTED:
                handleNotInterested();
                break;
            case HAVE:
                handleHave();
                break;
            case BITFIELD:
                handleBitField(messageLength - 1);
                break;
            case REQUEST:
                handleRequest();
                break;
            case PIECE:
                handlePiece(messageLength - 1);
                break;
        }
    }


    /**
     * Handles the action when the peer receives a CHOKE message from the neighbor.
     * It calculates the download rate for the last unchoked interval and resets the downloaded pieces counter.
     *
     * @throws IOException If an I/O error occurs while handling the choke message.
     */
    private void handleChoke() throws IOException {
        logger.info("Peer " + localPeer.getID() + " is choked by Peer " + neighbor.peerID);

        // Calculate the download rate for the last unchoked interval and reset the downloaded pieces counter.
        lastDownloadRateLock.writeLock().lock();
        lastDownloadRate = (float) downloadedPiecesSinceUnchoked /
                Duration.between(Instant.now(), lastUnchokedByNeighborAt).getSeconds();
        downloadedPiecesSinceUnchoked = 0;
        lastDownloadRateLock.writeLock().unlock();
    }

    /**
     * Handles the action when the peer receives an UNCHOKE message from the neighbor.
     * It marks the peer as unchoked by the neighbor and initiates a piece request.
     *
     * @throws IOException If an I/O error occurs while handling the unchoke message.
     */
    private void handleUnchoke() throws IOException {
        logger.info("Peer " + localPeer.getID() + " is unchoked by Peer " + neighbor.peerID);

        lastUnchokedByNeighborAt = Instant.now();
        requestPiece();
    }

    /**
     * Handles the action when the peer receives an INTERESTED message from the neighbor.
     * Updates the peer's interest status for the neighbor.
     */
    private void handleInterested() {
        logger.info("Peer " + localPeer.getID() + " received the 'INTERESTED' message from Peer " + neighbor.peerID);
        localPeer.addPeerInterest(neighbor.peerID, true);
    }

    /**
     * Handles the action when the peer receives a NOT INTERESTED message from the neighbor.
     * Updates the peer's interest status for the neighbor.
     */
    private void handleNotInterested() {
        logger.info("Peer " + localPeer.getID() + " received the 'NOT INTERESTED' message from Peer " + neighbor.peerID);
        localPeer.addPeerInterest(neighbor.peerID, false);
    }

    /**
     * Handles the action when the peer receives a HAVE message from the neighbor.
     * Updates the peer's record of pieces the neighbor has and responds with interest if the peer does not have that piece.
     *
     * @throws IOException If an I/O error occurs while handling the have message.
     */
    private void handleHave() throws IOException {
        logger.info("Peer " + localPeer.getID() + " received the 'HAVE' message from Peer " + neighbor.peerID);
        Integer pieceIndex = inStream.readInt();
        localPeer.updateNeighborPieceIndex(neighbor.peerID, pieceIndex);
        if (!localPeer.hasPiece(pieceIndex)) {
            writeMessageQueue.add(new Message(MessageType.INTERESTED, null));
        }
    }

    /**
     * Handles the action when the peer receives a BITFIELD message from the neighbor.
     * Updates the peer's bitfield information for the neighbor and responds with interest if applicable.
     *
     * @param messageLength The length of the incoming message.
     * @throws IOException If an I/O error occurs while handling the bitfield message.
     */
    private void handleBitField(int messageLength) throws IOException {
        byte[] neighborBitField = new byte[messageLength];
        inStream.readFully(neighborBitField);
        localPeer.setNeighborBitField(neighbor.peerID, neighborBitField);
        if (localPeer.getPieceRequestIndex(neighbor.peerID) != -1) {
            writeMessageQueue.add(new Message(MessageType.INTERESTED, null));
        }
    }

    // Restante do código permanece inalterado...


	// handleRequest acts on the REQUEST message.
	private void handleRequest() throws IOException {
		Integer pieceIndex = inStream.readInt();
		// drop message if the neighbor is choked
		// at the moment.
		if (isChoked) {
			return;
		}
		System.out.println("Received Request for Piece Index: " + pieceIndex);
		byte[] piece = localPeer.getPiece(pieceIndex);
		if (piece != null) {
			ByteBuffer bb = ByteBuffer.allocate(4 + piece.length); 
			bb.putInt(pieceIndex);
			bb.put(piece);
			writeMessageQueue.add(new Message(MessageType.PIECE, bb.array()));
		}
	}

	// handlePiece acts on the PIECE message.
	private void handlePiece(int mLength) throws IOException {
		Integer pieceIndex = inStream.readInt();
		byte[] data = new byte[mLength-4];
		inStream.readFully(data);
		System.out.println("Received Piece with piece index: " + pieceIndex);
		boolean pieceAdded = localPeer.addPiece(pieceIndex, data);
		downloadedPiecesSinceUnchoked++;
		if (pieceAdded) {
			logger.info("Peer " + localPeer.getID() + " has downloaded the piece " + pieceIndex +" from Peer " + neighbor.peerID);
		}
		requestPiece();
	}

	// requestPiece sends a request to the neighbor for a piece
	// that it's missing and the neighbor has.
	private void requestPiece() throws IOException {
		int pieceIndex = localPeer.getPieceRequestIndex(neighbor.peerID);
		if (pieceIndex == -1) {
			return;
		}
		System.out.println("Sending Request for Piece Index: " + pieceIndex);
		ByteBuffer bb = ByteBuffer.allocate(4); 
		bb.putInt(pieceIndex);
		writeMessageQueue.add(new Message(MessageType.REQUEST, bb.array()));
	}

	// performHandshake performs a handshake with a
	// neighbor.
	public void performHandshake() {
		sendHandshake();

		if (!handshakeReceived) {
			receiveHandshake();
		}	
	}

	// sendHandshake sends the handshake request to a neighbor.
	private void sendHandshake() {
		try {
			outStream.writeUTF(new HandshakeMessage(localPeer.getID()).getString());
			logger.info("Peer " + localPeer.getID() + " makes a connection to Peer " + neighbor.peerID);
		} catch (IOException e) {
			logger.info(e.toString());
		}
	}
	
	// chokeNeighbor chokes the neighbor and notifies it.
	public void chokeNeighbor() {
		isChoked = true;
		writeMessageQueue.add(new Message(MessageType.CHOKE, null));
	}
	
	// unchokeNeighbor unchokes the neighbor and notifies it.
	public void unchokeNeighbor() {
		isChoked = false;
		writeMessageQueue.add(new Message(MessageType.UNCHOKE, null));
	}

	// receiveHandshake receives and handles the handshake
	// request from the neighbor.
	private void receiveHandshake() {
		String neighborPeerID = "";
		while(!handshakeReceived) {
			try {
				String message = (String) inStream.readUTF();
				neighborPeerID = message.substring(28, 32);

				if (message.equalsIgnoreCase(new HandshakeMessage(neighborPeerID).getString())) {
					break;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		logger.info("Peer " + localPeer.getID() + " is connected from Peer " + neighborPeerID);
		handshakeReceived = true;
		localPeer.addClient(this);
	}

	// sendBitField sends the bitfield of the local peer
	// to the neighbor.
	public void sendBitField() throws IOException {
		if (localPeer.hasOnePiece()) {
			sendActualMessage(new Message(MessageType.BITFIELD, localPeer.getBitField()));
		}
	}

	// getDownloadRate returns the download rate for the peer from
	// the neighbor during the last unchoked interval.
	public float getDownloadRate() {
		lastDownloadRateLock.readLock().lock();
		try {
			return lastDownloadRate;
		} finally {
			lastDownloadRateLock.readLock().unlock();
		}
	}
	
	public void shutdown() {
		try {
			shutdown = true;
			while(!writeMessageQueue.isEmpty()) {
				// Do nothing.
			}
			outStream.flush();
			socket.close();
		} catch (IOException e) {
			// Do nothing
		}
	}
}