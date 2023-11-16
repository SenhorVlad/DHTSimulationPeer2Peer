package p2p;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * FileHandler manages access to the file used
 * by a peer for handling data transfer.
 */
class FileHandler {

    // pieceSize defines the size of a piece in bytes.
    private final int pieceSize;

    // Path where the file is stored.
    private final String path;

    // Flag indicating if the file is read-only.
    private final boolean readOnly;

    // FileLock makes the operations on the file thread-safe.
    private final ReadWriteLock fileLock = new ReentrantReadWriteLock();

    // File is the underlying file used by a peer for data transfer.
    private RandomAccessFile file;

    // DataStore to store pieces of data with their respective indices.
    private final HashMap<Integer, byte[]> dataStore;

    /**
     * FileHandler defines the constructor for creating
     * the file handler.
     * The file is opened in "rw" mode by default.
     *
     * @param path      Path where the file is stored.
     * @param readOnly  Flag indicating if the file is read-only.
     * @param pieceSize Size of a piece in bytes.
     */
    FileHandler(String path, boolean readOnly, int pieceSize) {
        this.path = path;
        this.pieceSize = pieceSize;
        this.readOnly = readOnly;
        dataStore = new HashMap<>();

        try {
            if (readOnly) {
                file = new RandomAccessFile(path, "r");
                loadFile();
            } else {
                file = new RandomAccessFile(path, "rw");
                file.setLength(Configs.Common.FileSize);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * getPiece returns the requested piece using the specified pieceIndex.
     * This operation is thread-safe.
     *
     * @param pieceIndex Index of the requested piece.
     * @return Byte array of the requested piece.
     * @throws IOException If an I/O error occurs.
     */
    public byte[] getPiece(int pieceIndex) throws IOException {
        fileLock.readLock().lock();
        try {
            return dataStore.get(pieceIndex);
        } finally {
            fileLock.readLock().unlock();
        }
    }

    /**
     * addPiece adds the given piece of data at the specified pieceIndex.
     * This assumes that the data block adheres to the pieceSize.
     * This operation is thread-safe.
     *
     * @param pieceIndex Index of the piece to be added.
     * @param data       Data block to be added.
     * @return True if the addition was successful, otherwise false.
     * @throws IOException If an I/O error occurs.
     */
    public boolean addPiece(int pieceIndex, byte[] data) throws IOException {
        boolean status = false;
        fileLock.writeLock().lock();
        try {
            dataStore.put(pieceIndex, data);
            status = true;
        } finally {
            fileLock.writeLock().unlock();
        }
        return status;
    }

    /**
     * Close the file and construct it if it's not read-only.
     */
    public void close() {
        fileLock.writeLock().lock();
        try {
            if (!readOnly)
                constructFile();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fileLock.writeLock().unlock();
        }
    }

    private void loadFile() throws IOException {
        int totalPieces = (int) Math.ceil((double) Configs.Common.FileSize / Configs.Common.PieceSize);
        for (int i = 0; i < totalPieces; i++) {
            int pieceLength = Math.min(pieceSize, Configs.Common.FileSize - i * pieceSize);
            byte[] data = new byte[pieceLength];
            file.seek(i * pieceSize);
            file.readFully(data);
            dataStore.put(i, data);
        }
    }

    private void constructFile() {
        int totalPieces = (int) Math.ceil((double) Configs.Common.FileSize / Configs.Common.PieceSize);
        try {
            for (int i = 0; i < totalPieces; i++) {
                file.seek(i * pieceSize);
                file.write(dataStore.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
