package com.argument.integration;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Internal permanent command journal, separate from the pending public AuditEvent model. */
@Component
public class WorkflowCommandHistory {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public WorkflowCommandHistory(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public void append(String actor,String operation,JsonNode command,JsonNode result){var e=mapper.createObjectNode();VersionJson.stamp(e,actor,VersionJson.now());e.put("operation",operation);e.set("command",command);e.set("result",result);jdbc.update("INSERT INTO workflow_command_history(id,actor_id,operation_key,occurred_at,payload_json) VALUES(?,?,?,?,?)",e.path("id").asText(),actor,operation,e.path("createdAt").asText(),e.toString());}
}
