package org.fitznet.fun.controller;

import io.micrometer.core.instrument.MeterRegistry;
import org.fitznet.fun.config.TestMetricsConfiguration;
import org.fitznet.fun.service.ButtonService;
import org.fitznet.fun.service.FirmwareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.fitznet.fun.utils.Constants.ESP32_ERROR_HEADER;
import static org.fitznet.fun.utils.Constants.ESP32_VERSION_HEADER;
import static org.fitznet.fun.utils.Constants.LATEST_VERSION_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestMetricsConfiguration.class)
@WebMvcTest(GamerBellController.class)
class GamerBellControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private ButtonService buttonService;

    @MockitoBean
    private FirmwareService firmwareService;

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnSessionCount() throws Exception {
        when(buttonService.getSessionCount()).thenReturn(5L);

        mockMvc.perform(get("/count"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"count\":5}"));
    }

    @Test
    void shouldReturnNotModifiedWhenVersionIsLatest() throws Exception {
        when(firmwareService.getLatestVersion()).thenReturn("v1.0.0");

        mockMvc.perform(get("/api/firmware/latest")
                        .header(ESP32_VERSION_HEADER, "v1.0.0"))
                .andExpect(status().isNotModified());

        verify(firmwareService, times(1)).getLatestVersion();
        verifyNoMoreInteractions(firmwareService);
        assertEquals(1.0, meterRegistry.get("gamerbell.firmware.checks.total")
                .tag("outcome", "up_to_date")
                .counter()
                .count());
    }

    @Test
    void shouldReturnServiceUnavailableWhenDownloadFails() throws Exception {
        when(firmwareService.getLatestVersion()).thenReturn("v1.0.1");
        when(firmwareService.ensureFirmwareReady("v1.0.1")).thenReturn(false);

        mockMvc.perform(get("/api/firmware/latest"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(ESP32_ERROR_HEADER,
                        "No firmware available. Create GitHub release or add local firmware.bin"));

        verify(firmwareService, times(1)).ensureFirmwareReady("v1.0.1");
        assertEquals(1.0, meterRegistry.get("gamerbell.firmware.checks.total")
                .tag("outcome", "download_failed")
                .counter()
                .count());
    }

    @Test
    void shouldServeFirmwareWhenAvailable() throws Exception {
        Path firmwarePath = createFirmwareFile(tempDir.resolve("firmware.bin"));
        FileSystemResource firmwareResource = new FileSystemResource(firmwarePath);

        when(firmwareService.getLatestVersion()).thenReturn("v1.0.2");
        when(firmwareService.ensureFirmwareReady("v1.0.2")).thenReturn(true);
        when(firmwareService.getFirmwareFile()).thenReturn(firmwareResource);

        mockMvc.perform(get("/api/firmware/latest"))
                .andExpect(status().isOk())
                .andExpect(header().string(LATEST_VERSION_HEADER, "v1.0.2"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
        assertEquals(1.0, meterRegistry.get("gamerbell.firmware.checks.total")
                .tag("outcome", "update_served")
                .counter()
                .count());
    }

    @Test
    void shouldAcceptDeviceLogAndReturnNoContent() throws Exception {
        String body = "{\"deviceId\":\"bell-1\",\"firmwareVersion\":\"v0.14.1\","
                + "\"level\":\"ERROR\",\"source\":\"count_fetch\",\"message\":\"HTTP 503\"}";

        mockMvc.perform(post("/api/devices/log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        assertEquals(1.0, meterRegistry.get("gamerbell.device.logs.total")
                .tag("level", "ERROR")
                .tag("source", "count_fetch")
                .counter()
                .count());
    }

    @Test
    void shouldRejectDeviceLogMissingRequiredFields() throws Exception {
        String body = "{\"firmwareVersion\":\"v0.14.1\"}";

        mockMvc.perform(post("/api/devices/log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private Path createFirmwareFile(Path path) throws IOException {
        Files.write(path, new byte[]{1, 2, 3});
        return path;
    }
}

