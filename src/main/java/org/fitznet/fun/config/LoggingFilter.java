package org.fitznet.fun.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.tracing.TraceContext;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that adds MDC (Mapped Diagnostic Context) fields for request tracing.
 * This enables correlation of all log messages within a single HTTP request lifecycle.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter implements Filter {

    private static final String REQUEST_ID_HEADER = TraceContext.REQUEST_ID_HEADER;
    private static final String CORRELATION_ID_HEADER = TraceContext.CORRELATION_ID_HEADER;

    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_CLIENT_IP = "clientIp";
    private static final String MDC_REQUEST_PATH = "requestPath";
    private static final String MDC_REQUEST_METHOD = "requestMethod";
    private static final String MDC_USER_AGENT = "userAgent";

    /**
     * Filters incoming requests to add MDC context for structured logging.
     *
     * @param request  the servlet request
     * @param response the servlet response
     * @param chain    the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        long startTime = System.currentTimeMillis();

        try {
            setupMDC(httpRequest, httpResponse);

            log.debug("Incoming request: {} {} from {}",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    getClientIp(httpRequest));

            chain.doFilter(request, response);

            long duration = System.currentTimeMillis() - startTime;

            log.info("Request completed: {} {} - status={} duration={}ms",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    httpResponse.getStatus(),
                    duration);

        } finally {
            clearMDC();
        }
    }

    /**
     * Sets up MDC context with request-specific fields.
     *
     * @param request  the HTTP servlet request
     * @param response the HTTP servlet response, which echoes the ids back to the caller
     */
    private void setupMDC(HttpServletRequest request, HttpServletResponse response) {
        // Generate or extract request ID (malformed inbound ids are replaced, never logged)
        String requestId = TraceContext.idOrNew(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_REQUEST_ID, requestId);

        // Extract or generate correlation ID for distributed tracing
        String correlationId = TraceContext.idOrDefault(request.getHeader(CORRELATION_ID_HEADER), requestId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(TraceContext.MDC_TRANSPORT, "http");
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        // Add request details
        MDC.put(MDC_CLIENT_IP, getClientIp(request));
        MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
        MDC.put(MDC_REQUEST_METHOD, request.getMethod());

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null) {
            MDC.put(MDC_USER_AGENT, truncate(userAgent, 100));
        }
    }

    /**
     * Clears all MDC context fields.
     */
    private void clearMDC() {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_CLIENT_IP);
        MDC.remove(MDC_REQUEST_PATH);
        MDC.remove(MDC_REQUEST_METHOD);
        MDC.remove(MDC_USER_AGENT);
        MDC.remove(TraceContext.MDC_TRANSPORT);
    }

    /**
     * Extracts the client IP address, considering proxy headers.
     *
     * @param request the HTTP servlet request
     * @return the client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP if there are multiple
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Truncates a string to the specified maximum length.
     *
     * @param value     the string to truncate
     * @param maxLength the maximum length
     * @return the truncated string
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}


