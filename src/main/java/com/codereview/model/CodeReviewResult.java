package com.codereview.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeReviewResult {

    private String repositoryUrl;
    private String branch;
    private String commitSha;
    private LocalDateTime reviewTimestamp;
    private ReviewStatus status;
    private ReviewSummary summary;

    @Builder.Default
    private List<CodeIssue> issues = new ArrayList<>();

    @Builder.Default
    private List<VulnerableDependency> vulnerableDependencies = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public enum ReviewStatus {
        SUCCESS,
        PARTIAL,
        FAILED,
        DISABLED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReviewSummary {

        private int totalFiles;
        private int filesAnalyzed;
        private int totalIssues;

        @JsonProperty("issuesBySeverity")
        @Builder.Default
        private Map<CodeIssue.Severity, Integer> issuesBySeverity = new HashMap<>();

        @JsonProperty("issuesByType")
        @Builder.Default
        private Map<CodeIssue.IssueType, Integer> issuesByType = new HashMap<>();

        @JsonProperty("issuesByCategory")
        @Builder.Default
        private Map<String, Integer> issuesByCategory = new HashMap<>();

        private int vulnerableDependenciesCount;
        private Double totalEffortMinutes;
        private String qualityGateStatus;
        private long analysisTimeMs;
    }
}
