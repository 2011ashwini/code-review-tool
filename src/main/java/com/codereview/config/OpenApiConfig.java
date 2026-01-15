package com.codereview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * OpenAPI/Swagger configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codeReviewOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Code Review Tool API")
                        .description("A comprehensive code review API that analyzes Java/Spring Boot codebases " +
                                "using SonarQube standards and AI-powered analysis. " +
                                "Detects code smells, security vulnerabilities, and vulnerable dependencies.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Code Review Tool")
                                .email("support@codereview.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
