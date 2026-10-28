package com.argument.integration;

import com.argument.version.local.LocalFixtureLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cross-branch regressions: persistent C guide metadata and the shared identity boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeamMergeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LocalFixtureLoader fixtures;
    @Autowired LocalAccountLoader accounts;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers;

    @BeforeEach void setup() throws Exception {
        fixtures.run(null);
        accounts.run(null);
    }

    String login(String username) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content(mapper.createObjectNode().put("username", username)
                        .put("password", "demo").toString()))
            .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer " + mapper.readTree(response.getContentAsByteArray()).path("data").path("token").asText();
    }

    @Test void relationDefinitionsReadFromMigratedPersistentGuideSchema() throws Exception {
        mvc.perform(get("/api/v1/guide-versions/v-g-12/relations")
                .header("Authorization", login("admin.wang")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(5)))
            .andExpect(jsonPath("$.data.items[0].code").value("S"))
            .andExpect(jsonPath("$.data.items[0].directionRule", not(blankOrNullString())))
            .andExpect(jsonPath("$.data.items[0].minParticipants").value(2))
            .andExpect(jsonPath("$.data.items[4].code").value("I"));
    }

    @Test void annotatorCannotWriteRelationDefinitionsThroughNewCPatchRoute() throws Exception {
        mvc.perform(patch("/api/v1/relation-definitions/r12-s")
                .header("Authorization", login("annotator.chen"))
                .contentType("application/json").content("{\"definition\":\"unauthorized\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test void standaloneMockPropositionEndpointIsNotPublished() throws Exception {
        org.assertj.core.api.Assertions.assertThat(handlers.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream()).toList())
            .noneMatch(path -> path.contains("mock-propositions"));
        mvc.perform(get("/api/v1/annotation-drafts/f67848e1-a06d-4600-8454-19055e720d2c/mock-propositions")
                .header("Authorization", login("admin.wang")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
