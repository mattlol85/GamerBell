package org.fitznet.fun.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceAuthHandshakeInterceptorTest {

    private static final String SECRET = "s3cr3t-token";

    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    private ServerHttpRequest request(HttpHeaders headers, String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://gamerbell.example/ws" + (query == null ? "" : "?" + query)));
        return request;
    }

    private DeviceAuthHandshakeInterceptor interceptor(boolean enabled, String token) {
        return new DeviceAuthHandshakeInterceptor(enabled, token, "X-GamerBell-Token", "token");
    }

    @Test
    void allowsEveryHandshakeWhenDisabled() {
        Map<String, Object> attrs = new HashMap<>();
        boolean allowed = interceptor(false, "")
                .beforeHandshake(request(new HttpHeaders(), null), mock(ServerHttpResponse.class), handler, attrs);
        assertTrue(allowed);
    }

    @Test
    void rejectsWhenEnabledButNoServerToken() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean allowed = interceptor(true, "  ")
                .beforeHandshake(request(new HttpHeaders(), null), response, handler, new HashMap<>());
        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void acceptsValidTokenFromHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-GamerBell-Token", SECRET);
        boolean allowed = interceptor(true, SECRET)
                .beforeHandshake(request(headers, null), mock(ServerHttpResponse.class), handler, new HashMap<>());
        assertTrue(allowed);
    }

    @Test
    void acceptsValidTokenFromQueryParam() {
        boolean allowed = interceptor(true, SECRET)
                .beforeHandshake(request(new HttpHeaders(), "token=" + SECRET),
                        mock(ServerHttpResponse.class), handler, new HashMap<>());
        assertTrue(allowed);
    }

    @Test
    void rejectsMissingToken() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        boolean allowed = interceptor(true, SECRET)
                .beforeHandshake(request(new HttpHeaders(), null), response, handler, new HashMap<>());
        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsWrongToken() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-GamerBell-Token", "nope");
        boolean allowed = interceptor(true, SECRET)
                .beforeHandshake(request(headers, null), response, handler, new HashMap<>());
        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
