package org.fitznet.fun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents a GitHub release retrieved from the GitHub API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubReleaseDto {

    @JsonProperty("tag_name")
    private String tagName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("draft")
    private Boolean draft;

    @JsonProperty("prerelease")
    private Boolean prerelease;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("published_at")
    private OffsetDateTime publishedAt;

    @JsonProperty("assets")
    private List<GitHubAssetDto> assets;

    @JsonProperty("body")
    private String body;

    @JsonProperty("html_url")
    private String htmlUrl;
}

