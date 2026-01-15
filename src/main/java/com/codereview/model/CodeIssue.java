package com.codereview.model;

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
public class CodeIssue {

    private String id;
    private String ruleId;
    private String ruleName;
    private Severity severity;
    private IssueType type;
    private String category;
    private String filePath;
    private Integer lineNumber;
    private Integer endLineNumber;
    private Integer column;
    private String message;
    private String description;
    private String codeSnippet;
    private String suggestion;
    private String sonarQubeReference;
    private Double effortMinutes;
    private String source;

    public enum Severity {
        BLOCKER,
        CRITICAL,
        MAJOR,
        MINOR,
        INFO
    }

    public enum IssueType {
        BUG,
        VULNERABILITY,
        CODE_SMELL,
        SECURITY_HOTSPOT,
        DEPRECATED_API,
        PERFORMANCE,
        MAINTAINABILITY,
        RELIABILITY,
        DOCUMENTATION
    }
}
