package com.codereview.analyzer.sonar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.codereview.analyzer.AbstractJavaAnalyzer;
import com.codereview.model.CodeIssue;
import com.codereview.model.CodeIssue.IssueType;
import com.codereview.model.CodeIssue.Severity;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;

import lombok.extern.slf4j.Slf4j;

/**
 * Analyzer for potential bugs based on SonarQube rules.
 */
@Slf4j
@Component
public class BugAnalyzer extends AbstractJavaAnalyzer {

    private static final Set<String> DEPRECATED_METHODS = Set.of(
            "stop", "suspend", "resume", "destroy" // Thread methods
    );

    @Override
    public String getAnalyzerId() {
        return "sonar-bug";
    }

    @Override
    public String getAnalyzerName() {
        return "SonarQube Bug Analyzer";
    }

    @Override
    protected List<CodeIssue> analyzeCompilationUnit(Path filePath, CompilationUnit cu, String originalContent) {
        List<CodeIssue> issues = new ArrayList<>();

        // Check for null pointer dereference risks (S2259)
        issues.addAll(checkNullPointerDereference(filePath, cu, originalContent));

        // Check for String comparison with == (S4973)
        issues.addAll(checkStringComparison(filePath, cu, originalContent));

        // Check for equals() on incompatible types (S2159)
        issues.addAll(checkEqualsOnIncompatibleTypes(filePath, cu, originalContent));

        // Check for return in finally block (S1143)
        issues.addAll(checkReturnInFinally(filePath, cu, originalContent));

        // Check for double-checked locking (S2168)
        issues.addAll(checkDoubleCheckedLocking(filePath, cu, originalContent));

        // Check for deprecated thread methods (S1147)
        issues.addAll(checkDeprecatedThreadMethods(filePath, cu, originalContent));

        // Check for hashCode without equals (S1206)
        issues.addAll(checkHashCodeWithoutEquals(filePath, cu, originalContent));

        // Check for synchronization on non-final fields (S1860)
        issues.addAll(checkSynchronizationOnNonFinal(filePath, cu, originalContent));

        // Check for infinite loops (S2189)
        issues.addAll(checkInfiniteLoops(filePath, cu, originalContent));

        return issues;
    }

