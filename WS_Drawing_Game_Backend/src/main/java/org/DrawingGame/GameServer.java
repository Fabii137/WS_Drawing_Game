package org.DrawingGame;

import com.google.gson.Gson;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameServer extends WebSocketServer {
    private final int MAX_SIZE = 10;
    private final int port;
    private final String host;
    private final List<GameSession> gameSessions = new ArrayList<>();
    private final Map<WebSocket, GameSession> playerToGameSession = new HashMap<>();

    public GameServer(String host, int port) {
        super(new InetSocketAddress(host, port));
        this.host = host;
        this.port = port;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        System.out.println("Player connected: " + webSocket.getRemoteSocketAddress());
        String queryString = clientHandshake.getResourceDescriptor();

        int idx = queryString.lastIndexOf("=");
        if(idx == -1) {
            webSocket.close();
        }
        String username = queryString.substring(idx+1);

        //TODO: find a solution for multiple name problem (create lobby?)
        GameSession availableSession = null;
        for(GameSession session : gameSessions) {
            if(session.getGameSize() < MAX_SIZE && !session.doesNameExist(username)) {
                availableSession = session;
            }
        }

        if(availableSession == null) {
            availableSession = new GameSession();
            gameSessions.add(availableSession);
        }

        Player newPlayer = new Player(webSocket, username);
        availableSession.addPlayer(newPlayer);
        playerToGameSession.put(newPlayer.getWebSocket(), availableSession);
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        System.out.println("Player disconnected: " + webSocket.getRemoteSocketAddress());
        if(playerToGameSession.containsKey(webSocket)) {
            GameSession session = playerToGameSession.get(webSocket);
            session.deletePlayer(webSocket);
            playerToGameSession.remove(webSocket);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        GameSession game = playerToGameSession.get(webSocket);
        if(game != null) {
            game.handleMessage(webSocket, s);
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        System.out.println("An Error occurred: ");
        e.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started on " + host + ":" + port);
    }
}
