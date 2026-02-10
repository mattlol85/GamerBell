package org.fitznet.fun.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ButtonEventDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldIgnoreUnknownFieldsWhenDeserializing() throws Exception {
        String payload = "{\"buttonEvent\":\"PRESSED\",\"deviceId\":\"dev-1\",\"type\":\"CONNECTED\"}";

        ButtonEventDto dto = objectMapper.readValue(payload, ButtonEventDto.class);

        assertNotNull(dto);
        assertEquals(ButtonEvent.PRESSED, dto.getButtonEvent());
        assertEquals("dev-1", dto.getDeviceId());
    }
}

