package org.fitznet.fun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for error/log reports sent from ESP32 devices.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceLogDto {
    private String deviceId;
    private String firmwareVersion;
    private DeviceLogLevel level;
    private String source;
    private String message;
}
