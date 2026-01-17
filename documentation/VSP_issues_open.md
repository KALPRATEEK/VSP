# VSP – Issues Export (Aktualisiert)

_Letztes Update: 2026-01-16 (manuell überarbeitet)_

_Source: GitHub repo KALPRATEEK/VSP (export via GitHub CLI)_

_Count: 44 issues (24 abgeschlossen, 20 offen) – **55% Fortschritt**_


---

## ✅ ABGESCHLOSSENE ISSUES (Application Layer)

Die folgenden Issues wurden bereits vollständig implementiert:

### ✅ Domain Types (A1-A5) – 5 Issues
- ✅ #18 – ISSUE A5 – NetworkConfig [COMPLETED]
- ✅ #19 – ISSUE A4 – SimulationParameters [COMPLETED]
- ✅ #21 – ISSUE A2 – SimulationConfig [COMPLETED]
- ✅ #22 – ISSUE A1 – SimulationEvent [COMPLETED]
- ✅ #20 – ISSUE A3 – MetricsSnapshot [COMPLETED]

### ✅ Core Execution Layer (E1-E3) – 3 Issues
- ✅ #39 – ISSUE E1 – Node [COMPLETED]
- ✅ #40 – ISSUE E2 – NodeContext [COMPLETED]
- ✅ #41 – ISSUE E3 – Flooding Leader Election [COMPLETED]

### ✅ Event System (D1-D2) – 2 Issues
- ✅ #37 – ISSUE D1 – SimulationEventPublisher [COMPLETED]
- ✅ #38 – ISSUE D2 – SimulationEventBus [COMPLETED]

### ✅ SimulationEngine (C1-C2) – 2 Issues
- ✅ #35 – ISSUE C1 – SimulationEngine [COMPLETED]
- ✅ #36 – ISSUE C2 – SimulationEventPublisher anbinden [COMPLETED]

### ✅ SimulationControl (B1-B12) – 12 Issues
- ✅ #23 – ISSUE B1 – initializeNetwork [COMPLETED]
- ✅ #24 – ISSUE B2 – selectAlgorithm [COMPLETED]
- ✅ #25 – ISSUE B3 – startSimulation [COMPLETED]
- ✅ #26 – ISSUE B4 – pauseSimulation / resumeSimulation [COMPLETED]
- ✅ #27 – ISSUE B5 – stopSimulation [COMPLETED]
- ✅ #28 – ISSUE B6 – getCurrentVisualization [COMPLETED]
- ✅ #29 – ISSUE B7 – registerVisualizationListener [COMPLETED]
- ✅ #30 – ISSUE B8 – getMetrics [COMPLETED]
- ✅ #31 – ISSUE B9 – getCurrentConfig [COMPLETED]
- ✅ #32 – ISSUE B10 – loadConfig [COMPLETED]
- ✅ #33 – ISSUE B11 – exportRunData [COMPLETED]
- ✅ #34 – ISSUE B12 – getLogs [COMPLETED]

### ✅ Middleware – 1 Issue
- ✅ InMemoryMessagingPort [COMPLETED] (für Tests)

**Status: 24/44 Issues sind abgeschlossen (55% Fortschritt)**

---

## 🎯 EMPFOHLENE IMPLEMENTIERUNGSREIHENFOLGE (Offene Issues)

### ~~Phase 1: SimulationEngine & Core Execution~~ ✅ ABGESCHLOSSEN
~~1. ⭐ **#35 (C1)** – SimulationEngine implementieren~~ ✅
~~2. ⭐ **#36 (C2)** – SimulationEventPublisher anbinden~~ ✅

### ~~Phase 2: SimulationControl Façade~~ ✅ ABGESCHLOSSEN
~~3. **#24 (B2)** – selectAlgorithm implementieren~~ ✅
~~4. **#25 (B3)** – startSimulation implementieren~~ ✅
~~5. **#26 (B4)** – pauseSimulation / resumeSimulation~~ ✅
~~6. **#27 (B5)** – stopSimulation~~ ✅

