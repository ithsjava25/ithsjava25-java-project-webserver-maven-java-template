package org.example;

import org.example.server.TcpServer;

public class App {
    public static void main(String[] args) {
        new TcpServer(8080).start();
    }
}