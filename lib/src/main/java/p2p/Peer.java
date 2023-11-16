package p2p;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

// Peer defines a single peer in the
// network and the properties associated
// with it.
public class Peer {
    private PeerInfo info;
    private int prefNeighborLimit;
    private int unchokeInterval;
    private final ReadWriteLock bitFieldLock = new ReentrantReadWriteLock();
    private BitSet bitFieldSet;
    private ConcurrentHashMap<String, BitSet> neighborBitFields;
    private ArrayList<Client> preferredNeighbors;
    private List<String> interestedPeers;
    private Client optimisticallyUnchokedNeighbor;
    private int totalPieces;
    private Logger logger;
    private ConcurrentHashMap<String, Client> neighbors;
    private ConcurrentHashMap<String, HashSet<Integer>> pieceTracker;
    private Thread server;
    private FileHandler fileHandler;
    private Timer taskTimer;

    public Peer(PeerInfo peerInfo) {
        info = peerInfo;
        prefNeighborLimit = Configs.Common.NumberOfPreferredNeighbors;
        unchokeInterval = Configs.Common.UnchokingInterval;
        logger = P2PLog.GetLogger(peerInfo.peerID);
        neighbors = new ConcurrentHashMap<String, Client>();
        neighborBitFields = new ConcurrentHashMap<String, BitSet>();
        preferredNeighbors = new ArrayList<Client>();
        interestedPeers = Collections.synchronizedList(new ArrayList<String>());
        pieceTracker = new ConcurrentHashMap<String, HashSet<Integer>>();
        pieceTracker.put(peerInfo.peerID, new HashSet<Integer>());

        totalPieces = (int) Math.ceil((double) Configs.Common.FileSize / Configs.Common.PieceSize);
        bitFieldSet = new BitSet(totalPieces);
        if (peerInfo.hasFile) {
            bitFieldSet.set(0, totalPieces);
            for (int i = 0; i < totalPieces; i++)
                pieceTracker.get(peerInfo.peerID).add(i);
        }

        fileHandler = new FileHandler(Configs.Common.FileName, peerInfo.hasFile, Configs.Common.PieceSize);
    }

	// getPort returns the port number on which the peer
	// is running.
	public Integer getPort() {
		return info.port;
	}

	// getID returns the ID of the peer.
	public String getID() {
		return info.peerID;
	}

	// hasFile returns true if the peer has the file.
	public boolean hasOnePiece() {
		return pieceTracker.get(getID()).size() > 0;
	}

	// addClient adds the client to the list of neighbors maintained by the peer.
	public void addClient(Client client) {
		neighborBitFields.put(client.getID(), new BitSet(bitFieldSet.size()));
		pieceTracker.put(client.getID(), new HashSet<Integer>());
		neighbors.put(client.getID(), client);
	}

	// start does the following:
	// 1. performs a handshake with the list of input peers.
	// 2. starts a server to listen for incoming connections.
	public void start(ArrayList<PeerInfo> peers) {
		for(int i = 0; i< peers.size(); i++) {
			Client client = new Client(this, peers.get(i), logger);
			Thread clientThread = new Thread(client);
			clientThread.start();
		}
		server = new Server(this, logger);
		server.start();

		taskTimer = new Timer(true);
		taskTimer.schedule(completionCheckTask(), 10000, 5000);
		taskTimer.schedule(preferredNeighborTask(), 0, Configs.Common.UnchokingInterval * 1000);
		taskTimer.schedule(optimisticallyUnchokedNeighborTask(), 0, Configs.Common.OptimisticUnchokingInterval * 1000);

	}

	// completionCheckTask runs the completion checker to 
	// determine if all the neighbors have received the file.
	// If this condition is met, the peer shuts itself down.
	private TimerTask completionCheckTask() {
		return new TimerTask() {
			public void run() {
				ArrayList<Client> completedPeers = getCompletedPeers();
				if (completedPeers.size() == neighbors.size() && pieceTracker.get(getID()).size() == totalPieces) {
					shutdown();
					//shutdown();
				//	taskTimer.cancel();
				}
			}
		};
	}

	// preferredNeighborTask is used to recalculate the 
	// set of preferred neighbors for the peer.
	private TimerTask preferredNeighborTask() {
		return new TimerTask() {
			public void run() {
				if (neighbors.size() > 0) {
					determinePreferredNeighbors();
				}
			}
		};
	}

	// optimisticallyUnchokedNeighborTask is used to recalculate
	// the optimistically unchoked neighbor for the peer.
	private TimerTask optimisticallyUnchokedNeighborTask() {
		return new TimerTask() {
			public void run() {
				if (neighbors.size() > 0) {
					determineOptimisticallyUnchokedNeighbor();
				}
			}
		};
	}

