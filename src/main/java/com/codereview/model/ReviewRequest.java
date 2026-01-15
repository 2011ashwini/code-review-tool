package com.codereview.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewRequest {

    @NotBlank(message = "Repository URL is required")
    private String repositoryUrl;

    @Builder.Default
    private String branch = "main";

    private String commitSha;

    @Builder.Default
    private ReviewOptions options = new ReviewOptions();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewOptions {

        @Builder.Default
        private boolean enabled = true;

        @Builder.Default
        private boolean sonarQubeRulesEnabled = true;

        @Builder.Default
        private boolean vulnerabilityScanEnabled = true;

        @Builder.Default
        private boolean aiReviewEnabled = true;

        private List<String> includePaths;
        private List<String> excludePaths;
        private List<String> fileExtensions;
    }
}
