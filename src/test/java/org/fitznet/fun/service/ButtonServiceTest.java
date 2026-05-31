package org.fitznet.fun.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ButtonServiceTest {

    private ButtonService buttonService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        buttonService = new ButtonService(meterRegistry);
    }

    @Test
    void shouldIncrementCountWhenSessionAdded() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");

        buttonService.addSession(session);

        assertEquals(1, buttonService.getSessionCount());
        assertEquals(1.0, meterRegistry.get("gamerbell.websocket.sessions.active").gauge().value());
    }

    @Test
    void shouldDecrementCountWhenSessionRemoved() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        buttonService.addSession(session);

        buttonService.removeSession(session);

        assertEquals(0, buttonService.getSessionCount());
    }

    @Test
    void shouldSendMessageToAllOpenSessions() throws IOException {
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("test-session-1");
        when(session2.getId()).thenReturn("test-session-2");
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        buttonService.addSession(session1);
        buttonService.addSession(session2);

        buttonService.broadcastMessage("test message");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
        assertEquals(2.0, meterRegistry.get("gamerbell.broadcast.deliveries.total")
                .tag("result", "success")
                .counter()
                .count());
    }

    @Test
    void shouldSkipClosedSessionsWhenBroadcasting() throws IOException {
        WebSocketSession openSession = mock(WebSocketSession.class);
        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(openSession.getId()).thenReturn("open-session");
        when(closedSession.getId()).thenReturn("closed-session");
        when(openSession.isOpen()).thenReturn(true);
        when(closedSession.isOpen()).thenReturn(false);

        buttonService.addSession(openSession);
        buttonService.addSession(closedSession);

        buttonService.broadcastMessage("test message");

        verify(openSession).sendMessage(any(TextMessage.class));
        verify(closedSession, never()).sendMessage(any(TextMessage.class));
        assertEquals(1.0, meterRegistry.get("gamerbell.broadcast.deliveries.total")
                .tag("result", "skipped")
                .counter()
                .count());
    }

    @Test
    void shouldHandleIOExceptionWhenBroadcasting() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);
        doThrow(new IOException("Connection lost")).when(session).sendMessage(any(TextMessage.class));

        buttonService.addSession(session);

        // Should not throw
        assertDoesNotThrow(() -> buttonService.broadcastMessage("test message"));
        assertEquals(1.0, meterRegistry.get("gamerbell.broadcast.deliveries.total")
                .tag("result", "failure")
                .counter()
                .count());
    }

    @Test
    void shouldNotThrowWhenLoggingEvent() {
        // Simple smoke test - logEvent just logs
        assertDoesNotThrow(() -> buttonService.logEvent("device-1", "PRESSED"));
    }

    @Test
    void shouldHandleNullSessionIdWithoutFailing() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(null);

        // Should not throw even with null session ID
        assertDoesNotThrow(() -> buttonService.addSession(session));
        assertEquals(1, buttonService.getSessionCount());

        assertDoesNotThrow(() -> buttonService.removeSession(session));
        assertEquals(0, buttonService.getSessionCount());
    }

    @Test
    void shouldHandleNullSessionIdDuringBroadcast() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(null);
        when(session.isOpen()).thenReturn(true);

        buttonService.addSession(session);

        // Should not throw even with null session ID
        assertDoesNotThrow(() -> buttonService.broadcastMessage("test message"));
        verify(session).sendMessage(any(TextMessage.class));
    }
}
