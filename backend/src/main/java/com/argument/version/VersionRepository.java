package com.argument.version;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VersionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public VersionRepository(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public void lockCase(String caseId) {
        // INSERT-if-absent is safe on MySQL and H2 MODE=MySQL; the row lock serializes all case mutations.
        jdbc.update("INSERT INTO annotation_case_counters(task_document_id,last_version_number) VALUES (?,0) ON DUPLICATE KEY UPDATE task_document_id=task_document_id",caseId);
        jdbc.queryForObject("SELECT last_version_number FROM annotation_case_counters WHERE task_document_id=? FOR UPDATE",Integer.class,caseId);
    }
    public int nextNumber(String caseId) {
        jdbc.update("UPDATE annotation_case_counters SET last_version_number=last_version_number+1 WHERE task_document_id=?",caseId);
        return jdbc.queryForObject("SELECT last_version_number FROM annotation_case_counters WHERE task_document_id=?",Integer.class,caseId);
    }
    public ObjectNode snapshot(String id) {
        var rows=jdbc.query("SELECT s.snapshot_json,v.status,v.record_version,v.updated_at,v.updated_by_id FROM annotation_versions v JOIN annotation_version_snapshots s ON s.version_id=v.id WHERE v.id=?",(rs,i)->{
            var snapshot=VersionJson.parse(mapper,rs.getString(1)); var version=(ObjectNode)snapshot.get("version");
            version.put("status",rs.getString(2)); version.put("recordVersion",rs.getInt(3));
            version.put("updatedAt",rs.getTimestamp(4).toInstant().toString()); version.put("updatedById",rs.getString(5)); return snapshot;
        },id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"版本不存在");
        return rows.get(0);
    }
    public List<ObjectNode> versions(String caseId) {
        return jdbc.queryForList("SELECT id FROM annotation_versions WHERE task_document_id=? ORDER BY version_number,id",String.class,caseId)
            .stream().map(this::snapshot).map(s->(ObjectNode)s.get("version")).toList();
    }
    public void insert(ObjectNode snapshot) {
        var v=snapshot.path("version"); String time=v.path("createdAt").asText();
        jdbc.update("INSERT INTO annotation_versions(id,task_document_id,assignment_id,source_draft_id,version_number,status,record_version,created_at,created_by_id,updated_at,updated_by_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            v.path("id").asText(),v.path("taskDocumentId").asText(),v.path("assignmentId").asText(),v.path("sourceDraftId").asText(),v.path("versionNumber").asInt(),v.path("status").asText(),1,
            Timestamp.from(Instant.parse(time)),v.path("createdById").asText(),Timestamp.from(Instant.parse(time)),v.path("updatedById").asText());
        jdbc.update("INSERT INTO annotation_version_snapshots(version_id,snapshot_json) VALUES (?,?)",v.path("id").asText(),snapshot.toString());
    }
    public void withdraw(ObjectNode version,String actor,String time) {
        int changed=jdbc.update("UPDATE annotation_versions SET status='WITHDRAWN',record_version=record_version+1,updated_at=?,updated_by_id=? WHERE id=? AND record_version=? AND status='SUBMITTED'",
            Timestamp.from(Instant.parse(time)),actor,version.path("id").asText(),version.path("recordVersion").asInt());
        if(changed!=1) throw new BusinessException(ErrorCode.VERSION_CONFLICT,"版本已变化，请刷新后重试");
    }
    public boolean effective(ObjectNode version) {
        return !"WITHDRAWN".equals(version.path("status").asText()) && jdbc.queryForObject("SELECT COUNT(*) FROM adjudication_returned_versions WHERE version_id=?",Integer.class,version.path("id").asText())==0;
    }
    public boolean referenced(String versionId) {
        return jdbc.queryForList("SELECT payload_json FROM adjudication_sessions",String.class).stream().map(s->VersionJson.parse(mapper,s)).anyMatch(s->{ for(var id:s.path("sourceVersionIds"))if(id.asText().equals(versionId))return true;return false; });
    }
    public void startAdjudication(ObjectNode version,String actor,String time) {
        jdbc.update("UPDATE annotation_versions SET status='UNDER_ADJUDICATION',record_version=record_version+1,updated_at=?,updated_by_id=? WHERE id=? AND status='SUBMITTED'",Timestamp.from(Instant.parse(time)),actor,version.path("id").asText());
    }
    public void finishAdjudication(ObjectNode version,String actor,String time) {
        jdbc.update("UPDATE annotation_versions SET status='ADJUDICATED',record_version=record_version+1,updated_at=?,updated_by_id=? WHERE id=? AND status='UNDER_ADJUDICATION'",Timestamp.from(Instant.parse(time)),actor,version.path("id").asText());
    }
    public ObjectNode receipt(String actor,String operation,String key,String hash) {
        var rows=jdbc.query("SELECT request_sha256,result_json,expires_at FROM annotation_mutation_receipts WHERE actor_id=? AND operation_key=? AND idempotency_key=? FOR UPDATE",(rs,i)->{
            if(rs.getTimestamp(3).toInstant().isBefore(Instant.now())) return null;
            if(!hash.equals(rs.getString(1))) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,"同一请求编号对应不同内容");
            return VersionJson.parse(mapper,rs.getString(2));
        },actor,operation,key);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void receipt(String actor,String operation,String key,String hash,ObjectNode result,ObjectNode before,ObjectNode after,String note) {
        // Expired receipts can be replaced; snapshots and workflow history are retained independently.
        jdbc.update("DELETE FROM annotation_mutation_receipts WHERE actor_id=? AND operation_key=? AND idempotency_key=? AND expires_at<?",actor,operation,key,Timestamp.from(Instant.now()));
        var now=Instant.now();
        jdbc.update("INSERT INTO annotation_mutation_receipts(actor_id,operation_key,idempotency_key,request_sha256,result_json,before_json,after_json,note_text,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            actor,operation,key,hash,result.toString(),before.toString(),after.toString(),note,Timestamp.from(now),Timestamp.from(now.plusSeconds(86400)));
    }
}
