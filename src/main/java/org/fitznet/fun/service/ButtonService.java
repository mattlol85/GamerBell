package org.fitznet.fun.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing WebSocket sessions and broadcasting messages to connected clients.
 * Provides comprehensive logging for session lifecycle and message delivery.
 */
@Slf4j
@Service
public class ButtonService {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicLong totalSessionsCreated = new AtomicLong(0);
    private final AtomicLong totalMessagesBroadcast = new AtomicLong(0);
    private final AtomicLong totalBroadcastFailures = new AtomicLong(0);

    /**
     * Adds a WebSocket session to the active sessions list.
     *
     * @param session the WebSocket session to add
     */
    public void addSession(WebSocketSession session) {
        sessions.add(session);
        long totalCreated = totalSessionsCreated.incrementAndGet();

        log.info("Session added - sessionId={}, activeSessions={}, totalSessionsCreated={}",
                getSessionId(session),
                sessions.size(),
                totalCreated);

        log.debug("Session pool state after add - activeSessions={}, sessionIds={}",
                sessions.size(),
                getSessionIds());
    }

    /**
     * Removes a WebSocket session from the active sessions list.
     *
     * @param session the WebSocket session to remove
     */
    public void removeSession(WebSocketSession session) {
        boolean removed = sessions.remove(session);

        if (removed) {
            log.info("Session removed - sessionId={}, activeSessions={}",
                    getSessionId(session),
                    sessions.size());
        } else {
            log.warn("Session removal failed - sessionId={} not found in pool, activeSessions={}",
                    getSessionId(session),
                    sessions.size());
        }

        log.debug("Session pool state after remove - activeSessions={}, sessionIds={}",
                sessions.size(),
                getSessionIds());
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
        int totalSessions = sessions.size();
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        long broadcastNumber = totalMessagesBroadcast.incrementAndGet();

        log.debug("Starting broadcast #{} - targetSessions={}, messageLength={}",
                broadcastNumber,
                totalSessions,
                message.length());

        long startTime = System.currentTimeMillis();

        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    successCount++;

                    log.trace("Message sent to session - sessionId={}, broadcastNumber={}",
                            getSessionId(session),
                            broadcastNumber);
                } else {
                    skippedCount++;
                    log.debug("Skipped closed session - sessionId={}", getSessionId(session));
                }
            } catch (IOException e) {
                failureCount++;
                totalBroadcastFailures.incrementAndGet();

                log.error("Failed to send message to session - sessionId={}, error={}, errorType={}",
                        getSessionId(session),
                        e.getMessage(),
                        e.getClass().getSimpleName());
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        log.info("Broadcast completed - broadcastNumber={}, success={}, failures={}, skipped={}, durationMs={}",
                broadcastNumber,
                successCount,
                failureCount,
                skippedCount,
                duration);

        if (failureCount > 0) {
            log.warn("Broadcast had failures - broadcastNumber={}, totalFailures={}, lifetimeFailures={}",
                    broadcastNumber,
                    failureCount,
                    totalBroadcastFailures.get());
        }
    }

    /**
     * Logs a button event for a device.
     *
     * @param deviceId  the identifier of the device
     * @param eventType the type of event (e.g., PRESSED, RELEASED)
     */
    public void logEvent(String deviceId, String eventType) {
        log.info("Button event logged - deviceId={}, eventType={}, timestamp={}",
                deviceId,
                eventType,
                System.currentTimeMillis());
    }

    /**
     * Returns service statistics for monitoring.
     *
     * @return string containing service statistics
     */
    public String getServiceStats() {
        return String.format("activeSessions=%d, totalCreated=%d, totalBroadcasts=%d, totalFailures=%d",
                sessions.size(),
                totalSessionsCreated.get(),
                totalMessagesBroadcast.get(),
                totalBroadcastFailures.get());
    }

    /**
     * Gets the session ID safely, returning "unknown" if session or ID is null.
     *
     * @param session the WebSocket session
     * @return the session ID or "unknown"
     */
    private String getSessionId(WebSocketSession session) {
        if (session == null) {
            return "unknown";
        }
        String id = session.getId();
        return id != null ? id : "unknown";
    }

    /**
     * Gets comma-separated list of active session IDs for debugging.
     *
     * @return session IDs as a string
     */
    private String getSessionIds() {
        return sessions.stream()
                .map(this::getSessionId)
                .reduce((a, b) -> a + "," + b)
                .orElse("none");
    }
}
