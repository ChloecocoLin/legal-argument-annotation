package com.argument.integration;

import com.argument.guide.mapper.GuideVersionMapper;
import com.argument.version.port.AnnotationDependencies;
import org.springframework.stereotype.Component;
import java.util.*;

/** Read-only bridge until the guide owner completes shared storage migration. SQL takes precedence. */
@Component
public class TaskResourceCatalog {
    private final GuideVersionMapper guides;
    private final AnnotationDependencies data;
    public TaskResourceCatalog(GuideVersionMapper guides,AnnotationDependencies data){this.guides=guides;this.data=data;}
    public List<Map<String,Object>> publishedGuides(){
        var result=new TreeMap<String,Map<String,Object>>();
        for(var guide:data.find("guideVersion",null,null))if(validId(guide.path("id").asText())&&"PUBLISHED".equals(guide.path("status").asText()))result.put(guide.path("id").asText(),Map.of("id",guide.path("id").asText(),"versionCode",guide.path("versionCode").asText()));
        for(var guide:guides.selectList(null)){result.remove(guide.getId());if(validId(guide.getId())&&"PUBLISHED".equals(guide.getStatus()))result.put(guide.getId(),Map.of("id",guide.getId(),"versionCode",guide.getVersionCode()));}
        return List.copyOf(result.values());
    }
    public boolean isPublished(String id){return publishedGuides().stream().anyMatch(g->id.equals(g.get("id")));}
    private boolean validId(String id){try{com.argument.version.VersionJson.uuid(id);return true;}catch(com.argument.common.exception.BusinessException ex){return false;}}
}