    private List<CodeIssue> checkNullPointerDereference(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            // Check if calling method on a potentially null value
            methodCall.getScope().ifPresent(scope -> {
                if (scope.toString().endsWith("orElse(null)") ||
                        scope.toString().contains("get()")) {
                    int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S2259")
                            .ruleName("Null pointers should not be dereferenced")
                            .severity(Severity.BLOCKER)
                            .type(IssueType.BUG)
                            .category("Null Pointer")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("A NullPointerException could be thrown; the return value of this method may be null.")
                            .description("Dereferencing a potentially null pointer leads to NullPointerExceptions at runtime.")
                            .suggestion("Add null checks or use Optional.map() instead of get().")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2259")
                            .effortMinutes(10.0)
                            .source(getAnalyzerId())
                            .build());
                }
            });
        });

        return issues;
    }

    private List<CodeIssue> checkStringComparison(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(BinaryExpr.class).forEach(binaryExpr -> {
            if (binaryExpr.getOperator() == BinaryExpr.Operator.EQUALS ||
                    binaryExpr.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {

                String left = binaryExpr.getLeft().toString();
                String right = binaryExpr.getRight().toString();

                // Check if either side is a string literal or likely string variable
                boolean leftIsString = left.startsWith("\"") || left.contains("String") ||
                        left.toLowerCase().contains("name") || left.toLowerCase().contains("text");
                boolean rightIsString = right.startsWith("\"") || right.contains("String") ||
                        right.toLowerCase().contains("name") || right.toLowerCase().contains("text");

                if ((leftIsString || rightIsString) &&
                        !binaryExpr.getLeft().isNullLiteralExpr() &&
                        !binaryExpr.getRight().isNullLiteralExpr()) {

                    int lineNumber = binaryExpr.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S4973")
                            .ruleName("Strings should not be compared using == or !=")
                            .severity(Severity.MAJOR)
                            .type(IssueType.BUG)
                            .category("String Comparison")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("Use equals() to compare strings instead of == or !=.")
                            .description("Using == compares object references, not string content. " +
                                    "This often leads to unexpected behavior.")
                            .suggestion("Use .equals() or Objects.equals() for string comparison.")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-4973")
                            .effortMinutes(5.0)
                            .source(getAnalyzerId())
                            .build());
                }
            }
        });

        return issues;
    }

    private List<CodeIssue> checkEqualsOnIncompatibleTypes(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (methodCall.getNameAsString().equals("equals") && methodCall.getArguments().size() == 1) {
                String arg = methodCall.getArgument(0).toString();
                String scope = methodCall.getScope().map(Object::toString).orElse("");

                // Detect obvious type mismatches
                if ((scope.contains("Integer") && arg.contains("Long")) ||
                        (scope.contains("Long") && arg.contains("Integer")) ||
                        (scope.contains("String") && (arg.matches("\\d+") || arg.contains("Integer")))) {

                    int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S2159")
                            .ruleName("Equals should not be used to compare objects of incompatible types")
                            .severity(Severity.MAJOR)
                            .type(IssueType.BUG)
                            .category("Type Mismatch")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("Remove this call to 'equals'; comparisons between unrelated types always return false.")
                            .description("Comparing objects of incompatible types will always return false.")
                            .suggestion("Ensure both objects are of compatible types before comparison.")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2159")
                            .effortMinutes(5.0)
                            .source(getAnalyzerId())
                            .build());
                }
            }
        });

        return issues;
    }

    private List<CodeIssue> checkReturnInFinally(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(com.github.javaparser.ast.stmt.TryStmt.class).forEach(tryStmt -> {
            tryStmt.getFinallyBlock().ifPresent(finallyBlock -> {
                List<ReturnStmt> returns = finallyBlock.findAll(ReturnStmt.class);
                if (!returns.isEmpty()) {
                    int lineNumber = returns.get(0).getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S1143")
                            .ruleName("Return statements should not occur in finally blocks")
                            .severity(Severity.BLOCKER)
                            .type(IssueType.BUG)
                            .category("Control Flow")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("Remove this return statement from this finally block.")
                            .description("A return in a finally block will suppress any exception thrown in try or catch blocks.")
                            .suggestion("Move the return statement outside the finally block.")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1143")
                            .effortMinutes(30.0)
                            .source(getAnalyzerId())
                            .build());
                }
            });
        });

        return issues;
    }

    private List<CodeIssue> checkDoubleCheckedLocking(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(SynchronizedStmt.class).forEach(syncStmt -> {
            // Check for if statement containing synchronized with nested if
            syncStmt.findAncestor(com.github.javaparser.ast.stmt.IfStmt.class).ifPresent(outerIf -> {
                List<com.github.javaparser.ast.stmt.IfStmt> innerIfs = syncStmt.getBody().findAll(com.github.javaparser.ast.stmt.IfStmt.class);
                if (!innerIfs.isEmpty()) {
                    // Check if the conditions are similar (double-checked locking pattern)
                    String outerCondition = outerIf.getCondition().toString();
                    for (com.github.javaparser.ast.stmt.IfStmt innerIf : innerIfs) {
                        if (innerIf.getCondition().toString().contains(
                                outerCondition.replace("==", "").replace("null", "").trim())) {
                            int lineNumber = syncStmt.getBegin().map(b -> b.line).orElse(1);
                            issues.add(CodeIssue.builder()
                                    .id(generateIssueId())
                                    .ruleId("java:S2168")
                                    .ruleName("Double-checked locking should not be used")
                                    .severity(Severity.BLOCKER)
                                    .type(IssueType.BUG)
                                    .category("Concurrency")
                                    .filePath(filePath.toString())
                                    .lineNumber(lineNumber)
                                    .message("Remove this double-checked locking pattern.")
                                    .description("Double-checked locking is broken in Java without volatile. " +
                                            "It can lead to partially constructed objects being visible to other threads.")
                                    .suggestion("Use volatile for the instance field or use a static holder class pattern.")
                                    .codeSnippet(extractCodeSnippet(content, lineNumber, 3))
                                    .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2168")
                                    .effortMinutes(30.0)
                                    .source(getAnalyzerId())
                                    .build());
                            break;
                        }
                    }
                }
            });
        });

        return issues;
    }

    private List<CodeIssue> checkDeprecatedThreadMethods(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            String scope = methodCall.getScope().map(Object::toString).orElse("");

            if (DEPRECATED_METHODS.contains(methodName) &&
                    (scope.toLowerCase().contains("thread") || scope.isEmpty())) {
                int lineNumber = methodCall.getBegin().map(b -> b.line).orElse(1);
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S1147")
                        .ruleName("Deprecated Thread methods should not be used")
                        .severity(Severity.BLOCKER)
                        .type(IssueType.BUG)
                        .category("Deprecated API")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("Don't use the deprecated '%s()' method.", methodName))
                        .description("Thread.stop(), suspend(), resume() and destroy() are deprecated " +
                                "because they are inherently unsafe and can lead to deadlocks and data corruption.")
                        .suggestion("Use interrupt() and proper synchronization instead.")
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 1))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1147")
                        .effortMinutes(60.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkHashCodeWithoutEquals(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            List<MethodDeclaration> methods = classDecl.getMethods();

            boolean hasHashCode = methods.stream()
                    .anyMatch(m -> m.getNameAsString().equals("hashCode") && m.getParameters().isEmpty());
            boolean hasEquals = methods.stream()
                    .anyMatch(m -> m.getNameAsString().equals("equals") && m.getParameters().size() == 1);

            if (hasHashCode != hasEquals) {
                int lineNumber = classDecl.getBegin().map(b -> b.line).orElse(1);
                String missing = hasHashCode ? "equals()" : "hashCode()";
                issues.add(CodeIssue.builder()
                        .id(generateIssueId())
                        .ruleId("java:S1206")
                        .ruleName("hashCode and equals should be overridden together")
                        .severity(Severity.BLOCKER)
                        .type(IssueType.BUG)
                        .category("Object Contract")
                        .filePath(filePath.toString())
                        .lineNumber(lineNumber)
                        .message(String.format("This class overrides %s but not %s.",
                                hasHashCode ? "hashCode()" : "equals()", missing))
                        .description("If hashCode is overridden, equals must also be overridden and vice versa. " +
                                "Violating this contract breaks collections like HashSet and HashMap.")
                        .suggestion(String.format("Override %s to be consistent with %s.",
                                missing, hasHashCode ? "hashCode()" : "equals()"))
                        .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                        .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1206")
                        .effortMinutes(30.0)
                        .source(getAnalyzerId())
                        .build());
            }
        });

        return issues;
    }

    private List<CodeIssue> checkSynchronizationOnNonFinal(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        cu.findAll(SynchronizedStmt.class).forEach(syncStmt -> {
            String expr = syncStmt.getExpression().toString();

            // Check if synchronizing on a field (not 'this' or 'class')
            if (!expr.equals("this") && !expr.endsWith(".class")) {
                // Look for the field declaration
                cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).forEach(field -> {
                    field.getVariables().forEach(var -> {
                        if (var.getNameAsString().equals(expr) && !field.isFinal()) {
                            int lineNumber = syncStmt.getBegin().map(b -> b.line).orElse(1);
                            issues.add(CodeIssue.builder()
                                    .id(generateIssueId())
                                    .ruleId("java:S1860")
                                    .ruleName("Synchronization should not be based on non-final fields")
                                    .severity(Severity.BLOCKER)
                                    .type(IssueType.BUG)
                                    .category("Concurrency")
                                    .filePath(filePath.toString())
                                    .lineNumber(lineNumber)
                                    .message(String.format("Make '%s' final or synchronize on 'this'.", expr))
                                    .description("Synchronizing on a non-final field is dangerous because " +
                                            "the field can be reassigned while another thread holds the lock.")
                                    .suggestion("Make the lock object final or use a dedicated private final lock.")
                                    .codeSnippet(extractCodeSnippet(content, lineNumber, 2))
                                    .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-1860")
                                    .effortMinutes(15.0)
                                    .source(getAnalyzerId())
                                    .build());
                        }
                    });
                });
            }
        });

        return issues;
    }

    private List<CodeIssue> checkInfiniteLoops(Path filePath, CompilationUnit cu, String content) {
        List<CodeIssue> issues = new ArrayList<>();

        // Check for while(true) without break
        cu.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).forEach(whileStmt -> {
            if (whileStmt.getCondition().toString().equals("true")) {
                boolean hasBreak = !whileStmt.getBody().findAll(com.github.javaparser.ast.stmt.BreakStmt.class).isEmpty();
                boolean hasReturn = !whileStmt.getBody().findAll(ReturnStmt.class).isEmpty();
                boolean hasThrow = !whileStmt.getBody().findAll(com.github.javaparser.ast.stmt.ThrowStmt.class).isEmpty();

                if (!hasBreak && !hasReturn && !hasThrow) {
                    int lineNumber = whileStmt.getBegin().map(b -> b.line).orElse(1);
                    issues.add(CodeIssue.builder()
                            .id(generateIssueId())
                            .ruleId("java:S2189")
                            .ruleName("Loops should not be infinite")
                            .severity(Severity.BLOCKER)
                            .type(IssueType.BUG)
                            .category("Control Flow")
                            .filePath(filePath.toString())
                            .lineNumber(lineNumber)
                            .message("Add an exit condition to this loop.")
                            .description("An infinite loop without a break, return, or throw will cause " +
                                    "the program to hang indefinitely.")
                            .suggestion("Add a break condition, return statement, or timeout mechanism.")
                            .codeSnippet(extractCodeSnippet(content, lineNumber, 3))
                            .sonarQubeReference("https://rules.sonarsource.com/java/RSPEC-2189")
                            .effortMinutes(30.0)
                            .source(getAnalyzerId())
                            .build());
                }
            }
        });

        return issues;
    }
}
