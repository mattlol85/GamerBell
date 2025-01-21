package org.fitznet.fun.config;

import org.fitznet.fun.handler.SimpleWebSocketHandler;
import org.fitznet.fun.service.ButtonService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ButtonService buttonService;

    public WebSocketConfig(ButtonService buttonService) {
        this.buttonService = buttonService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SimpleWebSocketHandler(), "/ws")
                .setAllowedOrigins("*"); // Allow all origins for testing
    }

    @Bean
    public SimpleWebSocketHandler simpleWebSocketHandler() {
        return new SimpleWebSocketHandler();
    }
}
