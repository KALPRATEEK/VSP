# Änderungen und Setup-Anleitung

### Ausgangssituation
- Die Anwendung lief **nur im virtuellen Modus** (alle Knoten in einem JVM-Prozess)
- Distributed Mode war nicht funktionsfähig

### Aktueller Status

✅ **Funktioniert:**
- Virtual Mode (alle Knoten in einem Container)
- Distributed Mode (jeder Knoten in eigenem Container)
- Simulation Start und Algorithmus-Ausführung
- REST API und WebSocket-Verbindungen

⚠️ **Bekannte Probleme in der Distributed Variante (TODO):**
1. **Convergence Detection** funktioniert noch nicht
2. **Graph-Visualisierung** wird nicht angezeigt
3. **Metrics** werden nicht aktualisiert/aggregiert
4. **Habs mit über 10 Containern versucht und dort failed es bei mir .. :(**

---

## Setup-Anleitung Anleitung

### Voraussetzungen
- Docker (Desktop) installiert und gestartet
- Node.js und npm installiert (für Frontend)

cd VSP
```

---

## Modus 1: Virtual Mode (Entwicklung & Testing)

### Backend + Frontend starten

#### Schritt 1: Backend (Docker)
```bash
# In VSP-Hauptverzeichnis
docker-compose up --build
```

Das Backend ist verfügbar unter: `http://localhost:8080`

#### Schritt 2: Frontend (separater Terminal)
```bash
# In neuem Terminal-Fenster
cd frontend
npm install
npm run dev
```

Das Frontend ist verfügbar unter: `http://localhost:5173`

### Was passiert im Virtual Mode?
- Ein einzelner Docker-Container (`vsp-backend`)
- Alle Simulationsknoten laufen **in-memory** im selben JVM-Prozess
- Schnell und ideal für UI-Testing
- Environment: `SIMULATION_MODE=virtual` (Standard)

---

## Modus 2: Distributed Mode (Realistische Netzwerk-Simulation)

### Backend + Frontend starten

#### Schritt 1: Virtual Mode stoppen (falls läuft)
```bash
docker-compose down
```

#### Schritt 2: Distributed Mode starten
```bash
# In VSP-Hauptverzeichnis
docker-compose -f docker-compose-distributed.yml up --build
```

Das Backend ist verfügbar unter: `http://localhost:8080`

#### Schritt 3: Frontend (separater Terminal)
```bash
# In neuem Terminal-Fenster
cd frontend
npm install
npm run dev
```

Das Frontend ist verfügbar unter: `http://localhost:5173`

### Was passiert im Distributed Mode?
- Backend-Container (`vsp-backend`)
- Beim Start einer Simulation werden **zusätzliche Docker-Container** für jeden Knoten erstellt
- Jeder Knoten läuft in eigenem Container mit echter UDP-Kommunikation
- Realistische Netzwerk-Delays und -Verluste möglich
- Environment: `SIMULATION_MODE=distributed`

### Distributed Mode: Container überprüfen
```bash
# Alle laufenden Container anzeigen
docker ps

# Logs eines Node-Containers ansehen
docker logs vsp-node-node-0-<simulation-id>

# Alle Logs des Backends
docker-compose -f docker-compose-distributed.yml logs -f
```

---

## Simulation über UI starten

1. Frontend öffnen: `http://localhost:5173`
2. Netzwerk konfigurieren:
   - Node Count: z.B. 5
   - Topology: z.B. RING
3. Algorithmus wählen: `flooding-leader-election`
4. "Initialize Network" klicken
5. "Start Simulation" klicken

---

## Modus wechseln

### Von Virtual → Distributed:
```bash
docker-compose down
docker-compose -f docker-compose-distributed.yml up --build
```

### Von Distributed → Virtual:
```bash
docker-compose -f docker-compose-distributed.yml down
docker-compose up --build
```

**Wichtig:** Frontend läuft unabhängig und muss nicht neu gestartet werden!

---

## Troubleshooting

### Backend startet nicht (Virtual Mode)
```bash
# Container stoppen und neu builden
docker-compose down
docker-compose up --build

# Logs prüfen
docker-compose logs
```

### Backend startet nicht (Distributed Mode)
```bash
# Container stoppen
docker-compose -f docker-compose-distributed.yml down

# Alte Node-Container aufräumen
docker ps -a | grep vsp-node | awk '{print $1}' | xargs docker rm -f

# Neu starten
docker-compose -f docker-compose-distributed.yml up --build
```

### Frontend verbindet sich nicht
- Prüfen ob Backend läuft: `http://localhost:8080/actuator/health`
- Frontend-Dev-Server läuft: `http://localhost:5173`
- Browser-Cache leeren (Strg+Shift+R)

### Port bereits belegt
```bash
# Port 8080 freigeben (Backend)
# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Port 5173 freigeben (Frontend)
netstat -ano | findstr :5173
taskkill /PID <PID> /F
```

---

## Bekannte Einschränkungen

### Funktioniert NICHT (aktuell):
1. **Convergence Detection**: Simulation erkennt nicht automatisch, wann der Algorithmus fertig ist
2. **Graph Visualization**: Kein visueller Graph der Knoten und Kanten
3. **Metrics Aggregation**: Metriken (Message Count, etc.) werden nicht korrekt zusammengefasst

### Workaround:
- Simulation manuell mit "Stop" Button beenden
- Logs im Terminal/Docker beobachten für Algorithmus-Fortschritt
- REST API für Status: `GET http://localhost:8080/api/simulations/<id>`

---

## Nützliche Befehle

```bash
# Alle Container stoppen
docker-compose down
docker-compose -f docker-compose-distributed.yml down

# Alle VSP-Container löschen
docker ps -a | grep vsp | awk '{print $1}' | xargs docker rm -f

# Alle VSP-Images löschen
docker images | grep vsp | awk '{print $3}' | xargs docker rmi -f

# Backend-Logs live ansehen (Virtual)
docker-compose logs -f

# Backend-Logs live ansehen (Distributed)
docker-compose -f docker-compose-distributed.yml logs -f

# Projekt neu builden (Maven)
mvn clean package -DskipTests

# Docker Images neu bauen
docker-compose build --no-cache
```

---

## Projektstruktur (Übersicht)

```
VSP/
├── core/                    # Domain-Modelle (SimulationEvent, NodeId, etc.)
├── middleware/              # MessagingPort (Virtual + UDP)
├── engine/                  # SimulationEngine (Virtual + Distributed)
├── control/                 # REST API + Spring Boot Backend
├── frontend/                # React/TypeScript UI
├── docker-compose.yml       # Virtual Mode Setup
├── docker-compose-distributed.yml  # Distributed Mode Setup
├── Dockerfile               # Backend Container Image
└── Dockerfile.node          # Node Container Image (für distributed)
```

---