	// determinePreferredNeighbors calculates and sets the
	// preferred neighbors based on the download rate from 
	// the peers.
	private void determinePreferredNeighbors() {
		// get eligible peers by removing the completed peers from
		// the list of all peers.
		ArrayList<Client> eligiblePeers = getPeersEligibleForUpload();
		if (optimisticallyUnchokedNeighbor != null) {
			eligiblePeers.remove(optimisticallyUnchokedNeighbor);
		}

		if (eligiblePeers.isEmpty())
			return;

		// sort the peers in decreasing order of the download rate.
		Collections.sort(eligiblePeers, new DownloadRateComparator());

		// pick the first k elements from the list where k
		// is the number of preferred neighbors.
		ArrayList<Client> newPreferredClients = 
				new ArrayList<Client>(eligiblePeers.subList(0, 
						Math.min(eligiblePeers.size(), Configs.Common.NumberOfPreferredNeighbors)));

		// unchoke all neighbors that have been picked in this round
		// and aren't currently choked.
		for (int i = 0; i < newPreferredClients.size(); i++) {
			Client pc = newPreferredClients.get(i);
			if (!preferredNeighbors.contains(pc) && pc != optimisticallyUnchokedNeighbor) {
				newPreferredClients.get(i).unchokeNeighbor();
			}
		}

		// choke all neighbors that are not in the new list but are unchoked.
		for (int i = 0; i < preferredNeighbors.size(); i++) {
			if (!newPreferredClients.contains(preferredNeighbors.get(i))) {
				preferredNeighbors.get(i).chokeNeighbor();
			}
		}

		// update preferred neighbors.
		preferredNeighbors = newPreferredClients;
		
		logger.info("Peer " + getID() + " has the preferred neighbors " + getIDList(preferredNeighbors));
	}

	private String getIDList(ArrayList<Client> clients) {
		String result = clients.get(0).getID();
		for (int i = 1; i < clients.size(); i++) {
			result += ", " + clients.get(i).getID();
		}
		return result;
	}

	// determineOptimisticallyUnchokedNeighbor calculates and sets the
	// optimistically unchoked neighbor for the peer via random
	// selection.
	private void determineOptimisticallyUnchokedNeighbor() {
		ArrayList<Client> eligiblePeers = getPeersEligibleForUpload();
		eligiblePeers.removeAll(preferredNeighbors);

		if (eligiblePeers.size() == 0) {
			return;
		}

		if (optimisticallyUnchokedNeighbor != null) {
			optimisticallyUnchokedNeighbor.chokeNeighbor();
		}

		Random rand = new Random();
		optimisticallyUnchokedNeighbor = eligiblePeers.get(rand.nextInt(eligiblePeers.size()));
		optimisticallyUnchokedNeighbor.unchokeNeighbor();

		logger.info("Peer " + getID() + " has the optimistically unchoked neighbor " + optimisticallyUnchokedNeighbor.getID());
	}

	// getPeersEligibleForUpload returns the list of peers that
	// are eligible for receiving a piece.
	private ArrayList<Client> getPeersEligibleForUpload() {
		//PeerHashTable.loadFromJson();
		PeerHashTable peerDHT = PeerHashTable.loadFromJson();
		
		peerDHT.get(0);
		//peerDHT.getHash(peerID, 1);
		ArrayList<Client> completedPeers = getCompletedPeers();
		ArrayList<Client> candidatePeers = new ArrayList<Client>(neighbors.values());
		candidatePeers.removeAll(completedPeers);
		return candidatePeers;
	}

	// getCompletedPeers returns list of peers that have
	// completed the file download in the p2p network.
	private ArrayList<Client> getCompletedPeers() {
		bitFieldLock.readLock().lock();
		ArrayList<Client> completedPeers = new ArrayList<Client>();
		Iterator<Entry<String, HashSet<Integer>>> pieceIterator = pieceTracker.entrySet().iterator();
		while (pieceIterator.hasNext()) {
			Map.Entry<String, HashSet<Integer>> peer = (Map.Entry<String, HashSet<Integer>>)pieceIterator.next();
			if (peer.getKey() == getID())
				continue;
			
			if (peer.getValue().size() == totalPieces)
				completedPeers.add(neighbors.get(peer.getKey()));
		}
		bitFieldLock.readLock().unlock();
		return completedPeers;
	}

	// updateNeighborBitField updates the bitfield info for a neighboring peer.
	// This operation is thread safe.
	public void updateNeighborPieceIndex(String peerID, Integer pieceIndex) {
		try {
			bitFieldLock.writeLock().lock();
			BitSet nBitSet = neighborBitFields.get(peerID);
			nBitSet.set(pieceIndex);
			neighborBitFields.put(peerID, nBitSet);
			pieceTracker.get(peerID).add(pieceIndex);
		} finally {
			bitFieldLock.writeLock().unlock();
		}
	}

	// setNeighborBitField sets the bitField for the given peer id in
	// the neighbor bit field map.
	public void setNeighborBitField(String peerID, byte[] bitField) {
		try {
			bitFieldLock.writeLock().lock();
			BitSet bitSet = getBitSet(bitField);
			neighborBitFields.put(peerID, bitSet);
			for (int i = 0; i < totalPieces; i++) {
				if (bitSet.get(i)) {
					pieceTracker.get(peerID).add(i);
				}
			}
		} finally {
			bitFieldLock.writeLock().unlock();
		}
	}

