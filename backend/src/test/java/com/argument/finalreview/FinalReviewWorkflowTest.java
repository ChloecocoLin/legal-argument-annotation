package com.argument.finalreview;

import com.argument.adjudication.*;
import com.argument.finalreview.local.LocalFinalReviewDependencies;
import com.argument.version.*;
import com.argument.version.local.*;
import com.argument.version.port.VersionIdentity;
import com.argument.common.exception.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FinalReviewWorkflowTest {
    static final String CASE="3cbb140f-0274-4871-8248-194271101a14",DRAFT="f67848e1-a06d-4600-8454-19055e720d2c",DRAFT2="ef21c243-05a2-4837-92b4-d56f3fb77639";
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired VersionService versionService;@Autowired VersionRepository versions;@Autowired VersionIdentity identity;
    @Autowired LocalFixtureLoader loader;@Autowired LocalAnnotationDependencies data;@Autowired AdjudicationService adjudication;@Autowired AdjudicationRepository sessions;@Autowired FinalReviewService service;
    @SpyBean FinalReviewRepository store;@SpyBean LocalFinalReviewDependencies deps;
    @BeforeEach void resetFixture() throws Exception {reset(store,deps);clean();loader.run(null);}
    @AfterEach void cleanup() {reset(store,deps);clean();}
    void clean() {for(String table:List.of("final_review_transitions","final_annotation_versions","adjudication_transitions","adjudication_returned_versions","adjudication_decisions","adjudication_items","adjudication_sessions","annotation_mutation_receipts","annotation_version_snapshots","annotation_versions","annotation_case_counters","local_annotation_dependencies"))jdbc.update("DELETE FROM "+table);}
    com.argument.version.port.VersionIdentity.Actor arbiter() {return identity.authenticate("Bearer dev-arbiter-zhao");}
    ObjectNode json(String s) throws Exception {return mapper.readValue(s,ObjectNode.class);}
    ObjectNode request(String path,ObjectNode body,String key,int rv) throws Exception {return mapper.readValue(mvc.perform(post(path).header("Authorization","Bearer dev-arbiter-zhao").header("Idempotency-Key",key).header("If-Match",rv).contentType("application/json").content(body.toString())).andReturn().getResponse().getContentAsString(),ObjectNode.class);}
    ObjectNode getAs(String token,String path) throws Exception {return mapper.readValue(mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString(),ObjectNode.class);}
    ObjectNode completeSession(boolean adoptRelation) {
        var a=versionService.submit(identity.authenticate("Bearer dev-annotator-chen"),DRAFT,VersionJson.uuid(),1,true,"");var b=versionService.submit(identity.authenticate("Bearer dev-annotator-liu"),DRAFT2,VersionJson.uuid(),1,true,"");var body=mapper.createObjectNode();body.putArray("sourceVersionIds").add(a.path("id").asText()).add(b.path("id").asText());var s=adjudication.create(arbiter(),CASE,VersionJson.uuid(),body);String id=s.path("id").asText();
        for(var item:sessions.items(id,"PROPOSITION")){var cmd=mapper.createObjectNode().put("reason","核查来源");if("AGREEMENT".equals(item.path("conflictType").asText()))cmd.put("action","CONFIRM_AGREEMENT");else {String object=item.path("candidateElementIds").get(0).asText();cmd.put("action","ADOPT_VERSION").put("sourceObjectId",object);for(var snapshot:adjudication.snapshots(s))for(var e:snapshot.path("elements"))if(object.equals(e.path("id").asText()))cmd.put("sourceVersionId",snapshot.path("version").path("id").asText());}adjudication.decideLocalProposition(arbiter(),item.path("id").asText(),VersionJson.uuid(),1,cmd);}
        s=adjudication.complete(arbiter(),id,VersionJson.uuid(),sessions.session(id,false).path("recordVersion").asInt(),mapper.createObjectNode().put("confirmed",true),"PROPOSITION");
        for(var item:sessions.items(id,"RELATION")){var cmd=mapper.createObjectNode().put("reason","核查关系").put("action",adoptRelation?"ADOPT_VERSION":"DELETE");if(adoptRelation)cmd.put("sourceObjectId",item.path("candidateRelationIds").get(0).asText()).put("sourceVersionId",a.path("id").asText());adjudication.decideRelation(arbiter(),item.path("id").asText(),VersionJson.uuid(),1,cmd);}
        return adjudication.complete(arbiter(),id,VersionJson.uuid(),sessions.session(id,false).path("recordVersion").asInt(),mapper.createObjectNode().put("confirmed",true),"RELATION");
    }
    ObjectNode create(ObjectNode s,int number) {return service.create(arbiter(),s.path("id").asText(),VersionJson.uuid(),number);}
    String path(ObjectNode v,String action) {return "/api/v1/final-annotation-versions/"+v.path("id").asText()+"/"+action;}
    ObjectNode submit(ObjectNode v,int rv) throws Exception {return request(path(v,"review"),json("{\"action\":\"SUBMIT\"}"),VersionJson.uuid(),rv);}
    void certifiedAdapterForStateMachineTest() {doAnswer(inv->{ObjectNode snapshot=inv.getArgument(0);return new FinalReviewDependencies.Evidence(true,"PRODUCTION",FinalReviewService.contentDigest(snapshot),snapshot.path("decisionDigest").asText(),snapshot.path("version").path("guideVersionId").asText(),List.of());}).when(deps).assess(any(),any());}
    @Test void independentCompleteSnapshotRemapsParticipantsAndRetainsAllSources() {
        var s=completeSession(true);var originals=adjudication.snapshots(s);var v=create(s,1);var snapshot=service.read(arbiter(),v.path("id").asText());assertThat(snapshot.path("version").path("status").asText()).isEqualTo("DRAFT");
        var originalIds=new HashSet<String>();originals.forEach(o->{o.path("elements").forEach(e->originalIds.add(e.path("id").asText()));o.path("relations").forEach(r->originalIds.add(r.path("id").asText()));});var ids=new HashSet<String>();for(String field:List.of("elements","relations"))snapshot.path(field).forEach(o->{assertThat(originalIds).doesNotContain(o.path("id").asText());assertThat(o.path("ownerId").asText()).isEqualTo(v.path("id").asText());ids.add(o.path("id").asText());});
        for(var p:snapshot.path("participants")){assertThat(ids).contains(p.path("relationId").asText(),p.path("targetId").asText());assertThat(originalIds).doesNotContain(p.path("targetId").asText());}
        assertThat(snapshot.path("version").path("provenance").toString()).contains("EXCLUDED_ELEMENT","decisionId","sourceVersionIds");assertThat(snapshot.path("version").path("graphId")).isEqualTo(snapshot.path("graph").path("id"));assertThat(store.versions(CASE)).hasSize(1);
        assertThat(adjudication.snapshots(s).get(0).path("elements")).isEqualTo(originals.get(0).path("elements"));assertThat(adjudication.snapshots(s).get(0).path("version").path("status").asText()).isEqualTo("ADJUDICATED");
    }
    @Test void deletedRelationHasExplicitExcludedProvenanceAndNoDanglingGraph() {
        var s=completeSession(false);var v=create(s,1);var snapshot=service.read(arbiter(),v.path("id").asText());assertThat(snapshot.path("relations")).isEmpty();assertThat(snapshot.path("participants")).isEmpty();assertThat(snapshot.path("version").path("provenance").toString()).contains("EXCLUDED_RELATION");assertThat(service.workspace(arbiter(),v.path("id").asText()).path("quality").path("healthy").asBoolean()).isTrue();
    }
    @Test void frozenPublicResponsesAndStrictCommands() throws Exception {
        var s=completeSession(true);String key=VersionJson.uuid(),createPath="/api/v1/adjudication-sessions/"+s.path("id").asText()+"/final-versions";var body=json("{\"versionNumber\":1}");var first=request(createPath,body,key,1);assertThat(first.path("data").fieldNames()).toIterable().containsExactlyInAnyOrder("id","status");assertThat(request(createPath,body,key,1).path("data")).isEqualTo(first.path("data"));
        var summary=getAs("dev-arbiter-zhao","/api/v1/final-annotation-versions/"+first.path("data").path("id").asText());assertThat(summary.path("data").fieldNames()).toIterable().containsExactlyInAnyOrder("id","status","elementIds","provenanceCount");
        assertThat(request(createPath,json("{\"versionNumber\":2,\"status\":\"APPROVED\"}"),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();assertThat(request(createPath,json("{\"versionNumber\":1.5}"),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();
        assertThat(request(path((ObjectNode)first.get("data"),"approve"),json("{\"confirmed\":true,\"formalReady\":true}"),VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }
    @Test void incompleteAdjudicationCannotGenerateFinalSnapshot() throws Exception {
        var s=completeSession(true);s.put("status","RELATION_REVIEW");sessions.saveSession(s);assertThat(request("/api/v1/adjudication-sessions/"+s.path("id").asText()+"/final-versions",json("{\"versionNumber\":1}"),VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("UNDECIDED_ITEMS");assertThat(store.versions(CASE)).isEmpty();
    }
    @Test void missingProvenanceRollsBackSnapshotCreation() {
        var s=completeSession(true);doAnswer(inv->{var r=(ObjectNode)inv.callRealMethod();((com.fasterxml.jackson.databind.node.ArrayNode)r.get("provenance")).removeAll();return r;}).when(deps).resolve(any(),anyList(),anyList(),anyList(),anyList());assertThatThrownBy(()->create(s,1)).isInstanceOf(BusinessException.class);assertThat(store.versions(CASE)).isEmpty();assertThat(adjudication.snapshots(s).get(0).path("version").path("status").asText()).isEqualTo("UNDER_ADJUDICATION");
    }
    @Test void wrongTextBlocksReviewAndCannotBeCertifiedAway() throws Exception {
        var s=completeSession(true);var v=create(s,1);var corrupted=service.read(arbiter(),v.path("id").asText());((ObjectNode)corrupted.path("elements").get(0)).put("text","错误切片");jdbc.update("UPDATE final_annotation_versions SET snapshot_json=? WHERE id=?",corrupted.toString(),v.path("id").asText());certifiedAdapterForStateMachineTest();assertThat(submit(v,1).path("error").path("code").asText()).isEqualTo("SOURCE_INCOMPLETE");assertThat(service.read(arbiter(),v.path("id").asText()).path("version").path("status").asText()).isEqualTo("DRAFT");
    }
    @Test void localPreviewCanSubmitButNeverApprove() throws Exception {
        var v=create(completeSession(true),1);assertThat(submit(v,1).path("data").path("status").asText()).isEqualTo("IN_REVIEW");assertThat(request(path(v,"approve"),json("{\"confirmed\":true}"),VersionJson.uuid(),2).path("error").path("code").asText()).isEqualTo("SOURCE_INCOMPLETE");assertThat(service.workspace(arbiter(),v.path("id").asText()).path("quality").path("canApprove").asBoolean()).isFalse();
    }
    @Test void approvalWithTestEvidenceStopsAtApprovedAndIsReadOnly() throws Exception {
        var s=completeSession(true);var v=create(s,1);assertThat(submit(v,1).path("success").asBoolean()).isTrue();certifiedAdapterForStateMachineTest();String key=VersionJson.uuid();var command=json("{\"confirmed\":true}");var first=request(path(v,"approve"),command,key,2);assertThat(first.path("data").path("status").asText()).isEqualTo("APPROVED");assertThat(request(path(v,"approve"),command,key,2).path("data")).isEqualTo(first.path("data"));
        var result=service.read(arbiter(),v.path("id").asText()).path("version");assertThat(result.path("committedAt").isNull()).isTrue();assertThat(result.path("approvedAt").asText()).matches(".*T\\d{2}:\\d{2}:\\d{2}Z");assertThat(sessions.session(s.path("id").asText(),false).path("status").asText()).isEqualTo("FINAL_REVIEW");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/final-review-approved-response.json"),first.toString(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(request(path(v,"return"),json("{\"reason\":\"改动\"}"),VersionJson.uuid(),3).path("success").asBoolean()).isFalse();assertThatThrownBy(()->create(s,2)).isInstanceOf(BusinessException.class);
    }
    @Test void returnHistorySurvivesReceiptExpiryAndRebuildPreservesOldSnapshot() throws Exception {
        var s=completeSession(true);var v=create(s,1);var content=service.read(arbiter(),v.path("id").asText());submit(v,1);String key=VersionJson.uuid();var body=json("{\"reason\":\"请复核图覆盖\"}");var returned=request(path(v,"return"),body,key,2);assertThat(returned.path("data").path("status").asText()).isEqualTo("DRAFT");assertThat(request(path(v,"return"),body,key,2).path("data")).isEqualTo(returned.path("data"));
        jdbc.update("DELETE FROM annotation_mutation_receipts");assertThat(store.history(v.path("id").asText()).toString()).contains("请复核图覆盖");var next=create(s,2);assertThat(next.path("id")).isNotEqualTo(v.path("id"));assertThat(service.read(arbiter(),v.path("id").asText()).path("elements")).isEqualTo(content.path("elements"));assertThat(submit(v,3).path("error").path("code").asText()).isEqualTo("LOCKED_RESOURCE");
    }
    @Test void emptyReasonStaleVersionAndSameKeyDifferentReasonAreRejected() throws Exception {
        var v=create(completeSession(true),1);submit(v,1);assertThat(request(path(v,"return"),json("{\"reason\":\"\"}"),VersionJson.uuid(),2).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
        var stale=request(path(v,"return"),json("{\"reason\":\"修订\"}"),VersionJson.uuid(),1);assertThat(stale.path("error").path("details").toString()).contains("currentRecordVersion");String key=VersionJson.uuid();request(path(v,"return"),json("{\"reason\":\"甲\"}"),key,2);assertThat(request(path(v,"return"),json("{\"reason\":\"乙\"}"),key,2).path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }
    @Test void approvalChecksExpertsAgainAndRequiredExpertCannotBeOmitted() throws Exception {
        var s=completeSession(true);var v=create(s,1);submit(v,1);certifiedAdapterForStateMachineTest();var doc=data.read("taskDocument",CASE);var task=data.read("task",doc.path("taskId").asText());task.put("expertConfirmationRequired",true);data.replace("task",task);assertThat(request(path(v,"approve"),json("{\"confirmed\":true}"),VersionJson.uuid(),2).path("success").asBoolean()).isFalse();
    }
    @Test void accessAndSuccessfulRetryRecheckLiveAssignment() throws Exception {
        var s=completeSession(true);var v=create(s,1);String readPath="/api/v1/final-annotation-versions/"+v.path("id").asText();for(String token:List.of("dev-admin-wang","dev-creator-li","dev-annotator-chen","dev-expert-qian"))assertThat(getAs(token,readPath).path("error").path("code").asText()).isEqualTo("FORBIDDEN");String key=VersionJson.uuid();var body=json("{\"action\":\"SUBMIT\"}");request(path(v,"review"),body,key,1);
        var assignment=data.find("assignment","taskDocumentId",CASE).stream().filter(a->"ARBITER".equals(a.path("assignmentRole").asText())).findFirst().orElseThrow();assignment.put("status","INACTIVE");data.replace("assignment",assignment);assertThat(request(path(v,"review"),body,key,1).path("error").path("code").asText()).isEqualTo("FORBIDDEN");assertThat(getAs("dev-arbiter-zhao",readPath).path("success").asBoolean()).isFalse();
    }
    @Test void concurrentGenerationProducesOneFinalVersion() throws Exception {
        var s=completeSession(true);var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(()->create(s,1));var second=pool.submit(()->create(s,1));assertThat(first.get(10,TimeUnit.SECONDS).path("id")).isEqualTo(second.get(10,TimeUnit.SECONDS).path("id"));assertThat(store.versions(CASE)).hasSize(1);}finally{pool.shutdownNow();}
    }
    @Test void transitionFailureRollsBackAllCreatedData() {
        var s=completeSession(true);doThrow(new BusinessException(ErrorCode.CONFLICT)).when(store).transition(eq("FV01"),anyString(),any(),any(),any());assertThatThrownBy(()->create(s,1)).isInstanceOf(BusinessException.class);assertThat(store.versions(CASE)).isEmpty();
    }
    @Test void changedDecisionCannotApproveStaleSnapshot() throws Exception {
        var s=completeSession(true);var v=create(s,1);submit(v,1);certifiedAdapterForStateMachineTest();String id=s.path("id").asText();var decision=sessions.decisions(id).get(0).deepCopy();decision.put("reason","已被更新的来源决定");jdbc.update("UPDATE adjudication_decisions SET payload_json=? WHERE id=?",decision.toString(),decision.path("id").asText());assertThat(request(path(v,"approve"),json("{\"confirmed\":true}"),VersionJson.uuid(),2).path("success").asBoolean()).isFalse();
    }
    @Test void staleFormalEvidenceCannotApproveDifferentContent() throws Exception {
        var v=create(completeSession(true),1);submit(v,1);doReturn(new FinalReviewDependencies.Evidence(true,"PRODUCTION","wrong-digest","wrong-decisions",VersionJson.uuid(),List.of())).when(deps).assess(any(),any());assertThat(request(path(v,"approve"),json("{\"confirmed\":true}"),VersionJson.uuid(),2).path("error").path("code").asText()).isEqualTo("SOURCE_INCOMPLETE");assertThat(service.read(arbiter(),v.path("id").asText()).path("version").path("status").asText()).isEqualTo("IN_REVIEW");
    }
    @Test void longReturnReasonUsesFixedLengthHashAndPreservesFullHistory() throws Exception {
        var v=create(completeSession(true),1);submit(v,1);String key=VersionJson.uuid(),reason="核查关系结构与来源".repeat(120);var body=mapper.createObjectNode().put("reason",reason);var first=request(path(v,"return"),body,key,2);assertThat(first.path("success").asBoolean()).isTrue();assertThat(request(path(v,"return"),body,key,2).path("data")).isEqualTo(first.path("data"));assertThat(store.history(v.path("id").asText()).toString()).contains(reason);assertThat(jdbc.queryForObject("SELECT request_sha256 FROM annotation_mutation_receipts WHERE operation_key=? AND idempotency_key=?",String.class,"FV04:"+v.path("id").asText(),key)).matches("[0-9a-f]{64}");
    }
}
