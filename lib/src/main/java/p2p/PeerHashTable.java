package p2p;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeerHashTable {
    private Map<String, Map<Integer, byte[]>> dataStore;
    private Map<String, String> pieceToPeerMapping; // Mapeamento de pedaço para peer

    public PeerHashTable() {
        dataStore = new HashMap<>();
        pieceToPeerMapping = new HashMap<>();
        
    }

    public void put(String peerId, int pieceIndex, String hash) {
        PeerHashTable peerHashTable = loadFromJson();

        if (peerHashTable.containsData(peerId, hash)) {
            // O dado com o mesmo hash já foi adicionado por este peer, não faça nada
            return;
        }

        // Converte o hash em um array de bytes
        byte[] hashBytes = hash.getBytes();

        // Armazena o peerId, pieceIndex e hash na DHT
        dataStore.computeIfAbsent(peerId, k -> new HashMap<>()).put(pieceIndex, hashBytes);

        // Salve a tabela atualizada em formato JSON
        saveToJson(peerHashTable);
    }

    public String getHash(String peerId, int pieceIndex) {
        // Verifica se o peerId está na tabela de hash
        if (dataStore.containsKey(peerId)) {
            Map<Integer, byte[]> peerData = dataStore.get(peerId);
            // Verifica se o pieceIndex está na tabela de hash do peerId
            if (peerData.containsKey(pieceIndex)) {
                // Recupera o hash associado ao pieceIndex
                byte[] hashBytes = peerData.get(pieceIndex);
                return new String(hashBytes);
            }
        }
        return null; // Retorna null se o peerId ou pieceIndex não forem encontrados
    }

    public List<String> get(int pieceIndex) {
        List<String> peersWithPiece = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, byte[]>> entry : dataStore.entrySet()) {
            Map<Integer, byte[]> peerData = entry.getValue();
            if (peerData != null && peerData.containsKey(pieceIndex)) {
                peersWithPiece.add(entry.getKey()); // Adicione o ID do peer à lista
            }
        }

        return peersWithPiece;
    }


    public String getPeerForPiece(int pieceIndex) {
        // Recupera o peer associado ao pedaço de arquivo usando o mapeamento
        String hash = calculateSHA256(String.valueOf(pieceIndex));
        return pieceToPeerMapping.get(hash);
    }

    // Função para calcular o hash SHA-256
    private String calculateSHA256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data.getBytes());

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
    public void saveToJson(PeerHashTable peerHashTable) {
        try (Writer writer = new FileWriter("peer_data.json")) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(peerHashTable, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    } 
        public static PeerHashTable loadFromJson() {
            try (Reader reader = new FileReader("peer_data.json")) {
                Gson gson = new Gson();
                return gson.fromJson(reader, PeerHashTable.class);
            } catch (IOException e) {
                // Em caso de erro ao carregar o arquivo JSON, crie uma nova tabela vazia
                return new PeerHashTable();
            }
        }
        
        public boolean containsData(String peerId, String hash) {
            // Verifique se o dado com o mesmo peerId e hash existe na tabela
            Map<Integer, byte[]> peerData = dataStore.get(peerId);

            if (peerData != null) {
                for (Integer pieceIndex : peerData.keySet()) {
                    String storedHash = calculateSHA256(new String(peerData.get(pieceIndex)));
                    if (storedHash.equals(hash)) {
                        return true;
                    }
                }
            }

            return false;
        }

}
   