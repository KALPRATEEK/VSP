# Simulation API

> **Purpose**  
> This API specification describes all public interfaces of the distributed simulation backend.  
> Function signatures are derived from:
>
> - **Functional requirements**  
> - The **technical context** (Browser ↔ Backend, async, Docker, P2P nodes)  
> - The project’s **quality goals**:
>   - Correctness  
>   - Scalability  
>   - Transparency  
>   - Openness  
>   - Shared Resources  
>   - Distribution Transparency  
>   - Usability
>


---

# 0. Shared Domain Types

These domain records are shared across the application and middleware layers.  
They are shaped by requirements like “configure a network”, “run simulations with parameters”,  
and by quality goals such as **Correctness**, **Transparency**, **Scalability**, and **Usability**.

---

## 0.1 `NetworkConfig`

```java
public record NetworkConfig(
        int nodeCount,
        TopologyType topologyType
) { }
```

### Reasoning  
- **Functional requirements:**  
  Users must be able to define *how many nodes* the network has and *which topology* (line, ring, grid, random, …) is used.  
- **Technical context:**  
  Exchanged via HTTP/JSON between UI and backend → immutable record with primitive fields is ideal for serialization.  
- **Quality goals:**  
  - **Correctness:** network structure is explicitly defined and checked against constraints.  
  - **Transparency:** the effective topology is visible and inspectable as a simple DTO.  
  - **Usability:** a single, simple type encapsulates all topology-relevant parameters.

---

## 0.2 `SimulationParameters`

```java
public record SimulationParameters(
        long randomSeed,
        int maxSteps,
        int messageDelayMillis
) { }
```

### Reasoning  
- **Functional requirements:**  
  A simulation run must be configurable with respect to randomness, number of steps and artificial delays.  
- **Technical context:**  
  Parameters are provided once at start via a REST call; the engine then runs asynchronously.  
- **Quality goals:**  
  - **Correctness:** limiting `maxSteps` helps avoid uncontrolled or inconsistent simulation states.  
  - **Scalability:** `maxSteps` and delays support controlling resource usage across many runs.  
  - **Transparency:** explicit parameters make the behavior of a run understandable.  
  - **Usability:** all core run parameters are grouped in one structure that is easy to use from the UI.

---

## 0.3 `MetricsSnapshot`

```java
public record MetricsSnapshot(
        long simulatedTime,
        long realTimeMillis,
        long messageCount,
        long rounds,
        boolean converged,
        String leaderId
) {}
```

### Reasoning  
- **Functional requirements:**  
  The system must report progress and outcomes of consensus/election algorithms (e.g. convergence, message counts, elected leader).  
- **Technical context:**  
  Used by a metrics view that periodically polls the backend via HTTP.  
- **Quality goals:**  
  - **Transparency:** makes algorithm behavior and resource usage observable.  
  - **Scalability:** compact snapshots can be fetched frequently without overloading the system.  
  - **Usability:** provides a clear, aggregated view for teaching and analysis.

---

## 0.4 `SimulationConfig`

```java
public record SimulationConfig(
        NetworkConfig networkConfig,
        AlgorithmId algorithmId,
        SimulationParameters defaultParameters
) { }
```

### Reasoning  
- **Functional requirements:**  
  Users must be able to save a configured scenario (network + algorithm + parameters) and reload it later.  
- **Technical context:**  
  Serialized to/from JSON or files; used in HTTP APIs and possibly simple local storage.  
- **Quality goals:**  
  - **Transparency:** the complete configuration of a scenario is visible as a single DTO.  
  - **Openness:** scenarios can be exchanged or versioned using this stable format.  
  - **Usability:** simplifies saving/loading workflows by bundling all relevant data.

---

## 0.5 `SimulationEvent`

```java
public record SimulationEvent(
        long timestamp,
        String type,
        String nodeId,
        String peerId,
        String payloadSummary
) { }
```

### Reasoning  
- **Functional requirements:**  
  The system must emit events for visualization, logging, and analysis (e.g. messages sent, state changes).  
- **Technical context:**  
  Events are pushed to observers (e.g. WebSocket, logging, metrics) via an internal event bus.  
- **Quality goals:**  
  - **Transparency:** event streams provide insight into the dynamic behavior of the system.  
  - **Distribution Transparency:** consumer components (e.g. UI) see a uniform event representation, independent of where the event originated.  
  - **Usability:** `payloadSummary` is intentionally short and UI-friendly, making events easy to render and interpret.

