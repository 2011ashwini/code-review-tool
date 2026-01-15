package com.codereview.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.codereview.model.CodeIssue;
import com.codereview.model.CodeReviewResult;
import com.codereview.model.ReviewRequest;
import com.codereview.service.CodeReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(CodeReviewController.class)
class CodeReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CodeReviewService codeReviewService;

    @Test
    @DisplayName("Should return review result for valid request")
    void shouldReturnReviewResultForValidRequest() throws Exception {
        ReviewRequest request = ReviewRequest.builder()
                .repositoryUrl("https://github.com/test/repo")
                .branch("main")
                .build();

        CodeReviewResult result = CodeReviewResult.builder()
                .repositoryUrl("https://github.com/test/repo")
                .branch("main")
                .commitSha("abc123")
                .reviewTimestamp(LocalDateTime.now())
                .status(CodeReviewResult.ReviewStatus.SUCCESS)
                .issues(List.of(
                        CodeIssue.builder()
                                .id("1")
                                .ruleId("java:S1135")
                                .message("TODO found")
                                .severity(CodeIssue.Severity.INFO)
                                .type(CodeIssue.IssueType.CODE_SMELL)
                                .build()
                ))
                .build();

        when(codeReviewService.isReviewEnabled(any())).thenReturn(true);
        when(codeReviewService.reviewRepository(any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/test/repo"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.issues").isArray());
    }

    @Test
    @DisplayName("Should return 503 when review is disabled")
    void shouldReturn503WhenReviewDisabled() throws Exception {
        ReviewRequest request = ReviewRequest.builder()
                .repositoryUrl("https://github.com/test/repo")
                .branch("main")
                .build();

        when(codeReviewService.isReviewEnabled(any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    @DisplayName("Should return status")
    void shouldReturnStatus() throws Exception {
        when(codeReviewService.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/v1/review/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("Should validate request body")
    void shouldValidateRequestBody() throws Exception {
        String invalidRequest = "{}"; // Missing required repositoryUrl

        mockMvc.perform(post("/api/v1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}
