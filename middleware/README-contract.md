# Middleware Contract (Single Source of Truth) — v2.0

This document defines the **authoritative contract** for the middleware. All other components (engine, node algorithms, UI/metrics/logging) must align to this contract.

Supported execution modes:
- **`virtual`** — local, in-memory “virtual network” for fast dev/tests
- **`udp-docker`** — real UDP networking across Docker containers (one node per container)

Everything below applies to **both** modes unless explicitly noted.ted.

---

## 1. Goals & Non-Goals

### Goals
- Provide **peer-to-peer**, **event-driven**, **asynchronous**, **transient** messaging between nodes.
- Scale to **many nodes** with **bounded resources** (queues, worker pools).
- Expose observability through **SimulationEvents** (sent/received/error).

### Non-Goals
- No reliability guarantees (no ACKs, no retries, no exactly-once).
- No ordering guarantees.
- No RPC semantics / request-response blocking.
- No persistence / durability.

---

## 2. Public API Contract

### MessagingPort
Operations:
- `send(NodeId receiver, SimulationMessage message)`
- `broadcast(Set<NodeId> receivers, SimulationMessage message)`
- `registerHandler(NodeId nodeId, MessageHandler handler)`
- `unregisterHandler(NodeId nodeId)`

**Semantics (authoritative):**
- `send` / `broadcast` are **asynchronous**: they enqueue work and return immediately.
- **`send(...)` returns `true` iff the message was accepted by the middleware** (e.g., successfully enqueued for transmission).
- If `send(...)` returns `false`, the message was **dropped immediately** (e.g., queue full, unknown receiver, serialization failure), and an **ERROR** event MUST be emitted (if a publisher is attached).
- `registerHandler(nodeId, handler)` defines where inbound messages for `nodeId` are delivered.
- If a receiver has **no registered handler**, the message is **dropped** and an **ERROR** event SHOULD be emitted (if a publisher is attached).

### MessageHandler
- `onMessage(SimulationMessage msg)` is invoked **asynchronously** by middleware.
- Algorithms must tolerate **loss/reorder/duplication**.

---

## 3. Identity & Addressing

### NodeId (identity)
- `NodeId` is an **opaque string identifier**.
- **Project convention (required):** `NodeId.value` MUST match:
    - `node-(\d+)` (e.g., `node-0`, `node-1`, ...)

### NodeId ordering (required for leader election correctness)
Any logic that compares node IDs for “max” MUST compare by the **numeric suffix**:
- `node-2 < node-10` (numeric ordering)
- **Not** lexicographic string ordering.

### Address resolution (`udp-docker`)
- **Docker rule (required):** each node must be reachable by DNS hostname equal to its `NodeId.value`.
- Default mapping:
    - `host = receiver.value`
    - `port = UDP_PORT` (same port for all nodes inside the Docker network)

---

## 4. Message Model & Wire Schema

### SimulationMessage (authoritative fields)
- `sender: NodeId` (required)
- `receiver: NodeId` (required)
- `messageType: String` (required, non-blank)
- `payload: Object` (optional, must be JSON-serializable)
- `seq: Long` (optional, if present must be >= 0)

### JSON encoding rules
- Node-to-node transport uses **Jackson JSON**.
- Decode failures / invalid messages:
    - Must be **dropped**
    - Must emit **ERROR** (if publisher attached)

Compatibility:
- Unknown JSON fields MAY be ignored.
- Required fields must exist and validate.

---

## 5. Delivery Semantics (Best-Effort)

Middleware provides **best-effort** delivery:

Always true:
- Messages may be **dropped**, **delayed**, **reordered**, **duplicated**.
- No acknowledgments.
- No retransmission by middleware.

---

## 6. Observability (Simulation Events)

If an event publisher is attached, middleware emits:

- **MESSAGE_SENT**  
  Emitted **only when a message has been accepted by the middleware** (i.e., `send(...)` returned `true`).

- **MESSAGE_RECEIVED**  
  Emitted when a message is delivered to the registered handler.

