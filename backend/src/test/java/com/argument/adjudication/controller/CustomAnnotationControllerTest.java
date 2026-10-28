package com.argument.adjudication.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CustomAnnotationControllerTest {

    private static final String SESSION_ID = "aba39933-394e-41e0-aa76-1cf8acc57ba7";
    private static final String ITEM_ID = "058fa5ba-c4a8-457a-96f9-1efd15df8e20";
    private static final String SOURCE_ID = "007077eb-0059-41ed-bd82-bf5b90faf40f";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getContextReturnsLockedBasisAndCandidates() throws Exception {
        mockMvc.perform(
                        get("/api/v1/adjudication-sessions/{sessionId}/custom-context", SESSION_ID)
                                .param("itemKind", "PROPOSITION")
                                .param("itemId", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemKind").value("PROPOSITION"))
                .andExpect(jsonPath("$.data.session.id").value(SESSION_ID))
                .andExpect(jsonPath("$.data.item.id").value(ITEM_ID))
                .andExpect(jsonPath("$.data.sourceVersions", hasSize(2)))
                .andExpect(jsonPath("$.data.candidateElements", hasSize(2)))
                .andExpect(jsonPath("$.data.documentTextVersion.normalizedText").isString())
                .andExpect(jsonPath("$.data.annotationScope.startOffset").value(5))
                .andExpect(jsonPath("$.data.guideVersion.id").value("v-g-12"));
    }

    @Test
    void saveIsIdempotentForSameKeyAndPayload() throws Exception {
        String idempotencyKey = "test-" + UUID.randomUUID();
        String payload = validPayload("same-request");

        MvcResult first = mockMvc.perform(
                        post("/api/v1/adjudication-sessions/{sessionId}/custom-annotations", SESSION_ID)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.annotationId", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.elementIds", hasSize(1)))
                .andExpect(jsonPath("$.data.relationIds", hasSize(0)))
                .andExpect(jsonPath("$.data.idMap[0].clientKey").value("same-request"))
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/v1/adjudication-sessions/{sessionId}/custom-annotations", SESSION_ID)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(
                firstJson.at("/data/annotationId").asText(),
                secondJson.at("/data/annotationId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                firstJson.at("/data/elementIds/0").asText(),
                secondJson.at("/data/elementIds/0").asText());
    }

    @Test
    void sameIdempotencyKeyRejectsDifferentPayload() throws Exception {
        String idempotencyKey = "test-" + UUID.randomUUID();
        mockMvc.perform(
                        post("/api/v1/adjudication-sessions/{sessionId}/custom-annotations", SESSION_ID)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPayload("first-client")))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/adjudication-sessions/{sessionId}/custom-annotations", SESSION_ID)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPayload("second-client")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void saveRejectsOverlappingElements() throws Exception {
        String payload = """
                {
                  "itemKind": "PROPOSITION",
                  "itemId": "%s",
                  "customElements": [
                    {
                      "clientKey": "overlap-a",
                      "startOffset": 5,
                      "endOffset": 20,
                      "labelCode": "GM-L",
                      "sourceElementIds": ["%s"]
                    },
                    {
                      "clientKey": "overlap-b",
                      "startOffset": 10,
                      "endOffset": 24,
                      "labelCode": "GM-I",
                      "sourceElementIds": []
                    }
                  ],
                  "customRelations": [],
                  "reason": "验证重叠区间会被服务端拒绝"
                }
                """.formatted(ITEM_ID, SOURCE_ID);

        mockMvc.perform(
                        post("/api/v1/adjudication-sessions/{sessionId}/custom-annotations", SESSION_ID)
                                .header("Idempotency-Key", "test-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OFFSET_INVALID"));
    }

    private String validPayload(String clientKey) {
        return """
                {
                  "itemKind": "PROPOSITION",
                  "itemId": "%s",
                  "customElements": [
                    {
                      "clientKey": "%s",
                      "startOffset": 5,
                      "endOffset": 20,
                      "labelCode": "GM-L",
                      "sourceElementIds": ["%s"]
                    }
                  ],
                  "customRelations": [],
                  "reason": "裁定者根据锁定指南重新确认命题标签"
                }
                """.formatted(ITEM_ID, clientKey, SOURCE_ID);
    }
}
