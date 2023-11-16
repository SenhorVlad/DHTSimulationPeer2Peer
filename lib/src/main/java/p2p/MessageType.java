package p2p;

/**
 * Enumerates different types of messages exchanged between peers.
 */
enum MessageType {
	CHOKE,
	UNCHOKE,
	INTERESTED,
	NOT_INTERESTED,
	HAVE,
	BITFIELD,
	REQUEST,
	PIECE;

	/**
	 * Retrieves the MessageType based on the given index.
	 *
	 * @param typeIndex The index of the MessageType.
	 * @return The corresponding MessageType.
	 */
	public static MessageType getType(int typeIndex) {
		return MessageType.values()[typeIndex];
	}
}
