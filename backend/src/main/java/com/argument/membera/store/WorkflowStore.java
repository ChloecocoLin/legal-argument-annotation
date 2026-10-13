package com.argument.membera.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 成员 A 的 READY 对象内存库，启动时灌入同一批 Mock UUID。 */
@Component
public class WorkflowStore {

    private final ObjectMapper objectMapper;

    public final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> roles = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> documents = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> textVersions = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> scopes = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> taskDocuments = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> taskMembers = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> assignments = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> submissions = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> checkpoints = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> finalVersions = new ConcurrentHashMap<>();
    public final Map<String, String> idempotencyBodies = new ConcurrentHashMap<>();
    public final Map<String, Object> idempotencyResults = new ConcurrentHashMap<>();

    public WorkflowStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws Exception {
        loadList("mock-data/base/users.json", users);
        loadList("mock-data/base/roles.json", roles);
        loadList("mock-data/base/documents.json", documents);
        loadList("mock-data/base/document-text-versions.json", textVersions);
        loadList("mock-data/base/annotation-scopes.json", scopes);
        loadList("mock-data/base/tasks.json", tasks);
        loadList("mock-data/base/task-documents.json", taskDocuments);
        loadList("mock-data/base/task-members.json", taskMembers);
        loadList("mock-data/base/task-assignments.json", assignments);
        loadList("mock-data/core/segmentation-submissions.json", submissions);
        loadList("mock-data/core/segmentation-checkpoints.json", checkpoints);

        try (InputStream in = new ClassPathResource("mock-data/complete/final-version.json").getInputStream()) {
            Map<String, Object> root = objectMapper.readValue(in, new TypeReference<>() {});
            Object fav = root.get("finalAnnotationVersion");
            if (fav instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) map);
                finalVersions.put(String.valueOf(row.get("id")), row);
            }
        }
    }

    private void loadList(String path, Map<String, Map<String, Object>> target) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            List<Map<String, Object>> rows = objectMapper.readValue(in, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                target.put(String.valueOf(row.get("id")), new LinkedHashMap<>(row));
            }
        }
    }
}
