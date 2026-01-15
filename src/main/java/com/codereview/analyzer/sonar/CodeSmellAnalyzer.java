package com.codereview.analyzer.sonar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.codereview.analyzer.AbstractJavaAnalyzer;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.IfStmt;

import lombok.extern.slf4j.Slf4j;

/**
 * Analyzer for code smells based on SonarQube rules.
 */
@Slf4j
@Component
public class CodeSmellAnalyzer extends AbstractJavaAnalyzer {

    private static final int MAX_METHOD_LINES = 30;
    private static final int MAX_METHOD_PARAMETERS = 7;
    private static final int MAX_CLASS_LINES = 500;
    private static final int MAX_CYCLOMATIC_COMPLEXITY = 10;

    @Override
    public String getAnalyzerId() {
        return "sonar-code-smell";
    }

    @Override
    public String getAnalyzerName() {
        return "SonarQube Code Smell Analyzer";
    }

    @Override
    protected List<CodeIssue> analyzeCompilationUnit(Path filePath, CompilationUnit cu, String originalContent) {
        List<CodeIssue> issues = new ArrayList<>();

        // Check for long methods (S138)
        issues.addAll(checkLongMethods(filePath, cu, originalContent));

        // Check for too many parameters (S107)
        issues.addAll(checkTooManyParameters(filePath, cu, originalContent));

        // Check for empty catch blocks (S108)
        issues.addAll(checkEmptyCatchBlocks(filePath, cu, originalContent));

        // Check for magic numbers (S109)
        issues.addAll(checkMagicNumbers(filePath, cu, originalContent));

        // Check for TODO comments (S1135)
        issues.addAll(checkTodoComments(filePath, cu, originalContent));

        // Check for too long classes (S2972)
        issues.addAll(checkLongClasses(filePath, cu, originalContent));

        // Check for cognitive complexity (S3776)
        issues.addAll(checkCognitiveComplexity(filePath, cu, originalContent));

        // Check for single-letter variable names (S117)
        issues.addAll(checkVariableNaming(filePath, cu, originalContent));

        return issues;
    }

