package org.fitznet.fun.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.BellCountDto;
import org.fitznet.fun.dto.DeviceLogDto;
import org.fitznet.fun.service.ButtonService;
import org.fitznet.fun.service.FirmwareService;
import org.fitznet.fun.utils.JsonUtils;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.fitznet.fun.utils.Constants.ESP32_ERROR_HEADER;
import static org.fitznet.fun.utils.Constants.ESP32_MAC_ADDRESS_HEADER;
import static org.fitznet.fun.utils.Constants.ESP32_VERSION_HEADER;
import static org.fitznet.fun.utils.Constants.LATEST_VERSION_HEADER;

/**
 * REST controller for GamerBell API endpoints including session count and firmware updates.
 * Provides comprehensive structured logging for Loki integration.
 */
@Slf4j
@RestController
public class GamerBellController {

    private static final String MDC_DEVICE_MAC = "deviceMac";
    private static final String MDC_DEVICE_VERSION = "deviceVersion";
    private static final String MDC_DEVICE_ID = "deviceId";
    private static final String MDC_LOG_SOURCE = "logSource";

    final ButtonService buttonService;

    final FirmwareService firmwareService;
    final MeterRegistry meterRegistry;

    /**
     * Constructs a new GamerBellController with required services.
     *
     * @param buttonService   service for managing WebSocket sessions
     * @param firmwareService service for firmware version management
     * @param meterRegistry   Micrometer registry for firmware request metrics
     */
    public GamerBellController(ButtonService buttonService,
                               FirmwareService firmwareService,
                               MeterRegistry meterRegistry) {
        this.buttonService = buttonService;
        this.firmwareService = firmwareService;
        this.meterRegistry = meterRegistry;
        log.info("GamerBellController initialized");
    }

    /**
     * Returns the current count of active WebSocket sessions.
     *
     * @return JSON string containing the session count
     * @throws JsonProcessingException if serialization fails
     */
    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getCount() throws JsonProcessingException {
        long startTime = System.currentTimeMillis();

        long sessionCount = buttonService.getSessionCount();

        BellCountDto bellCountDto = BellCountDto.builder()
                .count(sessionCount)
                .build();

        String response = JsonUtils.OBJECT_MAPPER.writeValueAsString(bellCountDto);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Session count request - count={}, responseTimeMs={}", sessionCount, duration);

        return response;
    }

