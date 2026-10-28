package com.argument.membera.store;
import com.argument.integration.PersistentRecordMap;
import com.argument.finalreview.FinalReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** A's service facade, backed by the same durable facts as D. No seed final versions or independent caches. */
@Component
public class WorkflowStore {
 public final Map<String,Map<String,Object>> users,roles,documents,textVersions,scopes,tasks,taskDocuments,taskMembers,assignments,submissions,checkpoints,finalVersions;
 public WorkflowStore(JdbcTemplate jdbc,ObjectMapper mapper,FinalReviewRepository finals){
  users=new PersistentRecordMap(jdbc,mapper,"user");roles=new PersistentRecordMap(jdbc,mapper,"role");documents=new PersistentRecordMap(jdbc,mapper,"document");textVersions=new PersistentRecordMap(jdbc,mapper,"text");scopes=new PersistentRecordMap(jdbc,mapper,"scope");tasks=new PersistentRecordMap(jdbc,mapper,"task");taskDocuments=new PersistentRecordMap(jdbc,mapper,"taskDocument");taskMembers=new PersistentRecordMap(jdbc,mapper,"member");assignments=new PersistentRecordMap(jdbc,mapper,"assignment");submissions=new PersistentRecordMap(jdbc,mapper,"segmentationSubmission");checkpoints=new PersistentRecordMap(jdbc,mapper,"checkpoint");
  finalVersions=new AbstractMap<>(){
   public Map<String,Object> get(Object id){try{return mapper.convertValue(finals.snapshot((String)id).path("version"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(com.argument.common.exception.BusinessException e){if(e.getErrorCode()==com.argument.common.exception.ErrorCode.NOT_FOUND)return null;throw e;}}
   public Set<Entry<String,Map<String,Object>>> entrySet(){throw new UnsupportedOperationException("Final versions are read by immutable identity");}
  };
 }
}
