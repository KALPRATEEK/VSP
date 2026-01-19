package de.haw.vsp.simulation.control.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SimulationWebSocketHandler handler;

    public WebSocketConfig(SimulationWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                // ❗ KEINE Path-Variable
                .addHandler(handler, "/ws/simulations/**")
                // alternativ: "/ws/simulations/**"
                .setAllowedOrigins("*");
    }



}

