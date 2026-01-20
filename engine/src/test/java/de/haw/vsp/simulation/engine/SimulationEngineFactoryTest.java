package de.haw.vsp.simulation.engine;

import de.haw.vsp.simulation.core.SimulationEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for SimulationEngineFactory.
 *
 * Verifies:
 * - Mode selection (virtual vs distributed)
 * - Environment variable handling
 * - Default mode behavior
 * - Error handling for invalid modes
 */
@DisplayName("SimulationEngineFactory")
class SimulationEngineFactoryTest {

    private String originalSimulationMode;

    @BeforeEach
    void setUp() {
        // Save original environment variable
        originalSimulationMode = System.getenv("SIMULATION_MODE");
    }

    @AfterEach
    void tearDown() {
        // Note: Cannot restore environment variables in Java
        // Tests should be run in isolated environments
    }

    @Test
    @DisplayName("should create virtual engine by default when SIMULATION_MODE not set")
    void shouldCreateVirtualEngineByDefault() {
        // Given: SIMULATION_MODE not set (default)
        // When: Create engine without mode parameter
        SimulationEngine engine = SimulationEngineFactory.create();

        // Then: Should create DefaultSimulationEngine
        assertNotNull(engine);
        assertInstanceOf(DefaultSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("should create virtual engine when mode is 'virtual'")
    void shouldCreateVirtualEngineWhenModeIsVirtual() {
        // When: Create engine with virtual mode
        SimulationEngine engine = SimulationEngineFactory.create("virtual");

        // Then: Should create DefaultSimulationEngine
        assertNotNull(engine);
        assertInstanceOf(DefaultSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("should create distributed engine when mode is 'distributed'")
    void shouldCreateDistributedEngineWhenModeIsDistributed() {
        // Given: Real dependencies for distributed mode (orchestrator will be created internally)
        SimulationEventBus eventBus = mock(SimulationEventBus.class);

        // When: Create engine with distributed mode (orchestrator=null means create new one)
        SimulationEngine engine = SimulationEngineFactory.create("distributed", null, eventBus);

        // Then: Should create DistributedSimulationEngine
        assertNotNull(engine);
        assertInstanceOf(DistributedSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("should throw exception when distributed mode requires null eventBus")
    void shouldThrowExceptionWhenDistributedModeRequiresNullEventBus() {
        // Given: null orchestrator and null eventBus

        // When/Then: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEngineFactory.create("distributed", null, null)
        );

        assertTrue(exception.getMessage().contains("EventBus is required"));
    }

    @Test
    @DisplayName("should throw exception for unknown mode")
    void shouldThrowExceptionForUnknownMode() {
        // When/Then: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> SimulationEngineFactory.create("unknown-mode")
        );

        assertTrue(exception.getMessage().contains("Unknown simulation mode"));
        assertTrue(exception.getMessage().contains("unknown-mode"));
    }

    @Test
    @DisplayName("should handle case-insensitive mode names")
    void shouldHandleCaseInsensitiveModeNames() {
        // When: Create engines with different case
        SimulationEngine engine1 = SimulationEngineFactory.create("VIRTUAL");
        SimulationEngine engine2 = SimulationEngineFactory.create("Virtual");
        SimulationEngine engine3 = SimulationEngineFactory.create("vIrTuAl");

        // Then: All should create DefaultSimulationEngine
        assertInstanceOf(DefaultSimulationEngine.class, engine1);
        assertInstanceOf(DefaultSimulationEngine.class, engine2);
        assertInstanceOf(DefaultSimulationEngine.class, engine3);
    }

    @Test
    @DisplayName("should handle whitespace in mode names")
    void shouldHandleWhitespaceInModeNames() {
        // When: Create engine with whitespace
        SimulationEngine engine = SimulationEngineFactory.create("  virtual  ");

        // Then: Should create DefaultSimulationEngine
        assertInstanceOf(DefaultSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("should default to virtual mode when mode is null")
    void shouldDefaultToVirtualModeWhenModeIsNull() {
        // When: Create engine with null mode
        SimulationEngine engine = SimulationEngineFactory.create(null);

        // Then: Should create DefaultSimulationEngine
        assertInstanceOf(DefaultSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("should default to virtual mode when mode is blank")
    void shouldDefaultToVirtualModeWhenModeIsBlank() {
        // When: Create engine with blank mode
        SimulationEngine engine = SimulationEngineFactory.create("   ");

        // Then: Should create DefaultSimulationEngine
        assertInstanceOf(DefaultSimulationEngine.class, engine);
    }

    @Test
    @DisplayName("isVirtualMode should return true by default")
    void isVirtualModeShouldReturnTrueByDefault() {
        // When/Then: Should be virtual mode by default
        assertTrue(SimulationEngineFactory.isVirtualMode());
    }

    @Test
    @DisplayName("isDistributedMode should return false by default")
    void isDistributedModeShouldReturnFalseByDefault() {
        // When/Then: Should not be distributed mode by default
        assertFalse(SimulationEngineFactory.isDistributedMode());
    }
}
