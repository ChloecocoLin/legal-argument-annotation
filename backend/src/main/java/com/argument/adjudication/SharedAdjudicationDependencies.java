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
    private final com.argument.integration.StructureEngine engine;private final AnnotationDependencies data;private final ObjectMapper mapper;private final JdbcTemplate jdbc;
    public SharedAdjudicationDependencies(AnnotationDependencies data,ObjectMapper mapper,JdbcTemplate jdbc,com.argument.integration.StructureEngine engine){super(data,mapper,jdbc);this.data=data;this.mapper=mapper;this.jdbc=jdbc;this.engine=engine;}
    @Override public void freezePropositions(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions){
        var all=new HashMap<String,ObjectNode>();for(var s:snapshots)for(var e:s.path("elements"))all.put(e.path("id").asText(),(ObjectNode)e);var covered=new HashSet<String>();var mapping=mapper.createObjectNode().put("id",session.path("id").asText()).put("fixture",false);String unifiedId=VersionJson.uuid();mapping.put("unifiedSnapshotId",unifiedId);var aliases=mapping.putObject("elementMap");var unified=mapper.createObjectNode().put("id",unifiedId).put("sessionId",session.path("id").asText());unified.set("sourceVersionIds",session.get("sourceVersionIds"));var output=unified.putArray("elements");
        for(var item:items){var d=decisions.stream().filter(n->n.path("id").equals(item.path("decisionId"))).findFirst().orElseThrow();var candidates=new ArrayList<String>();var perVersion=new HashSet<String>();for(var id:item.path("candidateElementIds")){var e=all.get(id.asText());require(e!=null&&covered.add(id.asText())&&perVersion.add(e.path("ownerId").asText()),ErrorCode.UNDECIDED_PROPOSITIONS,"一对多拆分合并尚需 M13，不能任取映射端点");candidates.add(id.asText());}if("DELETE".equals(d.path("action").asText()))continue;
            ObjectNode chosen;if("CUSTOM".equals(d.path("action").asText())){var custom=data.read("custom",d.path("customAnnotationId").asText());require(custom.path("elements").size()==1,ErrorCode.INVALID_CUSTOM_GRAPH,"当前映射只接入一对一自定义结果");chosen=(ObjectNode)custom.path("elements").get(0);}else chosen=all.get("CONFIRM_AGREEMENT".equals(d.path("action").asText())?candidates.get(0):d.path("sourceObjectId").asText());require(chosen!=null,ErrorCode.SOURCE_INCOMPLETE,"命题来源失效");var e=chosen.deepCopy();e.put("id",VersionJson.uuid()).put("ownerId",unifiedId);e.putArray("sourceElementIds");candidates.forEach(id->((com.fasterxml.jackson.databind.node.ArrayNode)e.get("sourceElementIds")).add(id));output.add(e);if(e.path("isProposition").asBoolean())for(String id:candidates)aliases.put(id,e.path("id").asText());
        }
        require(covered.equals(all.keySet()),ErrorCode.UNDECIDED_PROPOSITIONS,"统一命题必须覆盖全部命题与非命题来源");var sorted=new ArrayList<ObjectNode>();output.forEach(e->sorted.add((ObjectNode)e));sorted.sort(Comparator.comparingInt(e->e.path("startOffset").asInt()));int number=0;for(var e:sorted)if(e.path("isProposition").asBoolean())e.put("displayNo","P"+(++number));else e.putNull("displayNo");unified.set("elements",mapper.valueToTree(sorted));
        // No session field is added to frozen J02. The adapter resolves this internal immutable mapping record.
        jdbc.update("INSERT INTO workflow_records VALUES('adjudicationMapping',?,?)",session.path("id").asText(),mapping.toString());jdbc.update("INSERT INTO workflow_records VALUES('unifiedSnapshot',?,?)",unifiedId,unified.toString());var index=mapper.createObjectNode();index.putObject("version").put("id",unifiedId);index.set("elements",unified.get("elements"));index.putArray("relations");index.putArray("participants");com.argument.integration.SnapshotObjectOwners.index(jdbc,index,"UNIFIED_PROPOSITION");
    }

    /** C's canonicalization and strongest matching, accumulated once per source object for N versions. */
    @Override public List<ObjectNode> compareRelations(ObjectNode session,List<ObjectNode> snapshots){
        var mapping=data.read("adjudicationMapping",session.path("id").asText()).path("elementMap");
        var groups=new ArrayList<List<com.argument.relation.service.CanonicalRelation>>();
        for(var snapshot:snapshots){
            var es=rows(snapshot,"elements");var rs=rows(snapshot,"relations");var ps=rows(snapshot,"participants");
            var canonicalizer=new com.argument.relation.service.RelationCanonicalizer(engine.source(es));
            var current=canonicalizer.canonicalize(engine.inspect(snapshot.path("version").path("id").asText(),es,rs,ps).relations(),snapshot.path("version").path("id").asText(),id->mapping.hasNonNull(id)?mapping.path(id).asText():null);
            var representatives=groups.stream().map(g->g.get(0)).toList();
            var matched=com.argument.relation.service.RelationMatcher.match(representatives,current);
            for(var pair:matched.pairs())groups.get(representatives.indexOf(pair.left())).add(pair.right());
            for(var relation:matched.unmatchedRight()){var group=new ArrayList<com.argument.relation.service.CanonicalRelation>();group.add(relation);groups.add(group);}
        }
        var result=new ArrayList<ObjectNode>();
        for(var group:groups){
            String type="AGREEMENT";
            if(group.size()!=snapshots.size()||group.stream().anyMatch(r->!r.unmappedIds().isEmpty()))type="EXISTENCE";
            else for(int a=0;a<group.size();a++)for(int b=a+1;b<group.size();b++){
                int score=com.argument.relation.service.RelationMatcher.pairScore(group.get(a),group.get(b));
                String difference=switch(score){case 4->"AGREEMENT";case 3->"DIRECTION";case 2->"PARTICIPANTS";case 1->"TYPE";default->"PARTICIPANTS";};
                if(!"AGREEMENT".equals(difference))type="AGREEMENT".equals(type)||type.equals(difference)?difference:"PARTICIPANTS";
            }
            var item=mapper.createObjectNode().put("conflictType",type);var ids=item.putArray("candidateRelationIds");group.forEach(r->ids.add(r.relationId()));result.add(item);
        }
        return result;
    }
    private List<ObjectNode> rows(com.fasterxml.jackson.databind.JsonNode n,String field){var result=new ArrayList<ObjectNode>();n.path(field).forEach(e->result.add(((ObjectNode)e).deepCopy()));return result;}
    /** Validate the selected result on actual unified IDs. Open inner items must be decided first. */
    @Override public void validateRelations(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions){
        var record=data.read("adjudicationMapping",session.path("id").asText());
        if(!record.hasNonNull("unifiedSnapshotId")){super.validateRelations(session,snapshots,items,decisions);return;}
        var unified=data.read("unifiedSnapshot",record.path("unifiedSnapshotId").asText());
        var all=new HashMap<String,ObjectNode>();var allParts=new ArrayList<ObjectNode>();
        for(var snapshot:snapshots){for(var r:rows(snapshot,"relations"))all.put(r.path("id").asText(),r);allParts.addAll(rows(snapshot,"participants"));}
        var selected=new ArrayList<ObjectNode>();var aliases=new HashMap<String,String>();var customElements=new HashMap<String,String>();
        for(var item:items)if("DECIDED".equals(item.path("status").asText())){
            var decision=decisions.stream().filter(d->d.path("id").equals(item.path("decisionId"))).findFirst().orElseThrow();
            if("DELETE".equals(decision.path("action").asText()))continue;
            ObjectNode chosen;
            if("CUSTOM".equals(decision.path("action").asText())){
                var custom=data.read("custom",decision.path("customAnnotationId").asText());
                require(custom.path("sessionId").equals(session.path("id"))&&custom.path("itemId").equals(item.path("id"))&&custom.path("relations").size()==1,ErrorCode.INVALID_CUSTOM_GRAPH,"自定义关系必须属于当前项");
                chosen=(ObjectNode)custom.path("relations").get(0);allParts.addAll(rows(custom,"participants"));
                for(var e:custom.path("elements")){var source=e.path("sourceElementIds");require(!source.isEmpty(),ErrorCode.INVALID_RELATION_MAPPING,"自定义端点来源缺失");String target=record.path("elementMap").path(source.get(0).asText()).asText();require(!target.isBlank(),ErrorCode.INVALID_RELATION_MAPPING,"自定义端点未映射");customElements.put(e.path("id").asText(),target);}
            }else chosen=all.get("CONFIRM_AGREEMENT".equals(decision.path("action").asText())?item.path("candidateRelationIds").get(0).asText():decision.path("sourceObjectId").asText());
            require(chosen!=null,ErrorCode.INVALID_RELATION_MAPPING,"采纳关系来源缺失");selected.add(chosen.deepCopy().put("ownerId",unified.path("id").asText()));for(var id:item.path("candidateRelationIds"))aliases.put(id.asText(),chosen.path("id").asText());
        }
        var parts=new ArrayList<ObjectNode>();var selectedIds=new HashSet<String>();selected.forEach(r->selectedIds.add(r.path("id").asText()));
        for(var p:allParts)if(selectedIds.contains(p.path("relationId").asText())){
            String old=p.path("targetId").asText();String target="ELEMENT".equals(p.path("targetKind").asText())?customElements.getOrDefault(old,record.path("elementMap").path(old).asText()):aliases.get(old);
            require(target!=null&&!target.isBlank(),ErrorCode.INVALID_RELATION_MAPPING,"端点已排除或内层关系尚未采纳，请先裁定内层");p.put("targetId",target);parts.add(p);
        }
        var checked=engine.inspect(unified.path("id").asText(),rows(unified,"elements"),selected,parts);
        require(checked.issues().stream().noneMatch(i->"ERROR".equals(i.level())),ErrorCode.INVALID_RELATION_MAPPING,"统一关系结构校验未通过："+checked.issues());
    }
}
