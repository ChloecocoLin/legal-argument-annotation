package com.argument.adjudication;

import com.argument.version.*;
import com.argument.version.local.*;
import com.argument.version.port.VersionIdentity;
import com.argument.adjudication.local.LocalAdjudicationDependencies;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AdjudicationWorkflowTest {
    static final String CASE="3cbb140f-0274-4871-8248-194271101a14",TASK="f102c523-b4ed-4323-81d3-0c564c4141a0",DRAFT="f67848e1-a06d-4600-8454-19055e720d2c",DRAFT2="ef21c243-05a2-4837-92b4-d56f3fb77639";
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired LocalFixtureLoader loader;@Autowired VersionService versions;@Autowired VersionIdentity identity;@Autowired AdjudicationService service;@Autowired AdjudicationRepository store;@Autowired LocalAnnotationDependencies data;
    @Autowired VersionRepository versionStore;
    @SpyBean LocalAdjudicationDependencies deps;
    @BeforeEach void resetFixture() throws Exception { reset(deps);clean();loader.run(null); }
    @AfterEach void cleanup() { clean(); }
    void clean() { for(String table:List.of("adjudication_transitions","adjudication_returned_versions","adjudication_decisions","adjudication_items","adjudication_sessions","annotation_mutation_receipts","snapshot_object_owners","annotation_version_snapshots","annotation_versions","annotation_case_counters","workflow_records"))jdbc.update("DELETE FROM "+table); }
    ObjectNode body(String json) throws Exception { return mapper.readValue(json,ObjectNode.class); }
    ObjectNode command(String token,String path,ObjectNode body,String key,int expected) throws Exception {
        var result=mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key",key).header("If-Match",expected).contentType("application/json").content(body.toString())).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(),ObjectNode.class);
    }
    ObjectNode getAs(String token,String path) throws Exception { return mapper.readValue(mvc.perform(get(path).header("Authorization","Bearer "+token)).andReturn().getResponse().getContentAsString(),ObjectNode.class); }
    List<ObjectNode> submitBoth() { return List.of(versions.submit(identity.authenticate("Bearer dev-annotator-chen"),DRAFT,VersionJson.uuid(),1,true,""),versions.submit(identity.authenticate("Bearer dev-annotator-liu"),DRAFT2,VersionJson.uuid(),1,true,"")); }
    ObjectNode source(List<ObjectNode> vs) { var b=mapper.createObjectNode();var ids=b.putArray("sourceVersionIds");vs.forEach(v->ids.add(v.path("id").asText()));return b; }
    ObjectNode create() { return service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),source(submitBoth())); }
    String base(ObjectNode s) { return "/api/v1/adjudication-sessions/"+s.path("id").asText(); }
    ObjectNode completeProps(ObjectNode s) throws Exception {
        String id=s.path("id").asText();
        for(var item:store.items(id,"PROPOSITION")) {
            var b=mapper.createObjectNode().put("action","AGREEMENT".equals(item.path("conflictType").asText())?"CONFIRM_AGREEMENT":"ADOPT_VERSION").put("reason","人工核查");
            if("ADOPT_VERSION".equals(b.path("action").asText())){String object=item.path("candidateElementIds").get(0).asText();for(var snap:service.snapshots(s))for(var e:snap.path("elements"))if(object.equals(e.path("id").asText()))b.put("sourceVersionId",snap.path("version").path("id").asText());b.put("sourceObjectId",object);}
            assertThat(command("dev-arbiter-zhao","/api/local/member-d/adjudication/proposition-items/"+item.path("id").asText()+"/fixture-decisions",b,VersionJson.uuid(),1).path("success").asBoolean()).isTrue();
        }
        var latest=store.session(id,false);var response=command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),latest.path("recordVersion").asInt());assertThat(response.path("success").asBoolean()).isTrue();return (ObjectNode)response.get("data");
    }
    @Test void sessionFreezesSourcesAndBlocksWithdrawalWithCompleteRead() throws Exception {
        var vs=submitBoth();var key=VersionJson.uuid();var b=source(vs);var s=service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,key,b);
        assertThat(service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,key,b)).isEqualTo(s);
        assertThat(service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),b).path("id")).isEqualTo(s.path("id"));
        assertThat(store.sessions(CASE)).hasSize(1);assertThat(store.items(s.path("id").asText(),"PROPOSITION")).hasSize(2);
        assertThat(getAs("dev-arbiter-zhao",base(s)).path("data").path("sourceVersionIds").size()).isEqualTo(2);
        assertThat(getAs("dev-annotator-chen",base(s)).path("error").path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(getAs("dev-admin-wang",base(s)).path("success").asBoolean()).isFalse();
        assertThat(command("dev-annotator-chen","/api/v1/annotation-versions/"+vs.get(0).path("id").asText()+"/withdraw",body("{\"reason\":\"修改\"}"),VersionJson.uuid(),2).path("success").asBoolean()).isFalse();
        assertThat(getAs("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/annotation-diff?phase=RELATION").path("error").path("code").asText()).isEqualTo("PROPOSITIONS_NOT_COMPLETE");
    }
    @Test void insufficientDuplicateAndCrossBaselineSourcesAreRejected() throws Exception {
        var vs=submitBoth();var b=source(List.of(vs.get(0)));
        assertThat(command("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/adjudication-sessions",b,VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("INSUFFICIENT_VERSIONS");
        b=source(List.of(vs.get(0),vs.get(0)));assertThat(command("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/adjudication-sessions",b,VersionJson.uuid(),1).path("success").asBoolean()).isFalse();
        jdbc.update("UPDATE annotation_version_snapshots SET snapshot_json=REPLACE(snapshot_json,?,?) WHERE version_id=?",vs.get(1).path("guideVersionId").asText(),VersionJson.uuid(),vs.get(1).path("id").asText());
        assertThat(command("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/adjudication-sessions",source(vs),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();assertThat(store.sessions(CASE)).isEmpty();
    }
    @Test void agreementMustBeExplicitAndExpertsBlockPhase() throws Exception {
        var s=create();assertThat(command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("UNDECIDED_PROPOSITIONS");
        var item=store.items(s.path("id").asText(),"PROPOSITION").stream().filter(i->"AGREEMENT".equals(i.path("conflictType").asText())).findFirst().orElseThrow();
        assertThat(command("dev-arbiter-zhao","/api/local/member-d/adjudication/proposition-items/"+item.path("id").asText()+"/fixture-decisions",body("{\"action\":\"DELETE\",\"reason\":\"删\"}"),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();
        doThrow(new BusinessException(ErrorCode.OPEN_EXPERT_ISSUES)).when(deps).checkExperts(any());
        assertThat(command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("OPEN_EXPERT_ISSUES");
    }
    @Test void completeTwoPhasesAndImmutableHistory() throws Exception {
        var s=completeProps(create());String id=s.path("id").asText();assertThat(s.path("status").asText()).isEqualTo("RELATION_REVIEW");
        assertThat(command("dev-arbiter-zhao",base(s)+"/relations/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),s.path("recordVersion").asInt()).path("error").path("code").asText()).isEqualTo("UNDECIDED_RELATIONS");
        var item=store.items(id,"RELATION").get(0);String key=VersionJson.uuid();var b=body("{\"action\":\"DELETE\",\"reason\":\"不采纳此关系\"}");String path="/api/v1/relation-adjudication-items/"+item.path("id").asText()+"/decisions";
        var result=command("dev-arbiter-zhao",path,b,key,1);assertThat(result.path("success").asBoolean()).isTrue();assertThat(command("dev-arbiter-zhao",path,b,key,1).path("data")).isEqualTo(result.path("data"));
        assertThat(command("dev-arbiter-zhao",path,body("{\"action\":\"DELETE\",\"reason\":\"另一个理由\"}"),key,1).path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        int rv=store.session(id,false).path("recordVersion").asInt();assertThat(command("dev-arbiter-zhao",base(s)+"/relations/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),rv).path("data").path("status").asText()).isEqualTo("FINAL_REVIEW");
        assertThat(command("dev-arbiter-zhao",path,b,VersionJson.uuid(),2).path("success").asBoolean()).isFalse();assertThat(store.decisions(id)).hasSize(3);
        assertThat(service.snapshots(s).get(0).path("elements").size()).isEqualTo(2);
    }
    @Test void invalidMappingAndStaleVersionCannotChangeDecision() throws Exception {
        var s=completeProps(create());var item=store.items(s.path("id").asText(),"RELATION").get(0);String path="/api/v1/relation-adjudication-items/"+item.path("id").asText()+"/decisions";
        var invalid=mapper.createObjectNode().put("action","ADOPT_VERSION").put("reason","采用").put("sourceVersionId",s.path("sourceVersionIds").get(1).asText()).put("sourceObjectId",item.path("candidateRelationIds").get(0).asText());
        assertThat(command("dev-arbiter-zhao",path,invalid,VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("INVALID_RELATION_MAPPING");
        assertThat(command("dev-arbiter-zhao",path,body("{\"action\":\"DELETE\",\"reason\":\"删\"}"),VersionJson.uuid(),2).path("error").path("details").toString()).contains("currentRecordVersion");assertThat(store.item(item.path("id").asText(),"RELATION").path("status").asText()).isEqualTo("OPEN");
    }
    @Test void returnReactivatesOnlyTargetsAndAllowsNewSessionWithReplacement() throws Exception {
        var s=create();var before=service.snapshots(s);var b=mapper.createObjectNode().put("reason","补正标签");b.putArray("targetAssignmentIds").add(before.get(0).path("version").path("assignmentId").asText());String key=VersionJson.uuid();
        var result=command("dev-arbiter-zhao",base(s)+"/return",b,key,1);assertThat(result.path("success").asBoolean()).isTrue();assertThat(command("dev-arbiter-zhao",base(s)+"/return",b,key,1).path("data")).isEqualTo(result.path("data"));
        jdbc.update("DELETE FROM annotation_mutation_receipts");assertThat(store.history(s.path("id").asText()).stream().filter(h->"J11".equals(h.path("operation").asText())).toList()).hasSize(1);assertThat(store.history(s.path("id").asText()).toString()).contains("补正标签",b.path("targetAssignmentIds").get(0).asText());
        assertThat(data.read("draft",DRAFT).path("status").asText()).isEqualTo("ACTIVE");assertThat(data.read("draft",DRAFT2).path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(versions.progress(identity.authenticate("Bearer dev-arbiter-zhao"),TASK).path("items").get(0).path("submittedCount").asInt()).isEqualTo(1);
        var replacement=versions.submit(identity.authenticate("Bearer dev-annotator-chen"),DRAFT,VersionJson.uuid(),3,true,"补正");assertThat(replacement.path("displayCode").asText()).isEqualTo("V3");
        var next=service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),source(List.of(replacement,(ObjectNode)before.get(1).get("version"))));assertThat(next.path("id")).isNotEqualTo(s.path("id"));assertThat(store.sessions(CASE)).hasSize(2);assertThat(service.snapshots(s).get(0).path("elements")).isEqualTo(before.get(0).path("elements"));
    }
    @Test void invalidReturnTargetsAndUnconfirmedActionsAreRejected() throws Exception {
        var s=create();var b=mapper.createObjectNode().put("reason","补正");b.putArray("targetAssignmentIds").add(VersionJson.uuid());assertThat(command("dev-arbiter-zhao",base(s)+"/return",b,VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("TARGET_ASSIGNMENT_INVALID");
        assertThat(command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":false}"),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();assertThat(command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":true,\"unexpected\":1}"),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();
    }
    @Test void failedComparatorRollsBackCreationAndLocks() {
        var source=source(submitBoth());doThrow(new BusinessException(ErrorCode.CONFLICT)).when(deps).comparePropositions(anyList());assertThatThrownBy(()->service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),source)).isInstanceOf(BusinessException.class);assertThat(store.sessions(CASE)).isEmpty();assertThat(data.read("taskDocument",CASE).path("status").asText()).isEqualTo("ANNOTATING");
    }
    @Test void simultaneousSessionCreationReturnsOneFixedSession() throws Exception {
        var b=source(submitBoth());var pool=Executors.newFixedThreadPool(2);try { var a=pool.submit(()->service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),b));var c=pool.submit(()->service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),b));assertThat(a.get(10,TimeUnit.SECONDS).path("id")).isEqualTo(c.get(10,TimeUnit.SECONDS).path("id"));assertThat(store.sessions(CASE)).hasSize(1); }finally{pool.shutdownNow();}
    }
    void localRecord(String kind,ObjectNode n) { jdbc.update("INSERT INTO workflow_records(object_kind,object_id,payload_json) VALUES (?,?,?)",kind,n.path("id").asText(),n.toString()); }
    @Test void threeIndependentSourcesHaveSeparateIdsAndSharedAgreementGroup() {
        var vs=new ArrayList<>(submitBoth());var snapshot=versionStore.snapshot(vs.get(0).path("id").asText()).deepCopy();var v=(ObjectNode)snapshot.get("version");
        var assignment=data.read("assignment",v.path("assignmentId").asText()).deepCopy();var member=data.read("member",assignment.path("taskMemberId").asText()).deepCopy();String userId=VersionJson.uuid();
        VersionJson.stamp(member,userId,VersionJson.now());member.put("userId",userId);localRecord("member",member);VersionJson.stamp(assignment,userId,VersionJson.now());assignment.put("userId",userId);assignment.put("taskMemberId",member.path("id").asText());localRecord("assignment",assignment);
        var user=data.read("user",vs.get(0).path("submittedById").asText()).deepCopy();user.put("id",userId);localRecord("user",user);
        VersionJson.stamp(v,userId,VersionJson.now());v.put("submittedById",userId);v.put("assignmentId",assignment.path("id").asText());v.put("sourceDraftId",VersionJson.uuid());v.put("versionNumber",3);v.put("displayCode","V3");
        var remap=new HashMap<String,String>();for(String field:List.of("elements","relations"))for(var n:snapshot.path(field))remap.put(n.path("id").asText(),VersionJson.uuid());
        for(String field:List.of("elements","relations"))for(var n:snapshot.path(field)){var o=(ObjectNode)n;o.put("id",remap.get(o.path("id").asText()));o.put("ownerId",v.path("id").asText());}
        for(var p:snapshot.path("participants")){var o=(ObjectNode)p;o.put("id",VersionJson.uuid());o.put("relationId",remap.get(o.path("relationId").asText()));o.put("targetId",remap.get(o.path("targetId").asText()));}
        var eids=v.putArray("elementIds");snapshot.path("elements").forEach(e->eids.add(e.path("id").asText()));var rids=v.putArray("relationIds");snapshot.path("relations").forEach(r->rids.add(r.path("id").asText()));versionStore.insert(snapshot);vs.add(v);
        var doc=data.read("taskDocument",CASE);doc.put("requiredAnnotators",3);data.replace("taskDocument",doc);var s=service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,VersionJson.uuid(),source(vs));
        assertThat(s.path("sourceVersionIds").size()).isEqualTo(3);for(var i:store.items(s.path("id").asText(),"PROPOSITION"))assertThat(i.path("candidateElementIds").size()).isEqualTo(3);
        assertThat(versions.progress(identity.authenticate("Bearer dev-arbiter-zhao"),TASK).path("items").get(0).path("submittedCount").asInt()).isEqualTo(3);
    }
    @Test void exactlyEightyPercentCannotCreateSession() throws Exception {
        var vs=submitBoth();var checkpoint=data.read("checkpoint",vs.get(0).path("segmentationCheckpointId").asText());checkpoint.put("agreementScore",0.8);data.replace("checkpoint",checkpoint);
        assertThat(command("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/adjudication-sessions",source(vs),VersionJson.uuid(),1).path("success").asBoolean()).isFalse();assertThat(store.sessions(CASE)).isEmpty();
    }
    @Test void deletedPropositionCannotRemainRelationEndpoint() throws Exception {
        var s=create();String id=s.path("id").asText();
        for(var item:store.items(id,"PROPOSITION")){String action="AGREEMENT".equals(item.path("conflictType").asText())?"CONFIRM_AGREEMENT":"DELETE";assertThat(command("dev-arbiter-zhao","/api/local/member-d/adjudication/proposition-items/"+item.path("id").asText()+"/fixture-decisions",mapper.createObjectNode().put("action",action).put("reason","核查"),VersionJson.uuid(),1).path("success").asBoolean()).isTrue();}
        var now=store.session(id,false);assertThat(command("dev-arbiter-zhao",base(s)+"/propositions/complete",body("{\"confirmed\":true}"),VersionJson.uuid(),now.path("recordVersion").asInt()).path("success").asBoolean()).isTrue();
        var item=store.items(id,"RELATION").get(0);var b=mapper.createObjectNode().put("action","ADOPT_VERSION").put("reason","采用").put("sourceVersionId",s.path("sourceVersionIds").get(0).asText()).put("sourceObjectId",item.path("candidateRelationIds").get(0).asText());
        assertThat(command("dev-arbiter-zhao","/api/v1/relation-adjudication-items/"+item.path("id").asText()+"/decisions",b,VersionJson.uuid(),1).path("error").path("code").asText()).isEqualTo("INVALID_RELATION_MAPPING");assertThat(store.item(item.path("id").asText(),"RELATION").path("status").asText()).isEqualTo("OPEN");
    }
    @Test void revokedAssignmentDeniesSuccessfulIdempotentRetry() throws Exception {
        var b=source(submitBoth());String key=VersionJson.uuid();service.create(identity.authenticate("Bearer dev-arbiter-zhao"),CASE,key,b);
        var a=data.find("assignment","taskDocumentId",CASE).stream().filter(n->"ARBITER".equals(n.path("assignmentRole").asText())).findFirst().orElseThrow();a.put("status","INACTIVE");data.replace("assignment",a);
        assertThat(command("dev-arbiter-zhao","/api/v1/task-documents/"+CASE+"/adjudication-sessions",b,key,1).path("error").path("code").asText()).isEqualTo("FORBIDDEN");
    }
}
