package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.SimulationEventBus;
import de.haw.vsp.simulation.middleware.MessagingPort;
import de.haw.vsp.simulation.middleware.MessagingPorts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating SimulationEngine instances based on execution mode.
 *
 * Supports two modes:
 * - "virtual": All nodes in single JVM process (DefaultSimulationEngine)
 * - "distributed": Each node in separate Docker container (DistributedSimulationEngine)
 *
 * Mode is selected via SIMULATION_MODE environment variable.
 * Default mode is "virtual" for backward compatibility.
 *
 * This factory enables seamless switching between local development
 * (virtual mode) and distributed execution (distributed mode) without
 * code changes.
 */
public class SimulationEngineFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationEngineFactory.class);

    private static final String ENV_SIMULATION_MODE = "SIMULATION_MODE";
    private static final String MODE_VIRTUAL = "virtual";
    private static final String MODE_DISTRIBUTED = "distributed";

    /**
     * Creates a SimulationEngine based on SIMULATION_MODE environment variable.
     *
     * Modes:
     * - "virtual" (default): DefaultSimulationEngine with in-memory messaging
     * - "distributed": DistributedSimulationEngine with Docker orchestration
     *
     * @return new SimulationEngine instance
     * @throws IllegalArgumentException if mode is invalid
     */
    public static SimulationEngine create() {
        String mode = getSimulationMode();
        return create(mode, null, null);
    }

    /**
     * Creates a SimulationEngine based on specified mode.
     *
     * @param mode simulation mode ("virtual" or "distributed")
     * @return new SimulationEngine instance
     * @throws IllegalArgumentException if mode is invalid or null
     */
    public static SimulationEngine create(String mode) {
        return create(mode, null, null);
    }

    /**
     * Creates a SimulationEngine with explicit dependencies.
     *
     * This method is primarily for testing and Spring integration.
     *
     * @param mode simulation mode ("virtual" or "distributed")
     * @param orchestrator Docker orchestrator (required for distributed mode, ignored for virtual)
     * @param eventBus event bus (required for distributed mode, ignored for virtual)
     * @return new SimulationEngine instance
     * @throws IllegalArgumentException if mode is invalid or required dependencies are null
     */
    public static SimulationEngine create(
        String mode,
        DockerNodeOrchestrator orchestrator,
        SimulationEventBus eventBus
    ) {
        if (mode == null || mode.isBlank()) {
            mode = MODE_VIRTUAL; // Default
        }

        mode = mode.trim().toLowerCase();

        LOG.info("Creating SimulationEngine in '{}' mode", mode);

        return switch (mode) {
            case MODE_VIRTUAL -> createVirtualEngine();
            case MODE_DISTRIBUTED -> createDistributedEngine(orchestrator, eventBus);
            default -> throw new IllegalArgumentException(
                "Unknown simulation mode: '" + mode + "'. " +
                "Valid modes: 'virtual', 'distributed'"
            );
        };
    }

    /**
     * Creates a virtual (single-process) simulation engine.
     *
     * @return DefaultSimulationEngine with virtual messaging
     */
    private static SimulationEngine createVirtualEngine() {
        LOG.info("Creating virtual simulation engine (all nodes in single JVM)");

        // Create virtual MessagingPort (in-memory)
        MessagingPort messagingPort = MessagingPorts.virtual();

        SimulationEngine engine = new DefaultSimulationEngine(messagingPort);

        LOG.info("Virtual simulation engine created successfully");
        return engine;
    }

    /**
     * Creates a distributed simulation engine with Docker orchestration.
     *
     * @param orchestrator Docker orchestrator (if null, creates new instance)
     * @param eventBus event bus (if null, throws exception - required for distributed mode)
     * @return DistributedSimulationEngine
     * @throws IllegalArgumentException if eventBus is null
     */
    private static SimulationEngine createDistributedEngine(
        DockerNodeOrchestrator orchestrator,
        SimulationEventBus eventBus
    ) {
        LOG.info("Creating distributed simulation engine (nodes in Docker containers)");

        // EventBus: required for distributed mode (cannot create here) - validate FIRST
        if (eventBus == null) {
            throw new IllegalArgumentException(
                "EventBus is required for distributed mode but was null. " +
                "Please provide SimulationEventBus instance."
            );
        }

        // Orchestrator: create if not provided
        if (orchestrator == null) {
            LOG.debug("No orchestrator provided, creating new DockerNodeOrchestrator");
            orchestrator = new DockerNodeOrchestrator();
        }

        SimulationEngine engine = new DistributedSimulationEngine(orchestrator, eventBus);

        LOG.info("Distributed simulation engine created successfully");
        return engine;
    }

    /**
     * Gets the simulation mode from environment variable.
     *
     * @return simulation mode ("virtual" or "distributed"), default "virtual"
     */
    private static String getSimulationMode() {
        String mode = System.getenv(ENV_SIMULATION_MODE);

        if (mode == null || mode.isBlank()) {
            LOG.debug("SIMULATION_MODE not set, defaulting to '{}'", MODE_VIRTUAL);
            return MODE_VIRTUAL;
        }

        mode = mode.trim().toLowerCase();
        LOG.debug("SIMULATION_MODE from environment: '{}'", mode);

        return mode;
    }

    /**
     * Checks if distributed mode is enabled.
     *
     * @return true if SIMULATION_MODE=distributed, false otherwise
     */
    public static boolean isDistributedMode() {
        return MODE_DISTRIBUTED.equals(getSimulationMode());
    }

    /**
     * Checks if virtual mode is enabled.
     *
     * @return true if SIMULATION_MODE=virtual or not set, false otherwise
     */
    public static boolean isVirtualMode() {
        return MODE_VIRTUAL.equals(getSimulationMode());
    }
}
