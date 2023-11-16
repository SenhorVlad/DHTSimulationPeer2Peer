package p2p;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchForPeer {

    public static int searchForKey(int id, String key) throws IOException {
        return searchForKeyRecursive(id, key, true);
    }

    private static int searchForKeyRecursive(int mainPeer, String key, boolean searchLeft) throws IOException {
        ArrayList<PeerInfo> neighborPeers = new ArrayList<PeerInfo>();
        String neighborString;

        if (searchLeft) {
            // Procurar na esquerda
            if (mainPeer - 1 < 1001) {
                mainPeer = 1005;
            }
            neighborString = Integer.toString(mainPeer - 1);
        } else {
            // Procurar na direita
            if (mainPeer + 1 > 1004) {
                mainPeer = 1001;
            }
            neighborString = Integer.toString(mainPeer + 1);
        }

        PeerInfo peerInfo = Configs.parsePeerInfos(neighborPeers, Configs.Constants.PeerInfoConfigPath, neighborString);

        if (peerInfo == null) {
            throw new IOException("could not parse peer info");
        }

        new Configs.Common(Configs.Constants.CommonConfigPath);
        Configs.Common.FileName = "C:/path/peer_" + neighborString + "/" + Configs.Common.FileName;

        Peer localPeer = new Peer(peerInfo);
        boolean itsHas = false;

        if (localPeer.getPiece(0) != null) {
            for (int pieceIndex = 0; pieceIndex < 4; pieceIndex++) {
                byte[] piece = localPeer.getPiece(pieceIndex);
                String hash = PeerProcess.calculateSHA256(piece);

                if (hash.equals(key)) {
                    itsHas = true;
                }
            }
        }

        // Verifica se o peer possui a chave
        if (itsHas) {
            return Integer.parseInt(neighborString);
        } else {
            // Recursivamente busca nos vizinhos
            if (searchLeft && mainPeer >= 1001) {
                int leftResult = searchForKeyRecursive(Integer.parseInt(neighborString), key, false);
                if (leftResult != -1) return leftResult;
            }

            if (!searchLeft && mainPeer <= 1005) {
                int rightResult = searchForKeyRecursive(Integer.parseInt(neighborString), key, false);
                if (rightResult != -1) return rightResult;
            }
        }

        // Se a chave não for encontrada, retorna -1 indicando erro
        return -1;
    }

    public static void main(String[] args) throws IOException {
        SearchForPeer dhtSimulator = new SearchForPeer();

        // Exemplo de busca pela chave 1003 no peer 1001
        int startPeerID = Integer.parseInt(args[0]);
        int result = searchForKey(Integer.parseInt(args[0]), args[1]);

        if (result != -1) {
        	try {
        		String comando = "java";
        		String classePrincipal = "C:\\path\\PeerProcess"; // Substitua pelo caminho completo da sua classe

        		// Argumentos opcionais
        		String[] argumentos = {args[0]};
        		String[] argumentos2 = {(String.valueOf(result))};

        		// Crie o processo usando o ProcessBuilder
        		ProcessBuilder processBuilder = new ProcessBuilder(comando, "-cp", ".", classePrincipal);
        		List<String> command = new ArrayList<>(Arrays.asList(comando, "-cp", ".", classePrincipal));
        		command.addAll(Arrays.asList(argumentos)); // Adiciona os argumentos ao comando

           		ProcessBuilder processBuilder2 = new ProcessBuilder(comando, "-cp", ".", classePrincipal);
        		List<String> command2 = new ArrayList<>(Arrays.asList(comando, "-cp", ".", classePrincipal));
        		command.addAll(Arrays.asList(argumentos2));
        		
        		processBuilder.command(command);
        		processBuilder2.command(command2);

        		processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        		processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        		processBuilder2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        		processBuilder2.redirectError(ProcessBuilder.Redirect.INHERIT);
        		// Inicie o processo
        		Process processo = processBuilder.start();
        		Process processo2 = processBuilder2.start();

        		// Aguarde o término do processo
        		int status = processo.waitFor();
        		int status2 = processo2.waitFor();

                // Imprima o status de saída
                System.out.println("Processo finalizado com status: " + status);
                System.out.println("Processo finalizado com status: " + status2);

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
			System.out.println("qualquercosia");
        } else {
            System.out.println("Chave não encontrada. Erro: Chave não existe nos vizinhos.");
        }
    }
}