### ~~Phase 3: Observation & Metrics (MITTEL)~~ ✅ ABGESCHLOSSEN
~~7. **#30 (B8)** – getMetrics~~ ✅
~~8. **#28 (B6)** – getCurrentVisualization~~ ✅
~~9. **#29 (B7)** – registerVisualizationListener~~ ✅
~~10. **#34 (B12)** – getLogs~~ ✅

### ~~Phase 4: Save / Load / Export (NIEDRIG)~~ ✅ ABGESCHLOSSEN
~~11. **#31 (B9)** – getCurrentConfig~~ ✅
~~12. **#32 (B10)** – loadConfig~~ ✅
~~13. **#33 (B11)** – exportRunData~~ ✅

### Phase 5: UI Layer (Nach Application Layer) ⭐ **NÄCHSTE PRIORITÄT**
14. **UI Issues #42-49** (UI-1 bis UI-8) ⭐ **START HIER**

### Middleware (wird von anderen umgesetzt)
- **#50-56** (MT1, MT2, MM1-MM5) – Nach unten sortiert

---

## 📊 STATUS-ÜBERSICHT

| Kategorie | Abgeschlossen | Offen | Gesamt |
|-----------|---------------|-------|--------|
| **Domain Types (A)** | 5 | 0 | 5 |
| **Core Execution (E)** | 3 | 0 | 3 |
| **Event System (D)** | 2 | 0 | 2 |
| **SimulationEngine (C)** | 2 | 0 | 2 |
| **SimulationControl (B)** | 12 | 0 | 12 |
| **UI Layer** | 0 | 8 | 8 |
| **Middleware** | 1 | 6 | 7 |
| **Setup/Doku** | 0 | 5 | 5 |
| **GESAMT** | **24** | **20** | **44** |

**Fortschritt: 55% abgeschlossen (24/44)**

---

## 🔴 OFFENE ISSUES – NACH PRIORITÄT SORTIERT

---

### 🎯 PHASE 1: SimulationEngine & Core Execution ✅ ABGESCHLOSSEN

---

## ✅ #35 – ISSUE C1 – SimulationEngine implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/35
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung der zentralen SimulationEngine.

**Inhalt:**
- Erzeugung der Nodes ✅
- Konfiguration des Algorithmus ✅
- Simulation Loop ✅
- Pause / Resume / Stop ✅
- `maxSteps` ⇒ stop ✅

**Akzeptanzkriterien:**
- Lifecycle ist korrekt ✅
- Simulation ist deterministisch steuerbar ✅

**Implementierung:**
- `engine/src/main/java/de/haw/vsp/simulation/engine/DefaultSimulationEngine.java`
- `engine/src/test/java/de/haw/vsp/simulation/engine/DefaultSimulationEngineTest.java`
- Alle Tests passing ✅

**Referenced Documentation:**
- Application Documentation § 5.2 SimulationEngine
- Application Documentation § 8.2 Node Lifecycle

---

## ✅ #36 – ISSUE C2 – SimulationEventPublisher anbinden [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/36
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Anbindung eines EventPublishers an die SimulationEngine.

**Inhalt:**
- Weiterleitung aller SimulationEvents ✅
- Entkopplung von konkreten Listenern ✅

**Akzeptanzkriterien:**
- Engine bleibt beobachtbar ✅
- Keine Abhängigkeit zu UI oder Logging ✅

**Implementierung:**
- `DefaultSimulationEngine.setEventPublisher()` implementiert
- Event-Publishing in Simulation Loop integriert
- Alle Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.4 SimulationEventPublisher
- Application Documentation § 7 Event-Driven Architecture

---

### 🎯 PHASE 2: SimulationControl Façade ✅ ABGESCHLOSSEN

---

## ✅ #24 – ISSUE B2 – selectAlgorithm implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/24
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.selectAlgorithm(...)`.

**Inhalt:**
- Auswahl und Konfiguration des Algorithmus ✅
- Validierung der AlgorithmId ✅

**Akzeptanzkriterien:**
- Algorithmus ist vor `startSimulation` festgelegt ✅
- Ungültige AlgorithmId wird abgelehnt ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- Alle Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-02

---

## ✅ #25 – ISSUE B3 – startSimulation implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/25
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.startSimulation(...)`.

