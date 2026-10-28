package com.argument.committrial;

import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("(local | test) & !prod")
public class CommitTrialRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public CommitTrialRepository(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
    public boolean committed(String caseId) {return jdbc.queryForObject("SELECT COUNT(*) FROM trial_final_commitments WHERE task_document_id=?",Integer.class,caseId)>0;}
    public void append(ObjectNode event) {
        jdbc.update("INSERT INTO trial_audit_events(id,task_id,task_document_id,object_id,occurred_at,payload_json) VALUES (?,?,?,?,?,?)",event.path("id").asText(),event.path("taskId").asText(),event.path("taskDocumentId").asText(),event.path("objectId").asText(),event.path("occurredAt").asText(),event.toString());
    }
    public void commitment(String caseId,String finalId,String auditId,ObjectNode evidence) {
        jdbc.update("INSERT INTO trial_final_commitments(task_document_id,final_version_id,audit_event_id,evidence_json) VALUES (?,?,?,?)",caseId,finalId,auditId,evidence.toString());
    }
    public List<ObjectNode> events() {return jdbc.queryForList("SELECT payload_json FROM trial_audit_events ORDER BY occurred_at DESC,id",String.class).stream().map(s->VersionJson.parse(mapper,s)).toList();}
}
