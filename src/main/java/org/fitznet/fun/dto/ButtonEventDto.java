package org.fitznet.fun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for button events received from ESP32 devices via WebSocket.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ButtonEventDto {
    private ButtonEvent buttonEvent;
    private String deviceId;
    private String firmwareVersion;
}
