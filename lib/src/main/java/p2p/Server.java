package p2p;

import java.util.logging.Logger;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class Server extends Thread {

	ServerSocket serverSocket;

	Peer localPeer;

	Logger logger;

	public Server(Peer peer, Logger p2pLogger) {
		localPeer = peer;
		logger = p2pLogger;
	}

	@Override
	public void run() {
		try {
			serverSocket = new ServerSocket(localPeer.getPort());
			while(true) {
				handleRequest(serverSocket.accept());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public void handleRequest(Socket sock) {
		try {
			DataInputStream inStream = new DataInputStream(sock.getInputStream());
			DataOutputStream outStream = new DataOutputStream(sock.getOutputStream());
			String message = (String) inStream.readUTF();
			String neighborPeerID = message.substring(28, 32); 
			PeerInfo neighbor = new PeerInfo(neighborPeerID, sock.getInetAddress().getHostAddress(), sock.getPort(), false);
			Client client = new Client(localPeer, neighbor, logger, sock, inStream, outStream);
			localPeer.addClient(client);
			Thread clientThread = new Thread(client);
			clientThread.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}