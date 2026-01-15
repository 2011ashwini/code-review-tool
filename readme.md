# Code Review Tool

A comprehensive Java/Spring Boot code review tool that analyzes GitHub repositories using SonarQube standards and AI-powered analysis.

## Comprehensive Java/Spring Boot Code Review Tool Overview

---

### 1. What the Project Does

This is a **comprehensive automated code review tool** that analyzes Java/Spring Boot GitHub repositories using multiple analysis techniques:
- **SonarQube-style static code analysis** to detect bugs, code smells, and security vulnerabilities
- **Dependency vulnerability scanning** to identify known CVEs in project dependencies
- **AI-powered code review** using OpenAI's GPT-4o-mini model for intelligent analysis
- **REST API interface** for easy integration with CI/CD pipelines

The tool provides structured JSON output with detailed findings, severity levels, fix recommendations, and effort estimates.

---

### 2. Main Components and Architecture

The project follows a clean layered architecture:

```
src/main/java/com/codereview/
├── CodeReviewApplication.java          # Main Spring Boot entry point
├── controller/                         # REST API endpoints
│   ├── CodeReviewController.java      # Main review endpoints
│   ├── AdminController.java           # Enable/disable and config management
│   └── GlobalExceptionHandler.java    # Exception handling
├── service/                           # Business logic layer
│   ├── CodeReviewService.java         # Orchestrates all analyses
│   ├── GitHubService.java             # Repository cloning and Git operations
│   ├── VulnerabilityScanner.java      # Dependency vulnerability detection
│   └── OpenAIReviewService.java       # AI-powered review integration
├── analyzer/                          # Code analysis implementations
│   ├── CodeAnalyzer.java              # Interface for all analyzers
│   ├── AbstractJavaAnalyzer.java      # Base class using JavaParser
│   └── sonar/                         # SonarQube-style analyzers
│       ├── SecurityAnalyzer.java      # Security vulnerability detection
│       ├── CodeSmellAnalyzer.java     # Code quality issues
│       └── BugAnalyzer.java           # Potential bugs
├── model/                             # Data models
│   ├── ReviewRequest.java             # Input request model
│   ├── CodeReviewResult.java          # Output result structure
│   ├── CodeIssue.java                 # Individual issue details
│   └── VulnerableDependency.java      # Vulnerable library info
└── config/                            # Configuration
    ├── CodeReviewProperties.java      # Application properties
    └── OpenApiConfig.java             # Swagger/OpenAPI setup
```

---

### 3. Key Features and Functionality

#### A. SonarQube-Style Static Analysis

Three specialized analyzers detect different issue categories:

**SecurityAnalyzer** (9 security rules):
- S3649: SQL injection detection
- S2076: OS command injection
- S4426: Weak cryptography (MD5, DES, RC4)
- S2068: Hardcoded credentials
- S1313: Hardcoded IPs
- S2245: Insecure random number generation
- S2083: Path traversal vulnerabilities
- S2755: XXE vulnerabilities
- S5145: Log injection attacks

**CodeSmellAnalyzer** (8 code quality rules):
- S138: Methods with too many lines (>30)
- S107: Methods with too many parameters (>7)
- S108: Empty catch blocks
- S109: Magic numbers
- S117: Poor variable naming
- S1135: TODO/FIXME comments tracking
- S2972: Classes with too many lines (>500)
- S3776: High cognitive complexity (>10)

**BugAnalyzer** (9 potential bug detection rules):
- S2259: Null pointer dereference risks
- S4973: String comparison with == instead of .equals()
- S2159: Incompatible type comparisons
- S1143: Return statements in finally blocks
- S2168: Double-checked locking anti-pattern
- S1147: Deprecated Thread methods
- S1206: hashCode without equals override
- S1860: Synchronization on non-final fields
- S2189: Infinite loops without exit conditions

#### B. Dependency Vulnerability Scanning

Built-in database of known vulnerable libraries with CVSS scores:
- Log4j (CVE-2021-44228, CVE-2021-45046)
- Spring Framework (CVE-2022-22965)
- Jackson, Commons Collections, Apache Struts
- Hibernate, Fastjson, Tomcat, Netty, SnakeYAML

Scans both Maven (pom.xml) and Gradle (build.gradle) files.

#### C. AI-Powered Review

- Uses OpenAI's GPT-4o-mini for cost-effectiveness
- Analyzes up to 20 Java files per review (non-test files)
- Follows structured prompt format for consistent JSON parsing
- Can be disabled independently from other analyses

#### D. GitHub Integration

- Clones repositories via JGit
- Supports branch specification and specific commit SHA checkout
- Handles private repositories with GitHub token authentication
- Auto-cleanup of cloned repositories

#### E. Master Enable/Disable Control

- Global on/off switch via configuration
- Per-analyzer toggles (SonarQube, vulnerability scan, AI review)
- Request-level option override
- Admin endpoints to toggle settings at runtime

---

### 4. REST API Endpoints

**Review Operations:**
```
POST /api/v1/review                    # Synchronous code review
POST /api/v1/review/async              # Asynchronous code review
GET  /api/v1/review/status             # Service status
```

**Admin Operations:**
```
POST /api/v1/admin/enable              # Enable all reviews
POST /api/v1/admin/disable             # Disable all reviews
POST /api/v1/admin/toggle              # Toggle with custom settings
GET  /api/v1/admin/config              # View current configuration
POST /api/v1/admin/settings            # Update analyzer settings
```

**Documentation:**
```
GET  /swagger-ui.html                  # Swagger UI
GET  /api-docs                         # OpenAPI specification
```

---

### 5. Data Models

