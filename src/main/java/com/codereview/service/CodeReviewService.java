package com.codereview.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.codereview.analyzer.CodeAnalyzer;
import com.codereview.config.CodeReviewProperties;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeReviewResult;
import com.codereview.model.CodeReviewResult.ReviewStatus;
import com.codereview.model.CodeReviewResult.ReviewSummary;
import com.codereview.model.ReviewRequest;
import com.codereview.model.VulnerableDependency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Main service orchestrating all code review operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final CodeReviewProperties properties;
    private final GitHubService gitHubService;
    private final List<CodeAnalyzer> analyzers;
    private final VulnerabilityScanner vulnerabilityScanner;
    private final OpenAIReviewService openAIReviewService;

    /**
     * Performs a comprehensive code review on a GitHub repository.
     *
     * @param request The review request containing repository details
     * @return The complete review result in JSON-compatible format
     */
    public CodeReviewResult reviewRepository(ReviewRequest request) {
        long startTime = System.currentTimeMillis();

        // Check if review is enabled
        if (!isReviewEnabled(request)) {
            return createDisabledResult(request);
        }

        Path repoPath = null;
        try {
            // Clone the repository
            log.info("Starting code review for repository: {}", request.getRepositoryUrl());
            repoPath = gitHubService.cloneRepositoryAtCommit(
                    request.getRepositoryUrl(),
                    request.getBranch(),
                    request.getCommitSha());

            String commitSha = gitHubService.getCurrentCommitSha(repoPath);

            // Collect all Java files for analysis
            List<Path> filesToAnalyze = collectFilesToAnalyze(repoPath, request.getOptions());
            log.info("Found {} files to analyze", filesToAnalyze.size());

            // Run all analyses
            List<CodeIssue> allIssues = new ArrayList<>();
            List<VulnerableDependency> vulnerableDependencies = new ArrayList<>();

            // SonarQube-style static analysis
            if (request.getOptions().isSonarQubeRulesEnabled() &&
                    properties.getAnalysis().getSonarqubeRules().isEnabled()) {
                allIssues.addAll(runStaticAnalysis(repoPath, filesToAnalyze));
            }

            // Vulnerability scanning
            if (request.getOptions().isVulnerabilityScanEnabled() &&
                    properties.getAnalysis().getVulnerabilityScan().isEnabled()) {
                vulnerableDependencies.addAll(vulnerabilityScanner.scanDependencies(repoPath));
            }

            // AI-powered review
            if (request.getOptions().isAiReviewEnabled() &&
                    properties.getAnalysis().getAiReview().isEnabled() &&
                    openAIReviewService.isAvailable()) {
                allIssues.addAll(runAIReview(repoPath, filesToAnalyze));
            }

            // Build the result
            long analysisTime = System.currentTimeMillis() - startTime;
            return buildReviewResult(request, commitSha, filesToAnalyze.size(), allIssues,
                    vulnerableDependencies, analysisTime);

        } catch (GitAPIException e) {
            log.error("Git operation failed: {}", e.getMessage());
            return createErrorResult(request, "Failed to clone repository: " + e.getMessage());
        } catch (Exception e) {
            log.error("Review failed: {}", e.getMessage(), e);
            return createErrorResult(request, "Review failed: " + e.getMessage());
        } finally {
            // Cleanup
            if (repoPath != null) {
                gitHubService.cleanupRepository(repoPath);
            }
        }
    }

    /**
     * Performs an async code review.
     */
    @Async
    public CompletableFuture<CodeReviewResult> reviewRepositoryAsync(ReviewRequest request) {
        return CompletableFuture.completedFuture(reviewRepository(request));
    }

    /**
     * Checks if review is enabled based on global and request settings.
     */
    public boolean isReviewEnabled(ReviewRequest request) {
        return properties.isEnabled() && request.getOptions().isEnabled();
    }

    /**
     * Toggles the global review enable/disable setting.
     */
    public void setReviewEnabled(boolean enabled) {
        properties.setEnabled(enabled);
        log.info("Code review {}.", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns current enabled status.
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private List<Path> collectFilesToAnalyze(Path repoPath, ReviewRequest.ReviewOptions options) {
        List<String> supportedExtensions = options.getFileExtensions() != null ?
                options.getFileExtensions() :
                properties.getAnalysis().getSupportedExtensions();

        List<String> exclusions = options.getExcludePaths() != null ?
                options.getExcludePaths() :
                properties.getAnalysis().getExclusions();

        try (Stream<Path> paths = Files.walk(repoPath)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.toString();
                        // Check if file has supported extension
                        boolean supported = supportedExtensions.stream()
                                .anyMatch(ext -> fileName.endsWith(ext));
                        // Check if file is not in excluded paths
                        boolean excluded = exclusions.stream()
                                .anyMatch(excl -> fileName.contains(excl.replace("*", "")));
                        return supported && !excluded;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error collecting files: {}", e.getMessage());
            return List.of();
        }
    }

    private List<CodeIssue> runStaticAnalysis(Path repoPath, List<Path> files) {
        List<CodeIssue> issues = new ArrayList<>();

        for (Path file : files) {
            try {
                String content = Files.readString(file);
                Path relativePath = repoPath.relativize(file);

                for (CodeAnalyzer analyzer : analyzers) {
                    if (analyzer.supports(file)) {
                        List<CodeIssue> fileIssues = analyzer.analyze(relativePath, content);
                        issues.addAll(fileIssues);
                    }
                }
            } catch (IOException e) {
                log.error("Error reading file {}: {}", file, e.getMessage());
            }
        }

        log.info("Static analysis found {} issues", issues.size());
        return issues;
    }

    private List<CodeIssue> runAIReview(Path repoPath, List<Path> files) {
        List<CodeIssue> issues = new ArrayList<>();

        // Limit AI review to important files to control cost
        List<Path> priorityFiles = files.stream()
                .filter(f -> f.toString().endsWith(".java"))
                .filter(f -> !f.toString().contains("Test"))
                .limit(20) // Limit to 20 files for cost control
                .collect(Collectors.toList());

        for (Path file : priorityFiles) {
            try {
                String content = Files.readString(file);
                Path relativePath = repoPath.relativize(file);
                issues.addAll(openAIReviewService.reviewFile(relativePath.toString(), content));
            } catch (IOException e) {
                log.error("Error during AI review of {}: {}", file, e.getMessage());
            }
        }

        log.info("AI review found {} issues", issues.size());
        return issues;
    }

    private CodeReviewResult buildReviewResult(ReviewRequest request, String commitSha,
                                                int totalFiles, List<CodeIssue> issues,
                                                List<VulnerableDependency> vulnerabilities, long analysisTime) {

        // Calculate summary statistics
        Map<CodeIssue.Severity, Integer> issuesBySeverity = new HashMap<>();
        Map<CodeIssue.IssueType, Integer> issuesByType = new HashMap<>();
        Map<String, Integer> issuesByCategory = new HashMap<>();
        double totalEffort = 0;

        for (CodeIssue issue : issues) {
            issuesBySeverity.merge(issue.getSeverity(), 1, Integer::sum);
            issuesByType.merge(issue.getType(), 1, Integer::sum);
            issuesByCategory.merge(issue.getCategory(), 1, Integer::sum);
            if (issue.getEffortMinutes() != null) {
                totalEffort += issue.getEffortMinutes();
            }
        }

        // Determine quality gate status
        String qualityGateStatus = determineQualityGateStatus(issues, vulnerabilities);

        ReviewSummary summary = ReviewSummary.builder()
                .totalFiles(totalFiles)
                .filesAnalyzed(totalFiles)
                .totalIssues(issues.size())
                .issuesBySeverity(issuesBySeverity)
                .issuesByType(issuesByType)
                .issuesByCategory(issuesByCategory)
                .vulnerableDependenciesCount(vulnerabilities.size())
                .totalEffortMinutes(totalEffort)
                .qualityGateStatus(qualityGateStatus)
                .analysisTimeMs(analysisTime)
                .build();

        return CodeReviewResult.builder()
                .repositoryUrl(request.getRepositoryUrl())
                .branch(request.getBranch())
                .commitSha(commitSha)
                .reviewTimestamp(LocalDateTime.now())
                .status(ReviewStatus.SUCCESS)
                .summary(summary)
                .issues(issues)
                .vulnerableDependencies(vulnerabilities)
                .build();
    }

    private String determineQualityGateStatus(List<CodeIssue> issues, List<VulnerableDependency> vulnerabilities) {
        long blockers = issues.stream().filter(i -> i.getSeverity() == CodeIssue.Severity.BLOCKER).count();
        long criticals = issues.stream().filter(i -> i.getSeverity() == CodeIssue.Severity.CRITICAL).count();
        long criticalVulns = vulnerabilities.stream()
                .flatMap(v -> v.getVulnerabilities().stream())
                .filter(v -> "CRITICAL".equals(v.getSeverity()))
                .count();

        if (blockers > 0 || criticalVulns > 0) {
            return "FAILED";
        } else if (criticals > 5) {
            return "WARNING";
        } else {
            return "PASSED";
        }
    }

    private CodeReviewResult createDisabledResult(ReviewRequest request) {
        return CodeReviewResult.builder()
                .repositoryUrl(request.getRepositoryUrl())
                .branch(request.getBranch())
                .reviewTimestamp(LocalDateTime.now())
                .status(ReviewStatus.DISABLED)
                .summary(ReviewSummary.builder()
                        .qualityGateStatus("SKIPPED")
                        .build())
                .build();
    }

    private CodeReviewResult createErrorResult(ReviewRequest request, String errorMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("error", errorMessage);

        return CodeReviewResult.builder()
                .repositoryUrl(request.getRepositoryUrl())
                .branch(request.getBranch())
                .reviewTimestamp(LocalDateTime.now())
                .status(ReviewStatus.FAILED)
                .metadata(metadata)
                .build();
    }
}
