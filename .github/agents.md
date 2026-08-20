# GamerBell — Agentic Development Guide

Welcome, Agent. This document describes the architecture, conventions, and developer workflow for the **GamerBell** service — a Spring Boot WebSocket relay that bridges physical ESP32 button devices with the Fitz-Net web UI and handles over-the-air (OTA) firmware updates.

---

## 🏗️ Architecture Overview

GamerBell is a lightweight, stateless relay with two responsibilities:

1. **WebSocket relay** — ESP32 devices and web browsers both connect to `/ws`. When an ESP32 sends a `PRESSED` or `RELEASED` event, GamerBell broadcasts it to all connected sessions.
2. **OTA firmware server** — ESP32 devices call `GET /api/firmware/latest` on startup. GamerBell fetches the latest `.bin` from GitHub Releases and streams it to the device if an update is available.

```
ESP32 Device ──────wss──────┐
                            │
Browser (fitznet.org) ──wss─┤── /ws ──► ButtonWebSocketHandler ──► ButtonService ──► broadcast to all
                            │
GitHub Releases ◄── FirmwareService ◄── GET /api/firmware/latest ◄── ESP32 Device
```

---

## 📦 Tech Stack

| Item | Value |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build tool | Gradle |
| Key modules | Spring WebSocket, Spring WebFlux, Spring Actuator, Lombok |
| Logging | Logstash Logback Encoder (JSON for Loki) |
| Testing | JUnit 5, WireMock, Testcontainers |

---

## 📁 Source Layout

```
src/main/java/org/fitznet/fun/
├── GamerBellApplication.java          <- Spring Boot entry point
├── config/
│   ├── CorsConfig.java                <- CORS filter for REST endpoints
│   ├── LoggingFilter.java             <- Structured HTTP request logging
│   └── WebSocketConfig.java           <- Registers /ws endpoint; reads cors.allowed-origins
├── controller/
│   └── GamerBellController.java       <- GET /count, GET /api/firmware/latest, POST /api/devices/log
├── dto/
│   ├── ButtonEvent.java               <- Enum: PRESSED, RELEASED
│   ├── ButtonEventDto.java            <- WS message payload: deviceId, buttonEvent, firmwareVersion
│   ├── BellCountDto.java              <- Response for /count
│   ├── GitHubReleaseDto.java          <- GitHub releases API response
│   └── GitHubAssetDto.java            <- GitHub release asset entry
├── handler/
│   └── ButtonWebSocketHandler.java    <- TextWebSocketHandler: routes events, broadcasts
├── service/
│   ├── ButtonService.java             <- Thread-safe session pool + broadcast
│   └── FirmwareService.java           <- GitHub OTA: version check, download, cache
└── utils/
    ├── Constants.java                 <- Shared header name constants
    └── JsonUtils.java                 <- Singleton ObjectMapper
```

---

## 🔌 API Reference

### WebSocket — `GET /ws` (upgrade)

Accepts any client (ESP32 or browser). No authentication required.

**Inbound message (from ESP32 or browser):**
```json
{ "deviceId": "web-mattlol85", "buttonEvent": "PRESSED", "firmwareVersion": "v1.0.1" }
```

**Broadcast to all clients on valid event:**
```json
{ "deviceId": "web-mattlol85", "buttonEvent": "PRESSED", "firmwareVersion": "v1.0.1" }
```

Only `PRESSED` and `RELEASED` events are broadcast. Unknown event types are logged as warnings and dropped.

---

### REST Endpoints

#### `GET /count`
Returns the number of active WebSocket sessions.
```json
{ "count": 3 }
```

#### `GET /api/firmware/latest`
OTA update endpoint for ESP32 devices.

| Header | Required | Description |
|---|---|---|
| `X-ESP32-Version` | No | Current firmware version on the device (e.g. `v1.0.0`) |
| `X-ESP32-Mac` | No | Device MAC address for logging |

**Responses:**
- `200 OK` + binary `.bin` stream + `X-Latest-Version` header — update available, serving firmware
- `304 Not Modified` — device is already on the latest version
- `503 Service Unavailable` + `X-Firmware-Error` header — no firmware available

