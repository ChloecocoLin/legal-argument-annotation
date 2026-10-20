package com.argument.integration;

import com.argument.common.auth.AuthContext;
import com.argument.common.exception.*;
import com.argument.common.response.ApiResponse;
import com.argument.membera.service.*;
import com.argument.membera.store.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Explicit internal read projections; frozen public response schemas are unchanged. */
@RestController
@RequestMapping("/api/v1/integration")
public class TaskIntegrationController {
    private final TaskService tasks;private final TaskResourceCatalog catalog;private final WorkflowStore store;private final UnifiedVersionIdentity identity;
    public TaskIntegrationController(TaskService tasks,TaskResourceCatalog catalog,WorkflowStore store,UnifiedVersionIdentity identity){this.tasks=tasks;this.catalog=catalog;this.store=store;this.identity=identity;}
    @GetMapping("/tasks/{taskId}/setup")
    public ApiResponse<Map<String,Object>> setup(@PathVariable String taskId){return ApiResponse.ok(tasks.setup(taskId));}
    @GetMapping("/task-options")
    public ApiResponse<Map<String,Object>> options(){
        var user=AuthContext.requireAnyRole("TASK_CREATOR");
        if(!identity.actor(user.userId()).permission("TASK_CREATE")||!identity.actor(user.userId()).permission("DOCUMENT_VIEW"))throw new BusinessException(ErrorCode.FORBIDDEN);
        var documents=new ArrayList<Map<String,Object>>();
        for(var scope:store.scopes.values()){
            if(!"CONFIRMED".equals(scope.get("status")))continue;
            var text=store.textVersions.get(scope.get("documentTextVersionId"));
            if(text==null||!"PUBLISHED".equals(text.get("status")))continue;
            var doc=store.documents.get(text.get("documentId"));if(doc==null)continue;
            var item=new LinkedHashMap<String,Object>();item.put("documentId",doc.get("id"));item.put("documentTextVersionId",text.get("id"));item.put("annotationScopeId",scope.get("id"));item.put("label",Objects.toString(doc.get("title"),Objects.toString(doc.get("caseNumber"),"裁判文书")));documents.add(item);
        }
        return ApiResponse.ok(Map.of("guides",catalog.publishedGuides(),"documents",documents));
    }
}
