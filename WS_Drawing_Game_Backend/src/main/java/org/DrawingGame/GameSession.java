package org.DrawingGame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class GameSession {
    private final Gson gson;
    private final Random rand = new Random();
    private final List<Player> players;
    private String word = null;
    private Player currentTurn;
    private int playerIdx = 0;
    private String lastCanvasState = null;
    private final List<String> words;
    private boolean isRunning = false;
    private final List<Player> guessedPlayers;

    public GameSession() {
        words = new ArrayList<>();
        gson = new Gson();
        players = new ArrayList<>();
        guessedPlayers = new ArrayList<>();
        try {
            readWordsFile();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }

    }

    public void addPlayer(Player player) {
        players.add(player);

        if(players.size() > 1) {
            if (!isRunning) {
                currentTurn = players.getFirst();
                playerIdx = 0;
                word = words.get(rand.nextInt(words.size()));
                broadcast(gson.toJson(Map.of("type", "start", "turn", currentTurn.getUsername())));
                isRunning = true;
                currentTurn.getWebSocket().send(gson.toJson(Map.of("type", "word", "data", word)));
            } else {
                player.getWebSocket().send(gson.toJson(Map.of("type", "start", "turn", currentTurn.getUsername())));
            }
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

        if(playerToRemove == currentTurn) {
            broadcast(gson.toJson(Map.of("type", "clear")));
            if(players.size() >= 2) {
                nextTurn();
            } else {
                isRunning = false;
                broadcast(gson.toJson(Map.of("type", "wait")));
            }
        }
        if(playerToRemove != null)
            players.remove(playerToRemove);
    }

    public int getGameSize() {
        return players.size();
    }
    public boolean doesNameExist(String username) {
        for(Player p : players) {
            if(p.getUsername().equals(username)) {
                return true;
            }
        }

        return false;
    }

    public void handleMessage(WebSocket ws, String message) {
        JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
        if(!jsonMessage.has("type"))
            return;

        String type = jsonMessage.get("type").getAsString();

        if("canvas".equals(type)) {
            if(getPlayerFromWebSocket(ws) == currentTurn) {
                String imgData = jsonMessage.get("data").getAsString();
                lastCanvasState = imgData;
                broadcastBut(currentTurn, gson.toJson(Map.of("type", "canvas", "data", imgData)));
            }
        }
        if("get_canvas".equals(type)) {
            if(lastCanvasState != null) {
                ws.send(gson.toJson(Map.of("type", "canvas", "data", lastCanvasState)));
            }
        }

        if("message".equals(type)) {
            if(jsonMessage.has("data")) {
                Player author = getPlayerFromWebSocket(ws);
                String msg = jsonMessage.get("data").getAsString();

                if(msg.equalsIgnoreCase(word) && author != currentTurn && !guessedPlayers.contains(author)) {
                    broadcast(gson.toJson(Map.of("type", "correct", "username", author.getUsername())));
                    int points = 1000 - (10 * guessedPlayers.size());
                    guessedPlayers.add(author);
                    author.addPoints(points);
                    ws.send(gson.toJson(Map.of("type", "points", "data", Integer.toString(author.getPoints()))));
                    if(checkDone()) {
                        endTurn();
                    }
                } else {
                    broadcastBut(author, gson.toJson(Map.of("type", "message", "data", msg, "username", author.getUsername())));
                }
            }
        }
    }

    private void broadcast(String message) {
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    private void broadcastBut(Player player, String message) {
        for(Player p : players) {
            if(p != player)
                p.getWebSocket().send(message);
        }
    }

    private void readWordsFile() throws FileNotFoundException {
        File file = new File("/home/words.txt");
        //File file = new File("words.txt");
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

    private boolean checkDone() {
        return guessedPlayers.size() == (players.size() - 1);
    }

    private void endTurn() {
        int turnPlayerPoints = 1000 - (10 * ((players.size() - 1) - guessedPlayers.size()));
        currentTurn.addPoints(turnPlayerPoints);
        currentTurn.getWebSocket().send(gson.toJson(Map.of("type", "points", "data", Integer.toString(currentTurn.getPoints()))));
        nextTurn();
    }

    private void nextTurn() {
        playerIdx = (playerIdx + 1) % players.size();
        currentTurn = players.get(playerIdx);
        word = words.get(rand.nextInt(words.size()));
        lastCanvasState = null;
        guessedPlayers.clear();
        broadcast(gson.toJson(Map.of("type", "clear")));
        broadcast(gson.toJson(Map.of("type", "start", "turn", currentTurn.getUsername())));
        currentTurn.getWebSocket().send(gson.toJson(Map.of("type", "word", "data", word)));
    }
}
