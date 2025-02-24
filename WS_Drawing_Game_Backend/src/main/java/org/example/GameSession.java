package org.example;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class GameSession {
    private Gson gson;
    private List<Player> players;

    public GameSession() {
        gson = new Gson();
        players = new ArrayList<>();
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void deletePlayer(Player player) {
        players.remove(player);
    }

    public int getGameSize() {
        return players.size();
    }

    public void handleMessage(String message) {

    }

    public void broadcast(String message) {
        for(Player p : players) {
            p.getWebSocket().send(message);
        }
    }
}
