package com.codereview.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.codereview.config.CodeReviewProperties;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for AI-powered code review using OpenAI's API.
 * Uses gpt-4o-mini for cost-effectiveness.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIReviewService {

    private final CodeReviewProperties properties;
    private OpenAiService openAiService;

    private static final String SYSTEM_PROMPT = """
            You are an expert code reviewer specializing in Java and Spring Boot applications.
            Your task is to analyze code and identify issues following SonarQube standards.

            For each issue you find, provide the response in this exact format:

            ISSUE_START
            SEVERITY: [BLOCKER|CRITICAL|MAJOR|MINOR|INFO]
            TYPE: [BUG|VULNERABILITY|CODE_SMELL|SECURITY_HOTSPOT|PERFORMANCE|MAINTAINABILITY]
            CATEGORY: [category name]
            LINE: [line number]
            RULE_ID: [java:SXXXX or custom rule id]
            RULE_NAME: [rule name]
            MESSAGE: [short description of the issue]
            DESCRIPTION: [detailed explanation]
            SUGGESTION: [how to fix it]
            ISSUE_END

            Focus on:
            1. Security vulnerabilities (SQL injection, XSS, etc.)
            2. Code smells and bad practices
            3. Performance issues
            4. Maintainability concerns
            5. Potential bugs
            6. Design pattern violations
            7. Best practices for Spring Boot

            Be concise but thorough. Only report actual issues, not style preferences.
            """;

    @PostConstruct
    public void init() {
        String apiKey = properties.getOpenai().getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
            log.info("OpenAI service initialized with model: {}", properties.getOpenai().getModel());
        } else {
            log.warn("OpenAI API key not configured. AI review will be disabled.");
        }
    }

    /**
     * Checks if the OpenAI service is available.
     */
    public boolean isAvailable() {
        return openAiService != null && properties.getOpenai().isEnabled();
    }

    /**
     * Reviews a file using OpenAI's API.
     *
     * @param filePath Path to the file being reviewed
     * @param content Content of the file
     * @return List of issues identified by AI
     */
    public List<CodeIssue> reviewFile(String filePath, String content) {
        if (!isAvailable()) {
            log.debug("OpenAI service not available, skipping AI review for {}", filePath);
            return List.of();
        }

        // Truncate large files to avoid token limits
        String truncatedContent = truncateContent(content, 8000);

        String userPrompt = String.format("""
                Please review the following Java code from file: %s

                ```java
                %s
                ```

                Identify all issues following SonarQube standards. Report each issue in the specified format.
                """, filePath, truncatedContent);

        try {
            List<ChatMessage> messages = List.of(
                    new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT),
                    new ChatMessage(ChatMessageRole.USER.value(), userPrompt)
            );

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(properties.getOpenai().getModel())
                    .messages(messages)
                    .maxTokens(properties.getOpenai().getMaxTokens())
                    .temperature(properties.getOpenai().getTemperature())
                    .build();

            String response = openAiService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            return parseAIResponse(filePath, response);

        } catch (Exception e) {
            log.error("Error during AI review of {}: {}", filePath, e.getMessage());
            return List.of();
        }
    }

    /**
     * Reviews multiple files in batch for efficiency.
     *
     * @param files Map of file paths to content
     * @return List of all issues found
     */
    public List<CodeIssue> reviewFiles(java.util.Map<String, String> files) {
        List<CodeIssue> allIssues = new ArrayList<>();

        for (var entry : files.entrySet()) {
            allIssues.addAll(reviewFile(entry.getKey(), entry.getValue()));
        }

        return allIssues;
    }

    private List<CodeIssue> parseAIResponse(String filePath, String response) {
        List<CodeIssue> issues = new ArrayList<>();

        Pattern issuePattern = Pattern.compile(
                "ISSUE_START\\s*" +
                        "SEVERITY:\\s*(\\w+)\\s*" +
                        "TYPE:\\s*(\\w+)\\s*" +
                        "CATEGORY:\\s*(.+?)\\s*" +
                        "LINE:\\s*(\\d+)\\s*" +
                        "RULE_ID:\\s*(.+?)\\s*" +
                        "RULE_NAME:\\s*(.+?)\\s*" +
                        "MESSAGE:\\s*(.+?)\\s*" +
                        "DESCRIPTION:\\s*(.+?)\\s*" +
                        "SUGGESTION:\\s*(.+?)\\s*" +
                        "ISSUE_END",
                Pattern.DOTALL);

        Matcher matcher = issuePattern.matcher(response);
        while (matcher.find()) {
            try {
                CodeIssue issue = CodeIssue.builder()
                        .id(java.util.UUID.randomUUID().toString().substring(0, 8))
                        .severity(parseSeverity(matcher.group(1).trim()))
                        .type(parseIssueType(matcher.group(2).trim()))
                        .category(matcher.group(3).trim())
                        .lineNumber(Integer.parseInt(matcher.group(4).trim()))
                        .ruleId(matcher.group(5).trim())
                        .ruleName(matcher.group(6).trim())
                        .message(matcher.group(7).trim())
                        .description(matcher.group(8).trim())
                        .suggestion(matcher.group(9).trim())
                        .filePath(filePath)
                        .source("openai-review")
                        .build();

                issues.add(issue);
            } catch (Exception e) {
                log.warn("Failed to parse AI issue: {}", e.getMessage());
            }
        }

        // If no structured issues were found, try to extract any mentioned issues
        if (issues.isEmpty() && response.contains("issue") || response.contains("problem")) {
            log.debug("No structured issues found in AI response for {}", filePath);
        }

        return issues;
    }

    private Severity parseSeverity(String severity) {
        try {
            return Severity.valueOf(severity.toUpperCase());
        } catch (Exception e) {
            return Severity.MINOR;
        }
    }

    private IssueType parseIssueType(String type) {
        try {
            return IssueType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            return IssueType.CODE_SMELL;
        }
    }

    private String truncateContent(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }

        // Try to truncate at a line boundary
        int cutoff = content.lastIndexOf('\n', maxChars);
        if (cutoff < maxChars / 2) {
            cutoff = maxChars;
        }

        return content.substring(0, cutoff) + "\n// ... [Content truncated for AI review]";
    }
}
