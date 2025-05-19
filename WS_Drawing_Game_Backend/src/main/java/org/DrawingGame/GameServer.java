package org.DrawingGame;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameServer extends WebSocketServer {
    private final int MAX_SIZE = 8;
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

        Map<String, String> queryParams = parseQueryString(queryString);
        String username = queryParams.get("username");

        if(username == null || username.trim().isEmpty()) {
            webSocket.close();
            return;
        }

        username = URLDecoder.decode(username, StandardCharsets.UTF_8); // converts ascii character from url to utf8

        GameSession availableSession = null;
        for(GameSession session : gameSessions) {
            if(session.getGameSize() < MAX_SIZE) {
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

    /**
     * parses all key-value pairs from the query string into a HashMap
     * @param queryString query string to parse
     * @return HashMap with all key-value pairs in the query string
     */
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        int queryStartIdx = queryString.indexOf('?');
        if(queryStartIdx == -1)
            return params;

        String[] pairs = queryString.substring(queryStartIdx+1).split("&");
        for(String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if(kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }

        return params;
    }
}
