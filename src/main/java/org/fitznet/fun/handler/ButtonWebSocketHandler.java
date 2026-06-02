package org.fitznet.fun.handler;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.ButtonEventDto;
import org.fitznet.fun.service.ButtonService;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static org.fitznet.fun.dto.ButtonEvent.PRESSED;
import static org.fitznet.fun.dto.ButtonEvent.RELEASED;
import static org.fitznet.fun.utils.JsonUtils.OBJECT_MAPPER;

/**
 * WebSocket handler for processing button events from ESP32 devices.
 * Provides extensive logging with MDC context for tracing WebSocket connections.
 */
@Component
@Slf4j
public class ButtonWebSocketHandler extends TextWebSocketHandler {

    private static final String MDC_SESSION_ID = "sessionId";
    private static final String MDC_DEVICE_ID = "deviceId";
    private static final String MDC_CLIENT_IP = "clientIp";

    private final ButtonService buttonService;
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a new ButtonWebSocketHandler with the required service.
     *
     * @param buttonService service for managing sessions and broadcasting messages
     * @param meterRegistry Micrometer registry for button event metrics
     */
    public ButtonWebSocketHandler(ButtonService buttonService, MeterRegistry meterRegistry) {
        this.buttonService = buttonService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Called after a new WebSocket connection is established.
     *
     * @param session the newly established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        try {
            setupSessionMDC(session);

            buttonService.addSession(session);

            log.info("WebSocket connection established - sessionId={}, remoteAddress={}, uri={}, totalSessions={}",
                    getSessionId(session),
                    session.getRemoteAddress(),
                    session.getUri(),
                    buttonService.getSessionCount());

            log.debug("WebSocket session details - handshakeHeaders={}, attributes={}",
                    session.getHandshakeHeaders(),
                    session.getAttributes());

        } finally {
            clearSessionMDC();
        }
    }

    /**
     * Called after a WebSocket connection is closed.
     *
     * @param session the closed WebSocket session
     * @param status  the close status
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        try {
            setupSessionMDC(session);

            buttonService.removeSession(session);

            log.info("WebSocket connection closed - sessionId={}, closeStatus={}, closeReason={}, remainingSessions={}",
                    getSessionId(session),
                    status.getCode(),
                    status.getReason() != null ? status.getReason() : "none",
                    buttonService.getSessionCount());

            if (status.getCode() != CloseStatus.NORMAL.getCode()) {
                log.warn("Abnormal WebSocket closure - sessionId={}, statusCode={}, reason={}",
                        getSessionId(session),
                        status.getCode(),
                        status.getReason());
            }

        } finally {
            clearSessionMDC();
        }
    }

    /**
     * Handles incoming text messages from WebSocket clients.
     *
     * @param session the WebSocket session
     * @param message the received text message
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        long startTime = System.currentTimeMillis();
        String payload = message.getPayload();

        try {
            setupSessionMDC(session);

            log.debug("Received WebSocket message - sessionId={}, payloadLength={}, payload={}",
                    getSessionId(session),
                    payload.length(),
                    truncatePayload(payload));

            ButtonEventDto event = OBJECT_MAPPER.readValue(payload, ButtonEventDto.class);
            String eventName = event.getButtonEvent().name().toLowerCase();

            MDC.put(MDC_DEVICE_ID, nullSafe(event.getDeviceId()));

            log.info("Parsed button event - deviceId={}, eventType={}, firmwareVersion={}",
                    nullSafe(event.getDeviceId()),
                    event.getButtonEvent(),
                    nullSafe(event.getFirmwareVersion()));

            if (PRESSED.equals(event.getButtonEvent()) || RELEASED.equals(event.getButtonEvent())) {
                meterRegistry.counter("gamerbell.button.events.total", "event", eventName).increment();
                String responseJson = OBJECT_MAPPER.writeValueAsString(event);

                log.info("Broadcasting button event to clients - eventType={}, deviceId={}, connectedClients={}",
                        event.getButtonEvent(),
                        nullSafe(event.getDeviceId()),
                        buttonService.getSessionCount());

                buttonService.broadcastMessage(responseJson);

                long duration = System.currentTimeMillis() - startTime;
                log.debug("Message processing completed - processingTimeMs={}", duration);
            } else {
                meterRegistry.counter("gamerbell.button.events.total", "event", eventName).increment();
                log.warn("Ignoring unknown button event type - eventType={}, deviceId={}",
                        event.getButtonEvent(),
                        nullSafe(event.getDeviceId()));
            }

        } catch (Exception e) {
            meterRegistry.counter("gamerbell.button.events.total", "event", "invalid").increment();
            meterRegistry.counter("gamerbell.websocket.errors.total", "type", "message_processing").increment();
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error handling WebSocket message - sessionId={}, error={}, errorType={}, processingTimeMs={}",
                    getSessionId(session),
                    e.getMessage(),
                    e.getClass().getSimpleName(),
                    duration,
                    e);
        } finally {
            clearSessionMDC();
        }
    }

    /**
     * Handles transport errors on the WebSocket connection.
     *
     * @param session   the WebSocket session
     * @param exception the transport exception
     */
    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        try {
            setupSessionMDC(session);
            meterRegistry.counter("gamerbell.websocket.errors.total", "type", "transport").increment();

            log.error("WebSocket transport error - sessionId={}, error={}, errorType={}",
                    getSessionId(session),
                    exception.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception);

        } finally {
            clearSessionMDC();
        }
    }

    /**
     * Sets up MDC context for the current WebSocket session.
     *
     * @param session the WebSocket session
     */
    private void setupSessionMDC(WebSocketSession session) {
        MDC.put(MDC_SESSION_ID, getSessionId(session));
        if (session.getRemoteAddress() != null) {
            MDC.put(MDC_CLIENT_IP, session.getRemoteAddress().toString());
        }
    }

    /**
     * Clears MDC context for the current WebSocket session.
     */
    private void clearSessionMDC() {
        MDC.remove(MDC_SESSION_ID);
        MDC.remove(MDC_DEVICE_ID);
        MDC.remove(MDC_CLIENT_IP);
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
     * Returns the value or "unknown" if null.
     *
     * @param value the value to check
     * @return the value or "unknown"
     */
    private String nullSafe(String value) {
        return value != null ? value : "unknown";
    }

    /**
     * Truncates payload for logging to avoid excessive log size.
     *
     * @param payload the payload to truncate
     * @return truncated payload string
     */
    private String truncatePayload(String payload) {
        int maxLength = 500;
        if (payload == null) {
            return "null";
        }
        if (payload.length() <= maxLength) {
            return payload;
        }
        return payload.substring(0, maxLength) + "...[truncated]";
    }
}