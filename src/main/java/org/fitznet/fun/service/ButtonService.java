package org.fitznet.fun.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ButtonService {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // Add a connected client
    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    // Remove a disconnected client
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    // Broadcast a message to all connected clients
    public void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                System.err.println("Error broadcasting message: " + e.getMessage());
            }
        }
    }

    // Persist an event or state (optional)
    public void logEvent(String deviceId, String eventType) {
        // Save to a database (e.g., MongoDB)
        System.out.printf("Logging event - Device: %s, Event: %s%n", deviceId, eventType);
    }
}
