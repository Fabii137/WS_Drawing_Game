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
    private final List<Integer> hintPositions = new ArrayList<>();
    private final List<Map<String, Object>> strokes = new ArrayList<>();

    private int maxPlayerID = 0;
    private String word;
    private Player currentTurn;
    private int playerIdx = 0;
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
    }

    public void deletePlayer(WebSocket player) {
        Player playerToRemove = getPlayerFromWebSocket(player);
        if (playerToRemove == null)
            return;

        boolean wasCurrentTurn = playerToRemove == currentTurn;
        players.remove(playerToRemove);

        if (wasCurrentTurn || players.size() < 2) {
            broadcast("clear");
            if (players.size() >= 2) {
                nextTurn();
            } else {
                stopGame();
                broadcast("wait");
            }
        } else {
            for(Player p : players) {
                sendScoreboard(p);
            }
        }


    }

    private void stopGame() {
        timeService.shutdown();
        isRunning = false;
        strokes.clear();
        hintPositions.clear();
        guessedPlayers.clear();
        broadcast("clear");
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
            case "stroke" -> handleStroke(ws, jsonMessage);
            case "clear" -> sendClear(ws);
            case "get_strokes" -> sendStrokes(ws);
            case "message" -> handleChatMessage(author, jsonMessage);
        }
    }

    private void handleStroke(WebSocket ws, JsonObject jsonMessage) {
        if(getPlayerFromWebSocket(ws) != currentTurn)
            return;

        JsonObject data = jsonMessage.getAsJsonObject("data");

        Map<String, Object> stroke = new HashMap<>();
        stroke.put("x1", data.get("x1").getAsDouble());
        stroke.put("y1", data.get("y1").getAsDouble());
        stroke.put("x2", data.get("x2").getAsDouble());
        stroke.put("y2", data.get("y2").getAsDouble());
        stroke.put("color", data.get("color").getAsString());
        stroke.put("width", data.get("width").getAsInt());
        strokes.add(stroke);

        broadcastBut(currentTurn, Map.of("type", "stroke", "data", stroke));
    }

    private void sendClear(WebSocket ws) {
        if (getPlayerFromWebSocket(ws) == currentTurn) {
            strokes.clear();
            broadcastBut(currentTurn, Map.of("type", "clear"));
        }
    }

    private void sendStrokes(WebSocket ws) {
        send(ws, Map.of("type", "clear"));
        for (Map<String, Object> stroke : strokes) {
            send(ws, Map.of("type", "stroke", "data", stroke));
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
            endTurn();
        }
    }


    private void resetGame() {
        stopGame();

        List<Player> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparing(Player::getPoints));

        for(int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            String display = String.format("%d → %s: %d", sortedPlayers.size() - i, player.getUsername(), player.getPoints());
            broadcast(Map.of("type", "message", "data", display));
            player.resetPoints();
        }
        startGame();
    }

    private void sendFullGameData(Player player) {
        WebSocket ws = player.getWebSocket();
        send(ws, Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()), "length", Integer.toString(word.length()), "round", currentRound, "maxRound", ROUND_COUNT));

        for(Player p : players) {
            sendScoreboard(p);
        }

        List<Map<String, String>> guessedPlayersData = new ArrayList<>();
        for (Player p : guessedPlayers) {
            guessedPlayersData.add(Map.of("id", Integer.toString(p.getId()), "username", p.getUsername()));
        }
        send(ws, Map.of("type", "guessed_players", "data", guessedPlayersData));

        for (Map<String, Object> stroke : strokes) {
            send(ws, Map.of("type", "stroke", "data", stroke));
        }

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
        turnsInRound = 0;
        word = words.get(rand.nextInt(words.size()));
        broadcast(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()), "length", Integer.toString(word.length()), "round", currentRound, "maxRound", ROUND_COUNT));
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

        if(currentRound > ROUND_COUNT) {
            resetGame();
            return;
        }

        playerIdx = (playerIdx + 1) % players.size();
        currentTurn = players.get(playerIdx);
        word = words.get(rand.nextInt(words.size()));
        strokes.clear();
        guessedPlayers.clear();
        hintPositions.clear();
        broadcast("clear");
        broadcast(Map.of("type", "start", "name", currentTurn.getUsername(), "id", Integer.toString(currentTurn.getId()), "length", Integer.toString(word.length()), "round", currentRound, "maxRound", ROUND_COUNT));
        send(currentTurn.getWebSocket(), Map.of("type", "word", "data", word));

        for(Player p : players) {
            sendScoreboard(p);
        }

        activateTimeService();
    }

    private void activateTimeService() {
        timeLeft = ROUND_DURATION;
        timeService = Executors.newScheduledThreadPool(1);

        int firstHint = (int) (ROUND_DURATION * 0.75);
        int secondHint = (int) (ROUND_DURATION * 0.50);
        int finalHint = (int) (ROUND_DURATION * 0.25);

        Runnable timeRunnable = () -> {
            broadcast(Map.of("type", "time", "data", Integer.toString(timeLeft)));

            if(hintPositions.size()-1 != word.length() && (timeLeft == firstHint || timeLeft == secondHint || timeLeft == finalHint)) {
                int idx;
                do {
                    idx = rand.nextInt(0, word.length());
                } while (hintPositions.contains(idx));
                hintPositions.add(idx);
                broadcastBut(currentTurn, Map.of("type" ,"hint", "position", Integer.toString(idx), "letter", Character.toString(word.charAt(idx))));
            }
            if(timeLeft == 0)
                endTurn();
            timeLeft--;
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

    private void broadcastBut(Player player, Map<String, Object> objMap) {
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
