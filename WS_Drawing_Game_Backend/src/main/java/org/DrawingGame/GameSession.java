package org.DrawingGame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameSession {
    private final int ROUND_DURATION = 300;
    private final int ROUND_COUNT = 5;
    private final Gson gson;
    private final Random rand = new Random();
    private final List<Player> players;
    private final List<Player> guessedPlayers;
    private final Map<Player, Integer> playerToPreparedPoints;
    private final List<String> words;
    private String word = null;
    private Player currentTurn;
    private int playerIdx = 0;
    private String lastCanvasState = null;
    private boolean isRunning = false;
    private int timeLeft;
    private int currentRound = 1;
    private int turnsInRound = 0;
    private ScheduledExecutorService timeService;


    public GameSession() {
        words = new ArrayList<>();
        gson = new Gson();
        players = new ArrayList<>();
        guessedPlayers = new ArrayList<>();
        playerToPreparedPoints = new HashMap<>();
        timeService = Executors.newScheduledThreadPool(1);
        timeLeft = ROUND_DURATION;
        try {
            readWordsFile();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }

    }

    public void addPlayer(Player player) {
        int playerID = 0;
        while(doesIDExist(playerID)) {
            playerID++;
        }
        player.setId(playerID);
        player.getWebSocket().send(gson.toJson(Map.of("type", "id", "data", Integer.toString(player.getId()))));

        players.add(player);

        if(players.size() >= 2) {
            if (!isRunning) {
                startGame();
            } else {
                sendFullGameData(player);
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
        if(playerToRemove == null) {
            System.out.println("Tried to delete player null");
            return;
        }
        boolean wasCurrentTurn = playerToRemove == currentTurn;
        players.remove(playerToRemove);

        if(wasCurrentTurn) {
            broadcast(gson.toJson(Map.of("type", "clear")));
            if(players.size() >= 2) {
                nextTurn();
            } else {
                timeService.shutdown();
                isRunning = false;
                lastCanvasState = "";
                broadcast(gson.toJson(Map.of("type", "clear")));
                broadcast(gson.toJson(Map.of("type", "wait")));
                
            }
        }

        if(players.size() == 1) {
            broadcast(gson.toJson(Map.of("type", "wait")));
        }

    }

    public int getGameSize() {
        return players.size();
    }

    private boolean doesIDExist(int id) {
        for(Player p : players) {
            if (p.getId() == id)
                return true;
        }
        return false;
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
                    guessedPlayers.add(author);

                    int basePoints = 1000;
                    int guessPosition = guessedPlayers.size();
                    int maxPlayers = players.size();
                    int points = 0;
                    if (maxPlayers > 2) {
                        double decayFactor = 0.7;
                        points = (int)(basePoints * Math.pow(decayFactor, guessPosition - 1));
                    } else if (maxPlayers == 2) {
                        points = basePoints;
                    }
                    playerToPreparedPoints.put(author, points);
                    ws.send(gson.toJson(Map.of("type", "points", "data", Integer.toString(author.getPoints()))));

                    if(checkDone()) {
                        if(currentRound == ROUND_COUNT) {
                            isRunning = false;
                            endTurn();
                            resetGame();
                            return;
                        }
                        endTurn();
                    }
                } else {
                    broadcastBut(author, gson.toJson(Map.of("type", "message", "data", msg, "username", author.getUsername())));
                }
            }
        }
    }

    private void resetGame() {
        List<Player> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparing(Player::getPoints));

        for(int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            String display = String.format("%d → %s: %d", sortedPlayers.size() - i, player.getUsername(), player.getPoints());
            broadcast(gson.toJson(Map.of("type", "message", "data", display)));
            player.resetPoints();
        }
        timeService.shutdown();
        startGame();
    }

    private void broadcast(String message) {
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    private void broadcastPoints() {
        for(Player p : players) {
            p.getWebSocket().send(gson.toJson(Map.of("type", "points", "data", Integer.toString(p.getPoints()))));
        }
    }

    private void broadcastBut(Player player, String message) {
        for(Player p : players) {
            if(p != player)
                p.getWebSocket().send(message);
        }
    }

    private void sendFullGameData(Player player) {
        WebSocket ws = player.getWebSocket();
        ws.send(gson.toJson(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()))));
        ws.send(gson.toJson(Map.of("type", "points", "data", "0")));
        if(lastCanvasState != null)
            ws.send(gson.toJson(Map.of("type", "canvas", "data", lastCanvasState)));

    }

    private void readWordsFile() throws FileNotFoundException {
//        File file = new File("/home/words.txt");
        File file = new File("words.txt");
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

    private void startGame() {
        currentTurn = players.getFirst();
        playerIdx = 0;
        currentRound = 1;
        word = words.get(rand.nextInt(words.size()));
        broadcast(gson.toJson(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()))));
        broadcastPoints();
        activateTimeService();

        isRunning = true;
        currentTurn.getWebSocket().send(gson.toJson(Map.of("type", "word", "data", word)));
    }

    private void endTurn() {
        int totalPoints = 0;
        for(Player p : playerToPreparedPoints.keySet()) {
            int preparedPoints = playerToPreparedPoints.get(p);
            p.addPoints(preparedPoints);
            totalPoints += preparedPoints;
        }
        playerToPreparedPoints.clear();
        int drawerPoints = (int)(totalPoints * 0.5);
        currentTurn.addPoints(drawerPoints);
        broadcastPoints();

        if(isRunning)
            nextTurn();
    }

    private void nextTurn() {
        timeService.shutdown();

        turnsInRound++;
        if (turnsInRound >= players.size()) {
            currentRound++;
            turnsInRound = 0;
        }

        playerIdx = (playerIdx + 1) % players.size();
        currentTurn = players.get(playerIdx);
        word = words.get(rand.nextInt(words.size()));
        lastCanvasState = null;
        guessedPlayers.clear();
        broadcast(gson.toJson(Map.of("type", "clear")));
        broadcast(gson.toJson(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()))));
        currentTurn.getWebSocket().send(gson.toJson(Map.of("type", "word", "data", word)));

        activateTimeService();
    }

    private void activateTimeService() {
        timeLeft = ROUND_DURATION;
        timeService = Executors.newScheduledThreadPool(1);
        Runnable timeRunnable = () -> {
            broadcast(gson.toJson(Map.of("type", "time", "data", timeLeft)));
            timeLeft--;
            if(timeLeft < 0)
                endTurn();
        };
        timeService.scheduleAtFixedRate(timeRunnable, 0, 1, TimeUnit.SECONDS);
    }
}
