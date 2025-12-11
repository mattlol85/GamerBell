# GamerBell 🔔

ESP32 Bell Button WebSocket Server - A Spring Boot application that manages WebSocket connections from ESP32 devices with button press events.

## Features

- 🔌 WebSocket server for real-time ESP32 device communication
- 📡 Button press/release event handling
- 🔄 Over-The-Air (OTA) firmware updates via GitHub releases
- 📊 Spring Boot Actuator health checks
- 🐳 Docker support with multi-platform images
- 🎯 Automatic versioning and releases via GitHub Actions

## Quick Start

### Using Docker Compose (Recommended)

1. **Pull and run with Docker Compose:**
   ```bash
   docker-compose up -d
   ```

2. **View logs:**
   ```bash
   docker-compose logs -f gamerbell
   ```

3. **Stop the service:**
   ```bash
   docker-compose down
   ```

### Using Docker Run

```bash
docker run -d \
  --name gamerbell \
  -p 8080:8080 \
  -v gamerbell-firmware:/app/firmware \
  --restart unless-stopped \
  mattlol85/bell-api:latest
```

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/mattlol85/GamerBell.git
   cd GamerBell
   ```

2. **Build with Gradle:**
   ```bash
   ./gradlew bootJar
   ```

3. **Run the JAR:**
   ```bash
   java -jar build/libs/GamerBell-*.jar
   ```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `FIRMWARE_STORAGE_PATH` | `./firmware` | Directory for firmware files |
| `FIRMWARE_GITHUB_REPO` | `mattlol85/Esp32FitznetBell` | GitHub repo for firmware releases |
| `FIRMWARE_FILENAME` | `firmware.bin` | Firmware binary filename |

### Docker Compose Configuration

Edit `docker-compose.yml` to customize environment variables:

```yaml
environment:
  SERVER_PORT: 8080
  FIRMWARE_STORAGE_PATH: /app/firmware
  FIRMWARE_GITHUB_REPO: mattlol85/Esp32FitznetBell
```

## API Endpoints

### WebSocket
- **`/ws`** - WebSocket endpoint for ESP32 device connections

### REST API
- **`GET /api/firmware/latest`** - Check for firmware updates and download
  - Header: `x-ESP32-version` - Current device firmware version
  - Returns: Firmware binary if update available, or 304 Not Modified

### Actuator Endpoints
- **`GET /actuator/health`** - Health check endpoint
- **`GET /actuator/info`** - Application info (version, git commit, etc.)

## ESP32 Integration

Your ESP32 devices should:

1. Connect to WebSocket at `ws://<server-ip>:8080/ws`
2. Send button events as JSON:
   ```json
   {
     "buttonEvent": "PRESSED",
     "deviceId": "device-name"
   }
   ```
3. Check for firmware updates periodically:
   ```
   GET /api/firmware/latest
   Header: x-ESP32-version: v1.0.0
   ```

## Development

### Local Development

```bash
# Run in development mode with auto-reload
./gradlew bootRun
```

### Build Docker Image Locally

```bash
docker build -t mattlol85/bell-api:local .
```

### Run Tests

```bash
./gradlew test
```

## CI/CD

This project uses GitHub Actions for automated:

- ✅ Building and testing on every push
- 🏷️ Semantic versioning based on commit messages
- 📦 GitHub releases with JAR artifacts
- 🐳 Multi-platform Docker images pushed to Docker Hub

### Commit Message Conventions

- `feat:` or `(feat)` - Minor version bump
- `fix:` or `(fix)` - Patch version bump
- `chore:` or `(chore)` - Patch version bump
- `!` anywhere - Major version bump (breaking change)
- `[skip release]` - Skip automatic release

## Docker Hub

Docker images are available at: [mattlol85/bell-api](https://hub.docker.com/r/mattlol85/bell-api)

**Tags:**
- `latest` - Latest stable release
- `v0.4.0` - Specific version with 'v' prefix
- `0.4.0` - Specific version number
- `sha-<commit>` - Specific git commit

## License

MIT License - See LICENSE file for details

## Author

Matt (mattlol85)

## Related Projects

- [Esp32FitznetBell](https://github.com/mattlol85/Esp32FitznetBell) - ESP32 firmware for button devices

