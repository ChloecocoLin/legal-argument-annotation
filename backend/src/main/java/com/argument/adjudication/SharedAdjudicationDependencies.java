package com.argument.adjudication;
import com.argument.adjudication.local.LocalAdjudicationDependencies;
import com.argument.common.exception.*;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

/** Real fixed-version inputs and human one-to-one choices. Complex split/merge remains gated by M13. */
@Component @Primary @Profile("(local | test) & !prod")
public class SharedAdjudicationDependencies extends LocalAdjudicationDependencies {
    private final AnnotationDependencies data;private final ObjectMapper mapper;private final JdbcTemplate jdbc;
    public SharedAdjudicationDependencies(AnnotationDependencies data,ObjectMapper mapper,JdbcTemplate jdbc){super(data,mapper,jdbc);this.data=data;this.mapper=mapper;this.jdbc=jdbc;}
    @Override public void freezePropositions(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions){
        var all=new HashMap<String,ObjectNode>();for(var s:snapshots)for(var e:s.path("elements"))all.put(e.path("id").asText(),(ObjectNode)e);var covered=new HashSet<String>();var mapping=mapper.createObjectNode().put("id",session.path("id").asText()).put("fixture",false);String unifiedId=VersionJson.uuid();mapping.put("unifiedSnapshotId",unifiedId);var aliases=mapping.putObject("elementMap");var unified=mapper.createObjectNode().put("id",unifiedId).put("sessionId",session.path("id").asText());unified.set("sourceVersionIds",session.get("sourceVersionIds"));var output=unified.putArray("elements");
        for(var item:items){var d=decisions.stream().filter(n->n.path("id").equals(item.path("decisionId"))).findFirst().orElseThrow();var candidates=new ArrayList<String>();var perVersion=new HashSet<String>();for(var id:item.path("candidateElementIds")){var e=all.get(id.asText());require(e!=null&&covered.add(id.asText())&&perVersion.add(e.path("ownerId").asText()),ErrorCode.UNDECIDED_PROPOSITIONS,"一对多拆分合并尚需 M13，不能任取映射端点");candidates.add(id.asText());}if("DELETE".equals(d.path("action").asText()))continue;
            ObjectNode chosen;if("CUSTOM".equals(d.path("action").asText())){var custom=data.read("custom",d.path("customAnnotationId").asText());require(custom.path("elements").size()==1,ErrorCode.INVALID_CUSTOM_GRAPH,"当前映射只接入一对一自定义结果");chosen=(ObjectNode)custom.path("elements").get(0);}else chosen=all.get("CONFIRM_AGREEMENT".equals(d.path("action").asText())?candidates.get(0):d.path("sourceObjectId").asText());require(chosen!=null,ErrorCode.SOURCE_INCOMPLETE,"命题来源失效");var e=chosen.deepCopy();e.put("id",VersionJson.uuid()).put("ownerId",unifiedId);e.putArray("sourceElementIds");candidates.forEach(id->((com.fasterxml.jackson.databind.node.ArrayNode)e.get("sourceElementIds")).add(id));output.add(e);if(e.path("isProposition").asBoolean())for(String id:candidates)aliases.put(id,e.path("id").asText());
        }
        require(covered.equals(all.keySet()),ErrorCode.UNDECIDED_PROPOSITIONS,"统一命题必须覆盖全部命题与非命题来源");var sorted=new ArrayList<ObjectNode>();output.forEach(e->sorted.add((ObjectNode)e));sorted.sort(Comparator.comparingInt(e->e.path("startOffset").asInt()));int number=0;for(var e:sorted)if(e.path("isProposition").asBoolean())e.put("displayNo","P"+(++number));else e.putNull("displayNo");unified.set("elements",mapper.valueToTree(sorted));
        // No session field is added to frozen J02. The adapter resolves this internal immutable mapping record.
        jdbc.update("INSERT INTO workflow_records VALUES('adjudicationMapping',?,?)",session.path("id").asText(),mapping.toString());jdbc.update("INSERT INTO workflow_records VALUES('unifiedSnapshot',?,?)",unifiedId,unified.toString());var index=mapper.createObjectNode();index.putObject("version").put("id",unifiedId);index.set("elements",unified.get("elements"));index.putArray("relations");index.putArray("participants");com.argument.integration.SnapshotObjectOwners.index(jdbc,index,"UNIFIED_PROPOSITION");
    }
}
