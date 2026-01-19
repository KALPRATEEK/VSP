# Deployment Guide: Virtual vs. Distributed Modes

This document explains how to run the distributed algorithm simulator in different execution modes.

## Overview

The simulator supports **two execution modes**, configurable via the `MW_MODE` environment variable:

### 1. **Virtual Mode** (Default)
- **Use Case**: Development, testing, teaching, single-machine demos
- **Characteristics**:
  - All nodes run in the same JVM process
  - In-memory message passing (very fast)
  - No network overhead
  - Easy to debug and visualize
  - Supports fault injection for testing

### 2. **UDP-Docker Mode**
- **Use Case**: Lab experiments, true distributed execution, realistic network conditions
- **Characteristics**:
  - Each node runs in a separate Docker container
  - Real UDP networking between containers
  - True distributed system behavior
  - Network delays and packet loss (realistic)
  - Scalable to many containers

---

## Mode 1: Virtual Mode (In-Memory)

### Configuration

```bash
# Environment variable (optional, this is the default)
export MW_MODE=virtual
```

### Running with Docker Compose

```bash
# Standard docker-compose (single backend container)
docker-compose up --build
```

### What happens:
- Single backend container starts
- REST API available at `http://localhost:8080`
- All simulation nodes run in-memory within the backend
- Fast execution, ideal for development and UI testing

### Advantages:
✅ Simple deployment (one container)  
✅ Fast execution  
✅ Easy debugging  
✅ Perfect for teaching and visualization  
✅ Supports fault injection (packet loss, delays, reordering)

### When to use:
- Development and testing
- Teaching distributed algorithms
- UI/visualization development
- Quick algorithm prototyping

---

## Mode 2: UDP-Docker Mode (Distributed)

### Configuration

Each node container requires these environment variables:

```bash
MW_MODE=udp-docker          # Enable UDP mode
NODE_ID=node-0              # Unique node identifier (e.g., node-0, node-1, ...)
UDP_PORT=9000               # UDP port for communication (default: 9000)
HOST_TEMPLATE={ID}          # Docker hostname pattern (default: {ID})
NODE_COUNT=5                # Total number of nodes (optional, for bounded config)
```

### Running with Docker Compose

```bash
# Distributed multi-container setup
docker-compose -f docker-compose-distributed.yml up --build
```

### What happens:
1. **Backend container** (vsp-backend):
   - REST API server
   - Runs in `virtual` mode
   - Coordinates simulations
   - Available at `http://localhost:8080`

2. **Node containers** (node-0, node-1, ..., node-4):
   - Each runs in `udp-docker` mode
   - Communicate via UDP over Docker bridge network
   - Hostname = Container name = Node ID
   - Real network delays and characteristics

### Architecture:

```
┌─────────────────┐
│   Browser/UI    │
│  (localhost)    │
└────────┬────────┘
         │ HTTP/REST
         ↓
┌─────────────────┐
│  vsp-backend    │
│  (virtual mode) │
│    Port 8080    │
└─────────────────┘
         
Docker Network (172.28.0.0/16)
         
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    node-0       │  │    node-1       │  │    node-2       │
│ (udp-docker)    │  │ (udp-docker)    │  │ (udp-docker)    │
│   Port 9000     │  │   Port 9000     │  │   Port 9000     │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┴────────────────────┘
                         UDP Messages
```

### Advantages:
✅ True distributed execution  
✅ Real network behavior (latency, packet loss)  
✅ Each node is isolated  
✅ Realistic for research and experimentation  
✅ Can scale to many containers

### When to use:
- Lab experiments with realistic network conditions
- Research on distributed algorithms
- Testing network partition scenarios
- Performance benchmarking under real network constraints

---

## Environment Variables Reference

### Common Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MW_MODE` | No | `virtual` | Execution mode: `virtual` or `udp-docker` |

### Virtual Mode Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `QUEUE_OUT_CAPACITY` | No | 1024 | Outbound queue size |
| `QUEUE_IN_CAPACITY` | No | 1024 | Inbound queue size |
| `QUEUE_OVERFLOW_POLICY` | No | `DROP_NEWEST` | Policy: `DROP_NEWEST`, `DROP_OLDEST`, `BLOCK` |
| `VIRTUAL_DROP_PROB` | No | 0.0 | Fault injection: packet drop probability (0.0-1.0) |
| `VIRTUAL_DELAY_MS` | No | 0 | Fault injection: message delay in milliseconds |
| `VIRTUAL_REORDER_WINDOW` | No | 0 | Fault injection: reordering window size |
| `VIRTUAL_DUP_PROB` | No | 0.0 | Fault injection: duplication probability (0.0-1.0) |

