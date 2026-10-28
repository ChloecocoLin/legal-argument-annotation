package com.argument.finalreview;

import com.argument.integration.StructureEngine;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Frozen input only: validation and assembly never create or update graphs/layouts. */
@Component
public class FrozenGraphValidation {
    private final StructureEngine engine;private final ObjectMapper mapper;
    public FrozenGraphValidation(StructureEngine engine,ObjectMapper mapper){this.engine=engine;this.mapper=mapper;}
    public ObjectNode inspect(ObjectNode snapshot){
        var v=snapshot.path("version");var result=engine.inspect(v.path("id").asText(),rows(snapshot,"elements"),rows(snapshot,"relations"),rows(snapshot,"participants"));
        var graph=mapper.createObjectNode().put("graphId",snapshot.path("graph").path("id").asText()).put("ownerType","FINAL_VERSION").put("ownerId",v.path("id").asText()).put("status",result.issues().stream().anyMatch(i->"ERROR".equals(i.level()))?"INVALID":"VALID").put("layoutVersion",0);
        graph.putNull("canvasWidth");graph.putNull("canvasHeight");graph.putArray("positions");graph.set("nodes",mapper.valueToTree(result.graph().nodes()));graph.set("edges",mapper.valueToTree(result.graph().edges()));graph.set("issues",mapper.valueToTree(result.issues()));graph.set("isolatedNodeIds",mapper.valueToTree(result.graph().isolatedNodeIds()));graph.put("componentCount",result.graph().componentCount());
        var evidence=graph.putObject("validationBinding");evidence.put("validatorVersion","C-RelationValidator+D-frozen-v1").put("contentDigest",FinalReviewService.contentDigest(snapshot)).put("decisionDigest",snapshot.path("decisionDigest").asText()).put("guideVersionId",v.path("guideVersionId").asText());return graph;
    }
    private List<ObjectNode> rows(JsonNode source,String field){var rows=new ArrayList<ObjectNode>();source.path(field).forEach(n->rows.add(((ObjectNode)n).deepCopy()));return rows;}
}
