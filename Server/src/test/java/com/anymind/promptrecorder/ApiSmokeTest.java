package com.anymind.promptrecorder;

import com.anymind.promptrecorder.storage.SpaceRegistry;
import com.anymind.promptrecorder.storage.SpaceRegistry.SpaceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "storage.root=target/test-data")
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpaceRegistry spaceRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthCheck() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk());
    }

    @Test
    void pushAndPull() throws Exception {
        SpaceInfo info = spaceRegistry.createSpace("test");
        String now = Instant.now().toString();
        String recordId = UUID.randomUUID().toString();

        ObjectNode push = objectMapper.createObjectNode();
        push.put("space_id", info.getSpaceId());
        push.put("space_secret", info.getSpaceSecret());
        push.put("device_id", "test-device");
        ObjectNode change = push.putArray("changes").addObject();
        change.put("id", recordId);
        change.put("content", "hello #p1 #tag");
        change.putArray("system_tags").add("#p1");
        change.putArray("user_tags").add("#tag");
        change.put("created_at", now);
        change.put("updated_at", now);
        change.put("deleted", false);
        change.put("base_rev", 0);

        MvcResult pushResult = mockMvc.perform(
                post("/sync/push")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(push.toString())
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode pushJson = objectMapper.readTree(pushResult.getResponse().getContentAsString());
        assertTrue(pushJson.get("server_rev_max").asLong() >= 1);

        ObjectNode pull = objectMapper.createObjectNode();
        pull.put("space_id", info.getSpaceId());
        pull.put("space_secret", info.getSpaceSecret());
        pull.put("since_rev", 0);
        pull.put("limit", 10);

        MvcResult pullResult = mockMvc.perform(
                post("/sync/pull")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(pull.toString())
            )
            .andExpect(status().isOk())
            .andReturn();

        JsonNode pullJson = objectMapper.readTree(pullResult.getResponse().getContentAsString());
        JsonNode changes = pullJson.get("changes");
        boolean found = false;
        if (changes.isArray()) {
            for (JsonNode node : changes) {
                if (recordId.equals(node.get("id").asText())) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found);
    }
}
