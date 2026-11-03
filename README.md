# GridMC: Pelican PaperMC Web-Starter & Auto-Shutdown

Schlüsselfertige Gesamtlösung für einen bedarfsgesteuerten Minecraft PaperMC-Server mit Web-Startbutton über die Pelican Panel API und automatischem Shutdown bei Leerlauf.

---

## 🏗️ Architektur & Funktionsweise

```
+-------------------------------------------------------------------+
|                           DER LOOP                                |
|                                                                   |
|   [Spieler im Browser]                                            |
|          │                                                        |
|          ▼ (HTTP POST /api/start)                                 |
|   [Node.js Webserver unter PM2] ──(Token geschützt)──► [Pelican]  |
|                                                           │       |
|                                                (Signal: start)    |
|                                                           ▼       |
|                                                 [PaperMC Server]  |
|                                                           │       |
|                                                   (Spielen...)    |
|                                                           │       |
|   [PaperMC Auto-Shutdown Modul]                           │       |
|          ▲                                                ▼       |
|          └─── 0 Spieler für 5 Minuten? ──────────► [Server Stopp] |
+-------------------------------------------------------------------+
```

---

## 📁 Projektstruktur

```text
gridmc/
├── .cursorrules                       # Cursor IDE AI-Regeln
├── .windsurfrules                     # Windsurf IDE AI-Regeln
├── .github/
│   └── copilot-instructions.md        # GitHub Copilot Anweisungen
├── .gitignore
├── README.md
├── IdleShutdown-1.0.0.jar             # Fertig kompilierte PaperMC Plugin-JAR
├── mc-starter/                        # Node.js Webserver (PM2)
│   ├── .env                           # Konfigurationsdatei (Port 9009, Pelican API)
│   ├── .env.example                   # Vorlage für Umgebungsvariablen
│   ├── package.json
│   ├── server.js                      # Express Backend mit Pelican API Client
│   └── public/
│       └── index.html                 # thegridcom.org Style & Mini-Projekt Branding
└── idle-shutdown-plugin/              # PaperMC Java-Quellcode (Maven)
    ├── pom.xml                        # Maven Build-Konfiguration (Paper-API 1.21 / Java 21)
    └── src/
        └── main/
            ├── java/de/starter/idleshutdown/
            │   └── IdleShutdownPlugin.java  # 5-Minuten Idle-Timer mit config.yml
            └── resources/
                ├── config.yml
                ├── plugin.yml
                └── paper-plugin.yml
```

---

## 🚀 Teil 1: Node.js Webserver Setup (PM2)

### 1. Konfiguration vorbereiten

Kopiere `.env.example` nach `.env` im Ordner `mc-starter/`:

```bash
cd mc-starter
cp .env.example .env
```

Passe die Werte in `.env` an:
```env
PORT=9009
PELICAN_URL=https://servers.thegridcom.org
SERVER_ID=5575dee1
SERVER_UUID=5575dee1-93e8-42bf-9f8a-7d360d78e547
PELICAN_API_KEY=dein_client_api_key_hier
COOLDOWN_SECONDS=45
```

> **Hinweis zum Pelican API-Key:**
> Erstelle im Pelican Panel unter **Account Settings -> API Credentials** einen neuen Client-API-Schlüssel mit Berechtigungen für Power/Server-Kontrolle. Verwende **nicht** den Application-API-Key des Administrators.

### 2. Abhängigkeiten installieren & testen

```bash
npm install
npm start
```

Rufe im Browser `http://localhost:9009` auf, um die Weboberfläche zu testen.

### 3. Produktiver Betrieb mit PM2 (z. B. auf `/opt/mc-starter`)

```bash
# PM2 global installieren (falls noch nicht vorhanden)
npm install -g pm2

# Dienst starten
pm2 start server.js --name "mc-starter"

# Boot-Persistenz einrichten
pm2 save
pm2 startup
```

---

## ☕ Teil 2: PaperMC Auto-Shutdown Plugin

Das Plugin prüft alle 20 Sekunden, ob Spieler online sind. Sobald 0 Spieler online sind, startet ein 5-Minuten-Countdown (6000 Ticks). Betritt ein Spieler den Server, wird der Countdown abgebrochen. Bleibt der Server leer, wird `Bukkit.shutdown()` aufgerufen.

### 1. Plugin kompilieren (Maven)

```bash
cd idle-shutdown-plugin
mvn clean package
```

Die fertige JAR-Datei liegt anschließend in:
`idle-shutdown-plugin/target/idle-shutdown-1.0.0.jar`

### 2. Installation auf dem Minecraft-Server

1. Kopiere die Datei `idle-shutdown-1.0.0.jar` in den `plugins/`-Ordner deines PaperMC-Servers (z. B. per SFTP oder Pelican File Manager).
2. Starte den PaperMC-Server neu.
3. Im Server-Log erscheint: `[IdleShutdown] IdleShutdown Modul aktiv: 5-Minuten-Limit.`

---

## 🤖 Teil 3: AI-Coding-Directives

Für KI-gestützte Entwicklungsumgebungen (Cursor, Windsurf, Copilot) sind die Schutz- und Architekturregeln bereits in folgenden Dateien hinterlegt:

- `.cursorrules`
- `.windsurfrules`
- `.github/copilot-instructions.md`

Diese Regeln garantieren:
- **Asynchrone I/O:** Keine blockierenden Socket/HTTP-Calls auf dem Paper Main-Thread.
- **Fehlerentkopplung:** Sauberes Abfangen von Netzwerkfehlern ohne Abstürze.
- **Sauberes Herunterfahren:** Streng über `Bukkit.shutdown()`, um Weltkorruption zu verhindern.
- **Sicherheits-Rate-Limiting:** Cooldown gegen Button-Spamming und Geheimhaltung der API-Keys.

