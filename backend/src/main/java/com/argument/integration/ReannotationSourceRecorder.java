package com.argument.integration;
import com.argument.finalreview.*;
import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;
/** Immutable lineage, written in the same T14 transaction as A's new task. */
@Component
public class ReannotationSourceRecorder {
 private final FinalReviewRepository finals;private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ReannotationSourceRecorder(FinalReviewRepository finals,JdbcTemplate jdbc,ObjectMapper mapper){this.finals=finals;this.jdbc=jdbc;this.mapper=mapper;}
 public void verify(String finalId,String caseId){var versions=finals.versions(caseId);require(!versions.isEmpty()&&finalId.equals(versions.get(versions.size()-1).path("id").asText()),ErrorCode.LOCKED_RESOURCE,"重标须使用该案例当前入库最终版");}
 public void record(String taskId,String sourceTaskId,String finalId,String actor){var snapshot=finals.snapshot(finalId);var lineage=mapper.createObjectNode();VersionJson.stamp(lineage,actor,VersionJson.now());lineage.put("id",taskId).put("sourceTaskId",sourceTaskId).put("sourceFinalVersionId",finalId).put("sourceContentDigest",FinalReviewService.contentDigest(snapshot));for(String field:java.util.List.of("taskDocumentId","documentTextVersionId","annotationScopeId","guideVersionId"))lineage.set(field,snapshot.path("version").path(field));jdbc.update("INSERT INTO workflow_records VALUES('reannotationSource',?,?)",taskId,lineage.toString());}
}
