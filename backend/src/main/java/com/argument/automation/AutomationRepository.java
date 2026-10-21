package com.argument.automation;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
/** Durable facts in the shared store; these never enter annotation snapshot ownership indices. */
@Repository @Profile("(local | test) & !prod")
public class AutomationRepository {
    private final JdbcTemplate jdbc;
    public AutomationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void insert(String kind,ObjectNode n){jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES (?,?,?)",kind,n.path("id").asText(),n.toString());}
    public void event(ObjectNode task,String operation,String outcome){var n=task.objectNode();VersionJson.stamp(n,task.path("createdById").asText(),VersionJson.now());n.put("automationTaskId",task.path("id").asText()).put("taskRecordVersion",task.path("recordVersion").asInt()).put("operation",operation).put("outcome",outcome).put("taskDocumentId",task.path("taskDocumentId").asText()).put("modelId",task.path("modelId").asText()).put("modelVersion",task.path("modelVersion").asText()).put("inputDigest",task.path("inputDigest").asText());n.set("failureCode",task.path("failureCode"));insert("automationEvent",n);}
}
