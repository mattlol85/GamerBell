package org.fitznet.fun.config;

import org.fitznet.fun.handler.ButtonWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration class for WebSocket endpoints.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ButtonWebSocketHandler simpleWebSocketHandler;

    /**
     * Constructs WebSocketConfig with the button event handler.
     *
     * @param simpleWebSocketHandler the handler for WebSocket button events
     */
    public WebSocketConfig(ButtonWebSocketHandler simpleWebSocketHandler) {
        this.simpleWebSocketHandler = simpleWebSocketHandler;
    }

    /**
     * Registers WebSocket handlers with the registry.
     *
     * @param registry the WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(simpleWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
    }
}