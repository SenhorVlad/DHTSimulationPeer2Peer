package p2p;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;


public final class Configs {

	static class Constants {
		static final String CommonConfigPath = "C:\\Userspath\\Common.cfg";
		static final String PeerInfoConfigPath = "C:\\Userspath\\PeerInfo.cfg";
	}

	public final static class Common {

        static class Constants {
            static final String PreferredNeighbors = "NumberOfPreferredNeighbors";
            static final String UnchokingInterval = "UnchokingInterval";
            static final String OptimisticUnchokingInterval = "OptimisticUnchokingInterval";
            static final String FileName = "FileName";
            static final String FileSize = "FileSize";
            static final String PieceSize = "PieceSize";
        }

		static int NumberOfPreferredNeighbors;
		static int UnchokingInterval;
		static int OptimisticUnchokingInterval;
		static String FileName;
		static int FileSize;
		static int PieceSize;

		Common(String fileName) {
			FileReader fr;
			BufferedReader br;
			try {
				fr = new FileReader(fileName);
				br = new BufferedReader(fr);
				String line = br.readLine();
				while (line != null) {
					String[] values = line.trim().split(" ");
					switch (values[0]) {
					case Configs.Common.Constants.PreferredNeighbors :
						NumberOfPreferredNeighbors = Integer.parseInt(values[1].trim());
						break;
					case Configs.Common.Constants.UnchokingInterval :
						UnchokingInterval = Integer.parseInt(values[1].trim());
						break;
					case Configs.Common.Constants.OptimisticUnchokingInterval :
						OptimisticUnchokingInterval = Integer.parseInt(values[1].trim());
						break;
					case Configs.Common.Constants.FileName :
						FileName = values[1].trim();
						break;
					case Configs.Common.Constants.FileSize :
						FileSize = Integer.parseInt(values[1].trim());
						break;
					case Configs.Common.Constants.PieceSize :
						PieceSize = Integer.parseInt(values[1].trim());
						break;
					}
					line = br.readLine();
				}
				br.close();
				fr.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

    public static PeerInfo parsePeerInfos(List<PeerInfo> peerInfos, String fileName, String targetPeer) {
        FileReader fr;
        BufferedReader br;
        PeerInfo peer = null;
        try {
            fr = new FileReader(fileName);
            br = new BufferedReader(fr);
            String line = br.readLine();
            while (line != null) {
                String[] tokens = line.split("\\s+");
                boolean hasFile = Integer.parseInt(tokens[3]) == 1;
                PeerInfo parsedPeer = new PeerInfo(tokens[0], tokens[1], Integer.parseInt(tokens[2]), hasFile);
                if (tokens[0].equals(targetPeer)) {
                    peer = parsedPeer;
                    break;
                }
                peerInfos.add(parsedPeer);
                line = br.readLine();
            }
            br.close();
            fr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return peer;
    }
}