**Inhalt:**
- Asynchroner Start der Simulation ✅
- Übergabe der SimulationParameters ✅
- Initialisierung von Metrics und Events ✅

**Akzeptanzkriterien:**
- Methode kehrt sofort zurück ✅
- Simulation läuft im Hintergrund ✅
- `maxSteps` wird berücksichtigt ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- Alle Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-03

---

## ✅ #26 – ISSUE B4 – pauseSimulation / resumeSimulation implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/26
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung der Pause- und Resume-Funktionalität.

**Inhalt:**
- Zustandswechsel der Simulation ✅
- Erhalt des internen Zustands ✅

**Akzeptanzkriterien:**
- Simulation kann pausiert und fortgesetzt werden ✅
- Kein Zustandsverlust ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- `engine/src/main/java/de/haw/vsp/simulation/engine/DefaultSimulationEngine.java`
- Alle Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-03

---

## ✅ #27 – ISSUE B5 – stopSimulation implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/27
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.stopSimulation(...)`.

**Inhalt:**
- Sauberes Beenden der Simulation ✅
- Finalisierung der Metriken ✅

**Akzeptanzkriterien:**
- Simulation endet deterministisch ✅
- MetricsSnapshot ist final konsistent ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- `engine/src/main/java/de/haw/vsp/simulation/engine/DefaultSimulationEngine.java`
- Alle Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-03

---

### ~~🎯 PHASE 3: Observation & Metrics (MITTEL)~~ ✅ ABGESCHLOSSEN

---

## ✅ #30 – ISSUE B8 – getMetrics implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/30
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.getMetrics(...)`.

**Inhalt:**
- Rückgabe des aktuellen MetricsSnapshot ✅

**Akzeptanzkriterien:**
- Snapshot reflektiert den tatsächlichen Simulationszustand ✅
- Snapshot ist jederzeit abrufbar ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 13 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- API Documentation § 0.3 MetricsSnapshot
- Application Documentation UC-04

---

## ✅ #28 – ISSUE B6 – getCurrentVisualization implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/28
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.getCurrentVisualization(...)`.

**Inhalt:**
- Erstellung eines read-only Snapshots ✅
- Ableitung aus EventStream und internem Zustand ✅

**Akzeptanzkriterien:**
- Snapshot ist konsistent ✅
- Snapshot beeinflusst die Simulation nicht ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 13 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- API Documentation § 1.2 VisualizationListener
- Application Documentation UC-05

---

## ✅ #29 – ISSUE B7 – registerVisualizationListener implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/29
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung der Registrierung von VisualizationListenern.

**Inhalt:**
- Registrierung eines Listeners ✅
- Weiterleitung relevanter SimulationEvents ✅

**Akzeptanzkriterien:**
- Listener erhält alle relevanten Events ✅
- Registrierung beeinflusst die Simulation nicht ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 12 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.2 VisualizationListener
- Application Documentation UC-05

---

## ✅ #34 – ISSUE B12 – getLogs implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/34
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.getLogs(...)`.

**Inhalt:**
- Filterung nach LogFilter ✅
- Aggregation interner Logs ✅

**Akzeptanzkriterien:**
- Logs sind vollständig ✅
- Logs sind nachvollziehbar und zeitlich korrekt ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 13 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-06

---

### ~~🎯 PHASE 4: Save / Load / Export (NIEDRIG)~~ ✅ ABGESCHLOSSEN

---

## ✅ #31 – ISSUE B9 – getCurrentConfig implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/31
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.getCurrentConfig(...)`.

**Inhalt:**
- Rückgabe der aktiven SimulationConfig ✅

**Akzeptanzkriterien:**
- Konfiguration ist vollständig rekonstruierbar ✅
- Rückgabe ist konsistent zum internen Zustand ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 13 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- API Documentation § 0.4 SimulationConfig

---

## ✅ #32 – ISSUE B10 – loadConfig implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/32
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.loadConfig(...)`.