    /**
     * Checks for firmware updates and serves the latest firmware binary if available.
     *
     * @param currentVersion the current firmware version from the ESP32 device header
     * @param deviceMac      the MAC address of the ESP32 device
     * @return firmware binary if update available, 304 if up-to-date, or error status
     */
    @GetMapping("/api/firmware/latest")
    public ResponseEntity<Resource> checkForUpdate(
            @RequestHeader(value = ESP32_VERSION_HEADER, required = false) String currentVersion,
            @RequestHeader(value = ESP32_MAC_ADDRESS_HEADER, required = false) String deviceMac) {

        long startTime = System.currentTimeMillis();
        Timer.Sample firmwareCheckSample = Timer.start(meterRegistry);
        String checkOutcome = "error";

        try {
            // Set MDC context for device tracking
            if (deviceMac != null) {
                MDC.put(MDC_DEVICE_MAC, deviceMac);
            }
            if (currentVersion != null) {
                MDC.put(MDC_DEVICE_VERSION, currentVersion);
            }

            log.info("Firmware update check initiated - deviceMac={}, currentVersion={}",
                    deviceMac != null ? deviceMac : "unknown",
                    currentVersion != null ? currentVersion : "unknown");

            String latestVersion = firmwareService.getLatestVersion();
            log.debug("Latest available firmware version: {}", latestVersion);

            if (currentVersion != null && currentVersion.equals(latestVersion)) {
                long duration = System.currentTimeMillis() - startTime;
                checkOutcome = "up_to_date";
                log.info("Device firmware up-to-date - deviceMac={}, version={}, responseTimeMs={}",
                        deviceMac != null ? deviceMac : "unknown",
                        currentVersion,
                        duration);
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            }

            log.info("Device requires firmware update - deviceMac={}, currentVersion={}, targetVersion={}",
                    deviceMac != null ? deviceMac : "unknown",
                    currentVersion != null ? currentVersion : "unknown",
                    latestVersion);

            // Ensure firmware is cached locally — thread-safe: only one download per version executes at a time
            boolean firmwareReady = firmwareService.ensureFirmwareReady(latestVersion);
            if (!firmwareReady) {
                long duration = System.currentTimeMillis() - startTime;
                checkOutcome = "download_failed";
                log.error("Firmware download failed - version={}, deviceMac={}, responseTimeMs={}",
                        latestVersion,
                        deviceMac != null ? deviceMac : "unknown",
                        duration);
                log.error("Manual action required: Create GitHub release at https://github.com/mattlol85/Esp32FitznetBell/releases with tag '{}' and upload .bin file", latestVersion);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Firmware-Error", "No firmware available. Create GitHub release or add local firmware.bin")
                        .build();
            }

            try {
                Resource firmware = firmwareService.getFirmwareFile();
                long firmwareSize = firmware.contentLength();

                log.info("Serving firmware update - deviceMac={}, version={}, sizeBytes={}",
                        deviceMac != null ? deviceMac : "unknown",
                        latestVersion,
                        firmwareSize);

                long duration = System.currentTimeMillis() - startTime;
                log.info("Firmware served successfully - deviceMac={}, version={}, sizeBytes={}, responseTimeMs={}",
                        deviceMac != null ? deviceMac : "unknown",
                        latestVersion,
                        firmwareSize,
                        duration);
                checkOutcome = "update_served";

                return ResponseEntity.ok()
                        .header(LATEST_VERSION_HEADER, latestVersion)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .contentLength(firmwareSize)
                        .body(firmware);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                checkOutcome = "serve_failed";
                log.error("Error serving firmware - deviceMac={}, error={}, errorType={}, responseTimeMs={}",
                        deviceMac != null ? deviceMac : "unknown",
                        e.getMessage(),
                        e.getClass().getSimpleName(),
                        duration,
                        e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(ESP32_ERROR_HEADER, "Internal error serving firmware")
                        .build();
            }
        } finally {
            MDC.remove(MDC_DEVICE_MAC);
            MDC.remove(MDC_DEVICE_VERSION);
            recordFirmwareCheck(checkOutcome, firmwareCheckSample);
        }
    }

    /**
     * Accepts an error/log report from an ESP32 device and emits it as a structured
     * log line for Loki, so device-side failures can be investigated remotely.
     *
     * @param deviceLogDto the device log payload
     * @return 204 No Content on success, 400 if required fields are missing
     */
    @PostMapping("/api/devices/log")
    public ResponseEntity<Void> logDeviceError(@RequestBody DeviceLogDto deviceLogDto) {
        if (deviceLogDto == null
                || deviceLogDto.getDeviceId() == null || deviceLogDto.getDeviceId().isBlank()
                || deviceLogDto.getMessage() == null || deviceLogDto.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String level = deviceLogDto.getLevel() != null ? deviceLogDto.getLevel().name() : "ERROR";
        String source = deviceLogDto.getSource() != null ? deviceLogDto.getSource() : "unknown";

        try {
            MDC.put(MDC_DEVICE_ID, deviceLogDto.getDeviceId());
            MDC.put(MDC_DEVICE_VERSION, deviceLogDto.getFirmwareVersion());
            MDC.put(MDC_LOG_SOURCE, source);

            String logMessage = "Device log received - deviceId={}, firmwareVersion={}, source={}, message={}";
            Object[] args = {
                    deviceLogDto.getDeviceId(),
                    deviceLogDto.getFirmwareVersion(),
                    source,
                    deviceLogDto.getMessage()
            };

            if ("ERROR".equals(level)) {
                log.error(logMessage, args);
            } else if ("WARN".equals(level)) {
                log.warn(logMessage, args);
            } else {
                log.info(logMessage, args);
            }

            meterRegistry.counter("gamerbell.device.logs.total", "level", level, "source", source).increment();

            return ResponseEntity.noContent().build();
        } finally {
            MDC.remove(MDC_DEVICE_ID);
            MDC.remove(MDC_DEVICE_VERSION);
            MDC.remove(MDC_LOG_SOURCE);
        }
    }

    private void recordFirmwareCheck(String outcome, Timer.Sample sample) {
        meterRegistry.counter("gamerbell.firmware.checks.total", "outcome", outcome).increment();
        sample.stop(Timer.builder("gamerbell.firmware.check.duration")
                .description("Time spent handling OTA firmware checks")
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
