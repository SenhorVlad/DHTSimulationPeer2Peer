package p2p;

/**
 * Represents a message exchanged between peers.
 */
class Message {
	
	// The type of the message.
	MessageType type;
	
	// The contents of the message. 
	byte[] body;
	
	/**
	 * Constructs a Message object.
	 *
	 * @param mType The type of the message.
	 * @param mBody The contents of the message.
	 */
	Message(MessageType mType, byte[] mBody) {
		type = mType;
		body = mBody;
	}
}
