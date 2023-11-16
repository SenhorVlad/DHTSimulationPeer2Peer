package p2p;

class PeerInfo {

	String peerID;

	String hostName;

	Integer port;

	Boolean hasFile;

	public PeerInfo(String peerID, String hostname, Integer port, Boolean hasFile){
		this.peerID = peerID;
		this.hostName = hostname;
		this.port = port;
		this.hasFile = hasFile;
	}
}