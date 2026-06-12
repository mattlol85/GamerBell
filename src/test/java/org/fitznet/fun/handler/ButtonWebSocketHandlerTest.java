package org.fitznet.fun.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.EOFException;
import java.io.IOException;
import org.fitznet.fun.dto.ButtonEventDto;
import org.fitznet.fun.service.ButtonService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.fitznet.fun.utils.JsonUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ButtonWebSocketHandlerTest {

    private ButtonService buttonService;
    private ButtonWebSocketHandler handler;
    private MeterRegistry meterRegistry;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        buttonService = mock(ButtonService.class);
        handler = new ButtonWebSocketHandler(buttonService, meterRegistry);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(ButtonWebSocketHandler.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ButtonWebSocketHandler.class)).detachAppender(logAppender);
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
        assertEquals(1.0, meterRegistry.get("gamerbell.button.events.total")
                .tag("event", "pressed")
                .counter()
                .count());
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
        assertEquals(1.0, meterRegistry.get("gamerbell.button.events.total")
                .tag("event", "held")
                .counter()
                .count());
    }

    @Test
    void shouldIgnoreInvalidJson() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-3");

        handler.handleTextMessage(session, new TextMessage("{invalid"));

        verify(buttonService, never()).broadcastMessage(anyString());
        assertEquals(1.0, meterRegistry.get("gamerbell.button.events.total")
                .tag("event", "invalid")
                .counter()
                .count());
        assertEquals(1.0, meterRegistry.get("gamerbell.websocket.errors.total")
                .tag("type", "message_processing")
                .counter()
                .count());
    }

    @Test
    void shouldLogExpectedDisconnectWithoutStackTrace() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-4");

        handler.handleTransportError(session, new EOFException());

        ILoggingEvent event = lastTransportLogEvent();
        assertEquals(Level.INFO, event.getLevel());
        assertNull(event.getThrowableProxy(), "Expected disconnects must not log a stack trace");
        assertEquals(1.0, meterRegistry.get("gamerbell.websocket.errors.total")
                .tag("type", "transport")
                .tag("exception", "EOFException")
                .counter()
                .count());
    }

    @Test
    void shouldLogConnectionResetAsExpectedDisconnect() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-5");

        handler.handleTransportError(session, new IOException("Connection reset by peer"));

        ILoggingEvent event = lastTransportLogEvent();
        assertEquals(Level.INFO, event.getLevel());
        assertNull(event.getThrowableProxy());
    }

    @Test
    void shouldLogWrappedEofCauseAsExpectedDisconnect() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-6");

        handler.handleTransportError(session, new RuntimeException("transport failure", new EOFException()));

        ILoggingEvent event = lastTransportLogEvent();
        assertEquals(Level.INFO, event.getLevel());
        assertNull(event.getThrowableProxy());
    }

    @Test
    void shouldLogUnexpectedTransportErrorAsError() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-7");

        handler.handleTransportError(session, new IllegalStateException("unexpected failure"));

        ILoggingEvent event = lastTransportLogEvent();
        assertEquals(Level.ERROR, event.getLevel());
        assertNotNull(event.getThrowableProxy(), "Unexpected transport errors must keep the stack trace");
        assertEquals(1.0, meterRegistry.get("gamerbell.websocket.errors.total")
                .tag("type", "transport")
                .tag("exception", "IllegalStateException")
                .counter()
                .count());
    }

    private ILoggingEvent lastTransportLogEvent() {
        return logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("transport error")
                        || e.getFormattedMessage().contains("disconnected abruptly"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No transport error log event captured"));
    }
}