---

# 1. Application Layer Interfaces

The application layer exposes the core **capabilities** of the simulation system.  
Interfaces are derived from functional needs (configure, execute, observe),  
the technical context (browser-based UI via HTTP + optional WebSocket),  
and the project’s quality goals: particularly **Correctness**, **Scalability**, **Transparency**, and **Usability**.

---

## 1.1 `SimulationControl`

`SimulationControl` is the **use-case façade** between the UI (config, control, metrics, visualization, logs)  
and the internal simulation core. It is **UI-framework-agnostic** and designed around coarse-grained capabilities,  
not single UI button clicks.

```java
public interface SimulationControl {

    // --- Capability: Create / Initialize a Simulation ---

    SimulationId initializeNetwork(NetworkConfig config);

    void selectAlgorithm(SimulationId simulationId, AlgorithmId algorithmId);

    // --- Capability: Execution Control (asynchronous) ---

    void startSimulation(SimulationId simulationId, SimulationParameters parameters);

    void pauseSimulation(SimulationId simulationId);

    void resumeSimulation(SimulationId simulationId);

    void stopSimulation(SimulationId simulationId);

    // --- Capability: Visualization / State Observation ---

    VisualizationSnapshot getCurrentVisualization(SimulationId simulationId);

    void registerVisualizationListener(
            SimulationId simulationId,
            VisualizationListener listener
    );

    // --- Capability: Metrics ---

    MetricsSnapshot getMetrics(SimulationId simulationId);

    // --- Capability: Save / Load / Export ---

    SimulationConfig getCurrentConfig(SimulationId simulationId);

    SimulationId loadConfig(SimulationConfig config);

    byte[] exportRunData(SimulationId simulationId, ExportFormat format);

    // --- Capability: Logs (intentionally simple) ---

    List getLogs(SimulationId simulationId, LogFilter filter);
}
```

### Reasoning – Create / Initialize

- **Functional requirements:**  
  - Create a simulation with a given network topology and node count.  
  - Choose which consensus/election algorithm will be used.  
- **Technical context:**  
  - Stateless HTTP: the backend must keep simulation state addressed via `SimulationId`, not via UI session state.  
  - Multiple simulations may exist in parallel.  
- **Quality goals:**  
  - **Correctness:** explicit configuration objects help validate that simulations are created with valid parameters.  
  - **Transparency:** the chosen network and algorithm are clearly represented and can be inspected or logged.  
  - **Usability:** provides a straightforward, high-level API for the UI to set up simulations.

### Reasoning – Execution Control (Start / Pause / Resume / Stop)

- **Functional requirements:**  
  - Start, pause, resume, and stop simulations without losing internal state.  
- **Technical context:**  
  - Browser/REST: requests must complete quickly; the simulation itself may run for many steps in the background.  
- **Quality goals:**  
  - **Scalability:** asynchronous control allows multiple simulations and users to be handled concurrently.  
  - **Correctness:** explicit lifecycle operations make simulation states (running, paused, stopped) easier to manage and reason about.  
  - **Usability:** UI commands map cleanly to these lifecycle methods.

### Reasoning – Visualization / State Observation

- **Functional requirements:**  
  - UI needs the current state of the simulation for rendering and may want live updates.  
- **Technical context:**  
  - REST polling (`getCurrentVisualization`) and WebSocket/event-based push (`registerVisualizationListener`).  
- **Quality goals:**  
  - **Transparency:** snapshots and events make internal state and evolution of the simulation visible.  
  - **Distribution Transparency:** the UI receives a unified view, independent of whether nodes are local or in separate containers.  
  - **Usability:** supports both simple polling-based UIs and more advanced live visualizations.

### Reasoning – Metrics

- **Functional requirements:**  
  - Display aggregated metrics for simulations (messages, rounds, runtime, convergence, leader).  
- **Technical context:**  
  - Metrics view typically pulls data at low frequency via HTTP.  
- **Quality goals:**  
  - **Transparency:** exposes measurable data about the simulation’s behavior.  
  - **Scalability:** aggregated metrics are compact, so multiple simulations can be monitored without overwhelming the system.

### Reasoning – Save / Load / Export

- **Functional requirements:**  
  - Save and reload simulation configurations.  
  - Export run data for external inspection or grading.  
