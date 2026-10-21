package com.argument.teachingtrial;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository @Profile("(local | test) & !prod")
public class TeachingRepository {
 private final JdbcTemplate jdbc;
 public TeachingRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void insert(String kind,ObjectNode n){jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES (?,?,?)",kind,n.path("id").asText(),n.toString());}
 public void event(String actor,String operation,String objectId,String digest){var e=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();VersionJson.stamp(e,actor,VersionJson.now());e.put("operation",operation).put("objectId",objectId).put("contentDigest",digest);insert("teachingEvent",e);}
}
