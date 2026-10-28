package com.argument.teachingtrial;
import com.argument.version.VersionJson;
import com.argument.integration.StructureEngine;
import com.argument.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;
/** Independent teaching object IDs; no source provenance or researcher identity is sent to students. */
@Component
public class TeachingSnapshot {
 private final ObjectMapper mapper;private final StructureEngine structures;
 public TeachingSnapshot(ObjectMapper mapper,StructureEngine structures){this.mapper=mapper;this.structures=structures;}
 public ObjectNode copy(ObjectNode source,String owner){var out=mapper.createObjectNode();var ids=new HashMap<String,String>();for(String f:List.of("elements","relations"))source.path(f).forEach(n->ids.put(n.path("id").asText(),VersionJson.uuid()));for(String f:List.of("elements","relations")){var rows=out.putArray(f);for(var raw:source.path(f)){var n=((ObjectNode)raw).deepCopy();n.remove(List.of("sourceElementIds","sourceRelationIds","sourceVersionIds","createdById","updatedById","createdAt","updatedAt"));n.put("id",ids.get(raw.path("id").asText())).put("ownerId",owner).put("ownerType","TEACHING_SNAPSHOT");rows.add(n);}}var parts=out.putArray("participants");for(var raw:source.path("participants")){var n=((ObjectNode)raw).deepCopy();n.remove(List.of("createdById","updatedById","createdAt","updatedAt"));n.put("id",VersionJson.uuid()).put("relationId",ids.get(raw.path("relationId").asText()));require(ids.containsKey(raw.path("targetId").asText()),ErrorCode.SOURCE_INCOMPLETE,"教学快照存在悬空参与项");n.put("targetId",ids.get(raw.path("targetId").asText()));parts.add(n);}out.put("ownerId",owner);return out;}
 public ObjectNode graph(ObjectNode s){var result=structures.inspect(s.path("ownerId").asText(),rows(s,"elements"),rows(s,"relations"),rows(s,"participants"));require(result.issues().stream().noneMatch(i->"ERROR".equals(i.level())),ErrorCode.GRAPH_INVALID,"教学结构验证未通过");var graph=mapper.createObjectNode();graph.set("nodes",mapper.valueToTree(result.graph().nodes()));graph.set("edges",mapper.valueToTree(result.graph().edges()));graph.put("componentCount",result.graph().componentCount());graph.set("isolatedNodeIds",mapper.valueToTree(result.graph().isolatedNodeIds()));return graph;}
 public static List<ObjectNode> rows(JsonNode n,String field){var list=new ArrayList<ObjectNode>();n.path(field).forEach(x->list.add(((ObjectNode)x).deepCopy()));return list;}
}