- **Technical context:**  
  - Backend prepares configuration and export data; the UI decides where and how to store it.  
- **Quality goals:**  
  - **Openness:** data formats (config and exports) are suitable for use in external tools or further processing.  
  - **Transparency:** exported data and configurations make simulation runs auditable.  
  - **Usability:** high-level operations (`getCurrentConfig`, `loadConfig`, `exportRunData`) simplify UI implementation.

### Reasoning – Logs

- **Functional requirements:**  
  - Provide access to log entries of a simulation for debugging and teaching.  
- **Technical context:**  
  - Simple log view in the browser, no heavy log infrastructure.  
- **Quality goals:**  
  - **Transparency:** logs make internal events and error conditions visible.  
  - **Usability:** a straightforward `getLogs` call is easy to integrate into a UI.

---

## 1.2 `VisualizationListener`

`VisualizationListener` defines the callback interface for components that want to react to live simulation events  
(e.g. visualization, live metrics, UI models).

```java
public interface VisualizationListener {
    void onEvent(SimulationEvent event);
}
```

### Reasoning  

- **Functional requirements:**  
  - Visualization and monitoring components must react to new events as they occur.  
- **Technical context:**  
  - Used in combination with an internal event bus and optional WebSocket adapters to propagate events to the browser.  
- **Quality goals:**  
  - **Transparency:** each event becomes visible to interested observers.  
  - **Distribution Transparency:** listeners do not need to know where the event originated in the distributed system.  
  - **Usability:** simple callback interface allows easy integration of different kinds of observers.

---

## 1.3 `SimulationEngine`

`SimulationEngine` encapsulates the execution of a **single simulation instance** in the application layer.  
It is typically managed by some Simulation Management/Context component and stays independent of UI details.

```java
public interface SimulationEngine {

    void createEngineAndNodes(NetworkConfig config);

    void configureAlgorithm(AlgorithmId algorithmId);

    void startSimulation(SimulationParameters parameters);

    void pauseSimulation();

    void resumeSimulation();

    void stopSimulation();

    void setEventPublisher(SimulationEventPublisher eventPublisher);
}
```

### Reasoning  

- **Functional requirements:**  
  - Create nodes and network structure according to a configuration.  
  - Configure or replace the current algorithm.  
  - Control the lifecycle of the simulation (start, pause, resume, stop).  
- **Technical context:**  
  - Nodes may run via in-memory messaging or via UDP across containers, but the engine’s control interface stays the same.  
- **Quality goals:**  
  - **Distribution Transparency:** the engine API hides whether nodes run in the same process, separate containers, or elsewhere.  
  - **Correctness:** clear lifecycle methods help keep simulation state transitions well-defined.  
  - **Scalability:** engines can be instantiated per simulation, supporting multiple concurrent runs.  
  - **Transparency:** hooking a `SimulationEventPublisher` into the engine exposes internal behavior without changing core logic.

---

## 1.4 `SimulationEventPublisher`

`SimulationEventPublisher` abstracts the event channel from the simulation core to the Observation & Analysis layer.  
Typical implementations will fan out events to visualization, metrics, and logging.

```java
public interface SimulationEventPublisher {
    void publish(SimulationEvent event);
}
```

### Reasoning  

- **Functional requirements:**  
  - All relevant simulation events must reach observers (visualization, metrics, logging).  
- **Technical context:**  
  - Implementations may be an in-process event bus or other dispatch mechanisms.  
- **Quality goals:**  
  - **Transparency:** ensures that state changes and message flows are observable.  
  - **Openness:** additional observers (e.g. new analysis tools) can subscribe without modifying the core.  
  - **Scalability:** event handling can be scaled or adapted in the publisher implementation without touching the simulation engine.


## 1.5 SimulationEventBus and SimulationEventListener

To support logging, metrics, and visualization, the simulation backend uses an event bus.  
It extends the existing write-side publisher (`SimulationEventPublisher`) with subscription capabilities.

```java
public interface SimulationEventListener {
    void onEvent(SimulationEvent event);
}

public interface SimulationEventBus extends SimulationEventPublisher {

    void subscribe(EventType type, SimulationEventListener listener);

    void unsubscribe(EventType type, SimulationEventListener listener);
}
```

### Reasoning

- **Functional Requirements**  
  - Components such as logging, metrics, and visualization must receive simulation events.  
  - Different consumers may need different event types.

