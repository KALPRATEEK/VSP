Table of Content
1 Introduction & Goals ............................................................... 2
1.1 Requirements overview ...................................................... 2
1.2 Quality goals ...................................................................... 3
1.3 Stakeholder ....................................................................... 4
2 Constraints ............................................................................. 5
2.1 Technical Constraints ........................................................ 5
2.2 Organizational Constraints ................................................. 5
3 Context & Scope ..................................................................... 6
3.1 Business Context (Actors & Interfaces) ............................... 6
3.2 Technical Context (Neighbors, Protocols, Formats) ............. 6
4 Solution Strategy ..................................................................... 7
4.1 Quality driven Architectural Approaches ............................. 7
4.2 Communication Model: Asynchronous, P2P ....................... 8
5. Building block view ............................................................... 10
6. Runtime view ....................................................................... 11
   7 Deployment view................................................................... 15
   Docker Bridge Network ..................................................... 15
   Communication Channels ................................................ 15
   8 Crosscutting Concepts .......................................................... 16
   8.1 Communication Concept ................................................. 16
   8.2 Event-Driven Architecture ................................................ 17
   8.3 Message Format & Serialization Concept .......................... 17
   8.4 SimulationControl Façade Concept ................................. 18
   8.5 Algorithm Extensibility Concept ....................................... 18
   8.6 Visualization Concept ...................................................... 19
   8.7 Logging & Error Handling Concept .................................... 20
   8.8 Metrics & Performance Measurement Concept ................. 20
   8.9 Configuration, Import & Export Concept ........................... 21
   8.10 Docker-Based Node Isolation Concept ........................... 21
   9 Architecture Decisions .......................................................... 22
   10 Quality ................................................................................ 22
   10.1 Quality Table .................................................................. 23
   10.2 Quality Scenarios ........................................................... 23
   1 Introduction & Goals
   1.1 Requirements overview
   The goal of this project is to provide an application, that visualizes a flooding algorithm and makes it configurable to the user. Specifically, the leader election algorithm is being implemented, and a solution is being built to easily implement other network algorithms in the future. This serves to conceptualize and put across network algorithms.
   The following goals have been established for this system:
   Priority
   Description
   F1
   The user can initialize a network with a given number of nodes
   F2
   The user can start/stop a chosen network algorithm
   F3
   The system visualizes the running algorithm
   F4
   The systems outputs convergence time, total message count, average messages per node, scalability scope regarding convergence time and if the leader election was correct.
   The following use-cases have been defined for this system:
   UC ID
   Name
   Goal
   Preconditions
   Trigger
   Postconditions
   UC-01
   Initialize Network
   Create a new simulation with N nodes and chosen topology (ring, grid, random).
   App is started; configuration panel accessible.
   Click “New Network”.
   Topology exists; node IDs and edges fixed and visible in UI.
   UC-02
   Select Algorithm
   Choose a concrete algorithm (initially: flooding-based leader election).
   Network topology initialized (UC-01).
   Select algorithm from list.
   Selected algorithm stored as “active” for the session.
   UC-03
   Start Simulation
   Start the active algorithm on the current topology.
   UC-01 & UC-02 completed; parameters valid (e.g., seed).
   Press “Start”.
   Simulation enters running state; metrics counters (re)initialized.
   UC-04
   Pause/Resume Simulation
   Temporarily pause or continue a running simulation.
   UC-03 has started a running simulation.
   Press “Pause” or “Resume”.
   Simulation state switches accordingly; no loss of internal state.
   UC-05
   Stop Simulation
   Terminate the run cleanly.
   UC-03 executed (simulation has run at least once).
   Press “Stop”.
   Final state fixed; metrics frozen and available.
   UC-06
   Inspect Visualization
   Observe message flow and node states in real time.
   A simulation exists (running or paused).
   Open visualization view.
   UI renders current states/events; no side effects on execution.
   UC-07
   View Metrics
   Retrieve run metrics (e.g., convergence time, number of messages, rounds).
   UC-03 or UC-05 produced metric data.
   Open metrics panel.
   Metrics displayed as numbers/plots; ready for export.
   UC-08
   Save/Load Configuration
   Save current network/algorithm settings to a file or load them from a file.
   Valid config in memory (save) or readable file (load).
   Click “Save” or “Load”.
   File written (save) or settings applied (load).
   UC-09
   Export Run Data
   Export events/metrics as JSON/CSV.
   A run has produced events/metrics.
   Click “Export”.
   Export file created at chosen location.
   UC-10
   View Errors/Logs
   Inspect runtime and error logs for diagnostics.
   Application has produced log messages.
   Open logs view.
   Logs shown with timestamps and severities.
   1.2 Quality goals
   This table contains the most crucial quality goals our architecture has to achieve, ordered by priority. A more detailed analysis of the quality goals can be found at section 10.
   Quality
   Description
   Motivation
   Correctness
   For every completed run on a connected network: – exactly one leader exists useful if the leader
   Producing a correct result is critical. If the result is incorrect, the simulation is meaningless.
