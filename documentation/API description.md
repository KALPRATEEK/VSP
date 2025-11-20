
# Simulation API

This document describes the core programmatic interfaces for controlling and observing the distributed consensus simulation.

We distinguish between:

- **Application Layer** interfaces: `SimulationControl`, `VisualizationListener`, `SimulationEngine`, `SimulationEventPublisher`
- **Middleware Layer** interfaces: `MessagingPort`

The structure of this document follows that separation.

---

## 0. Shared Domain Types

These records and types are used by multiple interfaces across the application.

### 0.1 `NetworkConfig`

```java
/**
 * Configuration for the initial network generation.
 * Defines how many nodes are created and which topology is used.
 */
public record NetworkConfig(
        int nodeCount,
        TopologyType topologyType
) { }
```

- **`nodeCount`** – number of nodes to create.  
- **`topologyType`** – type of network topology (e.g. LINE, RING, GRID, RANDOM, …).

---

### 0.2 `SimulationParameters`

```java
/**
 * Parameters for a concrete simulation run.
 * These influence randomness, runtime limits and artificial message delay.
 */
public record SimulationParameters(
        long randomSeed,
        int maxSteps,
        int messageDelayMillis
) { }
```

- **`randomSeed`** – seed for reproducibility of runs.  
- **`maxSteps`** – maximum number of simulation steps.  
- **`messageDelayMillis`** – (simulated) delay per message in milliseconds.

---

### 0.3 `MetricsSnapshot`

```java
/**
 * Aggregated metrics of a simulation run.
 * Represents a read-only snapshot at a given point in time.
 */
public record MetricsSnapshot(
        long simulatedTime,
        long realTimeMillis,
        long messageCount,
        long rounds,
        boolean converged,
        String leaderId
) { }
```

- **`simulatedTime`** – simulated time (e.g. step / round).  
- **`realTimeMillis`** – real execution time in milliseconds.  
- **`messageCount`** – number of messages sent.  
- **`rounds`** – number of completed rounds (algorithm-specific).  
- **`converged`** – `true` if the algorithm has converged.  
- **`leaderId`** – ID of the elected leader (if applicable), otherwise `null`.

---

### 0.4 `SimulationConfig`

```java
/**
 * Configuration of a simulation session (for save / load).
 * Captures network topology, algorithm choice and default parameters.
 */
public record SimulationConfig(
        NetworkConfig networkConfig,
        AlgorithmId algorithmId,
        SimulationParameters defaultParameters
) { }
```

- **`networkConfig`** – topology and node count.  
- **`algorithmId`** – identifier of the consensus/election algorithm.  
- **`defaultParameters`** – default run parameters for this simulation session.

---

### 0.5 `SimulationEvent`

```java
/**
 * Visualisation / observation event used for live updates.
 * Emitted by the simulation core and consumed by Observation & Analysis.
 */
public record SimulationEvent(
        long timestamp,
        String type,          // e.g. "MESSAGE_SENT", "MESSAGE_RECEIVED", "STATE_CHANGED", ...
        String nodeId,
        String peerId,
        String payloadSummary
) { }
```

- **`timestamp`** – timestamp (simulated or real) of the event.  
- **`type`** – event type (e.g. "MESSAGE_SENT", "STATE_CHANGED", "LEADER_ELECTED").  
- **`nodeId`** – affected node.  
- **`peerId`** – peer node (e.g. sender/receiver), may be `null`.  
- **`payloadSummary`** – short, UI-friendly summary of the event payload.

---

## 1. Application Layer Interfaces

This section covers all interfaces that belong to the **application layer**:  
`SimulationControl`, `VisualizationListener`, `SimulationEngine`, `SimulationEventPublisher`.

---

### 1.1 Interface: `SimulationControl`

The `SimulationControl` interface is the **use-case façade** between the User Interface  
(Config & Control View, Metrics View, Visualisation View, Logs View) and the internal simulation.

It is **use-case-oriented** and UI-framework-agnostic.

