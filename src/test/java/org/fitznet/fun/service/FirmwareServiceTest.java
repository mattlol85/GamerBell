package org.fitznet.fun.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirmwareServiceTest {

    private WireMockServer wireMockServer;
    private FirmwareService firmwareService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        firmwareService = new FirmwareService(WebClient.builder(), "http://localhost:" + wireMockServer.port());
        ReflectionTestUtils.setField(firmwareService, "githubRepo", "owner/repo");
        ReflectionTestUtils.setField(firmwareService, "firmwareStoragePath", tempDir.toString());
        ReflectionTestUtils.setField(firmwareService, "firmwareFilename", "firmware.bin");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldFetchLatestVersionFromGitHub() {
        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/releases/latest"))
                .willReturn(okJson("{" +
                        "\"tag_name\":\"v1.2.3\"," +
                        "\"published_at\":\"2025-12-10T00:00:00Z\"" +
                        "}")));

        String version = firmwareService.getLatestVersion();

        assertEquals("v1.2.3", version);
        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/repos/owner/repo/releases/latest")));
    }

    @Test
    void shouldReturnCachedVersionWhenFresh() {
        ReflectionTestUtils.setField(firmwareService, "cachedLatestVersion", "v9.9.9");
        ReflectionTestUtils.setField(firmwareService, "lastVersionCheckTime", Instant.now().toEpochMilli());

        String version = firmwareService.getLatestVersion();

        assertEquals("v9.9.9", version);
        wireMockServer.verify(0, getRequestedFor(urlEqualTo("/repos/owner/repo/releases/latest")));
    }

    @Test
    void shouldDownloadBinAssetFromGitHub() throws Exception {
        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/releases/tags/v1.0.0"))
                .willReturn(okJson("{" +
                        "\"tag_name\":\"v1.0.0\"," +
                        "\"assets\":[{" +
                        "\"name\":\"firmware.bin\"," +
                        "\"size\":3," +
                        "\"browser_download_url\":\"http://localhost:" + wireMockServer.port() + "/downloads/firmware.bin\"" +
                        "}]" +
                        "}")));

        wireMockServer.stubFor(get(urlEqualTo("/downloads/firmware.bin"))
                .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3})));

        boolean result = firmwareService.downloadLatestFirmware("v1.0.0");

        assertTrue(result);
        Path firmwarePath = tempDir.resolve("firmware.bin");
        assertTrue(Files.exists(firmwarePath));
        assertEquals(3, Files.size(firmwarePath));
    }

    @Test
    void shouldReturnFalseWhenNoBinAssetFound() {
        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/releases/tags/v1.0.0"))
                .willReturn(okJson("{" +
                        "\"tag_name\":\"v1.0.0\"," +
                        "\"assets\":[{" +
                        "\"name\":\"notes.txt\"," +
                        "\"size\":10," +
                        "\"browser_download_url\":\"http://localhost:" + wireMockServer.port() + "/downloads/notes.txt\"" +
                        "}]" +
                        "}")));

        boolean result = firmwareService.downloadLatestFirmware("v1.0.0");

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenReleaseMissing() {
        wireMockServer.stubFor(get(urlEqualTo("/repos/owner/repo/releases/tags/v1.0.0"))
                .willReturn(aResponse().withStatus(404)));

        boolean result = firmwareService.downloadLatestFirmware("v1.0.0");

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenFirmwareMissingOrUntracked() {
        boolean upToDateMissing = firmwareService.isFirmwareUpToDate("v1.0.0");
        assertFalse(upToDateMissing);

        ReflectionTestUtils.setField(firmwareService, "cachedFirmwareVersion", "v1.0.0");
        assertFalse(firmwareService.isFirmwareUpToDate("v1.0.0"));
    }
}

