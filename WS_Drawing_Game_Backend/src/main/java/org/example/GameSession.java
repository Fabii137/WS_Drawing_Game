package org.example;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.List;

public class GameSession {
    private Gson gson;
    private List<WebSocket> players;

    public GameSession() {
        gson = new Gson();
        players = new ArrayList<>();
    }

    void addPlayer(WebSocket player) {
        players.add(player);
    }
}
