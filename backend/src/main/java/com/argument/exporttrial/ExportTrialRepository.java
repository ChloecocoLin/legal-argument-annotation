package com.argument.exporttrial;

import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("(local | test) & !prod")
public class ExportTrialRepository {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public ExportTrialRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public void lockActor(String actor){jdbc.queryForObject("SELECT payload_json FROM workflow_records WHERE object_kind='user' AND object_id=? FOR UPDATE",String.class,actor);}
    public ObjectNode export(String id,boolean lock){return one("SELECT payload_json FROM trial_exports WHERE id=?"+(lock?" FOR UPDATE":""),id);}
    public List<ObjectNode> exports(){return many("SELECT payload_json FROM trial_exports ORDER BY id");}
    public void insertExport(ObjectNode n,ObjectNode input){jdbc.update("INSERT INTO trial_exports(id,status,payload_json,input_json) VALUES (?,?,?,?)",n.path("id").asText(),n.path("status").asText(),n.toString(),input.toString());}
    public ObjectNode input(String id){return one("SELECT input_json FROM trial_exports WHERE id=?",id);}
    public String queued(){var ids=jdbc.queryForList("SELECT id FROM trial_exports WHERE status='QUEUED' ORDER BY id LIMIT 1",String.class);return ids.isEmpty()?null:ids.get(0);}
    public void saveExport(ObjectNode n){jdbc.update("UPDATE trial_exports SET status=?,payload_json=? WHERE id=?",n.path("status").asText(),n.toString(),n.path("id").asText());}
    public void file(ObjectNode n,String exportId,ObjectNode generated){jdbc.update("INSERT INTO trial_files(id,export_id,payload_json,generated_json) VALUES (?,?,?,?)",n.path("id").asText(),exportId,n.toString(),generated==null?null:generated.toString());}
    public ObjectNode file(String id){return one("SELECT payload_json FROM trial_files WHERE id=?",id);}
    public String fileExport(String id){return jdbc.queryForObject("SELECT export_id FROM trial_files WHERE id=?",String.class,id);}
    public void download(ObjectNode n){jdbc.update("INSERT INTO trial_downloads(id,file_id,token_digest,payload_json) VALUES (?,?,?,?)",n.path("id").asText(),n.path("fileId").asText(),n.path("tokenDigest").asText(),n.toString());}
    public ObjectNode ticket(String digest){return one("SELECT payload_json FROM trial_downloads WHERE token_digest=?",digest);}
    public void event(ObjectNode n){jdbc.update("INSERT INTO trial_data_events(id,object_id,payload_json) VALUES (?,?,?)",n.path("id").asText(),n.path("objectId").asText(),n.toString());}
    public List<ObjectNode> events(){return many("SELECT payload_json FROM trial_data_events ORDER BY id");}
    public String receipt(String actor,String op,String key,String hash){var rows=jdbc.query("SELECT request_sha256,encrypted_result,expires_at FROM trial_export_receipts WHERE actor_id=? AND operation_key=? AND idempotency_key=? FOR UPDATE",(rs,i)->{if(java.time.Instant.parse(rs.getString(3)).isBefore(java.time.Instant.now()))return null;if(!hash.equals(rs.getString(1)))throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,"同一请求编号对应不同内容");return rs.getString(2);},actor,op,key);return rows.isEmpty()?null:rows.get(0);}
    public void receipt(String actor,String op,String key,String hash,String encrypted){jdbc.update("DELETE FROM trial_export_receipts WHERE actor_id=? AND operation_key=? AND idempotency_key=? AND expires_at<?",actor,op,key,VersionJson.now());jdbc.update("INSERT INTO trial_export_receipts(actor_id,operation_key,idempotency_key,request_sha256,encrypted_result,expires_at) VALUES (?,?,?,?,?,?)",actor,op,key,hash,encrypted,java.time.Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());}
    private ObjectNode one(String sql,String value){var rows=jdbc.queryForList(sql,String.class,value);if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"所请求的文件或记录不存在");return VersionJson.parse(mapper,rows.get(0));}
    private List<ObjectNode> many(String sql){return jdbc.queryForList(sql,String.class).stream().map(s->VersionJson.parse(mapper,s)).toList();}
}
