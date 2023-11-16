package p2p;

/**
 * HandshakeMessage defines the message type
 * used for a peer to establish a connection
 * with another peer.
 */
class HandshakeMessage {

    // headerString for the HandshakeMessage is constant at 
    // this point.
    static final String HEADER_STRING = "P2PFILESHARINGPROJ";
    
    // header represents the header in the handshake message.
    private final String header;
    
    // zeroBits represents the zero bits in the handshake message.
    private final byte[] zeroBits;
    
    // peerID is the String representation of the peerID.
    private final String peerID;
    
    /**
     * Constructor for HandshakeMessage.
     *
     * @param peerID Peer ID for the handshake message.
     */
    HandshakeMessage(String peerID) {
        this.header = HEADER_STRING;
        this.zeroBits = new byte[10];
        this.peerID = peerID;
    }

    /**
     * Constructs the handshake message string.
     *
     * @return Handshake message as a string.
     */
    String getString() {
        return (HEADER_STRING + new String(zeroBits) + peerID);
    }
}
