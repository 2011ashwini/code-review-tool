package com.codereview.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VulnerableDependency {

    private String groupId;
    private String artifactId;
    private String version;
    private String filePath;
    private List<Vulnerability> vulnerabilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Vulnerability {
        private String cveId;
        private String severity;
        private Double cvssScore;
        private String description;
        private String recommendation;
        private String referenceUrl;
    }
}
