package com.argument.committrial;

import com.argument.adjudication.*;
import com.argument.common.exception.*;
import com.argument.finalreview.*;
import com.argument.finalreview.local.LocalFinalReviewDependencies;
import com.argument.version.*;
import com.argument.version.local.*;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CommitAuditTrialWorkflowTest {
    static final String TASK="f102c523-b4ed-4323-81d3-0c564c4141a0",CASE="3cbb140f-0274-4871-8248-194271101a14",DRAFT="f67848e1-a06d-4600-8454-19055e720d2c",DRAFT2="ef21c243-05a2-4837-92b4-d56f3fb77639";
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired VersionService versions;@Autowired VersionRepository versionRepo;@Autowired VersionIdentity identity;
    @Autowired LocalFixtureLoader loader;@Autowired LocalAnnotationDependencies data;@Autowired AdjudicationService adjudication;@Autowired AdjudicationRepository sessions;@Autowired FinalReviewService review;@Autowired FinalReviewRepository finals;@Autowired FinalCommitTrialService commits;
    @SpyBean CommitTrialRepository audit;@SpyBean LocalFinalReviewDependencies deps;
    @BeforeEach void setup() throws Exception {reset(audit,deps);clean();loader.run(null);}
    @AfterEach void cleanup() {reset(audit,deps);clean();}
    void clean(){for(String t:List.of("trial_final_commitments","trial_audit_events","final_review_transitions","final_annotation_versions","adjudication_transitions","adjudication_returned_versions","adjudication_decisions","adjudication_items","adjudication_sessions","annotation_mutation_receipts","snapshot_object_owners","annotation_version_snapshots","annotation_versions","annotation_case_counters","workflow_records"))jdbc.update("DELETE FROM "+t);}
    com.argument.version.port.VersionIdentity.Actor arbiter(){return identity.authenticate("Bearer dev-arbiter-zhao");}
    ObjectNode confirm(){return mapper.createObjectNode().put("confirmed",true);}
    ObjectNode finalDraft() {return finalDraft(CASE,DRAFT,DRAFT2);}
    ObjectNode finalDraft(String caseId,String draft1,String draft2) {
        var a=versions.submit(identity.authenticate("Bearer dev-annotator-chen"),draft1,VersionJson.uuid(),1,true,"");var b=versions.submit(identity.authenticate("Bearer dev-annotator-liu"),draft2,VersionJson.uuid(),1,true,"");var body=mapper.createObjectNode();body.putArray("sourceVersionIds").add(a.path("id").asText()).add(b.path("id").asText());var s=adjudication.create(arbiter(),caseId,VersionJson.uuid(),body);String id=s.path("id").asText();
        for(var item:sessions.items(id,"PROPOSITION")){var cmd=mapper.createObjectNode().put("reason","测试裁定来源");if("AGREEMENT".equals(item.path("conflictType").asText()))cmd.put("action","CONFIRM_AGREEMENT");else{String object=item.path("candidateElementIds").get(0).asText();cmd.put("action","ADOPT_VERSION").put("sourceObjectId",object);for(var source:adjudication.snapshots(s))for(var e:source.path("elements"))if(object.equals(e.path("id").asText()))cmd.put("sourceVersionId",source.path("version").path("id").asText());}adjudication.decideLocalProposition(arbiter(),item.path("id").asText(),VersionJson.uuid(),1,cmd);}
        adjudication.complete(arbiter(),id,VersionJson.uuid(),sessions.session(id,false).path("recordVersion").asInt(),confirm(),"PROPOSITION");
        for(var item:sessions.items(id,"RELATION")){var cmd=mapper.createObjectNode().put("reason","测试关系来源").put("action","ADOPT_VERSION").put("sourceObjectId",item.path("candidateRelationIds").get(0).asText()).put("sourceVersionId",a.path("id").asText());adjudication.decideRelation(arbiter(),item.path("id").asText(),VersionJson.uuid(),1,cmd);}
        adjudication.complete(arbiter(),id,VersionJson.uuid(),sessions.session(id,false).path("recordVersion").asInt(),confirm(),"RELATION");return review.create(arbiter(),id,VersionJson.uuid(),1);
    }
    void evidence(){doAnswer(inv->{ObjectNode s=inv.getArgument(0);return new FinalReviewDependencies.Evidence(true,"PRODUCTION",FinalReviewService.contentDigest(s),s.path("decisionDigest").asText(),s.path("version").path("guideVersionId").asText(),List.of());}).when(deps).assess(any(),any());}
    ObjectNode approved(){var v=finalDraft();String id=v.path("id").asText();review.transition(arbiter(),id,VersionJson.uuid(),1,"FV03",mapper.createObjectNode().put("action","SUBMIT"));evidence();review.transition(arbiter(),id,VersionJson.uuid(),2,"FV05",confirm());return (ObjectNode)review.read(arbiter(),id).get("version");}
    ObjectNode commit(ObjectNode v,String key){return commits.commit(arbiter(),v.path("id").asText(),key,v.path("recordVersion").asInt(),confirm(),VersionJson.uuid());}
    ObjectNode getAs(String token,String path,int status) throws Exception {var r=mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(status);return mapper.readValue(r.getContentAsString(),ObjectNode.class);}
    ObjectNode postAs(String token,ObjectNode v,String key,int expected,ObjectNode body,int status) throws Exception {var r=mvc.perform(post("/api/v1/final-annotation-versions/"+v.path("id").asText()+"/commit").header("Authorization","Bearer "+token).header("Idempotency-Key",key).header("If-Match",expected).contentType("application/json").content(body.toString())).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(status);return mapper.readValue(r.getContentAsString(),ObjectNode.class);}
    int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    @Test void commitAtomicallyLocksAllSingleCaseResourcesAndPreservesContent() throws Exception {
        var v=approved();var before=review.read(arbiter(),v.path("id").asText());var sources=adjudication.snapshots(sessions.session(v.path("sessionId").asText(),false));String digest=FinalReviewService.contentDigest(before);
        var response=postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,confirm(),200);assertThat(response.path("data").fieldNames()).toIterable().containsExactlyInAnyOrder("status","committedAt");
        assertThat(response.path("data").path("status").asText()).isEqualTo("IN_DATABASE");assertThat(data.read("task",TASK).path("status").asText()).isEqualTo("COMPLETED");assertThat(data.read("taskDocument",CASE).path("status").asText()).isEqualTo("COMPLETED");assertThat(sessions.session(v.path("sessionId").asText(),false).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(count("trial_final_commitments")).isEqualTo(1);assertThat(count("trial_audit_events")).isEqualTo(1);var event=audit.events().get(0);assertThat(event.path("requestId")).isEqualTo(response.path("requestId"));assertThat(event.path("contentDigest").asText()).isEqualTo(digest);assertThat(event.path("beforeState").path("finalVersion").path("status").asText()).isEqualTo("APPROVED");assertThat(event.path("afterState").path("finalVersion").path("recordVersion").asInt()).isEqualTo(4);
        assertThat(event.toString()).doesNotContain("snapshot_json","text\":","token","password");assertThat(FinalReviewService.contentDigest(review.read(arbiter(),v.path("id").asText()))).isEqualTo(digest);
        for(var source:sources)assertThat(versionRepo.snapshot(source.path("version").path("id").asText())).isEqualTo(source);
        assertThat(review.workspace(arbiter(),v.path("id").asText()).path("quality").path("mode").asText()).isEqualTo("ARCHIVED");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/member-d-commit-trial-response.json"),response.toString(),java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/member-d-audit-trial-event.json"),event.toString(),java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void retriesReturnFirstResultAndCannotRepeatAuditOrChangeBody() throws Exception {
        var v=approved();String key=VersionJson.uuid();var first=commit(v,key);assertThat(commit(v,key)).isEqualTo(first);assertThat(count("trial_audit_events")).isEqualTo(1);
        assertThat(postAs("dev-arbiter-zhao",v,key,3,mapper.createObjectNode().put("confirmed",false),409).path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(postAs("dev-arbiter-zhao",v,VersionJson.uuid(),4,confirm(),409).path("error").path("code").asText()).isEqualTo("ALREADY_COMMITTED");
        jdbc.update("DELETE FROM annotation_mutation_receipts");assertThatThrownBy(()->commit(v,key)).isInstanceOf(BusinessException.class).hasMessageContaining("已经入库");assertThat(count("trial_audit_events")).isEqualTo(1);
    }
    @Test void auditFailureRollsBackFinalCaseTaskSessionHistoryAndReceipt() {
        var v=approved();String id=v.path("id").asText();int receipts=count("annotation_mutation_receipts"),history=count("final_review_transitions");doThrow(new IllegalStateException("audit write failure")).when(audit).append(any());
        assertThatThrownBy(()->commit(v,VersionJson.uuid())).isInstanceOf(IllegalStateException.class);assertThat(review.read(arbiter(),id).path("version")).isEqualTo(v);assertThat(data.read("taskDocument",CASE).path("status").asText()).isEqualTo("ADJUDICATING");assertThat(data.read("task",TASK).path("status").asText()).isEqualTo("IN_PROGRESS");assertThat(sessions.session(v.path("sessionId").asText(),false).path("status").asText()).isEqualTo("FINAL_REVIEW");assertThat(count("trial_final_commitments")).isZero();assertThat(count("trial_audit_events")).isZero();assertThat(count("annotation_mutation_receipts")).isEqualTo(receipts);assertThat(count("final_review_transitions")).isEqualTo(history);
    }
    @Test void failureAfterAuditInsertAlsoRollsBackTheAuditAndAllowsSafeRetry() {
        var v=approved();String key=VersionJson.uuid();doThrow(new IllegalStateException("receipt ledger failure")).when(audit).commitment(anyString(),anyString(),anyString(),any());assertThatThrownBy(()->commit(v,key)).isInstanceOf(IllegalStateException.class);assertThat(count("trial_audit_events")).isZero();assertThat(count("trial_final_commitments")).isZero();assertThat(review.read(arbiter(),v.path("id").asText()).path("version").path("status").asText()).isEqualTo("APPROVED");reset(audit);commit(v,key);assertThat(count("trial_audit_events")).isEqualTo(1);
    }
    @Test void parallelDifferentCasesCompleteTheTaskOnlyAfterBothCommit() throws Exception {
        var first=approved();var ids=new HashMap<String,String>();var records=mapper.readTree(getClass().getResourceAsStream("/fixtures/member-d-ready-submission.json")).path("records");var kinds=Set.of("taskDocument","assignment","draft","element","relation","participant","graph","layout","checkpoint");
        for(var r:records)if(kinds.contains(r.path("kind").asText()))ids.put(r.path("value").path("id").asText(),VersionJson.uuid());
        for(var r:records)if(kinds.contains(r.path("kind").asText())){String text=r.path("value").toString();for(var e:ids.entrySet())text=text.replace(e.getKey(),e.getValue());var value=mapper.readTree(text);jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES (?,?,?)",r.path("kind").asText(),value.path("id").asText(),text);}
        var next=finalDraft(ids.get(CASE),ids.get(DRAFT),ids.get(DRAFT2));String id=next.path("id").asText();review.transition(arbiter(),id,VersionJson.uuid(),1,"FV03",mapper.createObjectNode().put("action","SUBMIT"));review.transition(arbiter(),id,VersionJson.uuid(),2,"FV05",confirm());var second=(ObjectNode)review.read(arbiter(),id).get("version");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try{var a=pool.submit(()->{start.await();return commit(first,VersionJson.uuid());});var b=pool.submit(()->{start.await();return commit(second,VersionJson.uuid());});start.countDown();assertThat(a.get(15,TimeUnit.SECONDS).path("status").asText()).isEqualTo("IN_DATABASE");assertThat(b.get(15,TimeUnit.SECONDS).path("status").asText()).isEqualTo("IN_DATABASE");}finally{pool.shutdownNow();}
        assertThat(count("trial_final_commitments")).isEqualTo(2);assertThat(count("trial_audit_events")).isEqualTo(2);assertThat(data.read("task",TASK).path("status").asText()).isEqualTo("COMPLETED");
    }
    @Test void strictCommandsAndHistoricalApprovedSnapshotCannotBypassLatestCheck() throws Exception {
        var v=approved();postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,mapper.createObjectNode().put("confirmed",true).put("formalReady",true),400);postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,mapper.createObjectNode().put("confirmed",false),400);
        var copy=review.read(arbiter(),v.path("id").asText()).deepCopy();((ObjectNode)copy.get("version")).put("id",VersionJson.uuid()).put("versionNumber",2).put("status","DRAFT");var replacements=new java.util.HashMap<String,String>();replacements.put(v.path("id").asText(),copy.path("version").path("id").asText());for(String collection:java.util.List.of("elements","relations","participants"))for(var object:copy.path(collection))replacements.put(object.path("id").asText(),VersionJson.uuid());replacements.put(copy.path("graph").path("id").asText(),VersionJson.uuid());String copiedJson=copy.toString();for(var replacement:replacements.entrySet())copiedJson=copiedJson.replace(replacement.getKey(),replacement.getValue());finals.insert(VersionJson.parse(mapper,copiedJson));assertThat(postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,confirm(),423).path("error").path("code").asText()).isEqualTo("LOCKED_RESOURCE");assertThat(count("trial_audit_events")).isZero();
    }
    @Test void fileDatabaseRetainsAuditCommitmentAndSnapshotAfterCloseAndReopen(@TempDir Path directory) {
        var v=approved();commit(v,VersionJson.uuid());var snapshot=review.read(arbiter(),v.path("id").asText());var session=sessions.session(v.path("sessionId").asText(),false);var event=audit.events().get(0);
        String url="jdbc:h2:file:"+directory.resolve("commit-persistence").toAbsolutePath().toString().replace('\\','/')+";MODE=MySQL";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2","classpath:db/migration/common","classpath:db/migration/trial").load().migrate();
        var fileJdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));fileJdbc.update("INSERT INTO adjudication_sessions(id,task_document_id,status,payload_json) VALUES (?,?,?,?)",session.path("id").asText(),CASE,"COMPLETED",session.toString());new FinalReviewRepository(fileJdbc,mapper).insert(snapshot);var fileAudit=new CommitTrialRepository(fileJdbc,mapper);fileAudit.append(event);fileAudit.commitment(CASE,v.path("id").asText(),event.path("id").asText(),mapper.createObjectNode().put("contentDigest",FinalReviewService.contentDigest(snapshot)));fileJdbc.execute("SHUTDOWN");
        var reopened=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));assertThat(new CommitTrialRepository(reopened,mapper).events()).containsExactly(event);assertThat(new CommitTrialRepository(reopened,mapper).committed(CASE)).isTrue();assertThat(new FinalReviewRepository(reopened,mapper).snapshot(v.path("id").asText())).isEqualTo(snapshot);reopened.execute("SHUTDOWN");
    }
    @Test void concurrencyProducesExactlyOneCommitAndOneAudit() throws Exception {
        var v=approved();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);try{var work=new ArrayList<Future<String>>();for(int i=0;i<2;i++)work.add(pool.submit(()->{start.await();try{commit(v,VersionJson.uuid());return "ok";}catch(BusinessException e){return e.getErrorCode().getCode();}}));start.countDown();var results=new ArrayList<String>();for(var f:work)results.add(f.get(15,TimeUnit.SECONDS));assertThat(results).containsExactlyInAnyOrder("ok","ALREADY_COMMITTED");assertThat(count("trial_final_commitments")).isEqualTo(1);assertThat(count("trial_audit_events")).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @Test void firstCaseCommitDoesNotCompleteTaskWhileAnotherCaseIsOpen() {
        var v=approved();var extra=data.read("taskDocument",CASE).deepCopy().put("id",VersionJson.uuid()).put("status","ANNOTATING");jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES ('taskDocument',?,?)",extra.path("id").asText(),extra.toString());commit(v,VersionJson.uuid());assertThat(data.read("task",TASK).path("status").asText()).isEqualTo("IN_PROGRESS");assertThat(data.read("taskDocument",extra.path("id").asText()).path("status").asText()).isEqualTo("ANNOTATING");assertThat(data.read("taskDocument",CASE).path("status").asText()).isEqualTo("COMPLETED");
    }
    @Test void localPreviewAndStaleOrUnapprovedVersionsCannotCommit() throws Exception {
        var draft=finalDraft();assertThat(postAs("dev-arbiter-zhao",draft,VersionJson.uuid(),1,confirm(),409).path("error").path("code").asText()).isEqualTo("INVALID_FINAL_STATE");review.transition(arbiter(),draft.path("id").asText(),VersionJson.uuid(),1,"FV03",mapper.createObjectNode().put("action","SUBMIT"));evidence();review.transition(arbiter(),draft.path("id").asText(),VersionJson.uuid(),2,"FV05",confirm());reset(deps);
        assertThat(postAs("dev-arbiter-zhao",draft,VersionJson.uuid(),3,confirm(),409).path("error").path("code").asText()).isEqualTo("SOURCE_INCOMPLETE");assertThat(postAs("dev-arbiter-zhao",draft,VersionJson.uuid(),2,confirm(),409).path("error").path("code").asText()).isEqualTo("VERSION_CONFLICT");assertThat(count("trial_audit_events")).isZero();
    }
    @Test void authorityIsRecheckedOnRetryAndStrictBodiesCannotOverrideReadiness() throws Exception {
        var v=approved();String key=VersionJson.uuid();commit(v,key);for(String token:List.of("dev-admin-wang","dev-creator-li","dev-annotator-chen","dev-expert-qian"))postAs(token,v,VersionJson.uuid(),4,confirm(),403);
        var assignment=data.find("assignment","taskDocumentId",CASE).stream().filter(a->"ARBITER".equals(a.path("assignmentRole").asText())).findFirst().orElseThrow();assignment.put("status","DISABLED");data.replace("assignment",assignment);postAs("dev-arbiter-zhao",v,key,3,confirm(),403);
        assertThat(count("trial_audit_events")).isEqualTo(1);
    }
    @Test void auditQueriesRespectScopeAndDoNotGrantContentAccess() throws Exception {
        var v=approved();commit(v,VersionJson.uuid());String id=v.path("id").asText();var admin=getAs("dev-admin-wang","/api/v1/audit-events?objectId="+id+"&size=1",200);assertThat(admin.path("meta").path("total").asInt()).isEqualTo(1);assertThat(admin.path("data").path("items").size()).isEqualTo(1);assertThat(getAs("dev-creator-li","/api/v1/audit-events",200).path("data").path("total").asInt()).isEqualTo(1);
        getAs("dev-admin-wang","/api/v1/provenance/FinalAnnotationVersion/"+id,403);getAs("dev-creator-li","/api/v1/provenance/FinalAnnotationVersion/"+id,403);getAs("dev-annotator-chen","/api/v1/audit-events",403);getAs("dev-expert-qian","/api/v1/audit-events",403);
        var task=data.read("task",TASK);task.put("ownerUserId",VersionJson.uuid());data.replace("task",task);assertThat(getAs("dev-creator-li","/api/v1/audit-events",200).path("meta").path("total").asInt()).isZero();assertThat(count("trial_audit_events")).isEqualTo(1);
    }
    @Test void provenanceAndHistoryReadsAreStableReadOnlyAndPaginated() throws Exception {
        var v=approved();commit(v,VersionJson.uuid());String id=v.path("id").asText();var chain=getAs("dev-arbiter-zhao","/api/v1/provenance/FinalAnnotationVersion/"+id,200).path("data");assertThat(chain.path("sourceObjectIds").size()).isGreaterThan(0);assertThat(chain.path("decisionIds").size()).isEqualTo(3);
        var history=getAs("dev-arbiter-zhao","/api/v1/history/FinalAnnotationVersion/"+id+"?page=2&size=2",200);assertThat(history.path("meta").path("total").asInt()).isEqualTo(4);assertThat(history.path("data").path("items").get(1).path("recordVersion").asInt()).isEqualTo(4);assertThat(history.path("data").fieldNames()).toIterable().containsExactly("items");
        getAs("dev-arbiter-zhao","/api/v1/provenance/Task/"+TASK,400);getAs("dev-admin-wang","/api/v1/audit-events?from=tomorrow",400);getAs("dev-admin-wang","/api/v1/audit-events?size=101",400);getAs("dev-admin-wang","/api/v1/audit-events?from=2026-10-16T00:00:00Z&to=2026-10-15T00:00:00Z",400);assertThat(count("trial_audit_events")).isEqualTo(1);
    }
    @Test void expertRequirementAndChangedSnapshotStillBlockCertifiedCommit() throws Exception {
        var v=approved();var task=data.read("task",TASK);task.put("expertConfirmationRequired",true);data.replace("task",task);assertThat(postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,confirm(),409).path("error").path("code").asText()).isEqualTo("SOURCE_INCOMPLETE");task.put("expertConfirmationRequired",false);data.replace("task",task);var corrupted=review.read(arbiter(),v.path("id").asText());((ObjectNode)corrupted.path("elements").get(0)).put("text","tampered");jdbc.update("UPDATE final_annotation_versions SET snapshot_json=? WHERE id=?",corrupted.toString(),v.path("id").asText());postAs("dev-arbiter-zhao",v,VersionJson.uuid(),3,confirm(),409);assertThat(count("trial_final_commitments")).isZero();
    }
    @Test void committedResultCannotBeSubmittedWithdrawnReturnedOrRebuilt() throws Exception {
        var v=approved();commit(v,VersionJson.uuid());assertThatThrownBy(()->review.create(arbiter(),v.path("sessionId").asText(),VersionJson.uuid(),2)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->review.transition(arbiter(),v.path("id").asText(),VersionJson.uuid(),4,"FV04",mapper.createObjectNode().put("reason","change"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->versions.submit(identity.authenticate("Bearer dev-annotator-chen"),DRAFT,VersionJson.uuid(),2,true,"")).isInstanceOf(BusinessException.class);var source=versionRepo.versions(CASE).get(0);assertThatThrownBy(()->versions.withdraw(identity.authenticate("Bearer dev-annotator-chen"),source.path("id").asText(),VersionJson.uuid(),source.path("recordVersion").asInt(),"change")).isInstanceOf(BusinessException.class);assertThat(count("trial_audit_events")).isEqualTo(1);
    }
}