**Inhalt:**
- Wiederherstellung einer SimulationConfig ✅
- Initialisierung einer neuen Simulation ✅

**Akzeptanzkriterien:**
- Verhalten entspricht `initializeNetwork + selectAlgorithm` ✅
- Alte Simulation wird verworfen ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 14 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-07

---

## ✅ #33 – ISSUE B11 – exportRunData implementieren [COMPLETED]

- Link: https://github.com/KALPRATEEK/VSP/issues/33
- **Status: ✅ ABGESCHLOSSEN**

### Beschreibung

Implementierung von `SimulationControl.exportRunData(...)`.

**Inhalt:**
- Export von SimulationEvents und MetricsSnapshots ✅
- Unterstützung von CSV und JSON ✅

**Akzeptanzkriterien:**
- Export ist extern auswertbar ✅
- Export enthält vollständige Run-Daten ✅

**Implementierung:**
- `control/src/main/java/de/haw/vsp/simulation/control/DefaultSimulationControl.java`
- 16 Tests passing ✅

**Referenced Documentation:**
- API Documentation § 1.1 SimulationControl
- Application Documentation UC-08

---

### 🟡 UI LAYER (NACH APPLICATION LAYER)

---

## #42 – UI-1 – UI Application Shell & API-Anbindung

- Link: https://github.com/KALPRATEEK/VSP/issues/42

### Beschreibung

Implementierung der grundlegenden UI-Struktur und Anbindung an das Backend.

**Inhalt:**
- Initiales React-Projekt
- Zentrale API-Service-Schicht (HTTP + WebSocket)
- Fehler- und Ladezustände

**Akzeptanzkriterien:**
- UI startet lokal
- Backend-API ist zentral gekapselt
- API-Fehler werden sauber angezeigt

---

## #43 – UI-2 – Network Configuration View

- Link: https://github.com/KALPRATEEK/VSP/issues/43

### Beschreibung

UI zur Initialisierung einer Simulation (UC-01).

**Inhalt:**
- Eingabe von:
  - NodeCount
  - TopologyType
- Button: "Initialize Network"
- Aufruf von `initializeNetwork(...)`

**Akzeptanzkriterien:**
- Gültige Konfiguration startet eine neue Simulation
- Ungültige Eingaben werden validiert

---

## #44 – UI-3 – Algorithm Selection View

- Link: https://github.com/KALPRATEEK/VSP/issues/44

### Beschreibung

UI zur Auswahl des Algorithmus (UC-02).

**Inhalt:**
- Anzeige verfügbarer Algorithmen
- Aufruf von `selectAlgorithm(...)`

**Akzeptanzkriterien:**
- Algorithmus kann nur nach Initialisierung gewählt werden
- Auswahl wird im UI reflektiert

---

## #45 – UI-4 – Simulation Control Panel

- Link: https://github.com/KALPRATEEK/VSP/issues/45

### Beschreibung

UI zur Steuerung der Simulation (Start, Pause, Resume, Stop).

**Inhalt:**
- Buttons:
  - Start
  - Pause
  - Resume
  - Stop
- Aufruf der entsprechenden API-Methoden

**Akzeptanzkriterien:**
- Buttons spiegeln aktuellen Simulationszustand wider
- Ungültige Aktionen sind deaktiviert

---

## #46 – UI-5 – Live Visualization View

- Link: https://github.com/KALPRATEEK/VSP/issues/46

### Beschreibung

Visualisierung von Nodes, Topologie und MessageFlows.

**Inhalt:**
- Darstellung der Topologie
- Darstellung der Node-Zustände
- Animation von MESSAGE_SENT / MESSAGE_RECEIVED
- WebSocket-Event-Verarbeitung

**Akzeptanzkriterien:**
- Visualisierung aktualisiert sich live
- Keine UI-Aktion beeinflusst die Simulation

---

## #47 – UI-6 – Metrics View

