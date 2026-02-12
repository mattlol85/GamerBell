package org.fitznet.fun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Represents a GitHub release asset (file attached to a release).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubAssetDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("browser_download_url")
    private String browserDownloadUrl;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("state")
    private String state;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}

