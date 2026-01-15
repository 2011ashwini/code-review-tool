package com.codereview.analyzer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codereview.analyzer.sonar.SecurityAnalyzer;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;

class SecurityAnalyzerTest {

    private SecurityAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SecurityAnalyzer();
    }

    @Test
    @DisplayName("Should detect SQL injection vulnerability")
    void shouldDetectSqlInjection() {
        String code = """
                public class UserRepository {
                    public void findUser(String username) {
                        String query = "SELECT * FROM users WHERE name = '" + username + "'";
                        connection.executeQuery(query);
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("UserRepository.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S3649") &&
                        i.getType() == IssueType.VULNERABILITY &&
                        i.getSeverity() == Severity.BLOCKER));
    }

    @Test
    @DisplayName("Should detect weak cryptography")
    void shouldDetectWeakCryptography() {
        String code = """
                import java.security.MessageDigest;
                public class HashUtil {
                    public byte[] hash(String input) throws Exception {
                        MessageDigest md = MessageDigest.getInstance("MD5");
                        return md.digest(input.getBytes());
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("HashUtil.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S4426") &&
                        i.getCategory().equals("Weak Cryptography")));
    }

    @Test
    @DisplayName("Should detect hardcoded credentials")
    void shouldDetectHardcodedCredentials() {
        String code = """
                public class Config {
                    private String password = "secret123";
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("Config.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S2068") &&
                        i.getSeverity() == Severity.BLOCKER));
    }

    @Test
    @DisplayName("Should detect insecure random usage")
    void shouldDetectInsecureRandom() {
        String code = """
                import java.util.Random;
                public class TokenGenerator {
                    public String generateToken() {
                        Random random = new Random();
                        return String.valueOf(random.nextLong());
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("TokenGenerator.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S2245")));
    }

    @Test
    @DisplayName("Should return security analyzer info")
    void shouldReturnAnalyzerInfo() {
        assertEquals("sonar-security", analyzer.getAnalyzerId());
        assertEquals("SonarQube Security Analyzer", analyzer.getAnalyzerName());
    }
}
