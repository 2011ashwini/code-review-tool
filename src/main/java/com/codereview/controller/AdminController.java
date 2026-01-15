package com.codereview.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codereview.config.CodeReviewProperties;
import com.codereview.service.CodeReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for administrative operations including enable/disable toggle.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations for code review service")
public class AdminController {

    private final CodeReviewService codeReviewService;
    private final CodeReviewProperties properties;

    @Operation(summary = "Enable code review",
            description = "Enables the code review functionality globally")
    @PostMapping(value = "/enable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ToggleResponse> enableReview() {
        codeReviewService.setReviewEnabled(true);
        log.info("Code review enabled via API");
        return ResponseEntity.ok(new ToggleResponse(true, "Code review has been enabled"));
    }

    @Operation(summary = "Disable code review",
            description = "Disables the code review functionality globally")
    @PostMapping(value = "/disable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ToggleResponse> disableReview() {
        codeReviewService.setReviewEnabled(false);
        log.info("Code review disabled via API");
        return ResponseEntity.ok(new ToggleResponse(false, "Code review has been disabled"));
    }

    @Operation(summary = "Toggle code review",
            description = "Toggles the code review functionality based on the request body")
    @PostMapping(value = "/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ToggleResponse> toggleReview(@RequestBody ToggleRequest request) {
        codeReviewService.setReviewEnabled(request.enabled());
        String message = request.enabled() ? "Code review has been enabled" : "Code review has been disabled";
        log.info("Code review toggled to: {}", request.enabled());
        return ResponseEntity.ok(new ToggleResponse(request.enabled(), message));
    }

    @Operation(summary = "Get current configuration",
            description = "Returns the current code review configuration")
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfigurationResponse> getConfiguration() {
        return ResponseEntity.ok(new ConfigurationResponse(
                properties.isEnabled(),
                properties.getAnalysis().getSonarqubeRules().isEnabled(),
                properties.getAnalysis().getVulnerabilityScan().isEnabled(),
                properties.getAnalysis().getAiReview().isEnabled(),
                properties.getOpenai().getModel()
        ));
    }

    @Operation(summary = "Update analysis settings",
            description = "Updates which analyzers are enabled/disabled")
    @PostMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfigurationResponse> updateSettings(@RequestBody SettingsRequest request) {
        if (request.sonarQubeEnabled() != null) {
            properties.getAnalysis().getSonarqubeRules().setEnabled(request.sonarQubeEnabled());
        }
        if (request.vulnerabilityScanEnabled() != null) {
            properties.getAnalysis().getVulnerabilityScan().setEnabled(request.vulnerabilityScanEnabled());
        }
        if (request.aiReviewEnabled() != null) {
            properties.getAnalysis().getAiReview().setEnabled(request.aiReviewEnabled());
        }

        log.info("Analysis settings updated: sonar={}, vuln={}, ai={}",
                properties.getAnalysis().getSonarqubeRules().isEnabled(),
                properties.getAnalysis().getVulnerabilityScan().isEnabled(),
                properties.getAnalysis().getAiReview().isEnabled());

        return getConfiguration();
    }

    public record ToggleRequest(boolean enabled) {}
    public record ToggleResponse(boolean enabled, String message) {}
    public record SettingsRequest(Boolean sonarQubeEnabled, Boolean vulnerabilityScanEnabled, Boolean aiReviewEnabled) {}
    public record ConfigurationResponse(
            boolean globalEnabled,
            boolean sonarQubeRulesEnabled,
            boolean vulnerabilityScanEnabled,
            boolean aiReviewEnabled,
            String aiModel
    ) {}
}
