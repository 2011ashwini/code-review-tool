package com.codereview.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.codereview.config.CodeReviewProperties;
import com.codereview.service.CodeReviewService;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodeReviewService codeReviewService;

    @MockBean
    private CodeReviewProperties properties;

    @Test
    @DisplayName("Should enable review")
    void shouldEnableReview() throws Exception {
        mockMvc.perform(post("/api/v1/admin/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.message").value("Code review has been enabled"));

        verify(codeReviewService).setReviewEnabled(true);
    }

    @Test
    @DisplayName("Should disable review")
    void shouldDisableReview() throws Exception {
        mockMvc.perform(post("/api/v1/admin/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.message").value("Code review has been disabled"));

        verify(codeReviewService).setReviewEnabled(false);
    }

    @Test
    @DisplayName("Should toggle review")
    void shouldToggleReview() throws Exception {
        String toggleRequest = "{\"enabled\": true}";

        mockMvc.perform(post("/api/v1/admin/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toggleRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        verify(codeReviewService).setReviewEnabled(true);
    }

    @Test
    @DisplayName("Should return configuration")
    void shouldReturnConfiguration() throws Exception {
        CodeReviewProperties.AnalysisConfig analysisConfig = new CodeReviewProperties.AnalysisConfig();
        CodeReviewProperties.OpenAIConfig openAIConfig = new CodeReviewProperties.OpenAIConfig();
        openAIConfig.setModel("gpt-4o-mini");

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getAnalysis()).thenReturn(analysisConfig);
        when(properties.getOpenai()).thenReturn(openAIConfig);

        mockMvc.perform(get("/api/v1/admin/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalEnabled").value(true))
                .andExpect(jsonPath("$.aiModel").value("gpt-4o-mini"));
    }
}
