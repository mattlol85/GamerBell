package org.fitznet.fun.handler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.ButtonEventDto;
import org.fitznet.fun.service.ButtonService;
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
 */
@Component
@Slf4j
public class ButtonWebSocketHandler extends TextWebSocketHandler {

    private final ButtonService buttonService;

    /**
     * Constructs a new ButtonWebSocketHandler with the required service.
     *
     * @param buttonService service for managing sessions and broadcasting messages
     */
    public ButtonWebSocketHandler(ButtonService buttonService) {
        this.buttonService = buttonService;
    }

    /**
     * Called after a new WebSocket connection is established.
     *
     * @param session the newly established WebSocket session
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        buttonService.addSession(session);
        log.info("Client connected: {}", session.getId());
    }

    /**
     * Called after a WebSocket connection is closed.
     *
     * @param session the closed WebSocket session
     * @param status  the close status
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        buttonService.removeSession(session);
        log.info("Client disconnected: {}", session.getId());
    }

    /**
     * Handles incoming text messages from WebSocket clients.
     *
     * @param session the WebSocket session
     * @param message the received text message
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("Received message from client {}: {}", session.getId(), message.getPayload());
        try {
            ButtonEventDto event = OBJECT_MAPPER.readValue(message.getPayload(), ButtonEventDto.class);
            log.info("Parsed message: {}", event);

            if (PRESSED.equals(event.getButtonEvent()) || RELEASED.equals(event.getButtonEvent())) {
                log.info("Broadcasting message to connected clients: {}", event);
                String responseJson = OBJECT_MAPPER.writeValueAsString(event);
                buttonService.broadcastMessage(responseJson);
            }

        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage());
        }
    }
}