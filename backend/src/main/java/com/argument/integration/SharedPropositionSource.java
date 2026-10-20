package com.argument.integration;
import com.argument.common.auth.AuthContext;
import com.argument.common.exception.*;
import com.argument.proposition.*;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Implements C's existing read port against actual D drafts and frozen versions. */
@Component
public class SharedPropositionSource implements PropositionSource {
 private final SharedSnapshotReader reader;private final AnnotationDependencies data;private final UnifiedVersionIdentity identity;private final JdbcTemplate jdbc;
 public SharedPropositionSource(SharedSnapshotReader reader,AnnotationDependencies data,UnifiedVersionIdentity identity,JdbcTemplate jdbc){this.reader=reader;this.data=data;this.identity=identity;this.jdbc=jdbc;}
 private com.argument.version.port.VersionIdentity.Actor actor(){return identity.actor(AuthContext.require().userId());}
 private String type(String id){if(!data.find("draft","id",id).isEmpty())return "DRAFT";var owners=jdbc.queryForList("SELECT DISTINCT owner_type FROM snapshot_object_owners WHERE owner_id=?",String.class,id);if(owners.isEmpty())throw new BusinessException(ErrorCode.NOT_FOUND,"命题快照不存在");return owners.get(0);}
 public List<PropositionRef> findBySnapshot(String id){var result=new ArrayList<PropositionRef>();var snapshot=reader.read(actor(),type(id),id);var rows=new ArrayList<JsonNode>();snapshot.path("elements").forEach(rows::add);rows.sort(Comparator.comparingInt(n->n.path("startOffset").asInt()));rows.forEach(e->result.add(new PropositionRef(e.path("id").asText(),id,e.path("labelCode").asText(),e.hasNonNull("displayNo")?e.path("displayNo").asText():null,e.path("isProposition").asBoolean(),e.path("text").asText())));return List.copyOf(result);}
 public PropositionRef find(String id){String owner=null;try{var e=data.read("element",id);owner=e.path("ownerId").asText();}catch(BusinessException e){if(e.getErrorCode()!=ErrorCode.NOT_FOUND)throw e;}if(owner==null){var ids=jdbc.queryForList("SELECT owner_id FROM snapshot_object_owners WHERE object_id=? AND object_kind='elements'",String.class,id);if(ids.isEmpty())return null;owner=ids.get(0);}return findBySnapshot(owner).stream().filter(e->id.equals(e.id())).findFirst().orElse(null);}
 public Map<String,PropositionRef> mapByIds(Collection<String> ids){var result=new LinkedHashMap<String,PropositionRef>();if(ids!=null)for(String id:ids){var ref=find(id);if(ref!=null)result.put(id,ref);}return result;}
}
