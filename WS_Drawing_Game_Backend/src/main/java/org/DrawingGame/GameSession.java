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

    /**
     * reads file and adds the words to an array
     * @throws FileNotFoundException if file is not found
     */
    private void readWordsFile() throws FileNotFoundException {
        File file = new File("words.txt");
        Scanner scanner = new Scanner(file);
        while(scanner.hasNext()) {
            words.add(scanner.nextLine());
        }
    }


    /**
     * adds player to the game<br>
     * starts the game if player count is 2
     * @param player player to add
     */
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

    /**
     * removes player from game<br>
     * stops game if player count is below 2
     * @param player player to remove
     */
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

    /**
     * @param ws Websocket
     * @return Player with the given Websocket
     */
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

    /**
     * gets player count
     * @return player count
     */
    public int getGameSize() {
        return players.size();
    }

    /**
     * starts the game and broadcasts the data
     */
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

    /**
     * stops the game, broadcasts leaderboard and starts the next game
     */
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

    /**
     * stops the game<br>
     * (clears game state variables)
     */
    private void stopGame() {
        timeService.shutdown();
        isRunning = false;
        strokes.clear();
        hintPositions.clear();
        guessedPlayers.clear();
        broadcast("clear");
    }

    /**
     * adds the points from the current turn to the players and starts next turn
     */
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

    /**
     * starts next turn, resets variables and broadcasts scoreboard
     */
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

    /**
     * activates timer which broadcasts the time left in the current turn <br>
     * will send hints on 75%, 50% and 25%<br>
     * if time left is 0, it will end the turn
     */
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

    /**
     * gets called if websocket server receives a message<br>
     * parses json and will call the function in the message if given
     * @param ws Player which sent the message
     * @param message Message
     */
    public void handleMessage(WebSocket ws, String message) {
        JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
        if (!jsonMessage.has("type"))
            return;

        String type = jsonMessage.get("type").getAsString();
        Player author = getPlayerFromWebSocket(ws);

        switch (type) {
            case "stroke" -> handleStroke(ws, jsonMessage);
            case "clear" -> sendClear(ws);
            case "get_strokes" -> sendStrokes(ws);
            case "message" -> handleChatMessage(author, jsonMessage);
        }
    }

    /**
     * gets stroke and broadcasts it to other players
     * @param ws Player which sent the message
     * @param jsonMessage Json Message containing the stroke
     */
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

    /**
     * clears strokes and sends clear to other players
     * @param ws Player which sent the message
     */
    private void sendClear(WebSocket ws) {
        if (getPlayerFromWebSocket(ws) == currentTurn) {
            strokes.clear();
            broadcastBut(currentTurn, Map.of("type", "clear"));
        }
    }

    /**
     * sends all strokes that got drawn since last clear or turn change
     * @param ws Player which sent the message
     */
    private void sendStrokes(WebSocket ws) {
        send(ws, Map.of("type", "clear"));
        for (Map<String, Object> stroke : strokes) {
            send(ws, Map.of("type", "stroke", "data", stroke));
        }
    }

    /**
     * broadcasts chat message<br>
     * also if it is the current word, it will mark them as guessed
     * @param author Player which sent the message
     * @param jsonMessage Message
     */
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

    /**
     * will add the player to guessed players and calculate the points they will get <br>
     * broadcasts message containing the guessed player
     * @param author
     */
    private void processCorrectGuess(Player author) {
        broadcast(Map.of("type", "correct", "id", Integer.toString(author.getId()), "username", author.getUsername()));
        guessedPlayers.add(author);

        int points = players.size() > 2 ? (int) (BASE_POINTS * Math.pow(DECAY_FACTOR, guessedPlayers.size() - 1)) : BASE_POINTS;
        playerToPreparedPoints.put(author, points);

        if (checkDone()) {
            endTurn();
        }
    }

    /**
     * checks if all players have guessed
     * @return result
     */
    private boolean checkDone() {
        return guessedPlayers.size() == (players.size() - 1);
    }

    /**
     * sends full game state to the player
     * @param player Player to receive game state
     */
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

    /**
     * sends scoreboard(id, name, points) to the player
     * @param player Player to receive leaderboard
     */
    private void sendScoreboard(Player player) {
        List<Map<String, String>> scoreboardData = new ArrayList<>();
        for(Player p : players) {
            scoreboardData.add(Map.of("id", Integer.toString(p.getId()), "username", p.getUsername(), "points", Integer.toString(p.getPoints())));
        }
        send(player.getWebSocket(), Map.of("type", "scoreboard", "data", scoreboardData));
    }

    /**
     * broadcasts only the type
     * @param type type of the message
     */
    private void broadcast(String type) {
        String message = gson.toJson(Map.of("type", type));
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    /**
     * broadcasts full map
     * @param objMap object map of type and values
     */
    private void broadcast(Map<String, Object> objMap) {
        String message = gson.toJson(objMap);
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }

    /**
     * broadcasts full map to all players but the given player
     * @param player player to not send to
     * @param objMap object map of type and values
     */
    private void broadcastBut(Player player, Map<String, Object> objMap) {
        String message = gson.toJson(objMap);
        for(Player p : players) {
            if(p != player)
                p.getWebSocket().send(message);
        }
    }

    /**
     * send to player
     * @param ws websocket of player to send to
     * @param objMap object map of type and values
     */
    private void send(WebSocket ws, Map<String, Object> objMap) {
        String message = gson.toJson(objMap);
        ws.send(message);
    }
}