- Link: https://github.com/KALPRATEEK/VSP/issues/47

### Beschreibung

Anzeige der aggregierten Simulationsmetriken.

**Inhalt:**
- Anzeige von:
  - MessageCount
  - Rounds
  - Laufzeit
  - Converged
  - LeaderId
- Polling von `getMetrics(...)`

**Akzeptanzkriterien:**
- Werte aktualisieren sich regelmäßig
- Finale Werte bleiben nach Stop sichtbar

---

## #48 – UI-7 – Logs View

- Link: https://github.com/KALPRATEEK/VSP/issues/48

### Beschreibung

Anzeige der Simulationslogs.

**Inhalt:**
- Listenansicht der Logs
- Filter nach Severity / Zeit
- Aufruf von `getLogs(...)`

**Akzeptanzkriterien:**
- Logs sind chronologisch korrekt
- Fehler sind klar hervorgehoben

---

## #49 – UI-8 – Save / Load Configuration

- Link: https://github.com/KALPRATEEK/VSP/issues/49

### Beschreibung

UI zur Speicherung und zum Laden von SimulationConfig.

**Inhalt:**
- Button: Save Configuration
- Button: Load Configuration
- Nutzung von `getCurrentConfig(...)` und `loadConfig(...)`

**Akzeptanzkriterien:**
- Geladene Konfiguration erzeugt identische Simulation

---

### 🔵 MIDDLEWARE LAYER (WIRD VON ANDEREN UMGESETZT)

---

## #50 – MT 1 - Create UDP Adapter Abstraction

- Link: https://github.com/KALPRATEEK/VSP/issues/50

### Beschreibung

Implement an internal interface:

**TransportAdapter**
- void start()
- void stop()
- void send(byte[] datagram, TransportAddress target)
- void setReceiver(BiConsumer<byte[], TransportAddress> onDatagram)

---

## #51 – MT2 - Implement UdpTransportAdapter

- Link: https://github.com/KALPRATEEK/VSP/issues/51

### Beschreibung

**Responsibilities:**
- bind a UDP socket on a configured local port
- run a single receive thread:
  - loop: receive datagram → call onDatagram(bytes, senderAddress)
- send() uses DatagramSocket.send()
- stop() breaks loop cleanly (close socket, interrupt thread)

---

## #52 – MM 1 - Implement MessagingPortImpl

- Link: https://github.com/KALPRATEEK/VSP/issues/52

### Beschreibung

Create class:

`class MessagingPortImpl implements MessagingPort`

**Internal fields:**
- ConcurrentHashMap<NodeId, MessageHandler> handlers
- ObjectMapper objectMapper
- MessageValidator validator
- TransportConfig transportConfig
- TransportAdapter adapter
- SimulationEventPublisher eventPublisher (inject/setter)
- ExecutorService sendExecutor

---

## #53 – MM 2 - Define JSON Schema for SimulationMessage

- Link: https://github.com/KALPRATEEK/VSP/issues/53

### Beschreibung

```json
{
  "sender": "node-1",
  "receiver": "node-2",
  "msgType": "FLOOD",
  "payload": {...},
  "seq": 42
}
```

---

## #54 – MM3 - Implement Message Validation

- Link: https://github.com/KALPRATEEK/VSP/issues/54

### Beschreibung

**Implement MessageValidator:**
- required fields present
- sender/receiver not null/empty
- optional: receiver must exist in TransportConfig
- optionally reject unknown top-level fields (configure Jackson: FAIL_ON_UNKNOWN_PROPERTIES = true)

**On invalid inbound:**
- discard message
- publish SimulationEvent(type="ERROR", nodeId=?, peerId=?, payloadSummary="invalid message: ...")

---

## #55 – MM4 - Implement registerHandler(nodeId, handler)

- Link: https://github.com/KALPRATEEK/VSP/issues/55

### Beschreibung

- add/replace handler safely
- Implement unregisterHandler(nodeId): remove handler

