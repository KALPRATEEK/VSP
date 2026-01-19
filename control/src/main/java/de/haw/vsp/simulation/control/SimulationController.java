package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationControl simulationControl;

    public SimulationController(SimulationControl simulationControl) {
        this.simulationControl = simulationControl;
    }

    /* =========================================================
       Simulation lifecycle
       ========================================================= */

    @PostMapping
    public SimulationId createSimulation(
            @RequestBody NetworkConfig config
    ) {
        return simulationControl.initializeNetwork(config);
    }

    @PostMapping("/{id}/algorithm")
    public void selectAlgorithm(
            @PathVariable("id") String id,
            @RequestParam("algorithmId") String algorithmId
    ) {
        simulationControl.selectAlgorithm(
                new SimulationId(id),
                algorithmId
        );
    }

    @PostMapping("/{id}/start")
    public void startSimulation(
            @PathVariable("id") String id,
            @RequestBody SimulationParameters parameters
    ) {
        simulationControl.startSimulation(
                new SimulationId(id),
                parameters
        );
    }

    @PostMapping("/{id}/pause")
    public void pauseSimulation(
            @PathVariable("id") String id
    ) {
        simulationControl.pauseSimulation(
                new SimulationId(id)
        );
    }

    @PostMapping("/{id}/resume")
    public void resumeSimulation(
            @PathVariable("id") String id
    ) {
        simulationControl.resumeSimulation(
                new SimulationId(id)
        );
    }

    @PostMapping("/{id}/stop")
    public void stopSimulation(
            @PathVariable("id") String id
    ) {
        simulationControl.stopSimulation(
                new SimulationId(id)
        );
    }

    /* =========================================================
       Queries
       ========================================================= */

    @GetMapping("/{id}/visualization")
    public VisualizationSnapshot getVisualization(
            @PathVariable("id") String id
    ) {
        return simulationControl.getCurrentVisualization(
                new SimulationId(id)
        );
    }

    @GetMapping("/{id}/metrics")
    public MetricsSnapshot getMetrics(
            @PathVariable("id") String id
    ) {
        return simulationControl.getMetrics(
                new SimulationId(id)
        );
    }

    @GetMapping("/{id}/config")
    public SimulationConfig getCurrentConfig(
            @PathVariable("id") String id
    ) {
        return simulationControl.getCurrentConfig(
                new SimulationId(id)
        );
    }

    @GetMapping("/{id}/logs")
    public List<String> getLogs(
            @PathVariable("id") String id,
            @RequestParam(value = "filter", required = false) String filter
    ) {
        return simulationControl.getLogs(
                new SimulationId(id),
                filter
        );
    }

    /* =========================================================
       Export
       ========================================================= */

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportRunData(
            @PathVariable("id") String id,
            @RequestParam("format") String format
    ) {
        byte[] data = simulationControl.exportRunData(
                new SimulationId(id),
                format
        );

        String contentType = format.equalsIgnoreCase("CSV")
                ? "text/csv"
                : MediaType.APPLICATION_JSON_VALUE;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"simulation-" + id + "." + format.toLowerCase() + "\""
                )
                .body(data);
    }
}