```java
public interface SimulationControl {

    // UC-01: Initialize Network

    /**
     * Creates a new simulation with N nodes and the selected topology.
     * Node IDs and edges are assigned according to the NetworkConfig.
     *
     * @param config network configuration (number of nodes, topology)
     * @return a new SimulationId identifying this simulation session
     */
    SimulationId initializeNetwork(NetworkConfig config);

    // UC-02: Select Algorithm

    /**
     * Selects the active algorithm (e.g. "max-id-flooding") for this simulation.
     * Internally uses an AlgorithmFactory to instantiate the strategy.
     *
     * @param simulationId the target simulation session
     * @param algorithmId  identifier of the algorithm implementation
     */
    void selectAlgorithm(SimulationId simulationId, AlgorithmId algorithmId);

    // UC-03: Start Simulation

    /**
     * Starts the simulation asynchronously.
     * Returns immediately; execution is event-driven.
     *
     * @param simulationId the simulation to start
     * @param parameters   run parameters (seed, max steps, delays, ...)
     */
    void startSimulation(SimulationId simulationId, SimulationParameters parameters);

    // UC-04.1: Pause Simulation

    /**
     * Puts the simulation into a paused state without losing its internal state.
     *
     * @param simulationId the simulation to pause
     */
    void pauseSimulation(SimulationId simulationId);

    // UC-04.2: Resume Simulation

    /**
     * Resumes a paused simulation.
     *
     * @param simulationId the simulation to resume
     */
    void resumeSimulation(SimulationId simulationId);

    // UC-05: Stop Simulation

    /**
     * Stops the simulation in a controlled manner.
     * The final state is preserved for metrics and export.
     *
     * @param simulationId the simulation to stop
     */
    void stopSimulation(SimulationId simulationId);

    // UC-06: Inspect Visualisation

    /**
     * Provides a consistent snapshot of the current visualisation data.
     * Intended for polling by the UI.
     *
     * @param simulationId the target simulation
     * @return a snapshot suitable for rendering in the visualisation view
     */
    VisualizationSnapshot getCurrentVisualization(SimulationId simulationId);

    /**
     * Optional: Live stream of events (e.g. for WebSocket / observer pattern).
     * The concrete implementation may use reactive streams, callbacks, etc.
     *
     * @param simulationId the target simulation
     * @param listener     callback for streaming visualisation events
     */
    void registerVisualizationListener(SimulationId simulationId, VisualizationListener listener);

    // UC-07: View Metrics

    /**
     * Returns aggregated metrics for the current or most recent run.
     *
     * @param simulationId the target simulation
     * @return aggregated metrics snapshot
     */
    MetricsSnapshot getMetrics(SimulationId simulationId);

    // UC-08.1: Save Configuration

    /**
     * Returns the current simulation configuration (topology, algorithm, default parameters).
     * The UI or adapter decides how and where this data is stored.
     *
     * @param simulationId the target simulation
     * @return configuration that can be serialized and later re-loaded
     */
    SimulationConfig getCurrentConfig(SimulationId simulationId);

    // UC-08.2: Load Configuration

    /**
     * Creates a new simulation from a previously saved configuration.
     * Returns a new SimulationId.
     *
     * @param config a previously stored simulation configuration
     * @return a new SimulationId representing the loaded simulation
     */
    SimulationId loadConfig(SimulationConfig config);

    // UC-09: Export Run Data

    /**
     * Exports the events and/or metrics of a run into a chosen format.
     * The return value may be written to a file by the UI.
     *
     * @param simulationId the target simulation
     * @param format       export format (e.g. CSV, JSON, ...)
     * @return binary representation of the export (e.g. file content)
     */
    byte[] exportRunData(SimulationId simulationId, ExportFormat format);

    // UC-10: View Errors / Logs

    /**
     * Returns log entries for the simulation, filtered by level and/or time range.
     *
     * @param simulationId the target simulation
     * @param filter       filter for log level, time range etc.
     * @return list of log entries matching the filter
     */
    List getLogs(SimulationId simulationId, LogFilter filter);
}
```

#### 1.1.1 Typical Usage by UI Components

- **Config & Control View**
  - `initializeNetwork(...)`
  - `selectAlgorithm(...)`
  - `startSimulation(...)`
  - `pauseSimulation(...)`
  - `resumeSimulation(...)`
  - `stopSimulation(...)`
  - `getCurrentConfig(...)`
  - `loadConfig(...)`
  - `exportRunData(...)`

- **Metrics View**
  - `getMetrics(...)`

- **Visualisation View**
  - `getCurrentVisualization(...)`
  - `registerVisualizationListener(...)`

- **Logs View**
  - `getLogs(...)`

---

### 1.2 Interface: `VisualizationListener`

`VisualizationListener` defines the **callback interface** for components that react to live events from the simulation (e.g. visualisation, UI models).

```java
/**
 * Listener for visualisation events.
 * Implemented by components that update the UI model / view.
 */
public interface VisualizationListener {

    /**
     * Called whenever a new SimulationEvent is emitted by the core.
     *
     * @param event the emitted simulation event
     */
    void onEvent(SimulationEvent event);
}
```

#### 1.2.1 Role in the System

- Used by **SimulationControl** and/or the underlying infrastructure to push live updates (e.g. via WebSocket) to the UI.  
- Typically wired into an internal EventBus or directly into observers in the **Observation & Analysis** layer.

---

### 1.3 Interface: `SimulationEngine`

`SimulationEngine` encapsulates the **core execution** of a single simulation instance in the application layer’s Simulation Core.  
It is typically used by a `SimulationContext` or Simulation Management component.

