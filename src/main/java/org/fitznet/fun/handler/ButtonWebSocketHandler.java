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

@Component
@Slf4j
public class ButtonWebSocketHandler extends TextWebSocketHandler {

    private final ButtonService buttonService;

    public ButtonWebSocketHandler(ButtonService buttonService) {
        this.buttonService = buttonService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        buttonService.addSession(session);
        buttonService.broadcastMessage("Client connected: " + session.getId());
        log.info("Client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        buttonService.removeSession(session);
        log.info("Client disconnected: {}", session.getId());
    }

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