package com.argument.finalreview;

import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FinalReviewRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public FinalReviewRepository(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }
    public ObjectNode snapshot(String id) {
        var rows=jdbc.query("SELECT payload_json,snapshot_json FROM final_annotation_versions WHERE id=?",(rs,i)->{var s=VersionJson.parse(mapper,rs.getString(2));s.set("version",VersionJson.parse(mapper,rs.getString(1)));return s;},id);
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"最终版本不存在");return rows.get(0);
    }
    public List<ObjectNode> versions(String caseId) { return jdbc.queryForList("SELECT payload_json FROM final_annotation_versions WHERE task_document_id=? ORDER BY version_number",String.class,caseId).stream().map(s->VersionJson.parse(mapper,s)).toList(); }
    public void insert(ObjectNode snapshot) {
        var v=snapshot.path("version");jdbc.update("INSERT INTO final_annotation_versions(id,task_document_id,session_id,version_number,payload_json,snapshot_json) VALUES (?,?,?,?,?,?)",v.path("id").asText(),v.path("taskDocumentId").asText(),v.path("sessionId").asText(),v.path("versionNumber").asInt(),v.toString(),snapshot.toString());
        com.argument.integration.SnapshotObjectOwners.index(jdbc,snapshot,"FINAL_VERSION");
    }
    /** Frozen content is never updated by a workflow transition. */
    public void saveVersion(ObjectNode version) { jdbc.update("UPDATE final_annotation_versions SET payload_json=? WHERE id=?",version.toString(),version.path("id").asText()); }
    public void transition(String operation,String actor,ObjectNode command,ObjectNode before,ObjectNode after) {
        var n=mapper.createObjectNode();VersionJson.stamp(n,actor,VersionJson.now());n.put("operation",operation);n.put("finalVersionId",after.path("id").asText());n.set("command",command);n.set("before",before);n.set("after",after);
        int sequence=jdbc.queryForObject("SELECT COUNT(*) FROM final_review_transitions WHERE final_version_id=?",Integer.class,after.path("id").asText())+1;n.put("sequenceNumber",sequence);
        jdbc.update("INSERT INTO final_review_transitions(id,final_version_id,sequence_number,payload_json) VALUES (?,?,?,?)",n.path("id").asText(),after.path("id").asText(),sequence,n.toString());
    }
    public List<ObjectNode> history(String id) { return jdbc.queryForList("SELECT payload_json FROM final_review_transitions WHERE final_version_id=? ORDER BY sequence_number",String.class,id).stream().map(s->VersionJson.parse(mapper,s)).toList(); }
}
