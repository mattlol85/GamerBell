package org.fitznet.fun.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.fitznet.fun.dto.GitHubAssetDto;
import org.fitznet.fun.dto.GitHubReleaseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing firmware updates from GitHub releases.
 */
@Slf4j
@Service
public class FirmwareService {

    private final WebClient apiWebClient;
    private final WebClient downloadWebClient;
    private final MeterRegistry meterRegistry;
    private final ReentrantLock downloadLock = new ReentrantLock();

    @Value("${firmware.github.repo:}")
    private String githubRepo;

    @Value("${firmware.storage.path:./firmware}")
    private String firmwareStoragePath;

    @Value("${firmware.filename:firmware.bin}")
    private String firmwareFilename;

    private String cachedLatestVersion;
    private String cachedFirmwareVersion;
    private long lastVersionCheckTime = 0;
    private static final long VERSION_CACHE_DURATION_MS = 60000;

    /**
     * Constructs a FirmwareService with WebClient configuration.
     *
     * @param webClientBuilder  builder for creating WebClient instances
     * @param githubApiBaseUrl  base URL for GitHub API calls
     * @param meterRegistry     Micrometer registry for firmware metrics
     */
    public FirmwareService(WebClient.Builder webClientBuilder,
                           @Value("${firmware.github.api.base-url:https://api.github.com}") String githubApiBaseUrl,
                           MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.apiWebClient = webClientBuilder
                .baseUrl(githubApiBaseUrl)
                .build();

        HttpClient httpClient = HttpClient.create().followRedirect(true);
        this.downloadWebClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Fetches the latest release version from GitHub.
     *
     * @return latest version tag (e.g., "v1.0.1") or fallback default
     */
    public String getLatestVersion() {
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

        return cachedLatestVersion != null ? cachedLatestVersion : "v1.0.0";
    }

    /**
     * Ensures the firmware for the given version is cached locally, downloading it if necessary.
     * This method is thread-safe: only one download executes at a time per version, and
     * subsequent callers reuse the cached result without re-downloading.
     *
     * @param latestVersion the version that must be cached
     * @return true if firmware is ready to serve, false if download failed
     */
    public boolean ensureFirmwareReady(String latestVersion) {
        if (isFirmwareUpToDate(latestVersion)) {
            return true;
        }

        downloadLock.lock();
        try {
            // Re-check after acquiring the lock — another thread may have finished the download
            if (isFirmwareUpToDate(latestVersion)) {
                return true;
            }
            deleteOldFirmware();
            return downloadLatestFirmware(latestVersion);
        } finally {
            downloadLock.unlock();
        }
    }

    /**
     * Gets the firmware binary file as a Resource.
     *
     * @return Resource pointing to the firmware file
     */
    public Resource getFirmwareFile() {
        Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
        if (!firmwarePath.toFile().exists()) {
            log.error("Firmware file not found at: {}", firmwarePath.toAbsolutePath());
            throw new RuntimeException("Firmware file not found");
        }

        log.info("Serving firmware file: {} (size: {} bytes)",
                 firmwarePath.toAbsolutePath(), firmwarePath.toFile().length());
        return new FileSystemResource(firmwarePath.toFile());
    }

    /**
     * Downloads the latest firmware from GitHub release with retry on transient failures.
     *
     * @param version the version to download
     * @return true if download successful
     */
    public boolean downloadLatestFirmware(String version) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean downloadSuccessful = false;

        try {
            if (githubRepo == null || githubRepo.isEmpty()) {
                log.warn("Cannot download firmware: GitHub repo not configured");
                return false;
            }

            log.info("Attempting to download firmware version {} from GitHub repo: {}", version, githubRepo);

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

                for (GitHubAssetDto asset : release.getAssets()) {
                    if (asset.getName().endsWith(".bin")) {
                        log.info("Found firmware binary: {} ({} bytes)", asset.getName(), asset.getSize());
                        downloadSuccessful = downloadFirmwareFromUrl(asset.getBrowserDownloadUrl(), version);
                        return downloadSuccessful;
                    }
                }

                String availableFiles = release.getAssets().stream()
                        .map(GitHubAssetDto::getName)
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
        } finally {
            recordFirmwareDownload(downloadSuccessful, sample);
        }
    }

    /**
     * Checks if the firmware file is missing locally.
     *
     * @return true if the firmware file does not exist on disk
     */
    public boolean isFirmwareMissing() {
        Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
        boolean exists = firmwarePath.toFile().exists();
        log.debug("Firmware file exists: {}", exists);
        return !exists;
    }

    /**
     * Checks whether the locally cached firmware matches the given version.
     *
     * @param latestVersion the version to compare against
     * @return true if the cached firmware matches latestVersion
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
     * Deletes the locally cached firmware file. Silently ignores the case where the file
     * is already absent (e.g. deleted by a concurrent request).
     */
    public void deleteOldFirmware() {
        try {
            Path firmwarePath = Paths.get(firmwareStoragePath, firmwareFilename);
            boolean deleted = Files.deleteIfExists(firmwarePath);
            if (deleted) {
                log.info("Deleted old firmware file: {}", firmwarePath.toAbsolutePath());
            } else {
                log.debug("Firmware file was already absent, nothing to delete");
            }
            cachedFirmwareVersion = null;
        } catch (IOException e) {
            log.error("Failed to delete old firmware file: {}", e.getMessage(), e);
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
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(10))
                            .doBeforeRetry(signal -> log.warn("Retrying firmware download (attempt {}): {}",
                                    signal.totalRetries() + 1, signal.failure().getMessage())))
                    .doOnError(error -> log.error("Error during firmware download: {}", error.getMessage()))
                    .block();

            if (firmwareData != null && firmwareData.length > 0) {
                log.info("Downloaded {} bytes from GitHub", firmwareData.length);

                Path storagePath = Paths.get(firmwareStoragePath);
                Files.createDirectories(storagePath);

                // Write to a temp file first, then rename atomically to avoid partial reads
                Path tempPath = storagePath.resolve(firmwareFilename + ".tmp");
                Path finalPath = storagePath.resolve(firmwareFilename);
                Files.write(tempPath, firmwareData);
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                log.info("Firmware downloaded successfully: {} bytes written to {}",
                         firmwareData.length, finalPath.toAbsolutePath());
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

    private void recordFirmwareDownload(boolean successful, Timer.Sample sample) {
        String outcome = successful ? "success" : "failure";
        meterRegistry.counter("gamerbell.firmware.downloads.total", "outcome", outcome).increment();
        sample.stop(Timer.builder("gamerbell.firmware.download.duration")
                .description("Time spent fetching and caching firmware from GitHub")
                .tag("outcome", outcome)
                .register(meterRegistry));
    }
}
