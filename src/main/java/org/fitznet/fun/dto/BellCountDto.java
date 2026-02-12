package org.fitznet.fun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object representing the count of active WebSocket sessions.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BellCountDto {
    private Long count;
}
