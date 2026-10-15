package com.argument.finalreview.local;

import com.argument.finalreview.FinalReviewDependencies;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

@Component
@Profile("(local | test) & !prod")
public class LocalFinalReviewDependencies implements FinalReviewDependencies {
    private final AnnotationDependencies data;private final ObjectMapper mapper;
    public LocalFinalReviewDependencies(AnnotationDependencies data,ObjectMapper mapper) { this.data=data;this.mapper=mapper; }
    public ObjectNode resolve(ObjectNode session,List<ObjectNode> sources,List<ObjectNode> propositions,List<ObjectNode> relations,List<ObjectNode> decisions) {
        var mapping=data.read("adjudicationMapping",session.path("id").asText()).path("elementMap");
        var all=new HashMap<String,ObjectNode>();var owners=new HashMap<String,String>();
        for(var s:sources)for(String kind:List.of("elements","relations"))for(var o:s.path(kind)){all.put(o.path("id").asText(),(ObjectNode)o);owners.put(o.path("id").asText(),s.path("version").path("id").asText());}
        var result=mapper.createObjectNode();var elements=result.putArray("elements");var rs=result.putArray("relations");var ps=result.putArray("participants");var provenance=result.putArray("provenance");var aliases=new HashMap<String,String>();var chosenRelations=new HashSet<String>();
        for(String kind:List.of("ELEMENT","RELATION"))for(var item:kind.equals("ELEMENT")?propositions:relations) {
            var decision=decisions.stream().filter(d->d.path("id").equals(item.path("decisionId"))).findFirst().orElseThrow();String action=decision.path("action").asText();
            require(!"CUSTOM".equals(action),ErrorCode.INVALID_CUSTOM_GRAPH,"自定义结果需 B 的完整快照与正式映射适配器");
            var candidates=item.path(kind.equals("ELEMENT")?"candidateElementIds":"candidateRelationIds");require(candidates.isArray() && !candidates.isEmpty(),ErrorCode.SOURCE_INCOMPLETE,"候选集合缺失");
            var ids=new ArrayList<String>();candidates.forEach(id->ids.add(id.asText()));require(ids.stream().allMatch(all::containsKey),ErrorCode.SOURCE_INCOMPLETE,"候选来源不属于固定源版本");
            if("DELETE".equals(action)){provenance.add(provenance("EXCLUDED_"+kind,null,ids,owners,decision));continue;}
            boolean agreement="CONFIRM_AGREEMENT".equals(action);String chosen=agreement?ids.get(0):decision.path("sourceObjectId").asText();require(ids.contains(chosen),ErrorCode.SOURCE_INCOMPLETE,"采纳对象不属于此项");
            var object=all.get(chosen).deepCopy();if(kind.equals("ELEMENT")) {
                // This explicit adapter supports only the previously frozen one-to-one mapping.
                if(object.path("isProposition").asBoolean()){String unified=mapping.path(chosen).asText();require(!unified.isBlank() && ids.stream().allMatch(id->unified.equals(mapping.path(id).asText())),ErrorCode.SOURCE_INCOMPLETE,"冻结命题映射缺失或不一致");}
                elements.add(object);
            } else { rs.add(object);chosenRelations.add(chosen); }
            ids.forEach(id->aliases.put(id,chosen));var accepted=agreement?ids:List.of(chosen);provenance.add(provenance(kind,chosen,accepted,owners,decision));
            var excluded=ids.stream().filter(id->!accepted.contains(id)).toList();if(!excluded.isEmpty())provenance.add(provenance("EXCLUDED_"+kind,null,excluded,owners,decision));
        }
        for(var s:sources)for(var original:s.path("participants"))if(chosenRelations.contains(original.path("relationId").asText())){var p=((ObjectNode)original).deepCopy();String target=aliases.get(p.path("targetId").asText());require(target!=null,ErrorCode.INVALID_RELATION_MAPPING,"关系仍引用已排除或未映射对象");p.put("targetId",target);ps.add(p);}
        return result;
    }
    private ObjectNode provenance(String kind,String resultId,List<String> ids,Map<String,String> owners,ObjectNode decision) {
        var p=mapper.createObjectNode().put("resultKind",kind);if(resultId==null)p.putNull("resultId");else p.put("resultId",resultId);var vs=p.putArray("sourceVersionIds");ids.stream().map(owners::get).distinct().forEach(vs::add);var objects=p.putArray("sourceObjectIds");ids.forEach(objects::add);p.put("decisionId",decision.path("id").asText());p.putNull("expertIssueId");return p;
    }
    public Evidence assess(ObjectNode snapshot,ObjectNode session) {
        return new Evidence(false,"LOCAL_PREVIEW",com.argument.finalreview.FinalReviewService.contentDigest(snapshot),snapshot.path("decisionDigest").asText(),snapshot.path("version").path("guideVersionId").asText(),List.of(new Check("FORMAL_DEPENDENCIES_PENDING","切割与命题映射、专家规则及完整图示尚未接入正式验证，当前仅可联调送审和退回",false)));
    }
}
