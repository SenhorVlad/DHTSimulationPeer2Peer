package p2p;

import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Manages logging functionality for peer-to-peer communication.
 */
public class P2PLog {
	
	static Logger logger;
	
	/**
	 * Retrieves the logger for a specific peer ID.
	 *
	 * @param peerID The ID of the peer.
	 * @return The logger instance.
	 */
	public static Logger GetLogger(String peerID){
		try {
			// Define the log file path based on the peer ID.
			String logFile = "C:\\path\\peer_" + peerID + "/log_peer_" + peerID + ".log";
			
			// Create a FileHandler to manage logging to the specified file.
			FileHandler fileHandler = new FileHandler(logFile);
			
			// Define a custom formatter for log records.
			fileHandler.setFormatter(new SimpleFormatter() {
				private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

				@Override
				public synchronized String format(LogRecord lr) {
					return String.format(format, new Date(lr.getMillis()), lr.getLevel().getLocalizedName(),
							lr.getMessage());
				}
			});
			
			// Create a Logger instance and configure it.
			logger = Logger.getLogger(logFile);
			logger.setLevel(Level.INFO);
			logger.addHandler(fileHandler);
		} catch (Exception e) {
			// Print the stack trace in case of an exception.
			e.printStackTrace();
		}
		return logger;
	}
}