- **Technical Context**  
  - The bus operates inside the backend (in-process), receiving events from the simulation engine.  
  - Fan-out is handled within the backend, not by the simulation nodes.

- **Quality Goals**  
  - **Transparency**: Every relevant event becomes observable.  
  - **Openness**: New listeners (e.g., additional visualization tools) can subscribe without modifying the core.  
  - **Scalability**: Event fan-out supports multiple observers efficiently.  
  - **Distribution Transparency**: Event consumers do not care where the event originated (local JVM or external container).

---

## 1.6 Core Domain Interfaces: Node, NodeContext, NodeAlgorithm

These interfaces describe how the simulation engine interacts with nodes, and how nodes delegate behavior to algorithms.

```java
public interface Node {

    void onStart();

    void onMessage(NodeContext context, SimulationMessage message);
}

public interface NodeContext {

    NodeId self();

    Set<NodeId> neighbors();

    void send(NodeId target, SimulationMessage message);

    void broadcast(Set<NodeId> targets, SimulationMessage message);
}

public interface NodeAlgorithm {

    void onStart(NodeContext context);

    void onMessage(NodeContext context, SimulationMessage message);
}
```

### Reasoning – Node

- **Functional Requirements**  
  - The simulation engine must initialize nodes and deliver incoming messages to them.

- **Technical Context**  
  - Nodes are wired to the networking layer through `MessagingPort` and are controlled by the engine.

- **Quality Goals**  
  - **Correctness**: A clear node lifecycle (`onStart`, `onMessage`) ensures predictable behavior.  
  - **Usability**: Algorithm implementers interact with a simple, well-defined interface.

---

### Reasoning – NodeContext

- **Functional Requirements**  
  - Algorithms need messaging capability, node identity, and neighbor information.

- **Technical Context**  
  - `NodeContext` abstracts underlying transports (UDP, in-memory, etc.).

- **Quality Goals**  
  - **Distribution Transparency**: Algorithms never depend on transport details.  
  - **Shared Resources**: Network resources are accessed via a controlled abstraction.  
  - **Usability**: Convenient access to id, neighbors, and messaging operations.

---

### Reasoning – NodeAlgorithm

- **Functional Requirements**  
  - Algorithms must be replaceable without modifying the engine or node infrastructure.

- **Technical Context**  
  - Algorithm instances run inside nodes and use `NodeContext` for all interactions.

- **Quality Goals**  
  - **Openness**: New algorithms can be added by simply implementing this interface.  
  - **Correctness**: The engine guarantees a consistent lifecycle (`onStart` before messages).  
  - **Scalability**: Many nodes running the same algorithm can be managed efficiently.

---

## 1.7 Implementation Classes: SimulationNode and SimulationNodeContext

These are the concrete implementation classes for the Node and NodeContext interfaces.

### SimulationNode

```java
public class SimulationNode implements Node {
    
    public SimulationNode(NodeId nodeId, Set<NodeId> neighbors, 
                         NodeAlgorithm algorithm, NodeContext nodeContext);
    
    @Override
    public void onStart();
    
    @Override
    public void onMessage(NodeContext context, SimulationMessage message);
    
    public NodeId getNodeId();
    
    public Set<NodeId> getNeighbors();
    
    public boolean isStarted();
}
```

### Reasoning – SimulationNode

- **Functional Requirements**  
  - The simulation engine needs a concrete node implementation to execute algorithm logic.
  - Nodes must track their lifecycle state and delegate to pluggable algorithms.

- **Technical Context**  
  - Created by the simulation engine for each node in the network topology.
  - Manages the node's lifecycle and state.

- **Quality Goals**  
  - **Correctness**: Enforces that `onStart()` is called exactly once before message processing.
  - **Openness**: Accepts any `NodeAlgorithm` implementation via constructor injection.
  - **Usability**: Provides clear lifecycle guarantees and state inspection methods.

---

### SimulationNodeContext

```java
public class SimulationNodeContext implements NodeContext {
    
    public SimulationNodeContext(NodeId nodeId, Set<NodeId> neighbors, 
                                MessagingPort messagingPort);
    
    @Override
    public NodeId self();
    
    @Override
    public Set<NodeId> neighbors();
    
    @Override
    public void send(NodeId target, SimulationMessage message);
    
    @Override
    public void broadcast(Set<NodeId> targets, SimulationMessage message);
}
```

