package com.codereview.analyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.codereview.model.CodeIssue;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for Java code analyzers using JavaParser.
 */
@Slf4j
public abstract class AbstractJavaAnalyzer implements CodeAnalyzer {

    protected final JavaParser javaParser = new JavaParser();

    @Override
    public boolean supports(Path filePath) {
        return filePath.toString().endsWith(".java");
    }

    @Override
    public List<CodeIssue> analyze(Path filePath, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(content);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                issues.addAll(analyzeCompilationUnit(filePath, cu, content));
            } else {
                parseResult.getProblems().forEach(problem -> {
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S1135")
                            .ruleName("Parse Error")
                            .severity(CodeIssue.Severity.MAJOR)
                            .type(CodeIssue.IssueType.BUG)
                            .category("Syntax")
                            .filePath(filePath.toString())
                            .lineNumber(problem.getLocation()
                                    .flatMap(l -> Optional.of(l.getBegin().getRange()
                                            .map(r -> r.begin.line).orElse(1)))
                                    .orElse(1))
                            .message("Failed to parse Java file: " + problem.getMessage())
                            .source(getAnalyzerId())
                            .build());
                });
            }
        } catch (Exception e) {
            log.error("Error analyzing file {}: {}", filePath, e.getMessage());
        }

        return issues;
    }

    /**
     * Analyzes a parsed compilation unit.
     *
     * @param filePath Path to the source file
     * @param cu The parsed compilation unit
     * @param originalContent The original source content
     * @return List of issues found
     */
    protected abstract List<CodeIssue> analyzeCompilationUnit(Path filePath, CompilationUnit cu, String originalContent);

    protected String generateIssueId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    protected String extractCodeSnippet(String content, int lineNumber, int contextLines) {
        String[] lines = content.split("\n");
        int start = Math.max(0, lineNumber - contextLines - 1);
        int end = Math.min(lines.length, lineNumber + contextLines);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            snippet.append(String.format("%4d | %s%n", i + 1, lines[i]));
        }
        return snippet.toString();
    }
}
