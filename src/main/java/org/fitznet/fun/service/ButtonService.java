package org.fitznet.fun.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing WebSocket sessions and broadcasting messages to connected clients.
 */
@Slf4j
@Service
public class ButtonService {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    /**
     * Adds a WebSocket session to the active sessions list.
     *
     * @param session the WebSocket session to add
     */
    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * Removes a WebSocket session from the active sessions list.
     *
     * @param session the WebSocket session to remove
     */
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    /**
     * Returns the count of currently active WebSocket sessions.
     *
     * @return the number of active sessions
     */
    public long getSessionCount() {
        return sessions.size();
    }

    /**
     * Broadcasts a message to all open WebSocket sessions.
     *
     * @param message the message to broadcast
     */
    public void broadcastMessage(String message) {
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.error("Error broadcasting message: {}", e.getMessage());
            }
        }
    }

    /**
     * Logs a button event for a device.
     *
     * @param deviceId  the identifier of the device
     * @param eventType the type of event (e.g., PRESSED, RELEASED)
     */
    public void logEvent(String deviceId, String eventType) {
        //save to some db in the future
        log.info("Logging event - Device: {}, Event: {}", deviceId, eventType);
    }
}
