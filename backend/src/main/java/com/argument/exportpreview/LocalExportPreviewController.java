package com.argument.exportpreview;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.finalreview.FinalReviewRepository;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Explicit read-only development projection. This does not implement EX01–EX05 or create files/records. */
@RestController
@Profile("(local | test) & !prod")
public class LocalExportPreviewController {
    private final AnnotationDependencies data;
    private final FinalReviewRepository finals;
    private final VersionIdentity identity;
    private final ObjectMapper mapper;
    public LocalExportPreviewController(AnnotationDependencies data,FinalReviewRepository finals,VersionIdentity identity,ObjectMapper mapper) {
        this.data=data;this.finals=finals;this.identity=identity;this.mapper=mapper;
    }
    @GetMapping("/api/local/member-d/export-preview")
    @Transactional(readOnly=true)
    public ApiResponse<ObjectNode> preview(@RequestHeader(value="Authorization",required=false) String auth) {
        var actor=identity.authenticate(auth);
        if (!(actor.role("ADMIN") || actor.role("TASK_CREATOR")) || !actor.permission("EXPORT_CREATE"))
            throw new BusinessException(ErrorCode.FORBIDDEN,"仅管理员或有导出权限的任务创建者可预览");
        var result=mapper.createObjectNode().put("mode","LOCAL_PREVIEW").put("exportAvailable",false)
                .put("reason","入库、导出和下载模型待团队确认，本页只读取已入库版本，不创建导出任务或文件");
        var items=result.putArray("items");
        for(var task:data.find("task",null,null)) {
            if (!actor.role("ADMIN") && !actor.id().equals(task.path("ownerUserId").asText())) continue;
            for(var doc:data.find("taskDocument","taskId",task.path("id").asText())) {
                // Authorization and state are checked before fetching any frozen content.
                for(var version:finals.versions(doc.path("id").asText())) {
                    if (!"IN_DATABASE".equals(version.path("status").asText())) continue;
                    var source=data.read("document",doc.path("documentId").asText());
                    var snapshot=finals.snapshot(version.path("id").asText());
                    var item=items.addObject();item.set("id",version.get("id"));item.set("versionNumber",version.get("versionNumber"));
                    item.set("guideVersionId",version.get("guideVersionId"));item.put("guideVersionCode",data.read("guideVersion",version.path("guideVersionId").asText()).path("versionCode").asText());
                    item.set("title",source.get("title"));item.set("caseNumber",source.path("caseNumber"));item.set("sourceType",source.get("sourceType"));
                    item.put("elementCount",snapshot.path("elements").size());item.put("relationCount",snapshot.path("relations").size());
                    item.put("status","IN_DATABASE");var preview=item.putArray("preview");var labels=new HashMap<String,String>();
                    data.find("label","guideVersionId",version.path("guideVersionId").asText()).forEach(l->labels.put(l.path("id").asText(),l.path("code").asText()));
                    snapshot.path("elements").forEach(e->{if(preview.size()<5)preview.addObject().put("displayNo",e.path("displayNo").asText("—")).put("text",e.path("text").asText()).put("type",labels.getOrDefault(e.path("labelDefinitionId").asText(),"未识别标签")).set("id",e.path("id"));});
                }
            }
        }
        return ApiResponse.ok(result);
    }
}
