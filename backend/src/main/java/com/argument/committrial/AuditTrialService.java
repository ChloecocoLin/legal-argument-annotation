package com.argument.committrial;

import com.argument.common.exception.*;
import com.argument.finalreview.FinalReviewService;
import com.argument.version.VersionJson;
import com.argument.version.VersionService;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.argument.adjudication.AdjudicationService.require;

@Service @Profile("(local | test) & !prod")
@Transactional(readOnly=true)
public class AuditTrialService {
    private final CommitTrialRepository store;private final AnnotationDependencies data;private final VersionService access;private final FinalReviewService finals;private final ObjectMapper mapper;
    public AuditTrialService(CommitTrialRepository store,AnnotationDependencies data,VersionService access,FinalReviewService finals,ObjectMapper mapper) {this.store=store;this.data=data;this.access=access;this.finals=finals;this.mapper=mapper;}
    private void role(Actor actor) {require(actor.role("ADMIN") && actor.permission("AUDIT_VIEW") || actor.role("TASK_CREATOR") && actor.permission("TASK_VIEW") || actor.role("ARBITER") && actor.permission("FINAL_REVIEW"),ErrorCode.FORBIDDEN,"没有审计查询权限");}
    private boolean visible(Actor actor,ObjectNode e) {
        if(actor.role("ADMIN") && actor.permission("AUDIT_VIEW"))return true;
        if(actor.role("TASK_CREATOR") && actor.permission("TASK_VIEW") && actor.id().equals(data.read("task",e.path("taskId").asText()).path("ownerUserId").asText()))return true;
        return actor.role("ARBITER") && actor.permission("FINAL_REVIEW") && access.hasAssignment(actor,e.path("taskDocumentId").asText(),"ARBITER");
    }
    public ObjectNode list(Actor actor,Map<String,String> filters,int page,int size) {
        role(actor);require(Set.of("objectType","objectId","actorId","from","to","page","size").containsAll(filters.keySet()),ErrorCode.VALIDATION_ERROR,"不支持的审计查询字段");
        for(String field:List.of("objectId","actorId"))if(filters.containsKey(field))VersionJson.uuid(filters.get(field));
        if(filters.containsKey("objectType"))require(Set.of("FinalAnnotationVersion","Task","TaskDocument","AdjudicationSession").contains(filters.get("objectType")),ErrorCode.VALIDATION_ERROR,"不支持的审计对象类型");
        Instant from=time(filters.get("from")),to=time(filters.get("to"));require(from==null || to==null || !from.isAfter(to),ErrorCode.VALIDATION_ERROR,"开始时间不能晚于结束时间");
        var rows=store.events().stream().filter(e->visible(actor,e))
                .filter(e->!filters.containsKey("objectType") || filters.get("objectType").equals(e.path("objectType").asText()))
                .filter(e->!filters.containsKey("objectId") || filters.get("objectId").equals(e.path("objectId").asText()))
                .filter(e->!filters.containsKey("actorId") || filters.get("actorId").equals(e.path("actorUserId").asText()))
                .filter(e->from==null || !Instant.parse(e.path("occurredAt").asText()).isBefore(from))
                .filter(e->to==null || !Instant.parse(e.path("occurredAt").asText()).isAfter(to)).toList();
        var result=mapper.createObjectNode().put("total",rows.size());var items=result.putArray("items");rows.stream().skip((long)(page-1)*size).limit(size).forEach(items::add);return result;
    }
    public ObjectNode provenance(Actor actor,String type,String id) {
        require("FinalAnnotationVersion".equals(type),ErrorCode.VALIDATION_ERROR,"本批来源查询仅支持最终版本；其他对象待团队确认");
        // Reuse the original content authorization. ADMIN/creator audit access does not grant source access.
        var snapshot=finals.read(actor,id);var objects=new TreeSet<String>();var decisions=new TreeSet<String>();
        snapshot.path("version").path("provenance").forEach(p->{p.path("sourceObjectIds").forEach(v->objects.add(v.asText()));if(p.hasNonNull("decisionId"))decisions.add(p.path("decisionId").asText());});
        var result=mapper.createObjectNode();var source=result.putArray("sourceObjectIds");objects.forEach(source::add);var ds=result.putArray("decisionIds");decisions.forEach(ds::add);return result;
    }
    public ObjectNode history(Actor actor,String type,String id,int page,int size) {
        require("FinalAnnotationVersion".equals(type),ErrorCode.VALIDATION_ERROR,"本批历史查询仅支持最终版本；其他对象待团队确认");
        var workspace=finals.workspace(actor,id);var result=mapper.createObjectNode().put("total",workspace.path("history").size());var items=result.putArray("items");int i=0;
        for(var h:workspace.path("history")){if(i++<(long)(page-1)*size || items.size()>=size)continue;items.addObject().set("recordVersion",h.path("after").path("recordVersion"));((ObjectNode)items.get(items.size()-1)).set("at",h.path("createdAt"));}return result;
    }
    private Instant time(String value) {
        if(value==null)return null;
        try {require(value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"),ErrorCode.VALIDATION_ERROR,"查询时间必须为 UTC 秒精度格式");return Instant.parse(value);}
        catch(java.time.format.DateTimeParseException ex){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"查询时间无效");}
    }
}
