package com.argument.automation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
/** Explicit deterministic test fixture, no learned model and no network call. Never enabled in prod. */
@Component @Profile("(local | test) & !prod")
public class TestAutomationModelAdapter implements AutomationModelAdapter {
    public static final String ID="member-d-test-adapter",VERSION="1.0";
    private final ObjectMapper mapper;
    public TestAutomationModelAdapter(ObjectMapper mapper){this.mapper=mapper;}
    public ObjectNode descriptor(){return mapper.createObjectNode().put("modelId",ID).put("modelVersion",VERSION).put("executionMode","TEST_ADAPTER").put("providerVersion","deterministic-first-sentence-v1").put("available",true).put("displayName","本地测试适配器").put("description","非真实模型：取范围内首句，使用指南首个启用命题类型，置信度固定为 0；仅验证任务和候选流程").put("capability","ELEMENT_ONLY");}
    public ArrayNode generate(ObjectNode input){String text=input.path("text").path("normalizedText").asText();int start=input.path("scope").path("startOffset").asInt(),end=input.path("scope").path("endOffset").asInt();for(int i=start;i<end;i++)if("。！？\n".indexOf(text.charAt(i))>=0){end=i+1;break;}String label=null;for(var n:input.path("labels"))if(n.path("enabled").asBoolean(true)&&!java.util.Set.of("DISABLED","DELETED").contains(n.path("status").asText())&&"PROPOSITION".equals(n.path("category").asText())){label=n.path("code").asText();break;}if(label==null)throw new IllegalStateException("No active proposition label");var output=mapper.createArrayNode();output.addObject().put("suggestionKind","ELEMENT").put("startOffset",start).put("endOffset",end).put("candidateLabelCode",label).put("confidence",0.0).putArray("participantHints");return output;}
}
