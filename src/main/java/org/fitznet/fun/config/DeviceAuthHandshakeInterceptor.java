package org.fitznet.fun.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Handshake interceptor that enforces shared-secret device authentication on the
 * {@code /ws} WebSocket endpoint.
 *
 * <p>The {@code Origin} header is the only control on the raw handshake today, and
 * non-browser clients (the ESP32 bell, curl, scripts) are free to set any origin,
 * so origin restriction is not an authentication control. This interceptor requires
 * every client to present a shared secret before the socket is upgraded.
 *
 * <p>The token may be supplied either as an HTTP header (preferred for the ESP32
 * firmware) or as a query parameter (required for browsers, which cannot set custom
 * headers on the WebSocket handshake):
 * <ul>
 *   <li>header: {@code X-GamerBell-Token: <secret>}</li>
 *   <li>query:  {@code /ws?token=<secret>}</li>
 * </ul>
 *
 * <p>Configuration (all overridable via environment variables):
 * <ul>
 *   <li>{@code gamerbell.ws.auth.enabled} / {@code GAMERBELL_WS_AUTH_ENABLED} — default {@code false}</li>
 *   <li>{@code gamerbell.ws.auth.token} / {@code GAMERBELL_WS_TOKEN} — the shared secret</li>
 *   <li>{@code gamerbell.ws.auth.header-name} / {@code GAMERBELL_WS_HEADER_NAME} — default {@code X-GamerBell-Token}</li>
 *   <li>{@code gamerbell.ws.auth.query-param} / {@code GAMERBELL_WS_QUERY_PARAM} — default {@code token}</li>
 * </ul>
 *
 * <p>When {@code enabled} is {@code false} the interceptor allows every handshake
 * (preserving the previous behaviour) but logs a warning on startup so the missing
 * control is visible.
 */
@Component
@Slf4j
public class DeviceAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final boolean enabled;
    private final String token;
    private final String headerName;
    private final String queryParam;

    /**
     * Constructs the interceptor from configuration.
     *
     * @param enabled     whether token authentication is enforced
     * @param token       the shared secret every client must present
     * @param headerName  the HTTP header carrying the token
     * @param queryParam  the query parameter carrying the token
     */
    public DeviceAuthHandshakeInterceptor(
            @Value("${gamerbell.ws.auth.enabled:false}") boolean enabled,
            @Value("${gamerbell.ws.auth.token:}") String token,
            @Value("${gamerbell.ws.auth.header-name:X-GamerBell-Token}") String headerName,
            @Value("${gamerbell.ws.auth.query-param:token}") String queryParam) {
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.headerName = headerName;
        this.queryParam = queryParam;

        if (!enabled) {
            log.warn("WebSocket device authentication is DISABLED - /ws accepts unauthenticated clients. "
                    + "Set gamerbell.ws.auth.enabled=true and gamerbell.ws.auth.token=<secret> to enforce.");
        } else if (this.token.isEmpty()) {
            log.error("WebSocket device authentication is ENABLED but no token is configured "
                    + "(gamerbell.ws.auth.token / GAMERBELL_WS_TOKEN) - all handshakes will be rejected.");
        } else {
            log.info("WebSocket device authentication is ENABLED - header={}, queryParam={}", headerName, queryParam);
        }
    }

    /**
     * Validates the shared-secret token before the WebSocket upgrade completes.
     *
     * @param request    the handshake request
     * @param response   the handshake response
     * @param wsHandler  the target WebSocket handler
     * @param attributes the handshake attributes map
     * @return {@code true} to allow the handshake, {@code false} to reject it
     */
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) {
        if (!enabled) {
            return true;
        }

        if (token.isEmpty()) {
            log.error("Rejecting WebSocket handshake - authentication enabled but no server token configured, "
                    + "remoteAddress={}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return false;
        }

        String presented = extractToken(request);
        if (presented != null && constantTimeEquals(token, presented)) {
            return true;
        }

        log.warn("Rejecting unauthenticated WebSocket handshake - remoteAddress={}, tokenPresented={}",
                request.getRemoteAddress(), presented != null);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    /**
     * No-op post-handshake hook.
     *
     * @param request   the handshake request
     * @param response  the handshake response
     * @param wsHandler the target WebSocket handler
     * @param exception any exception raised during the handshake, or {@code null}
     */
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
                               @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    /**
     * Extracts the token from the configured header, falling back to the query parameter.
     *
     * @param request the handshake request
     * @return the presented token, or {@code null} if absent
     */
    private String extractToken(ServerHttpRequest request) {
        List<String> headerValues = request.getHeaders().get(headerName);
        if (headerValues != null && !headerValues.isEmpty()) {
            String value = headerValues.getFirst();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        MultiValueMap<String, String> params = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams();
        String queryValue = params.getFirst(queryParam);
        if (queryValue != null && !queryValue.isBlank()) {
            return queryValue.trim();
        }
        return null;
    }

    /**
     * Compares two strings in constant time to avoid leaking the secret via timing.
     *
     * @param expected the configured secret
     * @param actual   the presented value
     * @return {@code true} if the values are equal
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
