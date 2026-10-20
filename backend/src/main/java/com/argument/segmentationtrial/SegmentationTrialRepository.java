package com.argument.segmentationtrial;
import com.argument.version.VersionJson;
import com.argument.common.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository @Profile("(local | test) & !prod")
public class SegmentationTrialRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public SegmentationTrialRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public void append(String kind,ObjectNode n){jdbc.update("INSERT INTO trial_segmentation_records(id,record_kind,case_id,actor_id,payload_json) VALUES(?,?,?,?,?)",n.path("id").asText(),kind,n.path("taskDocumentId").asText(),n.path("createdById").asText(),n.toString());}
 public List<ObjectNode> list(String kind,String caseId){return jdbc.queryForList("SELECT payload_json FROM trial_segmentation_records WHERE record_kind=? AND case_id=? ORDER BY id",String.class,kind,caseId).stream().map(s->VersionJson.parse(mapper,s)).toList();}
 public ObjectNode get(String kind,String id){var rows=jdbc.queryForList("SELECT payload_json FROM trial_segmentation_records WHERE record_kind=? AND id=?",String.class,kind,id);if(rows.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND);return VersionJson.parse(mapper,rows.get(0));}
 /** Local bridge only; A replaces this with a transaction-joining shared checkpoint adapter. */
 public void checkpoint(ObjectNode n){jdbc.update("INSERT INTO local_annotation_dependencies(object_kind,object_id,payload_json) VALUES('checkpoint',?,?)",n.path("id").asText(),n.toString());}
}
