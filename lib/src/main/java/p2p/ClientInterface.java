package p2p;

import java.io.IOException;

//ClientInterface represents the interface defining the methods
//necessary for peer-to-peer communication between peers.
public interface ClientInterface {

 // performHandshake initiates the handshake process between peers
 // to establish a persistent connection for data transfer.
 void performHandshake();

 // sendBitField sends the local peer's bitfield to the neighboring peer,
 // informing about the available pieces.
 void sendBitField() throws IOException;
}
