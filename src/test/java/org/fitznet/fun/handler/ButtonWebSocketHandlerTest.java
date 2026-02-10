package org.fitznet.fun.handler;

import org.fitznet.fun.dto.ButtonEventDto;
import org.fitznet.fun.service.ButtonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.fitznet.fun.utils.JsonUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ButtonWebSocketHandlerTest {

    private ButtonService buttonService;
    private ButtonWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        buttonService = mock(ButtonService.class);
        handler = new ButtonWebSocketHandler(buttonService);
    }

    @Test
    void shouldBroadcastPressedEvent() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");

        ButtonEventDto event = ButtonEventDto.builder()
                .buttonEvent(org.fitznet.fun.dto.ButtonEvent.PRESSED)
                .deviceId("device-1")
                .build();

        String payload = OBJECT_MAPPER.writeValueAsString(event);

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(buttonService).broadcastMessage(messageCaptor.capture());
        ButtonEventDto result = OBJECT_MAPPER.readValue(messageCaptor.getValue(), ButtonEventDto.class);
        assertEquals(event.getButtonEvent(), result.getButtonEvent());
        assertEquals(event.getDeviceId(), result.getDeviceId());
    }

    @Test
    void shouldNotBroadcastHeldEvent() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-2");

        ButtonEventDto event = ButtonEventDto.builder()
                .buttonEvent(org.fitznet.fun.dto.ButtonEvent.HELD)
                .deviceId("device-2")
                .build();

        String payload = OBJECT_MAPPER.writeValueAsString(event);

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(buttonService, never()).broadcastMessage(anyString());
    }

    @Test
    void shouldIgnoreInvalidJson() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-3");

        handler.handleTextMessage(session, new TextMessage("{invalid"));

        verify(buttonService, never()).broadcastMessage(anyString());
    }
}

