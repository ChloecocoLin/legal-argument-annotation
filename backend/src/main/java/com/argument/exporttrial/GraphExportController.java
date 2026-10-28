package com.argument.exporttrial;
import com.argument.common.exception.ErrorCode;
import com.argument.common.response.ApiResponse;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.argument.adjudication.AdjudicationService.require;
/** Explicit trial endpoint; the public EX02 format contract remains JSON/XLSX. */
@RestController @Profile("(local | test) & !prod") @RequestMapping("/api/local/member-d/graph-exports")
public class GraphExportController {
    private final ExportTrialService service;private final VersionIdentity identity;
    public GraphExportController(ExportTrialService service,VersionIdentity identity){this.service=service;this.identity=identity;}
    @PostMapping public ApiResponse<ObjectNode> create(@RequestHeader(value="Authorization",required=false)String auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestBody ObjectNode body){
        var actor=identity.authenticate(auth);var fields=new HashSet<String>();body.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(Set.of("finalVersionIds","format","include")),ErrorCode.VALIDATION_ERROR,"图示仅接受冻结版本、格式及导出选项，不能上传图形内容");
        return ApiResponse.ok(service.createGraph(actor,key,body));
    }
}
