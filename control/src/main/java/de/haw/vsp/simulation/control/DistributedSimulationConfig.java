package de.haw.vsp.simulation.control;

import de.haw.vsp.simulation.core.SimulationEventBus;
import de.haw.vsp.simulation.core.SimulationId;
import de.haw.vsp.simulation.engine.DockerNodeOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring configuration for distributed simulation components.
 *
 * Provides beans required for distributed execution mode:
 * - DockerNodeOrchestrator for container management
 * - Event aggregation map for routing events from nodes to simulations
 * - SimulationControl façade
 */
@Configuration
public class DistributedSimulationConfig {

    /**
     * Provides DockerNodeOrchestrator as Spring bean.
     *
     * This orchestrator is used by DistributedSimulationEngine to manage
     * Docker containers for simulation nodes.
     *
     * Only active when SIMULATION_MODE=distributed
     *
     * @return Docker node orchestrator
     */
    @Bean
    @ConditionalOnProperty(name = "SIMULATION_MODE", havingValue = "distributed", matchIfMissing = false)
    public DockerNodeOrchestrator dockerNodeOrchestrator() {
        return new DockerNodeOrchestrator();
    }

    /**
     * Provides map for event aggregation from distributed nodes.
     *
     * This map is used by:
     * - EventAggregationController to route incoming events to correct simulation
     * - DefaultSimulationControl to register event buses for new simulations
     *
     * Key: SimulationId
     * Value: SimulationEventBus for that simulation
     *
     * @return concurrent map for event aggregation
     */
    @Bean
    public Map<SimulationId, SimulationEventBus> eventAggregationMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Provides SimulationControl façade as Spring bean.
     *
     * This façade is used by SimulationController REST API to manage simulations.
     * It receives the eventAggregationMap to register event buses for distributed mode.
     *
     * The dockerOrchestrator parameter is optional - it's only provided when
     * SIMULATION_MODE=distributed. In virtual mode, it will be null.
     *
     * @param dockerOrchestrator Docker orchestrator for managing node containers (optional)
     * @param eventAggregationMap shared map for event aggregation
     * @return SimulationControl instance
     */
    @Bean
    public SimulationControl simulationControl(
        @org.springframework.beans.factory.annotation.Autowired(required = false) DockerNodeOrchestrator dockerOrchestrator,
        Map<SimulationId, SimulationEventBus> eventAggregationMap
    ) {
        return new DefaultSimulationControl(dockerOrchestrator, eventAggregationMap);
    }
}
