package com.argument.adjudication.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The standalone B test used unauthenticated seeded SQL sessions. The integrated
 * workflow must reject those requests. Authenticated custom saves, idempotency,
 * source immutability and expert return are covered by OrdinaryDraftIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomAnnotationControllerTest {
    @Autowired MockMvc mvc;
    private static final String SESSION = "aba39933-394e-41e0-aa76-1cf8acc57ba7";
    private static final String ITEM = "058fa5ba-c4a8-457a-96f9-1efd15df8e20";

    @Test void customContextRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/adjudication-sessions/{id}/custom-context", SESSION)
                .param("itemKind", "PROPOSITION").param("itemId", ITEM))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void customWriteCannotUseDefaultDemoIdentity() throws Exception {
        mvc.perform(post("/api/v1/adjudication-sessions/{id}/custom-annotations", SESSION)
                .header("Idempotency-Key", "test-unauthenticated")
                .contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void callerSuppliedUserHeaderDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/adjudication-sessions/{id}/custom-context", SESSION)
                .header("X-User-Id", "4809fe74-569e-4906-a453-423c290b0525")
                .param("itemKind", "PROPOSITION").param("itemId", ITEM))
            .andExpect(status().isUnauthorized());
    }

    @Test void invalidBearerCannotFallBackToDemoExpert() throws Exception {
        mvc.perform(post("/api/v1/adjudication-sessions/{id}/custom-annotations", SESSION)
                .header("Authorization", "Bearer invalid-team-merge-token")
                .header("X-User-Id", "4809fe74-569e-4906-a453-423c290b0525")
                .header("Idempotency-Key", "test-invalid-token")
                .contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
