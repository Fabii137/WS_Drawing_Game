package org.DrawingGame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class GameSession {
    private Gson gson;
    Random rand = new Random();
    private List<Player> players;
    private String word = null;
    private Player turn;
    private int playerIdx = 0;
    private String lastCanvasState = null;
    private List<String> words;

    public GameSession() {
        words = new ArrayList<>();
        gson = new Gson();
        players = new ArrayList<>();
        try {
            readWordsFile("words.txt");
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }

    }

    //TODO: tab switches still clear canvas!!!!



    public void addPlayer(Player player) {
        players.add(player);

        if(players.size() > 1) {
            turn = players.getFirst();
            word = words.get(rand.nextInt(words.size()));
            broadcast(gson.toJson(Map.of("type", "start", "turn", turn.getUsername())));
            turn.getWebSocket().send(gson.toJson(Map.of("type", "word", "data", word)));
        } else {
            broadcast(gson.toJson(Map.of("type", "wait")));
        }

        if (lastCanvasState != null) {
            player.getWebSocket().send(gson.toJson(Map.of("type", "canvas", "data", lastCanvasState)));
        }
    }

    public void deletePlayer(WebSocket player) {
        Player playerToRemove = getPlayerFromWebSocket(player);
        if(playerToRemove == null)
            System.out.println("Tried to delete player null");

        players.remove(playerToRemove);

        if(playerToRemove == turn) {
            broadcast(gson.toJson(Map.of("type", "clear")));
            if(players.size() >= 2) {
                playerIdx = (playerIdx + 1) % players.size();
                turn = players.get(playerIdx);
                word = words.get(rand.nextInt(words.size()));
                broadcast(gson.toJson(Map.of("type", "start", "turn", turn.getUsername())));
                broadcast(gson.toJson(Map.of("type", "word", "data", word)));
            } else {
                broadcast(gson.toJson(Map.of("type", "wait")));
            }
        }
        if(playerToRemove != null)
            players.remove(playerToRemove);
    }

    public int getGameSize() {
        return players.size();
    }

    public void handleMessage(WebSocket ws, String message) {
        JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
        if(jsonMessage.has("type") && "canvas".equals(jsonMessage.get("type").getAsString())) {
            if(getPlayerFromWebSocket(ws) == turn) {
                String imgData = jsonMessage.get("data").getAsString();
                lastCanvasState = imgData;
                broadcast(gson.toJson(Map.of("type", "canvas", "data", imgData)));
            }
        }
        if(jsonMessage.has("type") && "get_canvas".equals(jsonMessage.get("type").getAsString())) {
            ws.send(gson.toJson(Map.of("type", "canvas", "data", lastCanvasState)));
        }
    }

    private void broadcast(String message) {
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    private void readWordsFile(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        Scanner scanner = new Scanner(file);
        while(scanner.hasNext()) {
            words.add(scanner.nextLine());
        }
    }

    private Player getPlayerFromWebSocket(WebSocket ws) {
        Player player = null;
        for(Player p : players)  {
            if(p.getWebSocket().equals(ws)) {
                player = p;
                break;
            }
        }
        return player;
    }
}
