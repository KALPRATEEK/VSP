 package de.haw.vsp.simulation.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimulationConfig.
 *
 * Covers:
 * - Valid configuration construction
 * - Validation of all components (networkConfig, algorithmId, defaultParameters)
 * - JSON serialization/deserialization (save/load without information loss)
 * - Reproducibility (identical configuration = identical behavior potential)
 * - Error message clarity
 */
class SimulationConfigTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Valid Construction")
    class ValidConstruction {

        @Test
        @DisplayName("should create valid SimulationConfig with all components")
        void shouldCreateValidConfig() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(42L, 100, 50);

            SimulationConfig config = new SimulationConfig(network, "MAX_ID_ELECTION", params);

            assertNotNull(config);
            assertEquals(network, config.networkConfig());
            assertEquals("MAX_ID_ELECTION", config.algorithmId());
            assertEquals(params, config.defaultParameters());
        }

        @Test
        @DisplayName("should create config with different algorithm")
        void shouldCreateConfigWithDifferentAlgorithm() {
            NetworkConfig network = new NetworkConfig(10, TopologyType.GRID);
            SimulationParameters params = new SimulationParameters(0L, 200, 0);

            SimulationConfig config = new SimulationConfig(network, "RAFT_CONSENSUS", params);

            assertEquals("RAFT_CONSENSUS", config.algorithmId());
        }

        @Test
        @DisplayName("should create config with algorithmId containing special characters")
        void shouldAllowAlgorithmIdWithSpecialCharacters() {
            NetworkConfig network = new NetworkConfig(3, TopologyType.LINE);
            SimulationParameters params = new SimulationParameters(1L, 10, 0);

            SimulationConfig config = new SimulationConfig(network, "algorithm-v2.1_test", params);

            assertEquals("algorithm-v2.1_test", config.algorithmId());
        }
    }

    @Nested
    @DisplayName("NetworkConfig Validation")
    class NetworkConfigValidation {

        @Test
        @DisplayName("should reject null networkConfig")
        void shouldRejectNullNetworkConfig() {
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(null, "TEST_ALGORITHM", params)
            );

            assertTrue(exception.getMessage().contains("networkConfig"));
            assertTrue(exception.getMessage().contains("must not be null"));
        }
    }

    @Nested
    @DisplayName("AlgorithmId Validation")
    class AlgorithmIdValidation {

        @Test
        @DisplayName("should reject null algorithmId")
        void shouldRejectNullAlgorithmId() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, null, params)
            );

            assertTrue(exception.getMessage().contains("algorithmId"));
            assertTrue(exception.getMessage().contains("null or blank"));
            assertTrue(exception.getMessage().contains("null"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("should reject blank algorithmId")
        void shouldRejectBlankAlgorithmId(String blankId) {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, blankId, params)
            );

            assertTrue(exception.getMessage().contains("algorithmId"));
            assertTrue(exception.getMessage().contains("null or blank"));
        }
    }

    @Nested
    @DisplayName("DefaultParameters Validation")
    class DefaultParametersValidation {

        @Test
        @DisplayName("should reject null defaultParameters")
        void shouldRejectNullDefaultParameters() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, "TEST_ALGORITHM", null)
            );

            assertTrue(exception.getMessage().contains("defaultParameters"));
            assertTrue(exception.getMessage().contains("must not be null"));
        }
    }

    @Nested
    @DisplayName("Reproducibility and Equality")
    class Reproducibility {

        @Test
        @DisplayName("should produce equal configs with same components")
        void shouldProduceEqualConfigsWithSameComponents() {
            NetworkConfig network1 = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationConfig config1 = new SimulationConfig(network1, "MAX_ID_ELECTION", params1);

            NetworkConfig network2 = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params2 = new SimulationParameters(42L, 100, 50);
            SimulationConfig config2 = new SimulationConfig(network2, "MAX_ID_ELECTION", params2);

            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("should produce different configs with different networkConfig")
        void shouldProduceDifferentConfigsWithDifferentNetwork() {
            NetworkConfig network1 = new NetworkConfig(5, TopologyType.RING);
            NetworkConfig network2 = new NetworkConfig(10, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(42L, 100, 50);

            SimulationConfig config1 = new SimulationConfig(network1, "TEST", params);
            SimulationConfig config2 = new SimulationConfig(network2, "TEST", params);

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("should produce different configs with different algorithmId")
        void shouldProduceDifferentConfigsWithDifferentAlgorithm() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(42L, 100, 50);

            SimulationConfig config1 = new SimulationConfig(network, "ALGORITHM_A", params);
            SimulationConfig config2 = new SimulationConfig(network, "ALGORITHM_B", params);

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("should produce different configs with different parameters")
        void shouldProduceDifferentConfigsWithDifferentParameters() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(99L, 100, 50);

            SimulationConfig config1 = new SimulationConfig(network, "TEST", params1);
            SimulationConfig config2 = new SimulationConfig(network, "TEST", params2);

            assertNotEquals(config1, config2);
        }
    }

    @Nested
    @DisplayName("JSON Serialization - Save/Load Without Information Loss")
    class JsonSerialization {

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(42L, 100, 50);
            SimulationConfig config = new SimulationConfig(network, "MAX_ID_ELECTION", params);

            String json = objectMapper.writeValueAsString(config);

            assertNotNull(json);
            assertTrue(json.contains("\"networkConfig\""));
            assertTrue(json.contains("\"algorithmId\":\"MAX_ID_ELECTION\""));
            assertTrue(json.contains("\"defaultParameters\""));
            assertTrue(json.contains("\"nodeCount\":5"));
            assertTrue(json.contains("\"topologyType\":\"RING\""));
            assertTrue(json.contains("\"randomSeed\":42"));
            assertTrue(json.contains("\"maxSteps\":100"));
            assertTrue(json.contains("\"messageDelayMillis\":50"));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            String json = """
                    {
                        "networkConfig": {
                            "nodeCount": 5,
                            "topologyType": "RING"
                        },
                        "algorithmId": "MAX_ID_ELECTION",
                        "defaultParameters": {
                            "randomSeed": 42,
                            "maxSteps": 100,
                            "messageDelayMillis": 50
                        }
                    }
                    """;

            SimulationConfig config = objectMapper.readValue(json, SimulationConfig.class);

            assertNotNull(config);
            assertEquals(5, config.networkConfig().nodeCount());
            assertEquals(TopologyType.RING, config.networkConfig().topologyType());
            assertEquals("MAX_ID_ELECTION", config.algorithmId());
            assertEquals(42L, config.defaultParameters().randomSeed());
            assertEquals(100, config.defaultParameters().maxSteps());
            assertEquals(50, config.defaultParameters().messageDelayMillis());
        }

        @Test
        @DisplayName("should roundtrip JSON serialization without information loss")
        void shouldRoundtripWithoutInformationLoss() throws JsonProcessingException {
            NetworkConfig network = new NetworkConfig(7, TopologyType.GRID);
            SimulationParameters params = new SimulationParameters(99999L, 500, 25);
            SimulationConfig original = new SimulationConfig(network, "RAFT_CONSENSUS", params);

            String json = objectMapper.writeValueAsString(original);
            SimulationConfig deserialized = objectMapper.readValue(json, SimulationConfig.class);

            assertEquals(original, deserialized);
            assertEquals(original.networkConfig(), deserialized.networkConfig());
            assertEquals(original.algorithmId(), deserialized.algorithmId());
            assertEquals(original.defaultParameters(), deserialized.defaultParameters());
        }

        @Test
        @DisplayName("should roundtrip with RANDOM topology")
        void shouldRoundtripWithRandomTopology() throws JsonProcessingException {
            NetworkConfig network = new NetworkConfig(10, TopologyType.RANDOM);
            SimulationParameters params = new SimulationParameters(0L, 1, 0);
            SimulationConfig original = new SimulationConfig(network, "TEST", params);

            String json = objectMapper.writeValueAsString(original);
            SimulationConfig deserialized = objectMapper.readValue(json, SimulationConfig.class);

            assertEquals(original, deserialized);
            assertEquals(TopologyType.RANDOM, deserialized.networkConfig().topologyType());
        }

        @Test
        @DisplayName("should roundtrip with LINE topology")
        void shouldRoundtripWithLineTopology() throws JsonProcessingException {
            NetworkConfig network = new NetworkConfig(8, TopologyType.LINE);
            SimulationParameters params = new SimulationParameters(12345L, 200, 100);
            SimulationConfig original = new SimulationConfig(network, "BFS_ALGORITHM", params);

            String json = objectMapper.writeValueAsString(original);
            SimulationConfig deserialized = objectMapper.readValue(json, SimulationConfig.class);

            assertEquals(original, deserialized);
            assertEquals(TopologyType.LINE, deserialized.networkConfig().topologyType());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("provideInvalidJsonScenarios")
        @DisplayName("should reject invalid JSON configurations")
        void shouldRejectInvalidJson(String scenario, String invalidJson) {
            assertThrows(
                    Exception.class,
                    () -> objectMapper.readValue(invalidJson, SimulationConfig.class)
            );
        }

        private static Stream<Arguments> provideInvalidJsonScenarios() {
            return Stream.of(
                    Arguments.of(
                            "null networkConfig",
                            """
                            {
                                "networkConfig": null,
                                "algorithmId": "TEST",
                                "defaultParameters": {
                                    "randomSeed": 0,
                                    "maxSteps": 10,
                                    "messageDelayMillis": 0
                                }
                            }
                            """
                    ),
                    Arguments.of(
                            "blank algorithmId",
                            """
                            {
                                "networkConfig": {
                                    "nodeCount": 5,
                                    "topologyType": "RING"
                                },
                                "algorithmId": "  ",
                                "defaultParameters": {
                                    "randomSeed": 0,
                                    "maxSteps": 10,
                                    "messageDelayMillis": 0
                                }
                            }
                            """
                    ),
                    Arguments.of(
                            "invalid networkConfig (nodeCount=0)",
                            """
                            {
                                "networkConfig": {
                                    "nodeCount": 0,
                                    "topologyType": "RING"
                                },
                                "algorithmId": "TEST",
                                "defaultParameters": {
                                    "randomSeed": 0,
                                    "maxSteps": 10,
                                    "messageDelayMillis": 0
                                }
                            }
                            """
                    ),
                    Arguments.of(
                            "invalid parameters (maxSteps=0)",
                            """
                            {
                                "networkConfig": {
                                    "nodeCount": 5,
                                    "topologyType": "RING"
                                },
                                "algorithmId": "TEST",
                                "defaultParameters": {
                                    "randomSeed": 0,
                                    "maxSteps": 0,
                                    "messageDelayMillis": 0
                                }
                            }
                            """
                    )
            );
        }
    }

    @Nested
    @DisplayName("Identical Simulation Behavior")
    class IdenticalBehavior {

        @Test
        @DisplayName("should enable identical behavior with same randomSeed")
        void shouldEnableIdenticalBehaviorWithSameRandomSeed() {
            // Same config should produce same behavior (determinism via randomSeed)
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(42L, 100, 50);
            SimulationConfig config1 = new SimulationConfig(network, "MAX_ID_ELECTION", params);
            SimulationConfig config2 = new SimulationConfig(network, "MAX_ID_ELECTION", params);

            // Both configs have same randomSeed, maxSteps, etc.
            assertEquals(config1.defaultParameters().randomSeed(),
                    config2.defaultParameters().randomSeed());
            assertEquals(config1, config2);
        }

        @Test
        @DisplayName("should produce different behavior with different randomSeed")
        void shouldProduceDifferentBehaviorWithDifferentRandomSeed() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params1 = new SimulationParameters(42L, 100, 50);
            SimulationParameters params2 = new SimulationParameters(99L, 100, 50);

            SimulationConfig config1 = new SimulationConfig(network, "TEST", params1);
            SimulationConfig config2 = new SimulationConfig(network, "TEST", params2);

            assertNotEquals(config1.defaultParameters().randomSeed(),
                    config2.defaultParameters().randomSeed());
            assertNotEquals(config1, config2);
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("should provide clear error message for null networkConfig")
        void shouldProvideCleanErrorMessageForNullNetworkConfig() {
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(null, "TEST", params)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("networkConfig"), "Error message should mention 'networkConfig'");
            assertTrue(message.contains("must not be null"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for null algorithmId")
        void shouldProvideCleanErrorMessageForNullAlgorithmId() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, null, params)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("algorithmId"), "Error message should mention 'algorithmId'");
            assertTrue(message.contains("null or blank"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for blank algorithmId")
        void shouldProvideCleanErrorMessageForBlankAlgorithmId() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);
            SimulationParameters params = new SimulationParameters(0L, 10, 0);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, "   ", params)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("algorithmId"), "Error message should mention 'algorithmId'");
            assertTrue(message.contains("null or blank"), "Error message should explain requirement");
        }

        @Test
        @DisplayName("should provide clear error message for null defaultParameters")
        void shouldProvideCleanErrorMessageForNullDefaultParameters() {
            NetworkConfig network = new NetworkConfig(5, TopologyType.RING);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new SimulationConfig(network, "TEST", null)
            );

            String message = exception.getMessage();
            assertTrue(message.contains("defaultParameters"), "Error message should mention 'defaultParameters'");
            assertTrue(message.contains("must not be null"), "Error message should explain requirement");
        }
    }
}