**ReviewRequest Input:**
- Repository URL (required)
- Branch (default: "main")
- Commit SHA (optional)
- Options to enable/disable specific analyses
- Include/exclude path filters
- Custom file extensions

**CodeReviewResult Output:**
- Repository metadata
- Analysis timestamp and duration
- ReviewStatus (SUCCESS, PARTIAL, FAILED, DISABLED)
- Summary statistics (file count, issue counts by severity/type/category)
- List of CodeIssue objects with detailed metadata
- List of VulnerableDependency objects
- Quality gate status (PASSED, WARNING, FAILED)

**CodeIssue Details:**
- Severity: BLOCKER, CRITICAL, MAJOR, MINOR, INFO
- Type: BUG, VULNERABILITY, CODE_SMELL, SECURITY_HOTSPOT, PERFORMANCE, etc.
- File path, line/column numbers
- Rule ID and reference to SonarQube docs
- Message, description, and fix suggestion
- Effort estimate in minutes
- Code snippet context

---

### 6. Configuration (application.yml)

```yaml
codereview:
  enabled: true                           # Master switch
  github:
    token: ${GITHUB_TOKEN}                # GitHub authentication
    api-url: https://api.github.com
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini                    # Cost-optimized model
    max-tokens: 4096
    temperature: 0.3
  analysis:
    sonarqubeRules:
      enabled: true
    vulnerabilityScan:
      enabled: true
    aiReview:
      enabled: true
    supportedExtensions: [.java, .xml, .properties, .yml, .yaml]
    exclusions: [target/, build/, .git/, .idea/, node_modules/]
```

---

### 7. Technical Stack

**Core:**
- Java 17
- Spring Boot 3.2.0
- Maven 3.8+

**Code Analysis:**
- JavaParser 3.25.7 (AST parsing for Java analysis)
- OWASP Dependency Check Core 8.4.3

**Git & GitHub:**
- JGit 6.7.0 (Git operations)
- GitHub API 1.318 (repository access)

**AI Integration:**
- OpenAI Java SDK 0.18.2

**Additional:**
- Lombok (boilerplate reduction)
- SpringDoc OpenAPI 2.3.0 (Swagger/OpenAPI docs)
- Jackson (JSON processing)
- Apache Commons

---

### 8. Quality Gate Logic

The service determines overall quality based on:
- **FAILED**: If any BLOCKER-severity issues or CRITICAL vulnerabilities found
- **WARNING**: If more than 5 CRITICAL-severity code issues
- **PASSED**: Otherwise

---

### 9. Workflow

1. **Request received** at `/api/v1/review` with repository details
2. **Repository cloned** to temporary directory via GitHub token
3. **File collection** filters for supported extensions and exclusions
4. **Three parallel analyses**:
   - Static analysis via SonarQube-style rules
   - Dependency scanning against vulnerability database
   - AI review for up to 20 important files
5. **Results aggregated** with statistics and quality gate status
6. **Cleanup** of cloned repository
7. **JSON response** returned with complete findings

---

### 10. Key Architectural Decisions

1. **Plugin Architecture**: Analyzer interface allows easy addition of new rule sets
2. **AST-Based Analysis**: JavaParser for precise syntax-aware analysis
3. **Cost-Optimized AI**: GPT-4o-mini instead of more expensive models
4. **Configurable Granularity**: Enable/disable at global, type, and request levels
5. **Async Support**: Optional asynchronous review processing
6. **Structured Output**: Consistent JSON format for CI/CD integration
7. **Separation of Concerns**: Clear layers for controllers, services, and analysis

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- GitHub Token (for private repositories)
- OpenAI API Key (optional, for AI review)

### Configuration

Set environment variables:

```bash
export GITHUB_TOKEN=your_github_token
export OPENAI_API_KEY=your_openai_api_key
```

### Running the Application

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

---

## Sample JSON Output

```json
{
  "repositoryUrl": "https://github.com/owner/repo",
  "branch": "main",
  "commitSha": "abc123def456",
  "reviewTimestamp": "2024-01-15T10:30:00",
  "status": "SUCCESS",
  "summary": {
    "totalFiles": 50,
    "filesAnalyzed": 50,
    "totalIssues": 15,
    "issuesBySeverity": {
      "BLOCKER": 2,
      "CRITICAL": 3,
      "MAJOR": 5,
      "MINOR": 5
    },
    "issuesByType": {
      "VULNERABILITY": 3,
      "BUG": 4,
      "CODE_SMELL": 8
    },
    "vulnerableDependenciesCount": 2,
    "qualityGateStatus": "FAILED"
  },
  "issues": [
    {
      "id": "abc12345",
      "ruleId": "java:S3649",
      "ruleName": "SQL queries should not be constructed from user input",
      "severity": "BLOCKER",
      "type": "VULNERABILITY",
      "category": "SQL Injection",
      "filePath": "src/main/java/UserRepository.java",
      "lineNumber": 42,
      "message": "Use parameterized queries instead of string concatenation",
      "suggestion": "Use PreparedStatement with parameterized queries",
      "sonarQubeReference": "https://rules.sonarsource.com/java/RSPEC-3649"
    }
  ],
  "vulnerableDependencies": [
    {
      "groupId": "org.apache.logging.log4j",
      "artifactId": "log4j-core",
      "version": "2.14.0",
      "vulnerabilities": [
        {
          "cveId": "CVE-2021-44228",
          "severity": "CRITICAL",
          "cvssScore": 10.0,
          "description": "Log4Shell - Remote code execution vulnerability",
          "recommendation": "Upgrade to version 2.17.1 or later"
        }
      ]
    }
  ]
}
```

---

## Building

```bash
mvn clean package
```

## Running Tests

```bash
mvn test
```

---

## License

MIT License
