package com.codereview.analyzer.sonar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.codereview.analyzer.AbstractJavaAnalyzer;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import lombok.extern.slf4j.Slf4j;

/**
 * Analyzer for security vulnerabilities based on SonarQube and OWASP rules.
 */
@Slf4j
@Component
public class SecurityAnalyzer extends AbstractJavaAnalyzer {

    private static final Set<String> SQL_METHODS = Set.of(
            "executeQuery", "executeUpdate", "execute", "prepareStatement",
            "createStatement", "nativeQuery", "createNativeQuery"
    );

    private static final Set<String> COMMAND_EXEC_METHODS = Set.of(
            "exec", "start", "command"
    );

    private static final Set<String> DANGEROUS_CRYPTO = Set.of(
            "MD5", "SHA1", "DES", "RC2", "RC4"
    );

    private static final Pattern HARDCODED_PASSWORD_PATTERN = Pattern.compile(
            "(password|passwd|pwd|secret|key|token|apikey|api_key)\\s*=\\s*[\"'][^\"']+[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HARDCODED_IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );

    @Override
    public String getAnalyzerId() {
        return "sonar-security";
    }

    @Override
    public String getAnalyzerName() {
        return "SonarQube Security Analyzer";
    }

    @Override
    protected List<CodeIssue> analyzeCompilationUnit(Path filePath, CompilationUnit cu, String originalContent) {
        List<CodeIssue> issues = new ArrayList<>();

        // Check for SQL injection vulnerabilities (S3649)
        issues.addAll(checkSqlInjection(filePath, cu, originalContent));

        // Check for command injection (S2076)
        issues.addAll(checkCommandInjection(filePath, cu, originalContent));

        // Check for weak cryptography (S4426)
        issues.addAll(checkWeakCryptography(filePath, cu, originalContent));

        // Check for hardcoded credentials (S2068)
        issues.addAll(checkHardcodedCredentials(filePath, cu, originalContent));

        // Check for hardcoded IPs (S1313)
        issues.addAll(checkHardcodedIPs(filePath, cu, originalContent));

        // Check for insecure random (S2245)
        issues.addAll(checkInsecureRandom(filePath, cu, originalContent));

        // Check for path traversal (S2083)
        issues.addAll(checkPathTraversal(filePath, cu, originalContent));

        // Check for XXE vulnerabilities (S2755)
        issues.addAll(checkXXE(filePath, cu, originalContent));

        // Check for Log injection (S5145)
        issues.addAll(checkLogInjection(filePath, cu, originalContent));

        return issues;
    }

    private List<CodeIssue> checkSqlInjection(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();

            if (SQL_METHODS.contains(methodName)) {
                methodCall.getArguments().forEach(arg -> {
                    if (arg.isBinaryExpr() || containsStringConcatenation(arg.toString())) {
                        int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                        issues.add(CodeIssue.builder()
                                .id(generateIssueId())
                                .ruleId("java:S3649")
                                .ruleName("SQL queries should not be constructed from user input")
                                .severity(Severity.BLOCKER)
                                .type(IssueType.VULNERABILITY)
                                .category("SQL Injection")
                                .filePath(filePath.toString())
                                .lineNumber(lineNumber)
                                .message("Ensure that string concatenation is not used to construct SQL queries. Use parameterized queries instead.")
                                .description("SQL injection vulnerabilities occur when user input is directly " +
                                        "concatenated into SQL queries. This can allow attackers to execute arbitrary SQL commands.")
                                .suggestion("Use PreparedStatement with parameterized queries or an ORM framework.")
                                .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-3649")
                                .effortMinutes(30.0)
                                .source(getAnalyzerId())
                                .build());
                    }
                });
            }
        });

