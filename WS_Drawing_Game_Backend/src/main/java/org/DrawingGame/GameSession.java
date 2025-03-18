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
    private static final int ROUND_DURATION = 300;
    private static final int ROUND_COUNT = 5;
    private static final double DECAY_FACTOR = 0.7;
    private static final int BASE_POINTS = 1000;

    private final Gson gson = new Gson();
    private final Random rand = new Random();
    private final List<Player> players = new ArrayList<>();
    private final List<Player> guessedPlayers = new ArrayList<>();
    private final Map<Player, Integer> playerToPreparedPoints = new HashMap<>();
    private final List<String> words = new ArrayList<>();

    private int maxPlayerID = 0;
    private String word;
    private Player currentTurn;
    private int playerIdx = 0;
    private String lastCanvasState;
    private boolean isRunning = false;
    private int timeLeft;
    private int currentRound = 1;
    private int turnsInRound = 0;
    private ScheduledExecutorService timeService = Executors.newScheduledThreadPool(1);


    public GameSession() {
        timeLeft = ROUND_DURATION;
        try {
            readWordsFile();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
            System.exit(-1);
        }
    }

    public void addPlayer(Player player) {
        WebSocket ws = player.getWebSocket();
        player.setId(maxPlayerID++);
        send(ws, Map.of("type", "id", "data", Integer.toString(player.getId())));
        players.add(player);

        if (players.size() >= 2) {
            if (!isRunning) {
                startGame();
            } else {
                sendFullGameData(player);
            }
        } else {
            broadcast("wait");
        }

        if (lastCanvasState != null) {
            send(ws, Map.of("type", "canvas", "data", lastCanvasState));
        }
    }

    public void deletePlayer(WebSocket player) {
        Player playerToRemove = getPlayerFromWebSocket(player);
        if (playerToRemove == null)
            return;

        boolean wasCurrentTurn = playerToRemove == currentTurn;
        players.remove(playerToRemove);

        if (wasCurrentTurn) {
            broadcast("clear");
            if (players.size() >= 2) {
                nextTurn();
            } else {
                stopGame();
            }
        }

        if (players.size() == 1) {
            broadcast("wait");
        }
    }

    private void stopGame() {
        timeService.shutdown();
        isRunning = false;
        lastCanvasState = "";
        broadcast("clear");
        broadcast("wait");
    }

    public int getGameSize() {
        return players.size();
    }

    public void handleMessage(WebSocket ws, String message) {
        JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
        if (!jsonMessage.has("type")) return;

        String type = jsonMessage.get("type").getAsString();
        Player author = getPlayerFromWebSocket(ws);

        switch (type) {
            case "canvas" -> updateCanvas(ws, jsonMessage);
            case "get_canvas" -> sendCanvas(ws);
            case "message" -> handleChatMessage(author, jsonMessage);
        }
    }

    private void updateCanvas(WebSocket ws, JsonObject jsonMessage) {
        if (getPlayerFromWebSocket(ws) == currentTurn) {
            lastCanvasState = jsonMessage.get("data").getAsString();
            broadcastBut(currentTurn, Map.of("type", "canvas", "data", lastCanvasState));
        }
    }

    private void sendCanvas(WebSocket ws) {
        if (lastCanvasState != null) {
            send(ws, Map.of("type", "canvas", "data", lastCanvasState));
        }
    }

    private void handleChatMessage(Player author, JsonObject jsonMessage) {
        if (jsonMessage.has("data")) {
            String msg = jsonMessage.get("data").getAsString();

            if (msg.equalsIgnoreCase(word) && author != currentTurn && !guessedPlayers.contains(author)) {
                processCorrectGuess(author);
            } else {
                broadcastBut(author, Map.of("type", "message", "data", msg, "username", author.getUsername()));
            }
        }
    }

    private void processCorrectGuess(Player author) {
        broadcast(Map.of("type", "correct", "id", Integer.toString(author.getId()), "username", author.getUsername()));
        guessedPlayers.add(author);

        int points = players.size() > 2 ? (int) (BASE_POINTS * Math.pow(DECAY_FACTOR, guessedPlayers.size() - 1)) : BASE_POINTS;
        playerToPreparedPoints.put(author, points);

        if (checkDone()) {
            if (currentRound == ROUND_COUNT) {
                isRunning = false;
                endTurn();
                resetGame();
            } else {
                endTurn();
            }
        }
    }


    private void resetGame() {
        List<Player> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparing(Player::getPoints));

        for(int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            String display = String.format("%d → %s: %d", sortedPlayers.size() - i, player.getUsername(), player.getPoints());
            broadcast(Map.of("type", "message", "data", display));
            player.resetPoints();
        }
        timeService.shutdown();
        startGame();
    }

    private void sendFullGameData(Player player) {
        WebSocket ws = player.getWebSocket();
        send(ws, Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId())));
        send(ws, Map.of("type", "points", "data", "0"));

        for(Player p : players) {
            sendScoreboard(p);
        }

        List<Map<String, String>> guessedPlayersData = new ArrayList<>();
        for (Player p : guessedPlayers) {
            guessedPlayersData.add(Map.of("id", Integer.toString(p.getId()), "username", p.getUsername()));
        }
        send(ws, Map.of("type", "guessed_players", "data", guessedPlayersData));

        if(lastCanvasState != null)
            send(ws, Map.of("type", "canvas", "data", lastCanvasState));

    }

    private void sendScoreboard(Player player) {
        List<Map<String, String>> scoreboardData = new ArrayList<>();
        for(Player p : players) {
            scoreboardData.add(Map.of("id", Integer.toString(p.getId()), "username", p.getUsername(), "points", Integer.toString(p.getPoints())));
        }
        send(player.getWebSocket(), Map.of("type", "scoreboard", "data", scoreboardData));
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
        broadcast(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId())));
        activateTimeService();

        for(Player p : players) {
            sendScoreboard(p);
        }


        isRunning = true;
        send(currentTurn.getWebSocket(), Map.of("type", "word", "data", word));
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
        broadcast("clear");
        broadcast(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId())));
        send(currentTurn.getWebSocket(), Map.of("type", "word", "data", word));

        for(Player p : players) {
            sendScoreboard(p);
        }

        activateTimeService();
    }

    private void activateTimeService() {
        timeLeft = ROUND_DURATION;
        timeService = Executors.newScheduledThreadPool(1);
        Runnable timeRunnable = () -> {
            broadcast(Map.of("type", "time", "data", Integer.toString(timeLeft)));
            timeLeft--;
            if(timeLeft < 0)
                endTurn();
        };
        timeService.scheduleAtFixedRate(timeRunnable, 0, 1, TimeUnit.SECONDS);
    }

    private void broadcast(String type) {
        String message = gson.toJson(Map.of("type", type));
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    private void broadcast(Map<String, Object> objMap) {
        String message = gson.toJson(objMap);
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    private void broadcastBut(Player player, Map<String, String> objMap) {
        String message = gson.toJson(objMap);
        for(Player p : players) {
            if(p != player)
                p.getWebSocket().send(message);
        }
    }

    private void send(WebSocket ws, Map<String, Object> objMap) {
        String message = gson.toJson(objMap);
        ws.send(message);
    }
}
