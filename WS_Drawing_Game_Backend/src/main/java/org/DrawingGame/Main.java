package org.DrawingGame;

public class Main {
    public static void main(String[] args) {
        GameServer server = new GameServer("0.0.0.0", 3000);
        server.start();
    }
}