package com.argument.version;

import com.argument.version.local.LocalAnnotationDependencies;
import com.argument.version.local.LocalFixtureLoader;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VersionWorkflowTest {
    static final String TASK="f102c523-b4ed-4323-81d3-0c564c4141a0",CASE="3cbb140f-0274-4871-8248-194271101a14";
    static final String DRAFT="f67848e1-a06d-4600-8454-19055e720d2c",DRAFT2="ef21c243-05a2-4837-92b4-d56f3fb77639";
    static final String CHEN="dev-annotator-chen",LIU="dev-annotator-liu",ARBITER="dev-arbiter-zhao";
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired LocalFixtureLoader loader; @Autowired VersionService service; @Autowired VersionIdentity identity; @Autowired Flyway flyway;
    @SpyBean LocalAnnotationDependencies dependencies;
    @BeforeEach void resetFixture() throws Exception {
        reset(dependencies);
        for(String table:List.of("annotation_mutation_receipts","snapshot_object_owners","annotation_version_snapshots","annotation_versions","annotation_case_counters","workflow_records"))jdbc.update("DELETE FROM "+table);
        loader.run(null);
    }
    ObjectNode response(String token,String path) throws Exception {
        return mapper.readValue(mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString(),ObjectNode.class);
    }
    ObjectNode submit(String token,String draft,String key,int version) throws Exception {
        var result=mvc.perform(post("/api/v1/annotation-drafts/"+draft+"/submit").header("Authorization","Bearer "+token).header("Idempotency-Key",key).header("If-Match",version).contentType("application/json").content("{\"confirmed\":true,\"note\":\"测试\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);return mapper.readValue(result.getResponse().getContentAsString(),ObjectNode.class);
    }
    ObjectNode withdraw(String id,String key,int version,String reason) throws Exception {
        var result=mvc.perform(post("/api/v1/annotation-versions/"+id+"/withdraw").header("Authorization","Bearer "+CHEN).header("Idempotency-Key",key).header("If-Match",version).contentType("application/json").content(mapper.createObjectNode().put("reason",reason).toString())).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(),ObjectNode.class);
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    @Test void submitFreezesCompleteSnapshotAndPreservesDraftIds() throws Exception {
        var original=dependencies.read("draft",DRAFT);var result=submit(CHEN,DRAFT,VersionJson.uuid(),1).path("data");
        var view=response(CHEN,"/api/v1/annotation-versions/"+result.path("id").asText()).path("data");
        assertThat(view.fieldNames()).toIterable().containsExactly("version","elements","relations","participants");
        assertThat(view.path("version").path("recordVersion").asInt()).isEqualTo(1);
        assertThat(view.path("version").path("submittedAt").asText()).matches(".*T\\d{2}:\\d{2}:\\d{2}Z");
        for(var element:view.path("elements")) {
            assertThat(element.path("ownerId")).isEqualTo(result.path("id"));assertThat(element.path("ownerType").asText()).isEqualTo("ANNOTATION_VERSION");
            assertThat(original.path("elementIds").toString()).doesNotContain(element.path("id").asText());
            assertThat(original.path("elementIds").toString()).contains(element.path("sourceElementIds").get(0).asText());
        }
        var ids=new HashSet<String>();view.path("elements").forEach(e->ids.add(e.path("id").asText()));view.path("relations").forEach(r->ids.add(r.path("id").asText()));
        for(var p:view.path("participants"))assertThat(ids).contains(p.path("targetId").asText());
        assertThat(dependencies.read("draft",DRAFT).path("elementIds")).isEqualTo(original.path("elementIds"));
        assertThat(dependencies.read("draft",DRAFT).path("recordVersion").asInt()).isEqualTo(2);
        var source=dependencies.read("element",original.path("elementIds").get(0).asText());source.put("text","之后的编辑");dependencies.replace("element",source);
        assertThat(response(CHEN,"/api/v1/annotation-versions/"+result.path("id").asText()).path("data").path("elements").get(0).path("text").asText()).isNotEqualTo("之后的编辑");
    }
    @Test void sameKeyRetryWinsBeforeStaleVersionAndDifferentBodyConflicts() throws Exception {
        String key=VersionJson.uuid();var first=submit(CHEN,DRAFT,key,1);var again=submit(CHEN,DRAFT,key,1);assertThat(again.path("data")).isEqualTo(first.path("data"));assertThat(count("annotation_versions")).isEqualTo(1);
        var bad=mvc.perform(post("/api/v1/annotation-drafts/"+DRAFT+"/submit").header("Authorization","Bearer "+CHEN).header("Idempotency-Key",key).header("If-Match",1).contentType("application/json").content("{\"confirmed\":true,\"note\":\"不同请求\"}")).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(409);assertThat(bad.getResponse().getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
    }
    @Test void progressAndReadsEnforceBlindIsolationAndPagination() throws Exception {
        var one=submit(CHEN,DRAFT,VersionJson.uuid(),1).path("data");submit(LIU,DRAFT2,VersionJson.uuid(),1);
        assertThat(response(CHEN,"/api/v1/tasks/"+TASK+"/progress").path("data").path("items").get(0).path("submittedCount").asInt()).isEqualTo(2);
        var list=response(CHEN,"/api/v1/task-documents/"+CASE+"/annotation-versions?page=1&size=1");
        assertThat(list.path("meta").path("total").asInt()).isEqualTo(1);assertThat(list.path("data").path("items").size()).isEqualTo(1);
        assertThat(response(ARBITER,"/api/v1/task-documents/"+CASE+"/annotation-versions").path("meta").path("total").asInt()).isEqualTo(2);
        assertThat(response(LIU,"/api/v1/annotation-versions/"+one.path("id").asText()).path("error").path("code").asText()).isEqualTo("BLIND_SCOPE_DENIED");
        assertThat(response("dev-creator-li","/api/v1/task-documents/"+CASE+"/annotation-versions").path("success").asBoolean()).isFalse();
        assertThat(response("dev-admin-wang","/api/v1/tasks/"+TASK+"/progress").path("success").asBoolean()).isTrue();
        assertThat(response("dev-expert-qian","/api/v1/tasks/"+TASK+"/progress").path("success").asBoolean()).isFalse();
        assertThat(response(CHEN,"/api/v1/task-documents/"+CASE+"/annotation-versions?size=101").path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(response(CHEN,"/api/v1/task-documents/"+CASE+"/annotation-versions?assignmentId=another").path("success").asBoolean()).isFalse();
    }
    @Test void withdrawPreservesSnapshotReactivatesDraftAndResubmitsAsNextNumber() throws Exception {
        var first=submit(CHEN,DRAFT,VersionJson.uuid(),1).path("data");String id=first.path("id").asText(),key=VersionJson.uuid();
        var before=response(CHEN,"/api/v1/annotation-versions/"+id).path("data");
        var result=withdraw(id,key,1,"补充关系");assertThat(result.path("data").path("status").asText()).isEqualTo("WITHDRAWN");assertThat(result.path("data").path("recordVersion").asInt()).isEqualTo(2);
        assertThat(withdraw(id,key,1,"补充关系").path("data")).isEqualTo(result.path("data"));
        assertThat(response(CHEN,"/api/v1/annotation-versions/"+id).path("data").path("elements")).isEqualTo(before.path("elements"));
        assertThat(dependencies.read("draft",DRAFT).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(response(CHEN,"/api/v1/tasks/"+TASK+"/progress").path("data").path("items").get(0).path("submittedCount").asInt()).isZero();
        assertThat(submit(CHEN,DRAFT,VersionJson.uuid(),3).path("data").path("displayCode").asText()).isEqualTo("V2");
    }
    @Test void staleIfMatchContainsCurrentResourceVersion() throws Exception {
        var d=dependencies.read("draft",DRAFT);d.put("recordVersion",2);dependencies.replace("draft",d);
        var result=mvc.perform(post("/api/v1/annotation-drafts/"+DRAFT+"/submit").header("Authorization","Bearer "+CHEN).header("Idempotency-Key",VersionJson.uuid()).header("If-Match",1).contentType("application/json").content("{\"confirmed\":true,\"note\":\"\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(409);assertThat(result.getResponse().getContentAsString()).contains("VERSION_CONFLICT","currentRecordVersion");assertThat(count("annotation_versions")).isZero();
    }
    @Test void rejectsMalformedRequestsMissingHeadersAndUnknownTokens() throws Exception {
        for(String body:List.of("{}","{\"confirmed\":true,\"note\":\"\",\"id\":\"injected\"}","{\"confirmed\":\"true\",\"note\":\"\"}","[]")) {
            var result=mvc.perform(post("/api/v1/annotation-drafts/"+DRAFT+"/submit").header("Authorization","Bearer "+CHEN).contentType("application/json").content(body)).andReturn();assertThat(result.getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(response("dev-not-known","/api/v1/tasks/"+TASK+"/progress").path("error").path("code").asText()).isEqualTo("UNAUTHENTICATED");
    }
    @Test void refusesUnconfirmedCheckpointAndTextTampering() {
        var draft=dependencies.read("draft",DRAFT);var checkpoint=dependencies.read("checkpoint",draft.path("segmentationCheckpointId").asText());checkpoint.put("status","PENDING");dependencies.replace("checkpoint",checkpoint);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("切割");assertThat(count("annotation_versions")).isZero();
        checkpoint.put("status","CONFIRMED");dependencies.replace("checkpoint",checkpoint);
        var text=dependencies.read("text",draft.path("documentTextVersionId").asText());text.put("normalizedText","被覆盖");dependencies.replace("text",text);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("哈希");
    }
    @Test void refusesWrongLabelsNonPropositionsAndIncompleteGraphs() {
        var draft=dependencies.read("draft",DRAFT);var element=dependencies.read("element",draft.path("elementIds").get(0).asText());element.put("labelCode","GM-C");dependencies.replace("element",element);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("标签");
        element.put("labelCode","GM-L");element.put("isProposition",false);dependencies.replace("element",element);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("类别");
        element.put("isProposition",true);dependencies.replace("element",element);
        var graph=dependencies.read("graph",draft.path("graphId").asText());graph.putArray("elementIds");dependencies.replace("graph",graph);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("覆盖");
    }
    @Test void rejectsCrossSnapshotParticipantsAndSelfReference() {
        var draft=dependencies.read("draft",DRAFT);String relation=draft.path("relationIds").get(0).asText();var p=dependencies.find("participant","relationId",relation).get(0);
        p.put("targetId",VersionJson.uuid());dependencies.replace("participant",p);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("本快照");
        p.put("targetKind","RELATION");p.put("targetId",relation);dependencies.replace("participant",p);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("自引用");
    }
    @Test void rejectsRevokedAssignmentEvenOnSuccessfulRetry() throws Exception {
        String key=VersionJson.uuid();submit(CHEN,DRAFT,key,1);var d=dependencies.read("draft",DRAFT);var a=dependencies.read("assignment",d.path("assignmentId").asText());a.put("status","REVOKED");dependencies.replace("assignment",a);
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,key,1,true,"测试")).hasMessageContaining("权限");assertThat(count("annotation_versions")).isEqualTo(1);
    }
    @Test void adjudicationReferenceAndTaskLockBlockWithdrawal() throws Exception {
        var v=submit(CHEN,DRAFT,VersionJson.uuid(),1).path("data");String id=v.path("id").asText();
        var session=mapper.createObjectNode().put("id",VersionJson.uuid());session.putArray("sourceVersionIds").add(id);
        jdbc.update("INSERT INTO workflow_records VALUES ('session',?,?)",session.path("id").asText(),session.toString());
        assertThat(withdraw(id,VersionJson.uuid(),1,"重标").path("error").path("code").asText()).isEqualTo("ALREADY_IN_ADJUDICATION");
        jdbc.update("DELETE FROM workflow_records WHERE object_kind='session'");var t=dependencies.read("task",TASK);t.put("status","COMPLETED");dependencies.replace("task",t);
        assertThat(withdraw(id,VersionJson.uuid(),1,"重标").path("error").path("code").asText()).isEqualTo("LOCKED_RESOURCE");
    }
    @Test void caseLockAssignsUniqueConsecutiveNumbersForConcurrentSubmit() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=executor.submit(()->{barrier.await();return service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"");});
            var b=executor.submit(()->{barrier.await();return service.submit(identity.authenticate("Bearer "+LIU),DRAFT2,VersionJson.uuid(),1,true,"");});
            assertThat(Set.of(a.get(20,TimeUnit.SECONDS).path("versionNumber").asInt(),b.get(20,TimeUnit.SECONDS).path("versionNumber").asInt())).containsExactlyInAnyOrder(1,2);assertThat(count("annotation_versions")).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }
    @Test void failedDraftUpdateRollsBackSnapshotNumberAndReceipt() {
        doThrow(new IllegalStateException("模拟保存草稿失败")).when(dependencies).replace(eq("draft"),any(ObjectNode.class));
        assertThatThrownBy(()->service.submit(identity.authenticate("Bearer "+CHEN),DRAFT,VersionJson.uuid(),1,true,"")).hasMessageContaining("失败");
        assertThat(count("annotation_versions")).isZero();assertThat(count("annotation_version_snapshots")).isZero();assertThat(count("annotation_mutation_receipts")).isZero();assertThat(count("annotation_case_counters")).isZero();
        assertThat(dependencies.read("draft",DRAFT).path("status").asText()).isEqualTo("ACTIVE");
    }
    @Test void migratingAgainDoesNotResetExistingVersions() throws Exception {
        submit(CHEN,DRAFT,VersionJson.uuid(),1);assertThat(flyway.migrate().migrationsExecuted).isZero();assertThat(count("annotation_versions")).isEqualTo(1);
    }
    @Test void localBrowserOriginCanSubmitButUnlistedOriginIsRejected() throws Exception {
        var result=mvc.perform(post("/api/v1/annotation-drafts/"+DRAFT+"/submit").header("Origin","http://127.0.0.1:5174").header("Authorization","Bearer "+CHEN).header("Idempotency-Key",VersionJson.uuid()).header("If-Match",1).contentType("application/json").content("{\"confirmed\":true,\"note\":\"\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var rejected=mvc.perform(get("/api/v1/tasks/"+TASK+"/progress").header("Origin","https://unlisted.example").header("Authorization","Bearer "+CHEN)).andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(403);
    }
    @Test void productionProfileDisablesAllLocalAdaptersEvenWithLocalAlsoActive() {
        var environment=new org.springframework.mock.env.MockEnvironment();environment.setActiveProfiles("prod","local");
        for(Class<?> type:List.of(LocalAnnotationDependencies.class,LocalFixtureLoader.class,com.argument.version.local.LocalVersionIdentity.class,com.argument.version.local.LocalContextController.class)) {
            var profile=type.getAnnotation(org.springframework.context.annotation.Profile.class);
            assertThat(environment.acceptsProfiles(org.springframework.core.env.Profiles.of(profile.value()))).isFalse();
        }
    }
}