### Reasoning – SimulationNodeContext

- **Functional Requirements**  
  - Algorithms need a concrete context implementation to access node identity, neighbors, and messaging.
  - All messaging operations must be delegated to the underlying transport layer.

- **Technical Context**  
  - Wraps a `MessagingPort` instance to abstract transport details (UDP, in-memory, etc.).
  - Created once per node and remains immutable regarding identity and neighbors.

- **Quality Goals**  
  - **Distribution Transparency**: Algorithms never see UDP sockets, Docker networking, or other transport details.
  - **Immutability**: Node identity and neighbor set cannot change during simulation, ensuring consistent behavior.
  - **Shared Resources**: MessagingPort handles resource sharing (sockets, ports) transparently.
  - **Usability**: Simple, validated API for all node-to-node communication needs.

---


---

# 2. Middleware Layer Interfaces

The middleware layer abstracts the underlying communication mechanisms between nodes.  
It reflects the P2P architecture, the usage of Docker/UDP and a focus on keeping node logic independent  
of low-level networking APIs.

---

## 2.1 `MessagingPort`

`MessagingPort` abstracts the transport channel for node-to-node communication.  
Node logic depends only on this interface and not on concrete networking details (UDP, TCP, in-memory).

```java
public interface MessagingPort {

    void send(NodeId receiver, SimulationMessage message);

    void broadcast(Set<NodeId> receivers, SimulationMessage message);

    void registerHandler(NodeId nodeId, MessageHandler handler);

    void unregisterHandler(NodeId nodeId);
}
```

### Reasoning  

- **Functional requirements:**  
  - Nodes must be able to send messages to individual peers and sets of neighbors (e.g. flooding protocols).  
  - Nodes must be able to receive messages via registered handlers.  
- **Technical context:**  
  - In the lab, messages are typically transported via UDP in a Docker bridge network, or via an in-memory implementation for tests.  
- **Quality goals:**  
  - **Distribution Transparency:** node algorithms work against a uniform messaging interface, regardless of concrete transport.  
  - **Shared Resources:** enables multiple nodes to share network resources (sockets, ports) behind a controlled abstraction.  
  - **Scalability:** different implementations (in-memory vs. UDP) allow scaling from simple local tests to many containers.  
  - **Openness:** new transport mechanisms can be added as further `MessagingPort` implementations without changing core logic.  

---

# 3. Architecture Justification Table

The following table summarizes how important API functions result from the combination  
of **functional requirements**, the **technical context**, and the project’s **quality goals**.

| Capability / API Function | Functional Requirements | Technical Context | Quality Goals (from project) | Resulting API Shape |
|---------------------------|-------------------------|-------------------|------------------------------|----------------------|
| Create Simulation | Create networks, select algorithms | Stateless HTTP/JSON, multiple simulations | Correctness, Transparency, Usability | `initializeNetwork(NetworkConfig)`, `selectAlgorithm(SimulationId, AlgorithmId)` |
| Start Simulation | Execute with parameters | Browser must return immediately | Scalability, Correctness, Usability | `startSimulation(SimulationId, SimulationParameters)` (non-blocking) |
| Control Execution | Pause / Resume / Stop | REST control commands | Correctness, Usability | Simple lifecycle methods (`pauseSimulation`, `resumeSimulation`, `stopSimulation`) |
| Visualization | Show state & live updates | REST + WebSocket / event bus | Transparency, Distribution Transparency, Usability | `getCurrentVisualization` (snapshot) + `registerVisualizationListener` (push) |
| Metrics | Understand algorithm progress | Pull-based HTTP polling | Transparency, Scalability | Compact `MetricsSnapshot` DTO |
| Save / Load | Reuse scenarios | JSON/file-based persistence | Openness, Transparency, Usability | `SimulationConfig`, `getCurrentConfig`, `loadConfig` |
| Export Runs | External analysis | File download, external tools | Openness, Transparency | `byte[] exportRunData(SimulationId, ExportFormat)` |
| Logs | Debugging, error analysis | Simple REST UI | Transparency, Usability | `getLogs(SimulationId, LogFilter)` kept intentionally simple |
| P2P Messaging | Node-to-node communication | Docker/UDP/InMemory | Distribution Transparency, Shared Resources, Scalability, Openness | `MessagingPort` with `send`, `broadcast`, handler registration |

