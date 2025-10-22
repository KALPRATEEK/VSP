# Projektbericht – Woche 1
**Projekt:** Flooding-basierte Leader Election als verteiltes System  
**Modul:** Verteilte Systeme  
**Team:** Elias Schmidt, Rodrigo Morales, Prateek Kalra, Mario Hansen  
**Zeitraum:** Woche 1 des Semesters

---

## 1. Einführung & Ziele

Ziel der ersten Projektwoche war es, die **methodische und technische Grundlage** für die Umsetzung eines verteilten Systems zur **Leader Election mittels Flooding-Algorithmus** zu schaffen.  
Der Fokus lag dabei auf der **Konsistenz zwischen Code und Dokumentation**, einer klaren **Teamorganisation** und dem Aufbau einer **funktionsfähigen Entwicklungsumgebung**.

Im Vordergrund stand die Vorbereitung der Infrastruktur, um in den kommenden Wochen die algorithmische Logik sauber und nachvollziehbar umsetzen zu können.

**Zentrale Ziele der Woche:**
- Etablierung der Entwicklungs- und Dokumentationsumgebung
- Definition des methodischen Vorgehens
- Erste Architekturüberlegungen und technische Basis (Docker, Netzwerkkommunikation)
- Beginn der konzeptionellen Modellierung des Flooding-Prozesses

---

## 2. Randbedingungen

### 2.1 Technische Randbedingungen
- **Programmiersprache:** Java
- **IDE:** IntelliJ IDEA
- **Containerisierung:** Docker
- **Kommunikation:** TCP/IP zwischen Containern
- **Versionsverwaltung & Dokumentation:** GitHub (Markdown-Struktur)

Jeder **Knoten läuft in einem eigenen Docker-Container** und kommuniziert über definierte Ports mit anderen Knoten. Diese Struktur simuliert realistische Netzwerkszenarien und ermöglicht Experimente mit Paketverzögerungen, Nachrichtenverlust und Flooding-Kollisionen.

### 2.2 Methodische Randbedingungen
- Vorgehen orientiert an **Scrum**, mit wöchentlichen Iterationen (Sprints)
- Kein **TDD** in der Anfangsphase, Fokus auf Funktionsvalidierung und algorithmische Korrektheit
- Dokumentation wird **parallel** zur Implementierung gepflegt („Living Documentation“)

### 2.3 Organisatorische Randbedingungen
- Kommunikation über wöchentliche Meetings (Planung, Review)
- Aufgabenkoordination über GitHub-Issues
- Teamstruktur:
    - **Projektkoordination & Kommunikation:** [Name]
    - **Backend & Netzwerklogik:** [Name]
    - **Containerisierung & Deployment:** [Name]
    - **Dokumentation & Visualisierung:** [Name]

---

## 3. Kontext & Abgrenzung

Das System ist Teil des Moduls **Verteilte Systeme** und dient der praktischen Anwendung theoretischer Konzepte.  
Es bildet ein **abgeschlossenes Experimentierfeld** für dezentrale Koordination und Konsensbildung.

### 3.1 Systemkontext
Das Projekt umfasst ausschließlich die **interne Kommunikation der Knoten** in einem verteilten Netzwerk.  
Externe Systeme (z. B. Datenbanken oder externe APIs) werden **nicht angebunden**, um die algorithmische Komplexität zu fokussieren.

**Akteure und Systeme:**

| Akteur / System        | Beschreibung                                      | Interaktion                     |
|-------------------------|--------------------------------------------------|----------------------------------|
| **Node-Service**        | Einzelner Knoten im Netzwerk, führt Flooding aus | Sendet und empfängt Nachrichten  |
| **Network-Manager**     | Vermittelt Nachrichten zwischen Knoten           | Simuliert Netzwerkverbindungen   |
| **Visualization-Module** (geplant) | Darstellung der Kommunikation                   | Liest Nachrichtenlogs aus        |


### 3.2 Abgrenzung
Nicht Bestandteil der aktuellen Projektphase:
- Persistente Speicherung von Zuständen
- Authentifizierung oder Sicherheitsschichten
- Komplexe Netzwerk-Topologien (wird ggf. später simuliert)

---

## 4. Lösungsstrategie

Die Lösung basiert auf einer **dezentralen Architektur**, in der jeder Knoten gleichberechtigt agiert.  
Die Kommunikation erfolgt rein **nachrichtenbasiert** über TCP/IP innerhalb eines Docker-Netzwerks.  
Die Flooding-Strategie ermöglicht es, Informationen (z. B. Kandidaten-IDs) an alle erreichbaren Knoten zu verteilen, bis sich das System auf einen **Leader** geeinigt hat.

### 4.1 Technische Struktur
- **Node-Service:** empfängt und sendet Nachrichten, führt Leader-Wahl-Logik aus
- **Network-Manager:** koordiniert Containerverbindungen und simuliert Netzverhalten
- **Visualisierung:** (in Planung) zur Darstellung der Nachrichtenflüsse

### 4.2 Methodische Strategie
- Iterativ-inkrementelle Entwicklung mit klaren Sprint-Zielen
- Dokumentation in Markdown und Diagrammen (PlantUML & Mermaid)
- „Living Documentation“-Ansatz: Code und Beschreibung werden gemeinsam weiterentwickelt
- Fokus auf korrekte Algorithmusumsetzung vor Optimierung

### 4.3 Erste Ergebnisse
- Docker-Umgebung erfolgreich eingerichtet
- Prototypische Node-Kommunikation implementiert
- Grundstruktur des Flooding-Prozesses konzipiert
- Dokumentationsstruktur (Markdown + Diagramme) angelegt
- Erste UML- und Sequenzdiagramme erstellt

**Erste Erkenntnisse:**
- Containerisierung vereinfacht das Setup, erfordert aber präzises Port-Management
- Flooding muss um Duplikaterkennung erweitert werden, um Endlosschleifen zu vermeiden
- Die kontinuierliche Dokumentation erhöht die Qualität, aber auch den initialen Aufwand

---

## 5. Ausblick auf Woche 2

In der nächsten Woche sollen folgende Punkte umgesetzt werden:
- Stabile Implementierung des Flooding-Algorithmus
- Hinzufügen von Log-Komponenten zur Nachrichtenanalyse
- Beginn der Metrikerfassung (Nachrichtenanzahl, Konvergenzzeit)
- Erweiterung der Visualisierung mit Mermaid-Diagrammen  
