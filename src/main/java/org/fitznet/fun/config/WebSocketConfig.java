package org.fitznet.fun.config;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.handler.ButtonWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration class for WebSocket endpoints.
 * Registers WebSocket handlers and logs configuration details.
 */
@Configuration
@EnableWebSocket
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final ButtonWebSocketHandler simpleWebSocketHandler;

    /**
     * Constructs WebSocketConfig with the button event handler.
     *
     * @param simpleWebSocketHandler the handler for WebSocket button events
     */
    public WebSocketConfig(ButtonWebSocketHandler simpleWebSocketHandler) {
        this.simpleWebSocketHandler = simpleWebSocketHandler;
        log.info("WebSocketConfig initialized with ButtonWebSocketHandler");
    }

    /**
     * Registers WebSocket handlers with the registry.
     *
     * @param registry the WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("Registering WebSocket handler - path=/ws, allowedOrigins=explicit");
        registry.addHandler(simpleWebSocketHandler, "/ws")
                .setAllowedOrigins(
                        "https://fitznet.org",
                        "https://www.fitznet.org",
                        "https://fitznet.doomdns.org",
                        "https://api.fitznet.doomdns.org",
                        "https://gamerbell.fitznet.doomdns.org",
                        "http://localhost:3000"
                );
        log.debug("WebSocket handler registration complete");
    }
}