package com.argument.automation;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
/** Claim and publication use separate transactions; model work holds no database locks. */
@Service @Profile("(local | test) & !prod")
public class AutomationWorker {
    private final AutomationTaskService service;private final AnnotationDependencies data;private final AutomationModelAdapter adapter;
    public AutomationWorker(AutomationTaskService service,AnnotationDependencies data,AutomationModelAdapter adapter){this.service=service;this.data=data;this.adapter=adapter;}
    public boolean processOne(){for(var n:data.find("automationTask",null,null))if("RUNNING".equals(n.path("status").asText()))service.expire(n.path("id").asText());for(var n:data.find("automationTask",null,null))if("QUEUED".equals(n.path("status").asText())&&"TEST_ADAPTER".equals(n.path("executionMode").asText())){var job=service.claim(n.path("id").asText());if(job==null)return true;ArrayNode output;try{output=adapter.generate(job.input().deepCopy());}catch(RuntimeException e){service.finish(job,null,true);return true;}service.finish(job,output,false);return true;}return false;}
}
