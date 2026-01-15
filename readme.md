# Code Review Tool

A comprehensive Java/Spring Boot code review tool that analyzes GitHub repositories using SonarQube standards and AI-powered analysis.

## Features

- **SonarQube-Style Static Analysis**: Detects code smells, bugs, and security vulnerabilities following SonarQube rules
- **Vulnerability Scanning**: Identifies known vulnerable dependencies (CVEs) in your project
- **AI-Powered Review**: Uses OpenAI's GPT-4o-mini (least costly model) for intelligent code analysis
- **Enable/Disable Toggle**: Full control to enable or disable the review functionality
- **JSON Output**: All results are provided in structured JSON format
- **GitHub Integration**: Direct integration with GitHub repositories

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

Or configure in `application.yml`:

```yaml
codereview:
  enabled: true  # Master switch to enable/disable
  github:
    token: ${GITHUB_TOKEN}
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini  # Least costly OpenAI model
```

### Running the Application

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

## API Endpoints

### Review a Repository

```bash
POST /api/v1/review
Content-Type: application/json

{
  "repositoryUrl": "https://github.com/owner/repo",
  "branch": "main",
  "options": {
    "enabled": true,
    "sonarQubeRulesEnabled": true,
    "vulnerabilityScanEnabled": true,
    "aiReviewEnabled": true
  }
}
```

### Enable/Disable Review

```bash
# Enable
POST /api/v1/admin/enable

# Disable
POST /api/v1/admin/disable

# Toggle
POST /api/v1/admin/toggle
Content-Type: application/json
{"enabled": true}
```

### Check Status

```bash
GET /api/v1/review/status
GET /api/v1/admin/config
```

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

## SonarQube Rules Implemented

### Code Smells
- S138: Methods should not have too many lines
- S107: Methods should not have too many parameters
- S108: Nested blocks of code should not be empty
- S109: Magic numbers should not be used
- S117: Local variable naming conventions
- S1135: TODO comments tracking
- S2972: Classes should not have too many lines
- S3776: Cognitive Complexity

### Security Vulnerabilities
- S2068: Hardcoded credentials
- S2076: OS command injection
- S2083: Path traversal
- S2245: Insecure random
- S2755: XXE vulnerabilities
- S3649: SQL injection
- S4426: Weak cryptography
- S5145: Log injection

### Bugs
- S1143: Return in finally block
- S1147: Deprecated thread methods
- S1206: hashCode without equals
- S1860: Synchronization on non-final fields
- S2159: Incompatible types comparison
- S2168: Double-checked locking
- S2189: Infinite loops
- S2259: Null pointer dereference
- S4973: String comparison with ==

## Vulnerability Database

The tool includes detection for known vulnerable versions of:
- Log4j (CVE-2021-44228, CVE-2021-45046)
- Spring Framework (CVE-2022-22965)
- Jackson Databind
- Commons Collections
- Apache Struts
- Hibernate
- Fastjson
- Tomcat
- Netty
- SnakeYAML

## API Documentation

Swagger UI is available at: `http://localhost:8080/swagger-ui.html`

OpenAPI spec: `http://localhost:8080/api-docs`

## Building

```bash
mvn clean package
```

## Running Tests

```bash
mvn test
```

## Architecture

```
src/main/java/com/codereview/
├── CodeReviewApplication.java     # Main application
├── config/                        # Configuration classes
├── controller/                    # REST API controllers
├── model/                         # Data models (JSON output)
├── service/                       # Business logic
├── analyzer/                      # Code analyzers
│   └── sonar/                     # SonarQube-style rules
└── util/                          # Utility classes
```

## License

MIT License
