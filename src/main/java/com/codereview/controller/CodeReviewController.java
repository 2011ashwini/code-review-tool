package com.codereview.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codereview.model.CodeReviewResult;
import com.codereview.model.ReviewRequest;
import com.codereview.service.CodeReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for code review operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
@Tag(name = "Code Review", description = "Code review operations for GitHub repositories")
public class CodeReviewController {

    private final CodeReviewService codeReviewService;

    @Operation(summary = "Review a GitHub repository",
            description = "Performs a comprehensive code review on a GitHub repository using SonarQube standards and AI analysis")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review completed successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CodeReviewResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "503", description = "Review service is disabled")
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CodeReviewResult> reviewRepository(@Valid @RequestBody ReviewRequest request) {
        log.info("Received review request for repository: {}", request.getRepositoryUrl());

        if (!codeReviewService.isReviewEnabled(request)) {
            log.info("Code review is disabled");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(CodeReviewResult.builder()
                            .repositoryUrl(request.getRepositoryUrl())
                            .status(CodeReviewResult.ReviewStatus.DISABLED)
                            .build());
        }

        CodeReviewResult result = codeReviewService.reviewRepository(request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Review a repository asynchronously",
            description = "Starts an asynchronous code review and returns immediately")
    @PostMapping(value = "/async", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CompletableFuture<CodeReviewResult>> reviewRepositoryAsync(
            @Valid @RequestBody ReviewRequest request) {
        log.info("Received async review request for repository: {}", request.getRepositoryUrl());

        if (!codeReviewService.isReviewEnabled(request)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        CompletableFuture<CodeReviewResult> future = codeReviewService.reviewRepositoryAsync(request);
        return ResponseEntity.accepted().body(future);
    }

    @Operation(summary = "Get review service status",
            description = "Returns the current status of the review service")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewServiceStatus> getStatus() {
        return ResponseEntity.ok(new ReviewServiceStatus(
                codeReviewService.isEnabled(),
                "Code Review Service is " + (codeReviewService.isEnabled() ? "enabled" : "disabled")
        ));
    }

    public record ReviewServiceStatus(boolean enabled, String message) {}
}