        return issues;
    }

    private List<CodeIssue> checkCommandInjection(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            String scope = methodCall.getScope().map(Object::toString).orElse("");

            if ((scope.contains("Runtime") && methodName.equals("exec")) ||
                    (scope.contains("ProcessBuilder") && COMMAND_EXEC_METHODS.contains(methodName))) {

                methodCall.getArguments().forEach(arg -> {
                    if (!arg.isStringLiteralExpr()) {
                        int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                        issues.add(CodeIssue.builder()
                                .id(generateIssueId())
                                .ruleId("java:S2076")
                                .ruleName("OS commands should not be constructed from user input")
                                .severity(Severity.BLOCKER)
                                .type(IssueType.VULNERABILITY)
                                .category("Command Injection")
                                .filePath(filePath.toString())
                                .lineNumber(lineNumber)
                                .message("Ensure that OS command arguments are properly sanitized.")
                                .description("OS command injection occurs when user input is passed directly " +
                                        "to OS command execution without proper sanitization.")
                                .suggestion("Validate and sanitize all input. Use allowlists for command arguments where possible.")
                                .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2076")
                                .effortMinutes(30.0)
                                .source(getAnalyzerId())
                                .build());
                    }
                });
            }
        });

        return issues;
    }

    private List<CodeIssue> checkWeakCryptography(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (methodCall.getNameAsString().equals("getInstance")) {
                methodCall.getArguments().stream()
                        .filter(arg -> arg.isStringLiteralExpr())
                        .forEach(arg -> {
                            String algorithm = arg.asStringLiteralExpr().getValue();
                            if (DANGEROUS_CRYPTO.stream().anyMatch(dc -> algorithm.toUpperCase().contains(dc))) {
                                int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                                issues.add(CodeIssue.builder()
                                        .id(generateIssueId())
                                        .ruleId("java:S4426")
                                        .ruleName("Cryptographic keys should be robust")
                                        .severity(Severity.CRITICAL)
                                        .type(IssueType.VULNERABILITY)
                                        .category("Weak Cryptography")
                                        .filePath(filePath.toString())
                                        .lineNumber(lineNumber)
                                        .message(String.format("Use a stronger cryptographic algorithm than '%s'.", algorithm))
                                        .description("Weak cryptographic algorithms like MD5, SHA1, DES are vulnerable " +
                                                "to various attacks and should not be used for security purposes.")
                                        .suggestion("Use SHA-256 or stronger for hashing, AES-256 for encryption.")
                                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-4426")
                                        .effortMinutes(15.0)
                                        .source(getAnalyzerId())
                                        .build());
                            }
                        });
            }
        });

        return issues;
    }

    private List<CodeIssue> checkHardcodedCredentials(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = HARDCODED_PASSWORD_PATTERN.matcher(lines[i]);
            if (matcher.find()) {
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S2068")
                        .ruleName("Credentials should not be hard-coded")
                        .severity(Severity.BLOCKER)
                        .type(IssueType.VULNERABILITY)
                        .category("Hardcoded Credentials")
                        .filePath(filePath.toString())
                        .lineNumber(i + 1)
                        .message("Remove this hard-coded credential.")
                        .description("Hard-coded credentials are a security vulnerability. " +
                                "If the code is shared or decompiled, credentials can be exposed.")
                        .suggestion("Use environment variables, secrets management, or secure configuration.")
                        .codeSnippet(extractCodeSnippet(content, i + 1, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2068")
                        .effortMinutes(30.0)
                        .source(getAnalyzerId())
                        .build());
            }
        }

        return issues;
    }

    private List<CodeIssue> checkHardcodedIPs(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(StringLiteralExpr.class).forEach(literal -> {
            String value = literal.getValue();
            Matcher matcher = HARDCODED_IP_PATTERN.matcher(value);
            if (matcher.find() && !value.equals("127.0.0.1") && !value.equals("0.0.0.0")) {
                int lineNumber = literal.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S1313")
                        .ruleName("IP addresses should not be hardcoded")
                        .severity(Severity.MAJOR)
                        .type(IssueType.SECURITY_HOTSPOT)
                        .category("Hardcoded IP")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message("Make this IP address configurable.")
                        .description("Hardcoded IP addresses make the application inflexible " +
                                "and can expose internal network topology.")
                        .suggestion("Use configuration properties or environment variables.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1313")
                        .effortMinutes(10.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkInsecureRandom(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String typeName = creation.getTypeAsString();
            if (typeName.equals("Random") || typeName.equals("java.util.Random")) {
                int lineNumber = creation.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S2245")
                        .ruleName("Pseudorandom number generators should not be used for security purposes")
                        .severity(Severity.CRITICAL)
                        .type(IssueType.VULNERABILITY)
                        .category("Insecure Randomness")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message("Use 'java.security.SecureRandom' instead of 'java.util.Random' for security-sensitive operations.")
                        .description("java.util.Random is not cryptographically secure and should not be used " +
                                "for generating security tokens, passwords, or encryption keys.")
                        .suggestion("Use java.security.SecureRandom for security-sensitive random number generation.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2245")
                        .effortMinutes(5.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkPathTraversal(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String typeName = creation.getTypeAsString();
            if (typeName.equals("File") || typeName.equals("FileInputStream") ||
                    typeName.equals("FileOutputStream") || typeName.equals("FileReader") ||
                    typeName.equals("FileWriter")) {

                creation.getArguments().forEach(arg -> {
                    if (!arg.isStringLiteralExpr() && containsUserInput(arg.toString())) {
                        int lineNumber = creation.getBegin().map(b -> b.line).orElse(1);
                        issues.add(CodeIssue.builder()
                                .id(generateIssueId())
                                .ruleId("java:S2083")
                                .ruleName("I/O function calls should not be vulnerable to path injection attacks")
                                .severity(Severity.BLOCKER)
                                .type(IssueType.VULNERABILITY)
                                .category("Path Traversal")
                                .filePath(filePath.toString())
                                .lineNumber(lineNumber)
                                .message("Ensure file paths are validated before use.")
                                .description("Path traversal vulnerabilities allow attackers to access " +
                                        "files outside the intended directory using '../' sequences.")
                                .suggestion("Validate and canonicalize paths. Use allowlists for permitted directories.")
                                .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2083")
                                .effortMinutes(30.0)
                                .source(getAnalyzerId())
                                .build());
                    }
                });
            }
        });

        return issues;
    }

    private List<CodeIssue> checkXXE(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            String scope = methodCall.getScope().map(Object::toString).orElse("");

            if ((methodName.equals("newDocumentBuilder") || methodName.equals("newSAXParser") ||
                    methodName.equals("newInstance")) &&
                    (scope.contains("DocumentBuilderFactory") || scope.contains("SAXParserFactory") ||
                            scope.contains("XMLInputFactory"))) {

                // Check if there's a setFeature call to disable external entities
                boolean hasSecureConfig = cu.findAll(MethodCallExpr.class).stream()
                        .anyMatch(mc -> mc.getNameAsString().equals("setFeature") &&
                                mc.getArguments().toString().contains("external"));

                if (!hasSecureConfig) {
                    int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S2755")
                            .ruleName("XML parsers should not be vulnerable to XXE attacks")
                            .severity(Severity.BLOCKER)
                            .type(IssueType.VULNERABILITY)
                            .category("XXE")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("Disable access to external entities in XML parsing.")
                            .description("XML External Entity (XXE) attacks can read local files, " +
                                    "perform server-side request forgery, and cause denial of service.")
                            .suggestion("Disable DTDs and external entities: setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2755")
                            .effortMinutes(15.0)
                            .source(getAnalyzerId())
                            .build());
                }
            }
        });

        return issues;
    }

    private List<CodeIssue> checkLogInjection(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();
        Set<String> logMethods = Set.of("info", "debug", "warn", "error", "trace", "log");

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            String scope = methodCall.getScope().map(Object::toString).orElse("");

            if (logMethods.contains(methodName) &&
                    (scope.contains("log") || scope.contains("LOG") || scope.contains("logger") || scope.contains("LOGGER"))) {

                methodCall.getArguments().forEach(arg -> {
                    if (arg.isBinaryExpr() && containsUserInput(arg.toString())) {
                        int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                        issues.add(CodeIssue.builder()
                                .id(generateIssueId())
                                .ruleId("java:S5145")
                                .ruleName("Logging should not be vulnerable to injection attacks")
                                .severity(Severity.MAJOR)
                                .type(IssueType.VULNERABILITY)
                                .category("Log Injection")
                                .filePath(filePath.toString())
                                .lineNumber(lineNumber)
                                .message("Ensure user input is sanitized before logging.")
                                .description("Log injection can allow attackers to forge log entries, " +
                                        "corrupt logs, or inject malicious content into log viewers.")
                                .suggestion("Use parameterized logging with {} placeholders instead of string concatenation.")
                                .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-5145")
                                .effortMinutes(10.0)
                                .source(getAnalyzerId())
                                .build());
                    }
                });
            }
        });

        return issues;
    }

    private boolean containsStringConcatenation(String expression) {
        return expression.contains("+") && (expression.contains("\"") || expression.contains("'"));
    }

    private boolean containsUserInput(String expression) {
        String lower = expression.toLowerCase();
        return lower.contains("request") || lower.contains("param") ||
                lower.contains("input") || lower.contains("user") ||
                lower.contains("query") || lower.contains("header") ||
                lower.contains("cookie") || lower.contains("body");
    }
}
