package com.argument.exporttrial;

import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.argument.version.port.VersionIdentity.Actor;
import java.util.Set;

@Service @Profile("(local | test) & !prod")
public class ExportTrialWorker {
    private final ExportTrialRepository store;private final TrialFileStore files;private final StructuredExportWriter writer;private final ExportTrialService service;private final ObjectMapper mapper;
    public ExportTrialWorker(ExportTrialRepository store,TrialFileStore files,StructuredExportWriter writer,ExportTrialService service,ObjectMapper mapper){this.store=store;this.files=files;this.writer=writer;this.service=service;this.mapper=mapper;}
    @Transactional public boolean processOne(){String id=store.queued();if(id==null)return false;var n=store.export(id,true);if(!"QUEUED".equals(n.path("status").asText()))return false;String actor=n.path("requestedById").asText();var systemActor=new Actor(actor,"导出处理器",Set.of(),Set.of());String now=VersionJson.now();byte[] bytes;
        try{bytes=writer.write(store.input(id),n.path("format").asText());if(bytes.length>30_000_000)throw new IllegalStateException("Export size exceeds limit");}catch(RuntimeException ex){n.put("status","FAILED").put("failureCode","EXPORT_SERIALIZATION_FAILED").put("failureMessage","生成失败，内容未被截断或覆盖；请核对源数据后新建导出").put("completedAt",now);n.put("recordVersion",n.path("recordVersion").asInt()+1).put("updatedAt",now);store.saveExport(n);service.event(systemActor,"EX_WORKER",id,n,"FAILED",now);return true;}
        // File publication/DB/audit failures roll back and leave QUEUED for a real retry; no false COMPLETED.
        var file=mapper.createObjectNode();VersionJson.stamp(file,actor,now);String format=n.path("format").asText();file.put("fileName","legal-annotation-"+id+"."+format.toLowerCase()).put("mimeType",format.equals("JSON")?"application/json":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").put("sizeBytes",bytes.length).put("sha256",TrialFileStore.sha256(bytes)).put("storageKey",files.publish(file.path("id").asText(),bytes)).put("status","AVAILABLE");var generated=mapper.createObjectNode().put("purpose","FINAL_ANNOTATION_EXPORT").put("exportId",id).put("fileId",file.path("id").asText()).put("contentDigest",file.path("sha256").asText()).putNull("rendererId").putNull("rendererVersion").putNull("expiresAt");generated.set("sourceFinalVersionIds",n.path("finalVersionIds").deepCopy());var graphIds=generated.putArray("sourceGraphIds");store.input(id).path("graphs").forEach(g->graphIds.add(g.path("id").asText()));generated.putArray("componentManifest");store.file(file,id,generated);n.put("status","COMPLETED").put("completedAt",now).put("fileId",file.path("id").asText()).put("recordVersion",n.path("recordVersion").asInt()+1).put("updatedAt",now);store.saveExport(n);service.event(systemActor,"EX_WORKER",id,n,"COMPLETED",now);return true;
    }
}