#### `POST /api/devices/log`
Accepts an error/log report from an ESP32 device and emits it as a structured log line (JSON, via Logstash Logback Encoder) for Loki, so device-side failures can be investigated remotely. No persistence — this is log-only, matching GamerBell's stateless design.

**Body:**
```json
{
  "deviceId": "bell-1",
  "firmwareVersion": "v0.14.1",
  "level": "ERROR",
  "source": "count_fetch",
  "message": "HTTP 503"
}
```
`level` is one of `ERROR` | `WARN` | `INFO` (defaults to `ERROR` if omitted); `source` identifies the failure point (e.g. `count_fetch`, `websocket`, `ota`).

**Responses:**
- `204 No Content` — logged successfully
- `400 Bad Request` — missing `deviceId` or `message`

Also increments `gamerbell.device.logs.total{level,source}` (Micrometer/Prometheus).

---

## ⚙️ Configuration

### `application.properties`

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP server port (override with `SERVER_PORT` env var) |
| `cors.allowed-origins` | (all Fitz-Net origins) | Comma-separated list of allowed CORS origins |
| `firmware.github.repo` | `mattlol85/Esp32FitznetBell` | GitHub repo for firmware releases |
| `firmware.storage.path` | `./firmware` | Local directory to cache firmware binaries |
| `firmware.filename` | `firmware.bin` | Name of the firmware binary file |

### `application-dev.properties`
Extends `application.properties` — adds `http://localhost:3000` to `cors.allowed-origins` and enables DEBUG logging.

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | Override the HTTP server port |
| `FIRMWARE_GITHUB_REPO` | `mattlol85/Esp32FitznetBell` | GitHub repo owner/name for OTA firmware |
| `FIRMWARE_STORAGE_PATH` | `./firmware` | Directory to store downloaded firmware |
| `FIRMWARE_FILENAME` | `firmware.bin` | Firmware binary filename |

### CORS Allowed Origins (production)
```
https://fitznet.org
https://www.fitznet.org
https://fitznet.doomdns.org
https://api.fitznet.doomdns.org
https://gamerbell.fitznet.doomdns.org
https://gamerbell.fitznet.org
```
> **Adding a new domain?** Update `cors.allowed-origins` in both `application.properties` AND `application-dev.properties`.

---

## 🚀 Developer Workflow

### Build & Run
```powershell
# Build
.\gradlew.bat build

# Run locally (uses default application.properties)
.\gradlew.bat bootRun

# Run with dev profile (adds localhost:3000 to CORS, DEBUG logging)
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'

# Run tests
.\gradlew.bat test
```

### Git & Commit Conventions
Use conventional commits scoped to the area of change:
```
feat(websocket): add reconnect backoff for dropped clients
fix(cors): add new subdomain to allowed origins
chore(deps): bump spring-boot to 3.4.2
```

### Branch Naming
```
feature/short-description
fix/short-description
chore/short-description
```

---

## 💡 Agent Guidelines

1. **Never hardcode CORS origins** — always add to `cors.allowed-origins` in `application.properties` (and `application-dev.properties` for dev); the value flows into both REST and WebSocket CORS config automatically.
2. **WebSocket message contract** — `ButtonEventDto` is the shared schema between GamerBell and `fitz-net-website/src/components/WebSocketButton.jsx`. If you add or rename fields, update both sides.
3. **Firmware caching** — `FirmwareService` caches the latest GitHub version for 60 seconds (`VERSION_CACHE_DURATION_MS`). Cached `.bin` version is tracked in `cachedFirmwareVersion`; if null the service re-downloads on next request.
4. **No database** — GamerBell is intentionally stateless (no persistence). Session state is in-memory (`CopyOnWriteArrayList`). Do not add a database dependency without explicit instruction.
5. **Logging** — All classes use `@Slf4j` with MDC context (`sessionId`, `deviceId`, `clientIp`). Follow the existing structured log pattern (`key=value` pairs) so Loki can parse fields.
6. **Javadoc** — All public methods and classes must have Javadoc comments (enforced by `.github/copilot-instructions.md`).
7. **No wildcard imports** — use single explicit imports only.
8. **No nested classes** — create a separate file for each class; name packages wisely.
