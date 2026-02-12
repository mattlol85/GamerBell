package org.fitznet.fun.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.BellCountDto;
import org.fitznet.fun.service.ButtonService;
import org.fitznet.fun.service.FirmwareService;
import org.fitznet.fun.utils.JsonUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.fitznet.fun.utils.Constants.ESP32_ERROR_HEADER;
import static org.fitznet.fun.utils.Constants.ESP32_MAC_ADDRESS_HEADER;
import static org.fitznet.fun.utils.Constants.ESP32_VERSION_HEADER;
import static org.fitznet.fun.utils.Constants.LATEST_VERSION_HEADER;

/**
 * REST controller for GamerBell API endpoints including session count and firmware updates.
 */
@Slf4j
@RestController
public class GamerBellController {

    final ButtonService buttonService;

    final FirmwareService firmwareService;

    /**
     * Constructs a new GamerBellController with required services.
     *
     * @param buttonService   service for managing WebSocket sessions
     * @param firmwareService service for firmware version management
     */
    public GamerBellController(ButtonService buttonService, FirmwareService firmwareService) {
        this.buttonService = buttonService;
        this.firmwareService = firmwareService;
    }

    /**
     * Returns the current count of active WebSocket sessions.
     *
     * @return JSON string containing the session count
     * @throws JsonProcessingException if serialization fails
     */
    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getCount() throws JsonProcessingException {

        BellCountDto bellCountDto = BellCountDto.builder()
                .count(buttonService.getSessionCount())
                .build();

        return JsonUtils.OBJECT_MAPPER.writeValueAsString(bellCountDto);
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

        log.info("Firmware update check - Device MAC: {}, Current Version: {}",
                 deviceMac != null ? deviceMac : "unknown",
                 currentVersion != null ? currentVersion : "unknown");

        String latestVersion = firmwareService.getLatestVersion();
        log.debug("Latest available version: {}", latestVersion);

        if (currentVersion != null && currentVersion.equals(latestVersion)) {
            // Device is up to date
            log.info("Device is up to date (version: {})", currentVersion);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        log.info("Device needs update from {} to {}",
                 currentVersion != null ? currentVersion : "unknown",
                 latestVersion);

        // Check if cached firmware file matches the latest version
        if (!firmwareService.isFirmwareUpToDate(latestVersion)) {
            log.info("Cached firmware is outdated or missing. Deleting old firmware and downloading version: {}", latestVersion);
            firmwareService.deleteOldFirmware();
        }

        // Check if firmware file exists
        if (firmwareService.isFirmwareMissing()) {
            log.warn("Firmware file not found locally, attempting to download from GitHub");
            boolean downloaded = firmwareService.downloadLatestFirmware(latestVersion);
            if (!downloaded) {
                log.error("Failed to download firmware from GitHub. Please create a release at: " +
                         "https://github.com/mattlol85/Esp32FitznetBell/releases with tag '{}' and upload a .bin file",
                         latestVersion);
                log.error("Or manually place firmware.bin in the ./firmware directory");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Firmware-Error", "No firmware available. Create GitHub release or add local firmware.bin")
                        .build();
            }
        }

        try {
            // Device needs update - serve the file
            Resource firmware = firmwareService.getFirmwareFile();
            log.info("Serving firmware update: {} bytes to device {}",
                     firmware.contentLength(),
                     deviceMac != null ? deviceMac : "unknown");

            return ResponseEntity.ok()
                    .header(LATEST_VERSION_HEADER, latestVersion)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(firmware.contentLength())
                    .body(firmware);

        } catch (Exception e) {
            log.error("Error serving firmware: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(ESP32_ERROR_HEADER, "Internal error serving firmware")
                    .build();
        }
    }

}
