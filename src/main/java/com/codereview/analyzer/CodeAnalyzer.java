package com.codereview.analyzer;

import java.nio.file.Path;
import java.util.List;

import com.codereview.model.CodeIssue;

/**
 * Interface for code analyzers that detect issues in source code.
 */
public interface CodeAnalyzer {

    /**
     * Gets the unique identifier for this analyzer.
     */
    String getAnalyzerId();

    /**
     * Gets a human-readable name for this analyzer.
     */
    String getAnalyzerName();

    /**
     * Analyzes a file and returns any issues found.
     *
     * @param filePath Path to the file to analyze
     * @param content The content of the file
     * @return List of issues found in the file
     */
    List<CodeIssue> analyze(Path filePath, String content);

    /**
     * Checks if this analyzer supports the given file type.
     *
     * @param filePath Path to the file
     * @return true if this analyzer can process the file
     */
    boolean supports(Path filePath);
}
