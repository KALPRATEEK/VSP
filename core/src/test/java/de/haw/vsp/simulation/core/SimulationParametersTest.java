package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationParameters.
 *
 * Covers:
 * - Valid parameter construction
 * - Validation of maxSteps (must be > 0)
 * - Validation of messageDelayMillis (must be >= 0)
 * - Deterministic behavior (same seed = same randomness)
 * - JSON serialization/deserialization
 * - Error message clarity
 */
class SimulationParametersTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("should create valid SimulationParameters with typical values")
        void shouldCreateValidParameters() {
            SimulationParameters params = new SimulationParameters(12345L, 100, 50);

            assertNotNull(params);
            assertEquals(12345L, params.randomSeed());
            assertEquals(100, params.maxSteps());
            assertEquals(50, params.messageDelayMillis());
        }

        @Test
        @DisplayName("should allow maxSteps = 1 (minimum valid value)")
        void shouldAllowMinimumMaxSteps() {
            SimulationParameters params = new SimulationParameters(0L, 1, 0);

            assertEquals(1, params.maxSteps());
        }

        @Test
        @DisplayName("should allow messageDelayMillis = 0 (minimum valid value)")
        void shouldAllowZeroMessageDelay() {
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            assertEquals(0, params.messageDelayMillis());
        }

        @Test
        @DisplayName("should allow very large maxSteps")
        void shouldAllowLargeMaxSteps() {
            SimulationParameters params = new SimulationParameters(0L, 1_000_000, 0);

            assertEquals(1_000_000, params.maxSteps());
        }

        @Test
        @DisplayName("should allow negative randomSeed")
        void shouldAllowNegativeRandomSeed() {
            SimulationParameters params = new SimulationParameters(-999L, 10, 0);

            assertEquals(-999L, params.randomSeed());
        }
    }

    @Nested
    @DisplayName("maxSteps Validation")
    class MaxStepsValidation {

        @Test
        @DisplayName("should reject maxSteps = 0")
        void shouldRejectZeroMaxSteps() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationParameters(0L, 0, 0)
            );

            assertTrue(exception.getMessage().contains("maxSteps"));
            assertTrue(exception.getMessage().contains("greater than 0"));
            assertTrue(exception.getMessage().contains("0"));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -10, -100, Integer.MIN_VALUE})
        @DisplayName("should reject negative maxSteps")
        void shouldRejectNegativeMaxSteps(int invalidMaxSteps) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationParameters(0L, invalidMaxSteps, 0)
            );

            assertTrue(exception.getMessage().contains("maxSteps"));
            assertTrue(exception.getMessage().contains("greater than 0"));
            assertTrue(exception.getMessage().contains(String.valueOf(invalidMaxSteps)));
        }
    }

    @Nested
    @DisplayName("messageDelayMillis Validation")
    class MessageDelayValidation {

        @ParameterizedTest
        @ValueSource(ints = {-1, -10, -100, Integer.MIN_VALUE})
        @DisplayName("should reject negative messageDelayMillis")
        void shouldRejectNegativeMessageDelay(int invalidDelay) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationParameters(0L, 10, invalidDelay)
            );

            assertTrue(exception.getMessage().contains("messageDelayMillis"));
            assertTrue(exception.getMessage().contains("non-negative"));
            assertTrue(exception.getMessage().contains(String.valueOf(invalidDelay)));
        }
    }

    @Nested
    @DisplayName("Deterministic Behavior")
    class DeterministicBehavior {

        @Test
        @DisplayName("should produce equal instances with same parameters")
        void shouldProduceEqualInstancesWithSameParameters() {
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(42L, 100, 50);

            assertEquals(params1, params2);
            assertEquals(params1.hashCode(), params2.hashCode());
        }

        @Test
        @DisplayName("should produce different instances with different randomSeed")
        void shouldProduceDifferentInstancesWithDifferentSeed() {
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(99L, 100, 50);

            assertNotEquals(params1, params2);
        }

        @Test
        @DisplayName("should produce different instances with different maxSteps")
        void shouldProduceDifferentInstancesWithDifferentMaxSteps() {
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(42L, 200, 50);

            assertNotEquals(params1, params2);
        }

        @Test
        @DisplayName("should produce different instances with different messageDelayMillis")
        void shouldProduceDifferentInstancesWithDifferentDelay() {
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(42L, 100, 100);

            assertNotEquals(params1, params2);
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            SimulationParameters params = new SimulationParameters(12345L, 100, 50);

            String json = objectMapper.writeValueAsString(params);

            assertNotNull(json);
            assertTrue(json.contains("\"randomSeed\":12345"));
            assertTrue(json.contains("\"maxSteps\":100"));
            assertTrue(json.contains("\"messageDelayMillis\":50"));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "randomSeed": 12345,
                        "maxSteps": 100,
                        "messageDelayMillis": 50
                    }
                    """;

            SimulationParameters params = objectMapper.readValue(json, SimulationParameters.class);

            assertNotNull(params);
            assertEquals(12345L, params.randomSeed());
            assertEquals(100, params.maxSteps());
            assertEquals(50, params.messageDelayMillis());
        }

        @Test
        @DisplayName("should roundtrip JSON serialization without data loss")
        void shouldRoundtripJsonSerialization() throws JsonProcessingException {
            SimulationParameters original = new SimulationParameters(99999L, 500, 25);

            String json = objectMapper.writeValueAsString(original);
            SimulationParameters deserialized = objectMapper.readValue(json, SimulationParameters.class);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("should reject invalid JSON with maxSteps = 0")
        void shouldRejectInvalidJsonWithZeroMaxSteps() {
            String invalidJson = """
                    {
                        "randomSeed": 0,
                        "maxSteps": 0,
                        "messageDelayMillis": 0
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, SimulationParameters.class)
            );
        }

        @Test
        @DisplayName("should reject invalid JSON with negative messageDelayMillis")
        void shouldRejectInvalidJsonWithNegativeDelay() {
            String invalidJson = """
                    {
                        "randomSeed": 0,
                        "maxSteps": 10,
                        "messageDelayMillis": -5
                    }
                    """;

            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, SimulationParameters.class)
            );
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("should provide clear error message for invalid maxSteps")
        void shouldProvideCleanErrorMessageForMaxSteps() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationParameters(0L, -5, 0)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("maxSteps"), "Error message should mention 'maxSteps'");
            assertTrue(message.contains("greater than 0"), "Error message should explain requirement");
            assertTrue(message.contains("-5"), "Error message should show actual invalid value");
        }

        @Test
        @DisplayName("should provide clear error message for invalid messageDelayMillis")
        void shouldProvideCleanErrorMessageForMessageDelay() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationParameters(0L, 10, -100)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("messageDelayMillis"), "Error message should mention 'messageDelayMillis'");
            assertTrue(message.contains("non-negative"), "Error message should explain requirement");
            assertTrue(message.contains("-100"), "Error message should show actual invalid value");
        }
    }
}

