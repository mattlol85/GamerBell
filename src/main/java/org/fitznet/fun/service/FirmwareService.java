package org.fitznet.fun.service;

import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.GitHubReleaseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.springframework.web.reactive.function.client.WebClientResponseException.*;
import static reactor.netty.http.client.HttpClient.*;

@Slf4j
@Service
public class FirmwareService {

    private final WebClient apiWebClient;
    private final WebClient downloadWebClient;

    @Value("${firmware.github.repo:}")
    private String githubRepo;

    @Value("${firmware.storage.path:./firmware}")
    private String firmwareStoragePath;

    @Value("${firmware.filename:firmware.bin}")
    private String firmwareFilename;

    private String cachedLatestVersion;
    private String cachedFirmwareVersion; // The version of the firmware.bin file we have
    private long lastVersionCheckTime = 0;
    private static final long VERSION_CACHE_DURATION_MS = 60000;

    public FirmwareService(WebClient.Builder webClientBuilder) {
        // WebClient for GitHub API calls
        this.apiWebClient = webClientBuilder
                .baseUrl("https://api.github.com")
                .build();


        HttpClient httpClient = create().followRedirect(true);

        this.downloadWebClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                .build();
    }

    /**
     * Fetches the latest release version from GitHub
     * @return Latest version tag (e.g., "v1.0.1") or null if not available
     */
    public String getLatestVersion() {
        // Return cached version if still valid
        if (cachedLatestVersion != null &&
            (System.currentTimeMillis() - lastVersionCheckTime) < VERSION_CACHE_DURATION_MS) {
            log.debug("Returning cached version: {}", cachedLatestVersion);
            return cachedLatestVersion;
        }

        if (githubRepo == null || githubRepo.isEmpty()) {
            log.warn("GitHub repo not configured. Using default version.");
            return "v1.0.0";
        }

        try {
            log.info("Fetching latest release from GitHub repo: {}", githubRepo);
            GitHubReleaseDto response = apiWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/releases/latest")
                            .build(githubRepo.split("/")[0], githubRepo.split("/")[1]))
                    .retrieve()
                    .bodyToMono(GitHubReleaseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.getTagName() != null) {
                cachedLatestVersion = response.getTagName();
                lastVersionCheckTime = System.currentTimeMillis();
                log.info("Latest version from GitHub: {} (published: {})",
                         cachedLatestVersion, response.getPublishedAt());
                return cachedLatestVersion;
            }
        } catch (NotFound e) {
            log.warn("No releases found in GitHub repo: {}", githubRepo);
            log.warn("Create your first release at: https://github.com/{}/releases/new", githubRepo);
            log.warn("Using default version: v1.0.0");
        } catch (Exception e) {
            log.error("Failed to fetch latest version from GitHub: {} - {}",
                     e.getClass().getSimpleName(), e.getMessage());
        }

