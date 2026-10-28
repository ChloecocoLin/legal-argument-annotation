package com.argument.integration;

import com.argument.common.exception.*;
import com.argument.version.*;
import com.argument.version.port.*;
import com.argument.version.port.VersionIdentity.Actor;
import com.argument.finalreview.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.argument.adjudication.AdjudicationService.require;

/** Internal B/C projection boundary. Reads the same owner IDs, never copies content into mock tables. */
@Component
public class SharedSnapshotReader {
 private final AnnotationDependencies data;private final VersionService versions;private final FinalReviewService finals;private final FinalReviewRepository finalStore;private final ObjectMapper mapper;private final com.argument.adjudication.AdjudicationService adjud;
 public SharedSnapshotReader(AnnotationDependencies data,VersionService versions,FinalReviewService finals,FinalReviewRepository finalStore,ObjectMapper mapper,com.argument.adjudication.AdjudicationService adjud){this.data=data;this.versions=versions;this.finals=finals;this.finalStore=finalStore;this.mapper=mapper;this.adjud=adjud;}
 public ObjectNode read(Actor actor,String type,String id){VersionJson.uuid(id);
  if("ARBITER_CUSTOM".equals(type)){var custom=data.read("custom",id);adjud.read(actor,custom.path("sessionId").asText());return custom;}
  if("ANNOTATION_VERSION".equals(type))return versions.read(actor,id);
  if("FINAL_VERSION".equals(type)){var snapshot=finalStore.snapshot(id);var version=snapshot.path("version");var doc=data.read("taskDocument",version.path("taskDocumentId").asText());var task=data.read("task",doc.path("taskId").asText());if("IN_DATABASE".equals(version.path("status").asText())&&actor.permission("DOCUMENT_VIEW")&&(actor.role("ADMIN")||actor.role("TASK_CREATOR")&&actor.id().equals(task.path("ownerUserId").asText())))return snapshot;return finals.read(actor,id);}
  require("DRAFT".equals(type),ErrorCode.SOURCE_INCOMPLETE,"该快照类型尚未接入统一读取");var draft=data.read("draft",id);var a=data.read("assignment",draft.path("assignmentId").asText());String c=draft.path("taskDocumentId").asText();var member=data.read("member",a.path("taskMemberId").asText());var doc=data.read("taskDocument",c);
  require(actor.permission("ANNOTATION_WORK")&&"ACTIVE".equals(a.path("status").asText())&&"ANNOTATOR".equals(a.path("assignmentRole").asText())&&"ACTIVE".equals(member.path("status").asText())&&actor.id().equals(member.path("userId").asText())&&"ANNOTATOR".equals(member.path("memberRole").asText())&&member.path("taskId").equals(doc.path("taskId"))&&actor.id().equals(a.path("userId").asText())&&c.equals(a.path("taskDocumentId").asText())&&versions.hasAssignment(actor,c,"ANNOTATOR"),ErrorCode.BLIND_SCOPE_DENIED,"本人草稿在正式提交前保持独立");
  var result=mapper.createObjectNode();result.set("draft",draft);var elements=result.putArray("elements");draft.path("elementIds").forEach(v->elements.add(data.read("element",v.asText())));var relations=result.putArray("relations");var participants=result.putArray("participants");for(var v:draft.path("relationIds")){relations.add(data.read("relation",v.asText()));data.find("participant","relationId",v.asText()).forEach(participants::add);}if(draft.hasNonNull("graphId"))result.set("graph",data.read("graph",draft.path("graphId").asText()));return result;
 }
}
