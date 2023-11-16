package p2p;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PeerProcess {

	public static void main(String[] args) {
		if (args.length > 2) {
			System.out.println("The number of arguments passed to the program is " + args.length 
					+ " while it should be 1.\nUsage: java PeerProcess <peerId>");
			return;
		}
		
		new Configs.Common(Configs.Constants.CommonConfigPath);
		
		// option to override the default filename.
		if (args.length == 2) {
			Configs.Common.FileName = args[1];
		} else {
			Configs.Common.FileName = "C:\\path\\peer_" + args[0] + "\\" + Configs.Common.FileName;
		}
		
		System.out.println("Launching Peer " + args[0]);

		try{
			run(args[0]);
		} catch(IOException e){
			e.printStackTrace();
		}

	}

	public static void run(String peerID) throws IOException{
		ArrayList<PeerInfo> neighborPeers = new ArrayList<PeerInfo>();
		PeerInfo peerInfo = Configs.parsePeerInfos(neighborPeers, Configs.Constants.PeerInfoConfigPath, peerID);
		if (peerInfo == null) {
			throw new IOException("could not parse peer info");
		}

		Peer localPeer = new Peer(peerInfo);

		PeerHashTable peerDHT = PeerHashTable.loadFromJson();
		if(localPeer.hasPiece(0))
		{
			for (int pieceIndex = 0; pieceIndex < 2; pieceIndex++) {
			    byte[] piece = localPeer.getPiece(pieceIndex);
			    String hash = PeerProcess.calculateSHA256(piece);
			    peerDHT.put(peerID, pieceIndex, hash);
			    peerDHT.saveToJson(peerDHT);
			}
		}
		ArrayList<PeerInfo> neighborPeersVerified = new ArrayList<PeerInfo>();
		for (PeerInfo neighbor : neighborPeers)
		{
			for (String neighbor2 : peerDHT.get(0))
			{
				if(neighbor.peerID.toString().contentEquals(neighbor2.toString()))
				{
					neighborPeersVerified.add(neighbor);
				}
			}
		}
		peerDHT.get(2);
		peerDHT.getHash(peerID, 1);
		localPeer.start(neighborPeersVerified);
	}
	
	public static String calculateSHA256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data);

            StringBuilder hashStringBuilder = new StringBuilder();
            for (byte b : hashBytes) {
                hashStringBuilder.append(String.format("%02x", b));
            }

            return hashStringBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}