        // Fallback to cached version or default
        return cachedLatestVersion != null ? cachedLatestVersion : "v1.0.0";
    }

    /**
     * Gets the firmware binary file as a Resource
     * @return Resource pointing to the firmware file
     */
    public Resource getFirmwareFile() {
        Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
        File firmwareFile = firmwarePath.toFile();

        if (!firmwareFile.exists()) {
            log.error("Firmware file not found at: {}", firmwarePath.toAbsolutePath());
            throw new RuntimeException("Firmware file not found");
        }

        log.info("Serving firmware file: {} (size: {} bytes)",
                 firmwarePath.toAbsolutePath(), firmwareFile.length());
        return new FileSystemResource(firmwareFile);
    }

    /**
     * Downloads the latest firmware from GitHub release
     * @param version The version to download
     * @return true if download successful
     */
    public boolean downloadLatestFirmware(String version) {
        if (githubRepo == null || githubRepo.isEmpty()) {
            log.warn("Cannot download firmware: GitHub repo not configured");
            return false;
        }

        try {
            log.info("Attempting to download firmware version {} from GitHub repo: {}", version, githubRepo);

            // Get release assets
            String[] repoParts = githubRepo.split("/");
            GitHubReleaseDto release = apiWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/releases/tags/{tag}")
                            .build(repoParts[0], repoParts[1], version))
                    .retrieve()
                    .bodyToMono(GitHubReleaseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (release != null && release.getAssets() != null) {
                if (release.getAssets().isEmpty()) {
                    log.error("GitHub release {} exists but has no assets. Please upload a .bin file to the release.", version);
                    return false;
                }

                for (GitHubReleaseDto.GitHubAssetDto asset : release.getAssets()) {
                    String name = asset.getName();
                    // Look for .bin file
                    if (name.endsWith(".bin")) {
                        String downloadUrl = asset.getBrowserDownloadUrl();
                        log.info("Found firmware binary: {} ({} bytes)", name, asset.getSize());
                        return downloadFirmwareFromUrl(downloadUrl, version);
                    }
                }

                // Log available assets for troubleshooting
                String availableFiles = release.getAssets().stream()
                        .map(GitHubReleaseDto.GitHubAssetDto::getName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("none");
                log.error("GitHub release {} has assets but no .bin file found. Available files: {}",
                         version, availableFiles);
            }

            log.error("No .bin file found in GitHub release {}", version);
            return false;

        } catch (NotFound e) {
            log.error("GitHub release '{}' not found. Please create a release at: https://github.com/{}/releases/new",
                     version, githubRepo);
            return false;
        } catch (Exception e) {
            log.error("Failed to download firmware from GitHub: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private boolean downloadFirmwareFromUrl(String url, String version) {
        try {
            log.info("Downloading firmware from URL: {}", url);

            byte[] firmwareData = downloadWebClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                             clientResponse -> {
                                 log.error("Failed to download firmware: HTTP {}", clientResponse.statusCode());
                                 return clientResponse.createException();
                             })
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(60))
                    .doOnError(error -> log.error("Error during firmware download: {}", error.getMessage()))
                    .block();

            if (firmwareData != null && firmwareData.length > 0) {
                log.info("Downloaded {} bytes from GitHub", firmwareData.length);

                // Ensure directory exists
                Path storagePath = Paths.get(firmwareStoragePath);
                Files.createDirectories(storagePath);

                // Save firmware file
                Path firmwarePath = storagePath.resolve(firmwareFilename);
                try (FileOutputStream fos = new FileOutputStream(firmwarePath.toFile())) {
                    fos.write(firmwareData);
                }

                log.info("Firmware downloaded successfully: {} bytes written to {}",
                         firmwareData.length, firmwarePath.toAbsolutePath());
                cachedFirmwareVersion = version;
                log.info("Cached firmware version updated to: {}", cachedFirmwareVersion);
                return true;
            } else {
                log.error("Downloaded firmware data is null or empty");
            }

        } catch (IOException e) {
            log.error("Failed to save firmware file: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error downloading firmware: {} - {}",
                     e.getClass().getSimpleName(), e.getMessage(), e);
        }

        return false;
    }

    /**
     * Check if a firmware file is missing locally
     */
    public boolean isFirmwareMissing() {
        Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
        boolean exists = firmwarePath.toFile().exists();
        log.debug("Firmware file exists: {}", exists);
        return !exists;
    }

    /**
     * Check if the cached firmware file matches the latest version
     * @param latestVersion The latest version from GitHub
     * @return true if cached firmware matches latest version
     */
    public boolean isFirmwareUpToDate(String latestVersion) {
        if (isFirmwareMissing()) {
            log.debug("No firmware file exists, not up to date");
            return false;
        }

        if (cachedFirmwareVersion == null) {
            log.debug("No cached firmware version tracked, assuming out of date");
            return false;
        }

        boolean upToDate = cachedFirmwareVersion.equals(latestVersion);
        log.debug("Firmware up to date check: cached={}, latest={}, result={}",
                 cachedFirmwareVersion, latestVersion, upToDate);
        return upToDate;
    }

    /**
     * Delete the old firmware file when a new version is available
     */
    public void deleteOldFirmware() {
        try {
            Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
            if (Files.exists(firmwarePath)) {
                Files.delete(firmwarePath);
                log.info("Deleted old firmware file: {}", firmwarePath.toAbsolutePath());
                cachedFirmwareVersion = null;
            }
        } catch (IOException e) {
            log.error("Failed to delete old firmware file: {}", e.getMessage(), e);
        }
    }
}

