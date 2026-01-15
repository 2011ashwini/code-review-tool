package com.codereview.analyzer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codereview.analyzer.sonar.CodeSmellAnalyzer;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;

class CodeSmellAnalyzerTest {

    private CodeSmellAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new CodeSmellAnalyzer();
    }

    @Test
    @DisplayName("Should detect empty catch block")
    void shouldDetectEmptyCatchBlock() {
        String code = """
                public class TestClass {
                    public void testMethod() {
                        try {
                            doSomething();
                        } catch (Exception e) {
                        }
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("TestClass.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S108") &&
                        i.getType() == IssueType.CODE_SMELL));
    }

    @Test
    @DisplayName("Should detect method with too many parameters")
    void shouldDetectTooManyParameters() {
        String code = """
                public class TestClass {
                    public void testMethod(String a, String b, String c, String d,
                                          String e, String f, String g, String h) {
                        // Method with 8 parameters
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("TestClass.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S107") &&
                        i.getSeverity() == Severity.MAJOR));
    }

    @Test
    @DisplayName("Should detect TODO comments")
    void shouldDetectTodoComments() {
        String code = """
                public class TestClass {
                    // TODO: Fix this later
                    public void testMethod() {
                    }
                }
                """;

        List<CodeIssue> issues = analyzer.analyze(Path.of("TestClass.java"), code);

        assertTrue(issues.stream().anyMatch(i ->
                i.getRuleId().equals("java:S1135")));
    }

    @Test
    @DisplayName("Should support Java files only")
    void shouldSupportJavaFilesOnly() {
        assertTrue(analyzer.supports(Path.of("Test.java")));
        assertFalse(analyzer.supports(Path.of("test.xml")));
        assertFalse(analyzer.supports(Path.of("test.py")));
    }

    @Test
    @DisplayName("Should return analyzer info")
    void shouldReturnAnalyzerInfo() {
        assertEquals("sonar-code-smell", analyzer.getAnalyzerId());
        assertEquals("SonarQube Code Smell Analyzer", analyzer.getAnalyzerName());
    }
}