**Inbound handling logic:**
- adapter receives bytes
- deserialize JSON → SimulationMessage
- validate
- find handler by receiver (or by registered nodeId logic)
- if handler exists: call handler
- publish event "MESSAGE_RECEIVED"

**Important:** avoid deadlocks; handler invocation should not occur on UDP receive thread if you want safety.

simplest: hand off handler execution to a small executor (or reuse sendExecutor or a separate receiveExecutor)

---

## #56 – MM5 - Implement send() and broadcast()

- Link: https://github.com/KALPRATEEK/VSP/issues/56

_(Keine Beschreibung vorhanden.)_

---

### 🟢 PROJEKT-SETUP & DOKUMENTATION

---

## #7 – baustein sicht fix

- Link: https://github.com/KALPRATEEK/VSP/issues/7

### Beschreibung

read to dos and fix the baustein as per need

---

## #8 – fix run time layer

- Link: https://github.com/KALPRATEEK/VSP/issues/8

### Beschreibung

read to dos and fix it

---

## #9 – Documentation Middleware

- Link: https://github.com/KALPRATEEK/VSP/issues/9

### Beschreibung

Create the documentation for our Middleware with arc42 format

---

## #10 – 0.1 – Set up project foundation (Spring Boot + module structure)

- Link: https://github.com/KALPRATEEK/VSP/issues/10
- **Status: Vermutlich ✅ erledigt**

### Beschreibung

The entire project should be structured according to the architecture documentation.  
Backend uses Spring Boot as REST/Control API.  
Modules: core, middleware, engine, control.

**Tasks:**
- Create a new Maven or multi-module Spring Boot project.
- Create package structure: `simulation.core.*`, `simulation.middleware.*`, `simulation.engine.*`, `simulation.control.*`.
- Add a dummy REST endpoint `/api/health`.
- The project should start using `mvn spring-boot:run`.

**Acceptance Criteria:**
- Project compiles successfully.
- `/api/health` returns `{ "status": "ok" }`.
- Package structure follows the architecture design.

---

## #11 – 0.2 - Implement CI/CD pipeline (GitHub Actions)

- Link: https://github.com/KALPRATEEK/VSP/issues/11

### Beschreibung

Set up an automated CI/CD pipeline that builds and tests the backend.

**Tasks:**
- Create GitHub Actions workflow `.github/workflows/build.yml`.
- Steps: Checkout, Java 17 setup, Maven build, Maven test.
- Pull requests must pass the pipeline.

**Acceptance Criteria:**
- Pipeline triggers on each push and pull request.
- Build fails if tests fail.
- PRs show results for backend build and backend tests.

---

## #17 – Middleware_Impli

- Link: https://github.com/KALPRATEEK/VSP/issues/17

_(Keine Beschreibung vorhanden.)_

---

## 📋 SCHNELLÜBERSICHT: NÄCHSTE SCHRITTE

**Aktueller Fortschritt: 16/44 Issues abgeschlossen (36%)**

**Empfohlene Implementierungsreihenfolge:**

### ✅ Abgeschlossen (Phase 1-2):
- ✅ #35 (C1) – SimulationEngine
- ✅ #36 (C2) – SimulationEventPublisher anbinden
- ✅ #24 (B2) – selectAlgorithm
- ✅ #25 (B3) – startSimulation
- ✅ #26 (B4) – pauseSimulation / resumeSimulation
- ✅ #27 (B5) – stopSimulation

### ⭐ Nächste Schritte (Phase 3):
1. ⭐ **#30 (B8)** – getMetrics implementieren **← START HIER**
2. **#28 (B6)** – getCurrentVisualization
3. **#29 (B7)** – registerVisualizationListener
4. **#34 (B12)** – getLogs

### Danach (Phase 4):
5. **#31 (B9)** – getCurrentConfig
6. **#32 (B10)** – loadConfig
7. **#33 (B11)** – exportRunData

### Dann: **UI Layer** (#42-49)

**Middleware-Issues (#50-56)** werden von anderen umgesetzt und haben niedrige Priorität für das Kern-Team.

---

**Ende der Issue-Liste**