```java
/**
 * Core abstraction for running a single simulation instance.
 * Responsible for creating nodes, wiring the network and controlling
 * the lifecycle of the simulation.
 */
public interface SimulationEngine {

    /**
     * Creates all nodes and network connections according to the given config.
     *
     * @param config network configuration (number of nodes, topology)
     */
    void createEngineAndNodes(NetworkConfig config);

    /**
     * Configures or replaces the active election / consensus algorithm.
     * Usually delegates to an AlgorithmFactory and configures all nodes.
     *
     * @param algorithmId identifier of the algorithm implementation
     */
    void configureAlgorithm(AlgorithmId algorithmId);

    /**
     * Starts the simulation run with the given parameters.
     * Should be non-blocking and event-driven.
     *
     * @param parameters run parameters (seed, max steps, delays, ...)
     */
    void startSimulation(SimulationParameters parameters);

    /**
     * Pauses the current simulation without losing internal state.
     */
    void pauseSimulation();

    /**
     * Resumes a previously paused simulation.
     */
    void resumeSimulation();

    /**
     * Stops the simulation in a controlled manner and preserves final state.
     */
    void stopSimulation();

    /**
     * Injects the publisher used to emit SimulationEvents
     * towards the Observation & Analysis layer.
     *
     * @param eventPublisher publisher for simulation events
     */
    void setEventPublisher(SimulationEventPublisher eventPublisher);
}
```

#### 1.3.1 Collaboration with Other Components

- Called by **SimulationContext** or **SimulationManagement** to:
  - set up the network and nodes,
  - configure the algorithm,
  - start / pause / resume / stop a run.
- Uses **`SimulationEventPublisher`** to publish `SimulationEvent`s that are consumed by metrics, logging and visualisation components.

---

### 1.4 Interface: `SimulationEventPublisher`

`SimulationEventPublisher` is the **abstraction of the event channel** from the Simulation Core to the Observation & Analysis layer (EventBus, Metrics, Logging, Visualisation).

```java
/**
 * Abstraction for publishing simulation events from the Simulation Core
 * to the Observation & Analysis layer.
 *
 * Typical implementations use an internal EventBus and fan-out to
 * components like Metrics, Logging and Visualisation.
 */
public interface SimulationEventPublisher {

    /**
     * Publishes a single simulation event.
     * Implementations are responsible for dispatching the event to all
     * registered listeners / observers.
     *
     * @param event the simulation event to publish
     */
    void publish(SimulationEvent event);
}
```

#### 1.4.1 Typical Implementation

- Internally, an **EventBus** may be used to notify:
  - `VisualizationListener` instances,  
  - metrics aggregators,  
  - logging components.  
- `SimulationEngine` and/or individual `Node` instances call `publish(...)` to report:
  - message sends/receives,  
  - state changes,  
  - algorithm-specific events (e.g. leader elected).

---

## 2. Middleware Layer Interfaces

This section covers the interfaces that belong to the **middleware** and abstract the underlying communication mechanisms.

---

### 2.1 Interface: `MessagingPort`

`MessagingPort` abstracts the **underlying message transport** between nodes (e.g. UDP, TCP, Docker networking), so that node logic does not depend on a concrete transport technology.

```java
/**
 * Abstraction of the message transport between nodes.
 * Used by nodes to send and receive messages without depending on
 * concrete networking technology (UDP, TCP, Docker, ...).
 */
public interface MessagingPort {

    /**
     * Sends a message to a single receiver node.
     *
     * @param receiver the target node
     * @param message  the message to send
     */
    void send(NodeId receiver, SimulationMessage message);

    /**
     * Sends the same message to a set of receiver nodes.
     *
     * @param receivers target nodes
     * @param message   the message to send
     */
    void broadcast(Set<NodeId> receivers, SimulationMessage message);

    /**
     * Registers a handler that will be invoked for incoming messages
     * of the given node.
     *
     * @param nodeId  the node for which messages should be delivered
     * @param handler callback to handle incoming messages
     */
    void registerHandler(NodeId nodeId, MessageHandler handler);

    /**
     * Unregisters the message handler for the given node.
     *
     * @param nodeId the node whose handler should be removed
     */
    void unregisterHandler(NodeId nodeId);
}
```

Typical associated types (to be defined in your codebase):

```java
// Identifier of a node in the network.
public record NodeId(String value) { }

// Message payload exchanged between nodes.
public interface SimulationMessage { }

// Callback invoked on incoming messages.
@FunctionalInterface
public interface MessageHandler {
    void onMessage(NodeId sender, SimulationMessage message);
}
```

---

With this structure, the API documentation is clearly grouped by layer:

- **Application Layer**
  - `SimulationControl` – façade to the UI  
  - `VisualizationListener` – callback interface for visualisation / UI components  
  - `SimulationEngine` – core execution interface for a single simulation instance  
  - `SimulationEventPublisher` – event channel from core to observation & analysis  

- **Middleware Layer**
  - `MessagingPort` – transport abstraction for node-to-node messaging