    private List<CodeIssue> checkLongMethods(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int lines = method.getEnd().map(e -> e.line).orElse(0)
                    - method.getBegin().map(b -> b.line).orElse(0) + 1;

            if (lines > MAX_METHOD_LINES) {
                int lineNumber = method.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S138")
                        .ruleName("Methods should not have too many lines")
                        .severity(Severity.MAJOR)
                        .type(IssueType.CODE_SMELL)
                        .category("Maintainability")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Method '%s' has %d lines, which is greater than the %d authorized.",
                                method.getNameAsString(), lines, MAX_METHOD_LINES))
                        .description("A method that grows too large tends to aggregate too many responsibilities. " +
                                "Such methods inevitably become harder to understand and therefore harder to maintain.")
                        .suggestion("Split this method into smaller, more focused methods.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-138")
                        .effortMinutes(20.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkTooManyParameters(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int paramCount = method.getParameters().size();

            if (paramCount > MAX_METHOD_PARAMETERS) {
                int lineNumber = method.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S107")
                        .ruleName("Methods should not have too many parameters")
                        .severity(Severity.MAJOR)
                        .type(IssueType.CODE_SMELL)
                        .category("Maintainability")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Method '%s' has %d parameters, which is greater than the %d authorized.",
                                method.getNameAsString(), paramCount, MAX_METHOD_PARAMETERS))
                        .description("Methods with too many parameters are difficult to understand and maintain.")
                        .suggestion("Consider using a parameter object or builder pattern.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-107")
                        .effortMinutes(15.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkEmptyCatchBlocks(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(CatchClause.class).forEach(catchClause -> {
            if (catchClause.getBody().getStatements().isEmpty()) {
                int lineNumber = catchClause.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S108")
                        .ruleName("Nested blocks of code should not be empty")
                        .severity(Severity.MAJOR)
                        .type(IssueType.CODE_SMELL)
                        .category("Error Handling")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message("Either log or rethrow this exception.")
                        .description("An empty catch block silently swallows exceptions, " +
                                "hiding potential bugs and making debugging extremely difficult.")
                        .suggestion("At minimum, log the exception. Consider if recovery is possible.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-108")
                        .effortMinutes(5.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkMagicNumbers(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(LiteralExpr.class).forEach(literal -> {
            if (literal.isIntegerLiteralExpr() || literal.isDoubleLiteralExpr() || literal.isLongLiteralExpr()) {
                String value = literal.toString();

                // Skip common acceptable values
                if (value.equals("0") || value.equals("1") || value.equals("-1") ||
                        value.equals("0L") || value.equals("1L") || value.equals("0.0") || value.equals("1.0")) {
                    return;
                }

                // Check if it's inside a constant declaration
                boolean isConstant = literal.findAncestor(VariableDeclarator.class)
                        .map(vd -> vd.findAncestor(com.github.javaparser.ast.body.FieldDeclaration.class)
                                .map(fd -> fd.isStatic() && fd.isFinal())
                                .orElse(false))
                        .orElse(false);

                if (!isConstant) {
                    int lineNumber = literal.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S109")
                            .ruleName("Magic numbers should not be used")
                            .severity(Severity.MINOR)
                            .type(IssueType.CODE_SMELL)
                            .category("Maintainability")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message(String.format("Assign this magic number %s to a well-named constant.", value))
                            .description("Magic numbers make code harder to read and maintain. " +
                                    "Named constants are self-documenting and can be reused.")
                            .suggestion("Define a constant with a meaningful name for this value.")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-109")
                            .effortMinutes(2.0)
                            .source(getAnalyzerId())
                            .build());
                }
            }
        });

        return issues;
    }

    private List<CodeIssue> checkTodoComments(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.getAllComments().forEach(comment -> {
            String commentContent = comment.getContent().toUpperCase();
            if (commentContent.contains("TODO") || commentContent.contains("FIXME") || commentContent.contains("HACK")) {
                int lineNumber = comment.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S1135")
                        .ruleName("Track uses of TODO tags")
                        .severity(Severity.INFO)
                        .type(IssueType.CODE_SMELL)
                        .category("Maintainability")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message("Complete the task associated with this TODO comment.")
                        .description("TODO comments are useful for tracking work but should be addressed.")
                        .suggestion("Complete the TODO or create a ticket to track it.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1135")
                        .effortMinutes(0.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkLongClasses(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            int lines = classDecl.getEnd().map(e -> e.line).orElse(0)
                    - classDecl.getBegin().map(b -> b.line).orElse(0) + 1;

            if (lines > MAX_CLASS_LINES) {
                int lineNumber = classDecl.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S2972")
                        .ruleName("Classes should not have too many lines")
                        .severity(Severity.MAJOR)
                        .type(IssueType.CODE_SMELL)
                        .category("Maintainability")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Class '%s' has %d lines, which is greater than the %d authorized.",
                                classDecl.getNameAsString(), lines, MAX_CLASS_LINES))
                        .description("Classes that grow too large tend to aggregate too many responsibilities. " +
                                "Consider splitting into smaller, focused classes.")
                        .suggestion("Apply Single Responsibility Principle and extract related functionality into separate classes.")
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2972")
                        .effortMinutes(60.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkCognitiveComplexity(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            int complexity = calculateCyclomaticComplexity(method);

            if (complexity > MAX_CYCLOMATIC_COMPLEXITY) {
                int lineNumber = method.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S3776")
                        .ruleName("Cognitive Complexity of methods should not be too high")
                        .severity(Severity.CRITICAL)
                        .type(IssueType.CODE_SMELL)
                        .category("Complexity")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Refactor this method to reduce its Cognitive Complexity from %d to the %d allowed.",
                                complexity, MAX_CYCLOMATIC_COMPLEXITY))
                        .description("High cognitive complexity makes methods difficult to understand and maintain.")
                        .suggestion("Break down the method, extract helper methods, and simplify conditional logic.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-3776")
                        .effortMinutes((double) (complexity - MAX_CYCLOMATIC_COMPLEXITY) * 5)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkVariableNaming(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(VariableDeclarator.class).forEach(variable -> {
            String name = variable.getNameAsString();

            // Check for single letter names (except loop variables i, j, k)
            if (name.length() == 1 && !name.matches("[ijk]")) {
                int lineNumber = variable.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S117")
                        .ruleName("Local variable and method parameter names should comply with naming conventions")
                        .severity(Severity.MINOR)
                        .type(IssueType.CODE_SMELL)
                        .category("Naming")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Rename this local variable '%s' to match the regular expression pattern.", name))
                        .description("Single letter variable names (except for loop counters) reduce code readability.")
                        .suggestion("Use a descriptive name that explains the variable's purpose.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-117")
                        .effortMinutes(2.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private int calculateCyclomaticComplexity(MethodDeclaration method) {
        int complexity = 1; // Base complexity

        // Count if statements
        complexity += method.findAll(IfStmt.class).size();

        // Count binary expressions with && or ||
        complexity += method.findAll(BinaryExpr.class).stream()
                .filter(be -> be.getOperator() == BinaryExpr.Operator.AND ||
                        be.getOperator() == BinaryExpr.Operator.OR)
                .count();

        // Count switch cases
        complexity += method.findAll(com.github.javaparser.ast.stmt.SwitchEntry.class).size();

        // Count loops
        complexity += method.findAll(com.github.javaparser.ast.stmt.ForStmt.class).size();
        complexity += method.findAll(com.github.javaparser.ast.stmt.ForEachStmt.class).size();
        complexity += method.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).size();
        complexity += method.findAll(com.github.javaparser.ast.stmt.DoStmt.class).size();

        // Count catch clauses
        complexity += method.findAll(CatchClause.class).size();

        return complexity;
    }
}