	// hasPiece returns true if the peer has a given piece.
	public boolean hasPiece(Integer pieceIndex) {
		try {
			bitFieldLock.readLock().lock();
			return pieceTracker.get(getID()).contains(pieceIndex);
		} finally {
			bitFieldLock.readLock().unlock();
		}
	}

	// getBitField returns the bit field for the peer.
	public byte[] getBitField() {
		bitFieldLock.readLock().lock();
		byte[] bytes = new byte[(bitFieldSet.size() + 7) / 8];
		try {
			for (int i = 0; i<bitFieldSet.size(); i++) {
				if (bitFieldSet.get(i)) {
					bytes[bytes.length-i/8-1] |= 1<<(i%8);
				}
			}
		} finally {
			bitFieldLock.readLock().unlock();
		}	
		return bytes;
	}

	// Returns a bitset containing the values in bytes.
	private BitSet getBitSet(byte[] bytes) {
		BitSet bits = new BitSet();
		for (int i = 0; i < bytes.length * 8; i++) {
			if ((bytes[bytes.length - i / 8 - 1] & (1 << (i % 8))) > 0) {
				bits.set(i);
			}
		}
		return bits;
	}

	// getPieceRequestIndex returns the ID of the piece that
	// needs to be requested from a given peer.
	public int getPieceRequestIndex(String peerID) {
		try {
			bitFieldLock.readLock().lock();
			ArrayList<Integer> candidatePieces = new ArrayList<Integer>(pieceTracker.get(peerID));
			candidatePieces.removeAll(new ArrayList<Integer>(pieceTracker.get(getID())));
			if (!candidatePieces.isEmpty()) {
				return candidatePieces.get(new Random().nextInt(candidatePieces.size()));
			}
		} finally {
			bitFieldLock.readLock().unlock();
		}
		return -1;
	}

	// addPiece saves a given piece for the local peer.
	public boolean addPiece(Integer pieceIndex, byte[] data) throws IOException {
		bitFieldLock.writeLock().lock();
		try {
			if (pieceTracker.get(getID()).contains(pieceIndex))
				return false;
			
			if (!bitFieldSet.get(pieceIndex)) {
				fileHandler.addPiece(pieceIndex, data);
				bitFieldSet.set(pieceIndex);
				pieceTracker.get(getID()).add(pieceIndex);
				if (bitFieldSet.nextClearBit(0) >= totalPieces) {
					logger.info("Peer " + getID() + " has downloaded the complete file.");
				}
			}
		} finally {
			bitFieldLock.writeLock().unlock();
		}

		Iterator<Entry<String, Client>> neighborIterator = neighbors.entrySet().iterator();
		while(neighborIterator.hasNext()) {
			Map.Entry<String, Client> pair = (Map.Entry<String, Client>)neighborIterator.next();
			pair.getValue().sendHave(pieceIndex);
		}
		return true;
	}

	// getPiece returns the requested piece from the local peer.
	public byte[] getPiece(Integer pieceIndex) throws IOException {
		byte[] piece = null;
		bitFieldLock.readLock().lock();
		try {
			if (bitFieldSet.get(pieceIndex)) {
				piece = fileHandler.getPiece(pieceIndex);
			}
		} finally {
			bitFieldLock.readLock().unlock();
		}
		return piece;
	}
	public List<byte[]> getAllPieces() throws IOException {
	    List<byte[]> pieces = new ArrayList<>();

	    bitFieldLock.readLock().lock();
	    try {
	        for (int pieceIndex = 0; pieceIndex < 2; pieceIndex++) {
	            if (bitFieldSet.get(pieceIndex)) {
	                byte[] piece = fileHandler.getPiece(pieceIndex);
	                pieces.add(piece);
	            }
	        }
	    } finally {
	        bitFieldLock.readLock().unlock();
	    }

	    return pieces;
	}

	// shutdown shuts the peer down gracefully.
	private void shutdown() {
		Iterator<Entry<String, Client>> neighborIterator = neighbors.entrySet().iterator();
		while(neighborIterator.hasNext()) {
			Map.Entry<String, Client> pair = (Map.Entry<String, Client>)neighborIterator.next();
			pair.getValue().shutdown();
		}
		fileHandler.close();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.exit(0);
	}

	// addPeerInterest keeps track of the interest from peers.
	public void addPeerInterest(String peerID, boolean interested) {
		synchronized (interestedPeers) {
			if (interested)
				interestedPeers.add(peerID);
			else
				interestedPeers.remove(peerID);
		}
	}
}

// DownloadRateComparator sorts the clients in descending
// order of their download rates.
class DownloadRateComparator implements Comparator<Client> {
	@Override
	public int compare(Client c1, Client c2) {
		if (c1.getDownloadRate() < c2.getDownloadRate()) {
			return 1;
		} else if (c1.getDownloadRate() == c2.getDownloadRate()) {
			return 0;
		} 
		return -1;
	}

}