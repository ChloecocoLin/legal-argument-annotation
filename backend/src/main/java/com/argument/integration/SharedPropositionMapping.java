package com.argument.integration;
import com.argument.adjudication.AdjudicationService;
import com.argument.common.auth.AuthContext;
import com.argument.common.exception.*;
import com.argument.proposition.PropositionMappingSource;
import com.argument.version.port.AnnotationDependencies;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class SharedPropositionMapping implements PropositionMappingSource {
    private final AnnotationDependencies data;private final AdjudicationService adjud;private final UnifiedVersionIdentity identity;
    public SharedPropositionMapping(AnnotationDependencies data,AdjudicationService adjud,UnifiedVersionIdentity identity){this.data=data;this.adjud=adjud;this.identity=identity;}
    public Map<String,String> mappingTo(String source,String unified){var snapshot=data.read("unifiedSnapshot",unified);var session=adjud.read(identity.actor(AuthContext.require().userId()),snapshot.path("sessionId").asText());if(!AdjudicationService.contains(session.path("sourceVersionIds"),source))throw new BusinessException(ErrorCode.FORBIDDEN,"源版本不属于统一命题来源");var result=new LinkedHashMap<String,String>();var mapping=data.read("adjudicationMapping",session.path("id").asText());if(!unified.equals(mapping.path("unifiedSnapshotId").asText()))throw new BusinessException(ErrorCode.SOURCE_INCOMPLETE,"统一映射基准不一致");for(var s:adjud.snapshots(session))if(source.equals(s.path("version").path("id").asText()))for(var e:s.path("elements")){String id=e.path("id").asText();if(mapping.path("elementMap").has(id))result.put(id,mapping.path("elementMap").path(id).asText());}return Map.copyOf(result);}
}