- leaderId = max(NodeId) in the topology
  For invalid scenarios – the system must not report a "wrong" leader; - the run is marked as "not converged".
  Scalability
  Behaviour follows theoretical complexity: time ≈ O(D), messages ≈ O(E).
  Larger networks could otherwise not be explored, which reduces the value of the system for learning and experimentation.
  Transparency
  Every run produces a detailed SimulationEvent log containing at least:
  – timestamp (simulated and/or real time) – nodeId – eventType (MESSAGE_SENT, STATE_CHANGED, LEADER_ELECTED, ERROR, …)
  This ensures that runs are traceable, debuggable and reproducible for teaching and experimentation.
  1.3 Stakeholder
  Stakeholder
  Role/Interest
  Influencing Factors
  Student team
  Implementation, grading, learning
  Time availability (vacations, holidays like Christmas, exam periods), parallel courses/assignments; skills; equipment (TI lab / personal laptops)
  Supervisors/Instructors
  Evaluation, guidance
  Grading/feedback cycles, office hours, preferences regarding documentation/testing
  Target users
  Learning / visualization
  Usability in lab settings
  Tool
  Reasons for using it
  Java 17
  We all learned to program in Java throughout our studies. Java has minimal ramp-up; strong
  concurrency/networking APIs for distributed algorithms and a consistent runtime across lab PCs and laptops.
  JUnit 5
  Modern testing features (parameterized tests, extensions); helps verify algorithm correctness deterministically (seeded runs).
  GitHub
  built-in code review/branch protection; easy collaboration without extra infra;
  GitHub Projects (Kanban)
  Visual planning tied to Issues/PRs; keeps workload transparent while balancing other courses, will be used once our features will be implemented
  Docker
  Docker allows each simulation node to run its own container with an isolated process, memory, network stack. It facilitates Transparency.
  React
  We use React because we need a highly interactive UI with live-updating visualization, metrics, and logs. React’s component model maps cleanly to our main views (config, visualization, metrics, logs), integrates well with our JSON-based SimulationControl API.
  2 Constraints
  2.1 Technical Constraints
  •
  The project must implement a real distributed system, not a simulation
  •
  A visualization of the distributed algorithm’s behavior must be implemented.
  •
  The visualization must be architecturally scalable, i.e., its design should allow adding further nodes without fundamental redesign.
  •
  All experiments must be controllable (parameterizable) and repeatable under identical configurations.
  •
  The system must follow scientific and engineering standards according to Tanenbaum and the lecture script by Prof. Dr. Martin Becke.
  •
  Documentation and implementation must match exactly; inconsistencies result in failure.
  •
  The system must be executable in the TI Lab (Room 07.65) and on local developer machines.
  2.2 Organizational Constraints
  •
  Team consists of four members.
  •
  Weekly report to be submitted.
  •
  Reports are written in rotation, and each member must submit at least two reports during the semester.
  •
  Reports must explicitly connect lecture content with the practical project, and direct discussion of lecture material is required.
  •
  A functional, prototype-level implementation must be delivered by the end of the semester.
  •
  The final evaluation of the project takes place in a dedicated review session at the end of the semester.
  3 Context & Scope
  3.1 Business Context (Actors & Interfaces)
  Purpose: show who uses the system, what they input, and what they get back.
  Communication Partner
  Role / Who
  Inputs (to system)
  Outputs (from system)
  Notes
  User (Student / Instructor / Evaluator)
  Humn actor
  Simulation config: N, topology, seed, algorithm; Start/Stop commands; import run configs
  Visualization updates; Logs; Metrics (convergence time, message count, avg messages/node, leader ID); Exported reports (CSV/Text)
  Primary external actor.
  Local Operator / TA
  Humn actor
  Test scenarios, hardware constraints, scheduled demos
  System logs, test reports
  Rare, mainly course staff.
  3.2 Technical Context (Neighbors, Protocols, Formats)
  Purpose: Technical interfaces (channels and transmission media) linking your system to its environment. In addition a mapping of domain specific input/output to the channels.
  Neighbour / Channel
  From
  To
  Protocol / Format
  Direction
  Notes
  Node <-> Node
  Node process
  Node process
  UDP socket (localhost in Phase 1; container bridge in Phase 2). Payload: JSON {sender, receiver, msgType, value, seq}
  bidirectional
  Core simulation traffic , flooding messages, ack.
  Backend/Collector <-> Web UI
  Simulator backend
  Web client
  WebSocket (live events). Payload: JSON events for node state, message events, metrics updates.
  backend -> (mostly)
  Real-time visualization and control feedback.
  UI Control <-> Backend
  Web client
  Backend
  HTTP/WebSocket control messages (start, stop, configure) JSON
  UI ->backend
  User issues start/stop/config commands.
  Filesystem export/import
  System
  Local filesystem
  CSV (metrics), TXT/JSON (logs and configs)
  system <-> filesystem
  Persisted runs, post-run analysis.
  Docker (Phase 2 networking)
  Host Docker network
  node containers
  Docker bridge network; each container has own UDP connections between containers
  bidirectional
  Phase 2 only; does not introduce external services.
  4 Solution Strategy
  4.1 Quality driven Architectural Approaches
  The following table summarizes our main architecture approaches to achieve the project’s quality goals:
  Goal/Motivation
  Architectural Approach
  Details
  Correctness
  Deterministic simulation with validation
  Leader election and other algorithms are verified against predefined correctness criteria. (e.g. for leader election: hast the selected node the highest id?) Section 10.2, SC6
  Extensibility / Adding New Algorithms (Openness)
  Strategy pattern with well-defined interfaces
  Algorithms implement a common interface or abstract class, DTOs define communication boundaries. New algorithms can be added without modifying visualization or runtime code. Section 10.2, SC2, SC3
  Interoperability Communication (Openness)
  JSON message protocol over UDP implemented in a MessagingPort (Middleware)
  Messages between nodes are exchanged as JSON. Any other format is rejected with an error. This ensures platform- and language-independent communication. Section 10.2, SC3
  Performance Scaling with Network Size (Scalability)
  Modular, distributed simulation engine
  Simulation engine supports horizontal scaling by adding more containers. Internal algorithms and data structures designed to follow O(D) convergence time relative to network diameter. Section 10.2, SC4
  Usability / Smooth UI
  React frontend
  Visualization updates in real time (<200ms) using WebSockets. UI is decoupled from simulation execution to prevent performance interference. Section 10.2, SC5
  Efficient Resource Utilization (Shared Resources)
  Container-based execution with resource limits
  Each simulation node runs in its own Docker container. CPU and memory limits are enforced via Docker resource constraints. Section 10.2, SC1
  4.2 Communication Model: Asynchronous, P2P
  Asynchronous and Transient Communication
  Nodes communicate exclusively through asynchronous, transient messages, delivered through the abstract MessagingPort.
  Instead of remote procedure calls, each node sends messages and reacts to incoming messages in an event-driven manner.
  Messages may be lost or reordered; no blocking request/response behaviour is required.
