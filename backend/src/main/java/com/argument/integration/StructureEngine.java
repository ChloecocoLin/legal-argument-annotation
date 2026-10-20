package com.argument.integration;

import com.argument.proposition.*;
import com.argument.relation.dto.*;
import com.argument.relation.entity.*;
import com.argument.relation.service.*;
import com.argument.relation.rules.RelationRules;
import com.argument.graph.service.GraphAssembler;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Pure, snapshot-scoped bridge to C's validator/assembler. Never consults demo or mutable global facts. */
@Component
public class StructureEngine {
    public record Result(List<RelationIssue> issues,GraphAssembler.Assembled graph,List<RelationView> relations) {}
    public static List<ParticipantRequest> inputs(List<ObjectNode> parts){return parts.stream().map(p->new ParticipantRequest(p.path("ordinal").asInt(),p.path("role").asText(),p.path("targetKind").asText(),p.path("targetId").asText())).toList();}
    public Result inspect(String owner,List<ObjectNode> elements,List<ObjectNode> relations,List<ObjectNode> parts){
        var source=source(elements);var validator=new RelationValidator(source);var lookup=lookup(relations,parts);var views=new ArrayList<RelationView>();
        for(var r:relations){String id=r.path("id").asText();var participants=new ArrayList<ParticipantView>();for(var p:lookup.participantsOf(id)){var e=source.find(p.getTargetId());participants.add(new ParticipantView(p.getId(),p.getOrdinal(),p.getRole(),p.getTargetKind(),p.getTargetId(),e==null?null:e.displayNo(),e==null?null:e.labelCode(),e==null?null:e.text()));}
            views.add(new RelationView(id,r.path("ownerType").asText(),r.path("ownerId").asText(),r.path("relationDefinitionId").asText(),r.path("relationCode").asText(),null,r.path("directed").asBoolean(),r.path("status").asText(),List.of(),participants,expression(id,source,lookup,new HashSet<>())));
        }
        var assembler=new GraphAssembler(validator);var issues=new ArrayList<>(assembler.validate(views,lookup));
        // C's matrix validates J as a possible M source; also validate all recursive J leaves.
        for(var r:relations)if("M".equals(r.path("relationCode").asText()))for(var p:lookup.participantsOf(r.path("id").asText()))if("SOURCE".equals(p.getRole())&&"RELATION".equals(p.getTargetKind())&&!individualJoint(p.getTargetId(),source,lookup,new HashSet<>()))issues.add(RelationIssue.error("INVALID_PARTICIPANT","匹配组合的全部叶节点必须是个别判断"));
        var graph=assembler.assemble(source.findBySnapshot(owner).stream().filter(PropositionRef::proposition).toList(),views);return new Result(List.copyOf(issues),graph,views);
    }
    public List<ParticipantRequest> normalize(String code,List<ObjectNode> elements,List<ParticipantRequest> inputs){var validator=new RelationValidator(source(elements));return validator.renumber(validator.normalizeOrder(code,inputs));}
    private boolean individualJoint(String id,PropositionSource source,RelationLookup lookup,Set<String> path){if(!path.add(id))return false;var r=lookup.find(id);if(r==null||!"J".equals(r.getRelationCode()))return false;for(var p:lookup.participantsOf(id)){if("ELEMENT".equals(p.getTargetKind())){var e=source.find(p.getTargetId());if(e==null||!RelationRules.INDIVIDUAL_TYPES.contains(e.labelCode()))return false;}else if(!individualJoint(p.getTargetId(),source,lookup,path))return false;}path.remove(id);return true;}
    private String expression(String id,PropositionSource source,RelationLookup lookup,Set<String> path){if(!path.add(id))return "?";var r=lookup.find(id);if(r==null)return "?";var tokens=new ArrayList<String>();for(var p:lookup.participantsOf(id)){var e=source.find(p.getTargetId());tokens.add("RELATION".equals(p.getTargetKind())?expression(p.getTargetId(),source,lookup,path):e==null?"?":e.displayNo());}path.remove(id);return r.getRelationCode()+"("+String.join(",",tokens)+")";}
    public PropositionSource source(List<ObjectNode> elements){var refs=new LinkedHashMap<String,PropositionRef>();for(var e:elements)refs.put(e.path("id").asText(),new PropositionRef(e.path("id").asText(),e.path("ownerId").asText(),e.path("labelCode").asText(),e.hasNonNull("displayNo")?e.path("displayNo").asText():null,e.path("isProposition").asBoolean(),e.path("text").asText()));return new PropositionSource(){public List<PropositionRef> findBySnapshot(String id){return refs.values().stream().filter(e->id.equals(e.snapshotId())).toList();}public PropositionRef find(String id){return refs.get(id);}public Map<String,PropositionRef> mapByIds(Collection<String> ids){var result=new LinkedHashMap<String,PropositionRef>();for(String id:ids)if(refs.containsKey(id))result.put(id,refs.get(id));return result;}};}
    private RelationLookup lookup(List<ObjectNode> relations,List<ObjectNode> parts){var rs=new LinkedHashMap<String,Relation>();var ps=new ArrayList<RelationParticipant>();for(var row:relations){var r=new Relation();r.setId(row.path("id").asText());r.setOwnerId(row.path("ownerId").asText());r.setOwnerType(row.path("ownerType").asText());r.setRelationCode(row.path("relationCode").asText());r.setStatus(row.path("status").asText());rs.put(r.getId(),r);}for(var row:parts){var p=new RelationParticipant();p.setId(row.path("id").asText());p.setRelationId(row.path("relationId").asText());p.setOrdinal(row.path("ordinal").asInt());p.setRole(row.path("role").asText());p.setTargetKind(row.path("targetKind").asText());p.setTargetId(row.path("targetId").asText());ps.add(p);}return new RelationLookup(){public Relation find(String id){return rs.get(id);}public List<RelationParticipant> participantsOf(String id){return ps.stream().filter(p->id.equals(p.getRelationId())).sorted(Comparator.comparingInt(RelationParticipant::getOrdinal)).toList();}public List<RelationParticipant> participantsOfAll(Collection<String> ids){return ps.stream().filter(p->ids.contains(p.getRelationId())).toList();}public List<Relation> relationsOfOwner(String owner){return rs.values().stream().filter(r->owner.equals(r.getOwnerId())&&"ACTIVE".equals(r.getStatus())).toList();}};}
}
