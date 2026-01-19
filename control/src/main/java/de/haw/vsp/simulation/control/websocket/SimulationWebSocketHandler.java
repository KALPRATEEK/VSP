package de.haw.vsp.simulation.control.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.haw.vsp.simulation.core.*;
import de.haw.vsp.simulation.control.SimulationControl;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private final SimulationControl simulationControl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimulationWebSocketHandler(SimulationControl simulationControl) {
        this.simulationControl = simulationControl;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WS CONNECTED: " + session.getUri());

        SimulationId simulationId = extractSimulationId(session);

        VisualizationListener listener = event -> {
            try {
                VisualizationSnapshot snapshot =
                        simulationControl.getCurrentVisualization(simulationId);

                String json = objectMapper.writeValueAsString(snapshot);

                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception e) {
                // deliberately swallow to keep simulation running
            }
        };


        simulationControl.registerVisualizationListener(simulationId, listener);
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        // No explicit cleanup required:
        // InMemoryEventBus + listener GC is sufficient for this project
    }

    private SimulationId extractSimulationId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        return new SimulationId(id);
    }

}

