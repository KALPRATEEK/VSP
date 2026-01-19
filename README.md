# Project: Flooding-Based Leader Election as a Distributed System

**Module:** Distributed Systems  
**Team:** Elias Schmidt, Rodrigo Morales, Prateek Kalra, Mario Hansen  

## Overview

This project implements a distributed algorithm simulator with support for:
- ✅ Flooding-based leader election algorithm
- ✅ Multiple network topologies (ring, line, grid, random)
- ✅ **Dual execution modes**: Virtual (in-memory) and UDP-Docker (distributed)
- ✅ REST API for simulation control
- ✅ Real-time visualization
- ✅ Metrics and observability

## Quick Start

### Virtual Mode (Development/Teaching)
```bash
# Single container with in-memory nodes
docker-compose up --build

# Access UI
open http://localhost:8080
```

### Distributed Mode (Lab/Research)
```bash
# Multi-container with real UDP networking
docker-compose -f docker-compose-distributed.yml up --build

# Access UI
open http://localhost:8080
```

## Documentation

- **[DEPLOYMENT.md](DEPLOYMENT.md)** - Complete deployment guide for both modes
- **[documentation/API description.md](documentation/API%20description.md)** - API specification
- **[middleware/README-contract.md](middleware/README-contract.md)** - Middleware contract
- **[documentation/runtime views.md](documentation/runtime%20views.md)** - Runtime scenarios

## Architecture

```
┌─────────────┐
│   Browser   │ ← React Frontend
└──────┬──────┘
       │ HTTP/REST
┌──────┴──────┐
│   Control   │ ← Spring Boot REST API
│   (Backend) │
└──────┬──────┘
       │
┌──────┴──────┐
│   Engine    │ ← Simulation Engine
└──────┬──────┘
       │
┌──────┴──────┐
│ Middleware  │ ← MessagingPort (Virtual or UDP)
└─────────────┘
       │
    [Nodes] ← SimulationNodes with Algorithms
```

## Execution Modes

| Mode | Use Case | Characteristics |
|------|----------|----------------|
| **Virtual** | Development, Teaching | In-memory, fast, easy debugging |
| **UDP-Docker** | Lab, Research | Real UDP, realistic network behavior |

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed information.

## Building & Testing

```bash
# Build all modules
mvn clean package

# Run tests
mvn test

# Run specific module tests
mvn test -pl engine
```

## Project Structure

```
VSP/
├── core/           # Domain models, events, interfaces
├── middleware/     # MessagingPort implementations
├── engine/         # Simulation engine, nodes, algorithms
├── control/        # REST API, Spring Boot application
├── frontend/       # React UI (optional)
└── documentation/  # Architecture and API docs
```

## Weekly Report Schedule

| Submission Deadline    | Responsible Person | Status    |
|------------------------|--------------------|-----------|
| 29.10.2025 – 23:59 Uhr | Mario              | Submitted |
| 05.11.2025 – 23:59 Uhr | Elias              | Submitted |
| 12.11.2025 – 23:59 Uhr | Rodrigo            | Submitted |
| 19.11.2025 – 23:59 Uhr | Prateek            | Submitted |
| 26.11.2025 – 23:59 Uhr | Mario              | Submitted |
| 03.12.2025 – 23:59 Uhr | Elias              | Submitted |
| 10.12.2025 – 23:59 Uhr | Rodrigo            | Submitted |
| 17.12.2025 – 23:59 Uhr | Prateek            | Submitted |
| 07.01.2026 – 23:59 Uhr | Mario              | Submitted |
| 14.01.2026 – 23:59 Uhr | Elias              | Submitted |

## License

Academic project for HAW Hamburg - Distributed Systems course.