- **ERROR**  
  Emitted on:
  - immediate send rejection (e.g., `send(...)` returned `false`)
  - invalid message fields
  - missing handler
  - queue overflow or block timeout
  - decode errors
  - transport or socket failures

---

## 7. Queues, Backpressure, Bounded Resources

Middleware must be bounded-memory:
- Bounded outbound queue and bounded inbound queue (or equivalent).

`QueueOverflowPolicy`:
- `DROP_NEWEST`
- `DROP_OLDEST`
- `BLOCK` (bounded by timeout)

Rules:
- `send()` MUST NOT block indefinitely.
- If `BLOCK` is enabled, max block time is `QUEUE_BLOCK_TIMEOUT_MS`.
- On enqueue failure or timeout:
  - message is dropped
  - **ERROR** event MUST be emitted

---

## 8. Concurrency & Handler Execution

Thread safety:
- `send`, `broadcast`, `registerHandler`, `unregisterHandler` must be safe concurrently.

Handler execution:
- For a given receiver node, middleware SHOULD deliver **serially** (one-at-a-time).
- Different receiver nodes MAY execute concurrently.

Note: serial delivery does **not** imply ordering guarantees due to allowed delay/reorder faults.

---

## 9. Mode: `virtual` (Local Virtual Network)

Purpose:
- Fast local execution while mimicking distributed semantics.

Requirements:
- Delivery must be **asynchronous** (no inline direct call).
- Transient behavior (drops if no handler, bounded queues).
- **Serialization boundary (required):**
    - Encode message -> JSON bytes -> decode back before handler delivery.

Fault injection (optional, configurable):
- drop probability
- delay (fixed or min/max)
- reordering window
- duplication probability
- seeded randomness for reproducibility

Isolation:
- A virtual network instance MUST be per-simulation (no global/static registry leaking across tests).

---

## 10. Mode: `udp-docker` (Real Distributed)

Purpose:
- Real distributed execution across Docker containers.

Requirements:
- One node process per container.
- One `MessagingPort` instance per container bound to its local `NODE_ID`.
- UDP socket binds to `UDP_PORT` and receives JSON datagrams.

**Validation (required):**
- `receiver` argument must equal `message.receiver`  
  → otherwise: drop + **ERROR**
- `message.sender` must equal local `NODE_ID`  
  → otherwise: drop + **ERROR**
- Inbound UDP datagrams addressed to a different `receiver` than the local node  
  → MUST be dropped + **ERROR**

---

## 12. Error Handling

Errors are **observable but non-fatal**:
- Middleware MUST NOT crash on malformed input or network failures.
- Errors affect only the dropped message.
---

## 12. Configuration Keys

### Required
- `MW_MODE`: `virtual` | `udp-docker`

### Required for `udp-docker`
- `NODE_ID`: e.g., `node-7`
- `UDP_PORT`: e.g., `9000`

### Queueing (both modes)
- `QUEUE_OUT_CAPACITY` (default: 1024)
- `QUEUE_IN_CAPACITY` (default: 1024)
- `QUEUE_OVERFLOW_POLICY` (default recommendation: `DROP_NEWEST`)
- `QUEUE_BLOCK_TIMEOUT_MS` (only if policy = `BLOCK`)

### Virtual fault injection (optional, `virtual` only)
- `VIRTUAL_DROP_PROB` (0..1)
- `VIRTUAL_DELAY_MS` or (`VIRTUAL_DELAY_MIN_MS`, `VIRTUAL_DELAY_MAX_MS`)
- `VIRTUAL_REORDER_WINDOW`
- `VIRTUAL_DUP_PROB`
- `VIRTUAL_SEED`

---

## 13. Compliance Checklist (Refactor Guardrails)

Refactors must preserve:
1. API semantics for `MessagingPort`
2. Boolean `send()` acceptance semantics
3. Message schema: `sender/receiver/messageType/payload/seq`
4. Best-effort delivery (loss/reorder/dup allowed)
5. Event emission points: sent / received / error
6. Numeric NodeId ordering by suffix (not lexicographic)
7. `virtual` mode crosses a JSON serialization boundary
8. `udp-docker` resolves `NodeId.value` via Docker DNS hostname + common `UDP_PORT`
