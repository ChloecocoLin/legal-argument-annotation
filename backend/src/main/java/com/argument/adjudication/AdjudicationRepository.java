package com.argument.adjudication;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdjudicationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AdjudicationRepository(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }
    public ObjectNode session(String id,boolean lock) {
        var rows=jdbc.queryForList("SELECT payload_json FROM adjudication_sessions WHERE id=?"+(lock?" FOR UPDATE":""),String.class,id);
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"裁定会话不存在");
        return VersionJson.parse(mapper,rows.get(0));
    }
    public List<ObjectNode> sessions(String caseId) {
        return jdbc.queryForList("SELECT payload_json FROM adjudication_sessions WHERE task_document_id=? ORDER BY id",String.class,caseId).stream().map(s->VersionJson.parse(mapper,s)).toList();
    }
    public void insertSession(ObjectNode s) { jdbc.update("INSERT INTO adjudication_sessions(id,task_document_id,status,payload_json) VALUES (?,?,?,?)",s.path("id").asText(),s.path("taskDocumentId").asText(),s.path("status").asText(),s.toString()); }
    public void saveSession(ObjectNode s) { jdbc.update("UPDATE adjudication_sessions SET status=?,payload_json=? WHERE id=?",s.path("status").asText(),s.toString(),s.path("id").asText()); }
    public List<ObjectNode> items(String session,String kind) { return jdbc.queryForList("SELECT payload_json FROM adjudication_items WHERE session_id=? AND item_kind=? ORDER BY id",String.class,session,kind).stream().map(s->VersionJson.parse(mapper,s)).toList(); }
    public ObjectNode item(String id,String kind) {
        var rows=jdbc.queryForList("SELECT payload_json FROM adjudication_items WHERE id=? AND item_kind=?",String.class,id,kind);
        if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"裁定项不存在");return VersionJson.parse(mapper,rows.get(0));
    }
    public void insertItem(ObjectNode i,String kind) { jdbc.update("INSERT INTO adjudication_items(id,session_id,item_kind,payload_json) VALUES (?,?,?,?)",i.path("id").asText(),i.path("sessionId").asText(),kind,i.toString()); }
    public void saveItem(ObjectNode i) { jdbc.update("UPDATE adjudication_items SET payload_json=? WHERE id=?",i.toString(),i.path("id").asText()); }
    public void decision(ObjectNode d) { jdbc.update("INSERT INTO adjudication_decisions(id,session_id,item_id,payload_json) VALUES (?,?,?,?)",d.path("id").asText(),d.path("sessionId").asText(),d.path("itemId").asText(),d.toString()); }
    public List<ObjectNode> decisions(String session) { return jdbc.queryForList("SELECT payload_json FROM adjudication_decisions WHERE session_id=? ORDER BY id",String.class,session).stream().map(s->VersionJson.parse(mapper,s)).toList(); }
    public void returned(String version,String session) { jdbc.update("INSERT INTO adjudication_returned_versions(version_id,session_id) VALUES (?,?)",version,session); }
    public void transition(String operation,String actor,ObjectNode command,ObjectNode before,ObjectNode after) {
        var n=mapper.createObjectNode();VersionJson.stamp(n,actor,VersionJson.now());n.put("sessionId",after.path("id").asText());n.put("operation",operation);n.set("command",command);n.set("before",before);n.set("after",after);
        jdbc.update("INSERT INTO adjudication_transitions(id,session_id,payload_json) VALUES (?,?,?)",n.path("id").asText(),after.path("id").asText(),n.toString());
    }
    public List<ObjectNode> history(String session) { return jdbc.queryForList("SELECT payload_json FROM adjudication_transitions WHERE session_id=? ORDER BY id",String.class,session).stream().map(s->VersionJson.parse(mapper,s)).sorted(java.util.Comparator.comparing(n->n.path("createdAt").asText())).toList(); }
}
