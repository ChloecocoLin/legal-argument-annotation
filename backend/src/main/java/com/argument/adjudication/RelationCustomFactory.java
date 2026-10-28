package com.argument.adjudication;

import com.argument.common.exception.ErrorCode;
import com.argument.integration.StructureEngine;
import com.argument.relation.rules.RelationRules;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

/** Request-local endpoint copies are anchors, never a second proposition adjudication. */
@Component
public class RelationCustomFactory {
    private final AnnotationDependencies data;private final ObjectMapper mapper;private final StructureEngine engine;
    public RelationCustomFactory(AnnotationDependencies data,ObjectMapper mapper,StructureEngine engine){this.data=data;this.mapper=mapper;this.engine=engine;}
    public ObjectNode create(Actor actor,ObjectNode session,ObjectNode item,ObjectNode body){
        require("RELATION_REVIEW".equals(session.path("status").asText())&&Set.of("OPEN","DECIDED").contains(item.path("status").asText()),ErrorCode.INVALID_REVIEW_STATE,"当前关系项不可编辑");
        require(!body.path("reason").asText().isBlank()&&body.path("customRelations").size()==1,ErrorCode.INVALID_CUSTOM_GRAPH,"请填写理由并提交一条完整关系；复合结果需先评审映射模型");
        var mapping=data.read("adjudicationMapping",session.path("id").asText());var unified=data.read("unifiedSnapshot",mapping.path("unifiedSnapshotId").asText());
        var annotation=mapper.createObjectNode();VersionJson.stamp(annotation,actor.id(),VersionJson.now());annotation.put("sessionId",session.path("id").asText()).put("itemKind","RELATION").put("itemId",item.path("id").asText()).put("reason",body.path("reason").asText().trim());
        var response=mapper.createObjectNode();response.set("annotation",annotation);var elements=response.putArray("elements");var relations=response.putArray("relations");var participants=response.putArray("participants");var ids=response.putArray("idMap");var clientIds=new HashMap<String,String>();
        for(var input:body.path("customElements")){
            var matches=new ArrayList<JsonNode>();for(var e:unified.path("elements"))if(e.path("isProposition").asBoolean()&&e.path("startOffset").equals(input.path("startOffset"))&&e.path("endOffset").equals(input.path("endOffset"))&&e.path("labelCode").equals(input.path("labelCode")))matches.add(e);
            require(matches.size()==1&&!input.path("sourceElementIds").isEmpty(),ErrorCode.INVALID_CUSTOM_GRAPH,"关系端点须精确引用已冻结统一命题，不能修改命题内容");var original=matches.get(0);var seen=new HashSet<String>();for(var source:input.path("sourceElementIds"))require(seen.add(source.asText())&&mapping.path("elementMap").path(source.asText()).asText().equals(original.path("id").asText()),ErrorCode.INVALID_CUSTOM_GRAPH,"端点来源不属于统一命题");
            var e=((ObjectNode)original).deepCopy();VersionJson.stamp(e,actor.id(),VersionJson.now());e.put("ownerType","ARBITER_CUSTOM").put("ownerId",annotation.path("id").asText());e.set("sourceElementIds",input.get("sourceElementIds"));elements.add(e);require(clientIds.putIfAbsent(input.path("clientKey").asText(),e.path("id").asText())==null,ErrorCode.INVALID_CUSTOM_GRAPH,"clientKey 重复");ids.addObject().put("clientKey",input.path("clientKey").asText()).put("targetKind","ELEMENT").put("id",e.path("id").asText());
        }
        var input=body.path("customRelations").get(0);String code=input.path("relationCode").asText();var doc=data.read("taskDocument",session.path("taskDocumentId").asText());String guide=data.read("task",doc.path("taskId").asText()).path("guideVersionId").asText();var definitions=data.find("relationDefinition","guideVersionId",guide).stream().filter(d->code.equals(d.path("code").asText())).toList();require(definitions.size()==1&&definitions.get(0).path("directed").asBoolean()==RelationRules.isDirected(code),ErrorCode.SOURCE_INCOMPLETE,"指南关系定义缺失");
        var r=mapper.createObjectNode();VersionJson.stamp(r,actor.id(),VersionJson.now());r.put("ownerType","ARBITER_CUSTOM").put("ownerId",annotation.path("id").asText()).put("relationCode",code).put("directed",RelationRules.isDirected(code)).put("status","ACTIVE");r.set("relationDefinitionId",definitions.get(0).get("id"));var sources=new HashSet<String>();for(var source:input.path("sourceRelationIds"))require(sources.add(source.asText())&&AdjudicationService.contains(item.path("candidateRelationIds"),source.asText()),ErrorCode.INVALID_CUSTOM_GRAPH,"关系来源越出当前项或重复");require(!sources.isEmpty(),ErrorCode.INVALID_CUSTOM_GRAPH,"关系来源不能为空");r.set("sourceRelationIds",input.get("sourceRelationIds"));require(!clientIds.containsKey(input.path("clientKey").asText()),ErrorCode.INVALID_CUSTOM_GRAPH,"clientKey 重复");relations.add(r);ids.addObject().put("clientKey",input.path("clientKey").asText()).put("targetKind","RELATION").put("id",r.path("id").asText());
        var used=new HashSet<String>();for(var part:input.path("participants")){String target=clientIds.get(part.path("targetClientKey").asText());require(target!=null&&"ELEMENT".equals(part.path("targetKind").asText()),ErrorCode.INVALID_CUSTOM_GRAPH,"参与项须引用本请求的统一命题端点；嵌套自定义待复合映射模型");used.add(target);var p=mapper.createObjectNode();VersionJson.stamp(p,actor.id(),VersionJson.now());p.put("relationId",r.path("id").asText()).put("ordinal",part.path("ordinal").asInt()).put("role",part.path("role").asText()).put("targetKind","ELEMENT").put("targetId",target);participants.add(p);}
        require(used.equals(new HashSet<>(clientIds.values())),ErrorCode.INVALID_CUSTOM_GRAPH,"不能夹带未参与关系的命题副本");var es=new ArrayList<ObjectNode>();elements.forEach(e->es.add((ObjectNode)e));var ps=new ArrayList<ObjectNode>();participants.forEach(p->ps.add((ObjectNode)p));var check=engine.inspect(annotation.path("id").asText(),es,List.of(r),ps);require(check.issues().stream().noneMatch(i->"ERROR".equals(i.level())),ErrorCode.INVALID_CUSTOM_GRAPH,"自定义关系结构无效："+check.issues());var normalized=engine.normalize(code,es,StructureEngine.inputs(ps));for(int index=0;index<ps.size();index++){var part=normalized.get(index);ps.get(index).put("ordinal",part.ordinal()).put("role",part.role()).put("targetKind",part.targetKind()).put("targetId",part.targetId());}
        var elementIds=annotation.putArray("elementIds");elements.forEach(e->elementIds.add(e.path("id").asText()));annotation.putArray("relationIds").add(r.path("id").asText());return response;
    }
}
