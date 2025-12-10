package org.fitznet.fun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitHubAssetDto {

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
}