### UDP-Docker Mode Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `NODE_ID` | **Yes** | - | Unique node identifier (e.g., `node-0`) |
| `UDP_PORT` | No | 9000 | UDP port for node communication |
| `HOST_TEMPLATE` | No | `{ID}` | Hostname template. `{ID}` = NodeId value |
| `NODE_COUNT` | No | - | Number of nodes (enables bounded validation) |
| `MIN_ID` | No | 0 | Minimum node ID (for bounded mode) |
| `PEERS` | No | - | Explicit peers: `node-0:node-0:9000,node-1:node-1:9000,...` |

---

## Testing the Setup

### Virtual Mode

```bash
# Start the backend
docker-compose up

# Check health
curl http://localhost:8080/actuator/health

# Create a simulation via REST API
curl -X POST http://localhost:8080/api/simulations \
  -H "Content-Type: application/json" \
  -d '{"nodeCount": 5, "topologyType": "RING"}'
```

### UDP-Docker Mode

```bash
# Start all containers
docker-compose -f docker-compose-distributed.yml up

# Check backend health
curl http://localhost:8080/actuator/health

# Check node logs
docker logs vsp-node-0
docker logs vsp-node-1

# Verify UDP connectivity (from one node to another)
docker exec vsp-node-0 ping node-1
```

---

## Customizing Node Count

### For distributed mode, you can scale nodes:

1. **Edit `docker-compose-distributed.yml`**:
   - Add more `node-X` services
   - Update `NODE_COUNT` environment variable in all nodes
   - Ensure hostnames match NodeId (e.g., `hostname: node-5` for `NODE_ID=node-5`)

2. **Example for 10 nodes**:
```yaml
services:
  backend:
    # ... (unchanged)
  
  node-0:
    environment:
      - NODE_COUNT=10  # Update this
      # ... other vars
  
  # ... node-1 through node-9 ...
```

---

## Troubleshooting

### Virtual Mode Issues

**Problem**: Simulation runs but no events are visible  
**Solution**: Check that event publisher is configured correctly in backend

**Problem**: Tests fail with "queue full" errors  
**Solution**: Increase `QUEUE_OUT_CAPACITY` or `QUEUE_IN_CAPACITY`

### UDP-Docker Mode Issues

**Problem**: Nodes can't reach each other  
**Solution**: 
- Verify all nodes are on the same Docker network
- Check that `hostname` matches `NODE_ID`
- Ensure UDP port 9000 is exposed

**Problem**: `NODE_ID environment variable is required` error  
**Solution**: Add `NODE_ID` to environment variables in docker-compose

**Problem**: Messages not being delivered  
**Solution**:
- Check docker logs for networking errors
- Verify `HOST_TEMPLATE` is set correctly (default `{ID}` should work)
- Test connectivity: `docker exec node-0 ping node-1`

---

## Development Workflow

### Recommended workflow:

1. **Develop with Virtual Mode**
   - Fast iteration
   - Easy debugging
   - Use local IDE or single Docker container

2. **Test with UDP-Docker Mode**
   - Verify realistic network behavior
   - Test edge cases (packet loss, delays)
   - Run integration tests

3. **Deploy Production**
   - Choose mode based on requirements:
     - Teaching/Demo → Virtual Mode
     - Research/Lab → UDP-Docker Mode

---

## Performance Considerations

### Virtual Mode:
- ⚡ Very fast (in-memory, no serialization overhead)
- 📊 Can simulate thousands of messages per second
- 💾 Low memory overhead
- 🔧 Best for algorithm development

### UDP-Docker Mode:
- 🐌 Slower (real network, serialization, container overhead)
- 📊 Hundreds of messages per second (realistic)
- 💾 Higher memory overhead (one JVM per node)
- 🔬 Best for realistic experiments

---

## Next Steps

- **For Teaching**: Use virtual mode with fault injection to simulate network failures
- **For Research**: Use UDP-Docker mode to study real distributed behavior
- **For Development**: Start with virtual mode, validate with UDP-Docker mode

For more details, see:
- `middleware/README-contract.md` - Middleware specification
- `documentation/API description.md` - API documentation
- `docker-compose.yml` - Virtual mode configuration
- `docker-compose-distributed.yml` - UDP-Docker mode configuration
