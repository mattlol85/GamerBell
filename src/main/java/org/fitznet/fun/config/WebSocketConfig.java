package org.fitznet.fun.config;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.handler.ButtonWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
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
    private final DeviceAuthHandshakeInterceptor deviceAuthHandshakeInterceptor;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Constructs WebSocketConfig with the button event handler.
     *
     * @param simpleWebSocketHandler         the handler for WebSocket button events
     * @param deviceAuthHandshakeInterceptor shared-secret device auth for the /ws handshake
     */
    public WebSocketConfig(ButtonWebSocketHandler simpleWebSocketHandler,
                           DeviceAuthHandshakeInterceptor deviceAuthHandshakeInterceptor) {
        this.simpleWebSocketHandler = simpleWebSocketHandler;
        this.deviceAuthHandshakeInterceptor = deviceAuthHandshakeInterceptor;
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
                .addInterceptors(deviceAuthHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
        log.debug("WebSocket handler registration complete");
    }
}