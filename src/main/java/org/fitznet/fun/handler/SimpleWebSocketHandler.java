package org.fitznet.fun.handler;

import org.fitznet.fun.service.ButtonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SimpleWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ButtonService buttonService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Client connected: " + session.getId());
        buttonService.addSession(session);
        session.sendMessage(new TextMessage("You are now connected to the server"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        buttonService.removeSession(session);
        System.out.println("Client disconnected: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        System.out.println("Received message: " + payload);

        buttonService.logEvent(session.getId(), payload);
        buttonService.broadcastMessage(payload);
    }
}