5. Building block view
   5.1 Public Interfaces
   The following interfaces describe the visible API contracts between the Simulation Core, Middleware and Observation Components.
- SimulationControl - SimulationEventBus - MessagingPort - Node - NodeContext - NodeAlgorithm
  Their complete definitions are listed here.
6. Runtime view
----------------------------------------------------------------------------------
----------------------------------------------------------------------------------
----------------------------------------------------------------------------------
----------------------------------------------------------------------------------
----------------------------------------------------------------------------------

7 Deployment view
This section describes the technical infrastructure required to execute the distributed algorithm simulator, including the physical and virtual deployment nodes, network connections, and the mapping of building blocks to the infrastructure. The simulator is designed as a real distributed system, where each logical node runs in its own isolated environment.
7.1 Diagram
7.2 Infrastructure & Topology
Docker Bridge Network
•
Provides virtual LAN between containers
•
Each node container obtains its own IP address
•
All node-to-node communication uses UDP sockets over this bridge
•
Jackson JSON is used as the universal message format
Communication Channels
•
WebSocket: simulation engine → browser (visualization updates, metrics, logs)
•
HTTP: browser → simulation engine (control operations via SimulationControl API)
•
Filesystem: simulation engine ↔ host system for configuration loading, metric export, and logging
8 Crosscutting Concepts
This section describes concepts that apply across multiple building blocks of the distributed algorithm simulator. They ensure conceptual integrity, reduce redundancy, and provide a consistent foundation for implementing algorithms, communication, visualization, logging, and analysis.
8.1 Communication Concept
Motivation Distributed algorithms rely on asynchronous, unreliable message delivery. Modeling this realistically is essential for correctness, transparency, and scientific fidelity.
Concept Summary All nodes communicate asynchronously, transiently, and peer-to-peer through an abstract communication interface (MessagingPort).
Key properties:
•
No blocking calls (messages are events, not RPCs).
•
Messages may be delayed or reordered, matching real distributed system behavior.
•
Nodes are equal peers — there is no central broker, master, or scheduler.
•
Delivery is best-effort; correctness must rely on algorithmic guarantees, not transport guarantees.
Relevant Structures
•
MessagingPort interface defines logical delivery.
•
Concrete implementations (Docker-bridge UDP) are plugged in via adapters.
•
Nodes operate on logical NodeId values; they never hold direct references to neighbors.
Crosscutting Impact
•
Simulation Core
•
Node Algorithm Implementations
•
Observation & Analysis (event streams)
•
Docker deployment model
8.2 Event-Driven Architecture
Motivation
Multiple subsystems require access to the internal behavior of the simulation without coupling to node logic (visualization, metrics, logging, debugging).
Concept Summary
The simulation is fundamentally event-driven. Every significant action inside the distributed system produces a SimulationEvent. These events are routed through a shared EventBus, which supports the Observer Pattern.
Event Types
MESSAGE_SENT
MESSAGE_RECEIVED
STATE_CHANGED
LEADER_ELECTED
ERROR
Consumers
Visualization module (WebSocket live updates) MetricsCollector Logging subsystem Export subsystem UI snapshot queries (pull and push)
Crosscutting Impact
All building blocks in the Observation & Analysis domain UI/WebSocket adapter Metrics (UC-07) Logs (UC-10) Visualisation (UC-06)
8.3 Message Format & Serialization Concept
Motivation Both node-to-node traffic and backend-to-UI communication require a universal, language-agnostic, human-readable format.
Concept Summary All communication uses strict Jackson JSON payloads with documented fields (sender, receiver, msgType, value, seq …) as defined in the technical context .
Rules Non-JSON data is rejected with a clear error (Quality Scenario SC3). Messages remain consistent across different transports and environments. Same DTOs are used for UI communication, exports, and debugging. Crosscutting Impact Nodes MessagingPort UI adapters Logs Exports Algorithm implementations
8.4 SimulationControl Façade Concept
Motivation The simulator exposes a single, unified control surface to the UI and external tools. This is essential for simplicity, correctness, and consistency.
Concept Summary A Façade Pattern groups all operations needed for running simulations (UC-01 to UC-10) into one interface (SimulationControl). This approach avoids exposing internal building blocks and prevents “resource-oriented leakage” into the UI.
Responsibilities Network initialization Algorithm selection Start/pause/resume/stop Metrics & logs access Export & import of configs Visualization snapshots Crosscutting Impact UI design Simulation Management layer EventBus integration Metrics & Logging Save/load formats
8.5 Algorithm Extensibility Concept
Motivation
The system must easily support additional distributed algorithms beyond max-ID leader election.
Concept Summary
All algorithms implement a shared Strategy Pattern: a common interface defining how nodes initialize and process inbound/outbound events.
Rules
Algorithms must be fully encapsulated: they must not modify MessagingPort, EventBus, or UI logic.
They can rely on events, node state, and MessagingPort only.
AlgorithmFactory instantiates strategies based on the selected AlgorithmId.
Crosscutting Impact
Node implementations
SimulationCore
Metrics & correctness checks
Algorithm registry and configuration
Academic extensibility goals (Quality Scenario SC2)
8.6 Visualization Concept
Motivation
Visualization is one of the core goals of the system: it must be smooth, and must not affect algorithm behavior.
Concept Summary
Visualization is updated through non-intrusive event subscription. It is strictly read-only and maintains no causal influence over simulation timing or message delivery.
Details
UI receives updates via WebSocket (push) and snapshot queries (pull).
Rendering must occur <200ms after event production (SC5, SC7).
Visualization state and Simulation Core state are cleanly separated.
Crosscutting Impact
EventBus
UI
SimulationControl
Metrics/logging (indirect timing dependencies)
8.7 Logging & Error Handling Concept
Motivation
Errors and runtime logs are essential for debugging distributed algorithms and evaluating system behavior.
Concept Summary
All subsystems log events through a shared logging interface.
Error logs are also published via SimulationEvent(type = ERROR).
Logs are accessible synchronously through the façade (UC-10).
Crosscutting Impact
SimulationCore
MessagingPort
Node algorithms
UI log viewer
Export subsystem
Quality Scenario SC7 (Transparency)
8.8 Metrics & Performance Measurement Concept
Motivation
Metrics (message count, rounds, convergence time, leader correctness) are central to evaluating the correctness and complexity of distributed algorithms.
Concept Summary
MetricsCollector subscribes to the EventBus and updates counters.
Metrics are aggregated into immutable MetricsSnapshot objects.
Metric extraction does not block nodes and does not influence run timing (SC1).
Works uniformly across all algorithms.
Crosscutting Impact
EventBus
Algorithm logic
Visualization (numeric overlays)
Export functionality
UC-07 Metrics
8.9 Configuration, Import & Export Concept
Motivation
Experiment reproducibility, lecture exercises, and testing require loading, saving, and exporting consistent configurations.
Concept Summary
Configurations use typed DTOs: NetworkConfig, SimulationParameters, SimulationConfig.
Export subsystem produces JSON/CSV based on SimulationEvents & MetricsSnapshots.
Import restores complete simulation session state (UC-08 & UC-09).
Crosscutting Impact
SimulationControl
UI
EventBus data
Metrics and message-log structures
Academic reproducibility
8.10 Docker-Based Node Isolation Concept
Motivation
To mirror real distributed systems and ensure scalability, each node must execute in an isolated environment.
Concept Summary
Each logical node maps to one Docker container.
Containers communicate over Docker's virtual network via UDP.
Performance can be controlled via CPU/memory limits (supports SC1).
Crosscutting Impact
MessagingPort implementations
DevOps & deployment
Performance and scalability testing
Experiment reproducibility
9 Architecture Decisions
Title
Context
Decision
Consequences
ADR 1 – Façade Pattern for UI
UI needs access to complex subsystems (SimulationCore, Nodes, MessagingPort, EventBus, metrics, logging, visualization).
Introduce a Façade Pattern to provide a simplified API for simulation lifecycle operations.
Pro: Simplifies UI integration, hides internal complexity, Con: Central dependency (Façade must be updated if subsystems change)
ADR 2 – Observer Pattern for Events, Visualization, Metrics, Logging
Multiple subsystems must react to simulation events.
Implement EventBus using Observer Pattern; nodes publish events, observers subscribe (visualization, metrics, logs).
Pro: Loose coupling, easy to extend, clear separation of concerns
Con: Debugging harder, event ordering issues possible
ADR 3 – Strategy Pattern for Algorithm Extensibility
System must support multiple distributed algorithms without changing UI or infrastructure.
Introduce Strategy Pattern with AlgorithmFactory
Pro: Modular, adding new algorithms is easy, clear boundaries
Con: Careful state management needed, possible code duplication
ADR 4 – Docker-Based Node Isolation
Simulate real distributed execution with process, network, and memory isolation.
Each node runs in a separate Docker container (bridge mode).
Pro: Realistic network behavior, enables performance testing
Con: Higher overhead, requires Docker maintenance
10 Quality
10.1 Quality Table
Quality
Description
Scenario
Openness
New network algorithms can be added without modifying runtime or visualization (plugin API, DTO boundary).
SC2, SC3
Transparency
Runs are traceable and reproducible (seeded runs, event stream, exports).
SC7
Shared Resources
CPU/RAM/IO usage controlled; UI does not distort algorithm timing.
SC1
Distribution Transparency
The system behaves like a single unified unit from the user’s perspective.
SC7
Scalability
Behavior follows theoretical complexity: time ≈ O(D), messages ≈ O(E).
SC4
Correctness
The system produces correct results.
SC6
Usability
The system provides smooth user experience.
SC5
10.2 Quality Scenarios
•
SC1 – Efficient resource utilization for larger simulations (Shared Resources) Trigger: The system runs a simulation with 50-100 nodes. Response: CPU and memory usage remain within acceptable bounds Acceptance: a) CPU usage < 90 % of available cores b) Memory usage < 2GB for the simulation backend
•
SC2 – Extension through interfaces (Openness) Trigger: A developer wants to add a new algorithm. Response: A new algorithm is being implemented using the provided interface. Visualization and API do not need to be changed. Acceptance: The API remains unchanged, and the integration requires less than a working day.
•
SC3 – Communication via JSON (Openness)
Trigger: A new component on a different OS and in a different language wants to control simulations or retrieve results.
Response: The component communicates with the SimulationControl API via HTTP/JSON.The backend correctly interprets incoming JSON requests and returns
JSON responses according to the public DTOs. Acceptance: Requests in the documented JSON format are correctly parsed and processed. Invalid or non-JSON payloads are rejected with a clear error response (HTTP 4xx), without crashing the system.
•
SC4 – Performance growth with network size (Scalability) Trigger: The user increases the number of nodes N from 10 to 100. Response: The convergence time grows at most linear to the network diameter D.
Acceptance: Convergence time T(N) ∈ O(D).
•
SC5 – Starting an algorithm via the user interface (Usability) Trigger: The user presses the “start algorithm” button. Response: The system starts the algorithm and visualizes the algorithm on the user interface.
Acceptance: The on-screen update takes less than 200ms after the button click event.
•
SC6 – Leader Election (flood based) shows correct results (Correctness) Trigger: A simulation run with leader election completes and a leader node has been chosen. Response: The system verifies the result against the correctness criteria (e.g. highest ID)
Acceptance: The algorithm elects exactly one leader. The leader satisfies the correctness criteria. correctness criteria = leaderId = max(NodeId in topology) If the leader doesn’t satisfy the correctness criteria, a warning message is visualized.
•
SC7 – Visualization of the algorithm (Transparency) Trigger: A simulation has been started. Response: The system displays the current states of all nodes and the message flow in real time without affecting the execution of the simulation.
Acceptance: The UI updates node states and message flow within 200ms of an event occurring. Accessing the visualization does not modify the simulation’s internal state or metrics.