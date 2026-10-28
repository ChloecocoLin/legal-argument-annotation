package com.argument.integration;

import com.argument.version.*;
import com.argument.version.local.*;
import com.argument.segmentationtrial.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class IndependentSegmentationIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    @Autowired LocalFixtureLoader fixtures;@Autowired LocalAccountLoader accounts;@Autowired LocalAnnotationDependencies data;
    @Autowired UnifiedVersionIdentity identity;@Autowired SegmentationTrialService trial;
    String creator,chen,liu,arbiter,caseId,taskId,chenAssignment,liuAssignment;
    @BeforeEach void setup()throws Exception{
        clean();fixtures.run(null);accounts.run(null);creator=login("creator.li");chen=login("annotator.chen");liu=login("annotator.liu");arbiter=login("arbiter.zhao");
        var old=data.read("taskDocument","3cbb140f-0274-4871-8248-194271101a14");var command=mapper.createObjectNode().put("name","真实独立切割验收").put("guideVersionId",data.read("task",old.path("taskId").asText()).path("guideVersionId").asText()).put("taskType","FORMAL_RESEARCH").put("defaultRequiredAnnotators",2).put("autoPreannotationEnabled",false).put("expertConfirmationRequired",false).put("adjudicationReasonRequired",true);
        taskId=send("/api/v1/tasks",creator,VersionJson.uuid(),command,null,200).path("data").path("id").asText();
        var binding=mapper.createObjectNode().put("requiredAnnotators",2);for(String f:List.of("documentId","documentTextVersionId","annotationScopeId"))binding.set(f,old.get(f));
        caseId=send("/api/v1/tasks/"+taskId+"/documents",creator,VersionJson.uuid(),binding,null,200).path("data").path("id").asText();
        for(var token:List.of(chen,liu,arbiter))send("/api/v1/tasks/"+taskId+"/members",creator,VersionJson.uuid(),mapper.createObjectNode().put("userId",identity.authenticate("Bearer "+token).id()).put("memberRole",token.equals(arbiter)?"ARBITER":"ANNOTATOR"),null,200);
        var assignment=mapper.createObjectNode();var annotators=assignment.putArray("annotatorMemberIds");for(var m:data.find("member","taskId",taskId))if("ANNOTATOR".equals(m.path("memberRole").asText()))annotators.add(m.path("id").asText());else if("ARBITER".equals(m.path("memberRole").asText()))assignment.put("arbiterMemberId",m.path("id").asText());
        var r=mvc.perform(put("/api/v1/task-documents/"+caseId+"/assignments").header("Authorization","Bearer "+creator).header("Idempotency-Key",VersionJson.uuid()).header("If-Match",1).contentType("application/json").content(assignment.toString())).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        send("/api/v1/tasks/"+taskId+"/transitions",creator,VersionJson.uuid(),mapper.createObjectNode().put("action","PUBLISH"),1,200);
        send("/api/v1/tasks/"+taskId+"/transitions",creator,VersionJson.uuid(),mapper.createObjectNode().put("action","START"),data.read("task",taskId).path("recordVersion").asInt(),200);
        for(var a:data.find("assignment","taskDocumentId",caseId))if(a.path("userId").asText().equals(identity.authenticate("Bearer "+chen).id()))chenAssignment=a.path("id").asText();else if(a.path("userId").asText().equals(identity.authenticate("Bearer "+liu).id()))liuAssignment=a.path("id").asText();
    }
    @AfterEach void after(){clean();}
    void clean(){for(String table:List.of("workflow_command_history","trial_correction_rounds","trial_segmentation_records","trial_downloads","trial_files","trial_data_events","trial_exports","trial_export_receipts","trial_final_commitments","trial_audit_events","final_review_transitions","final_annotation_versions","adjudication_transitions","adjudication_returned_versions","adjudication_decisions","adjudication_items","adjudication_sessions","annotation_mutation_receipts","snapshot_object_owners","annotation_version_snapshots","annotation_versions","annotation_case_counters","workflow_records"))jdbc.update("DELETE FROM "+table);}
    String login(String name)throws Exception{var r=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.createObjectNode().put("username",name).put("password","demo").toString())).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(200);return mapper.readTree(r.getContentAsString()).path("data").path("token").asText();}
    ObjectNode send(String url,String token,String key,ObjectNode body,Integer version,int expected)throws Exception{var request=post(url).header("Authorization","Bearer "+token).header("Idempotency-Key",key).contentType("application/json").content(body.toString());if(version!=null)request.header("If-Match",version);var response=mvc.perform(request).andReturn().getResponse();assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);return (ObjectNode)mapper.readTree(response.getContentAsString());}
    ObjectNode input(String assignment){var body=mapper.createObjectNode().put("assignmentId",assignment);body.putArray("boundaries").addObject().put("startOffset",5).put("endOffset",20);body.withArray("boundaries").addObject().put("startOffset",20).put("endOffset",39);return body;}
    String submit(String token,String assignment)throws Exception{send("/api/v1/task-documents/"+caseId+"/segmentation-submissions",token,VersionJson.uuid(),input(assignment),null,200);return data.find("segmentationSubmission","assignmentId",assignment).stream().max(Comparator.comparingInt(s->s.path("attemptNumber").asInt())).orElseThrow().path("id").asText();}
    void review(String id,String action)throws Exception{send("/api/v1/segmentation-submissions/"+id+"/reviews",arbiter,VersionJson.uuid(),mapper.createObjectNode().put("action",action).put("reviewReason","逐条检查原文与连续边界"),1,200);}
    ObjectNode calculate(){return trial.calculate(identity.authenticate("Bearer "+arbiter),caseId,VersionJson.uuid(),mapper.createObjectNode());}
    ObjectNode confirmation(ObjectNode calculation){var body=mapper.createObjectNode().put("calculationId",calculation.path("id").asText()).put("reason","已核对每人审核通过的完整独立输入");body.set("unifiedBoundaries",input(chenAssignment).get("boundaries"));return body;}

    @Test void realNewTaskRequiresReviewedDistinctInputsThenLocksOneCheckpoint()throws Exception{
        String c=submit(chen,chenAssignment),l=submit(liu,liuAssignment);assertThat(data.read("segmentationSubmission",c).path("reviewedAt").isNull()).isTrue();
        assertThatThrownBy(this::calculate).isInstanceOf(com.argument.common.exception.BusinessException.class);
        review(c,"ACCEPT");assertThatThrownBy(this::calculate).isInstanceOf(com.argument.common.exception.BusinessException.class);review(l,"ACCEPT");
        var calculation=calculate();assertThat(calculation.path("aggregateScore").asDouble()).isEqualTo(1);assertThat(calculation.path("inputSubmissionIds")).containsExactlyInAnyOrder(mapper.getNodeFactory().textNode(c),mapper.getNodeFactory().textNode(l));
        var stats=mvc.perform(get("/api/v1/task-documents/"+caseId+"/segmentation-agreement").header("Authorization","Bearer "+creator)).andReturn().getResponse();assertThat(stats.getStatus()).isEqualTo(200);assertThat(mapper.readTree(stats.getContentAsString()).path("data").path("submissionCount").asInt()).isEqualTo(2);
        var cp=trial.confirm(identity.authenticate("Bearer "+arbiter),caseId,VersionJson.uuid(),confirmation(calculation));assertThat(data.read("checkpoint",cp.path("id").asText()).path("sourceSubmissionIds")).hasSize(2);
        send("/api/v1/task-documents/"+caseId+"/segmentation-submissions",chen,VersionJson.uuid(),input(chenAssignment),null,423);
        assertThatThrownBy(()->trial.confirm(identity.authenticate("Bearer "+arbiter),caseId,VersionJson.uuid(),confirmation(calculation))).isInstanceOf(com.argument.common.exception.BusinessException.class);
        assertThat(data.find("draft","taskDocumentId",caseId)).isEmpty();
    }
    @Test void returnedSubmissionIsReplacedByNewIdentityAndAcceptedHistoryCannotBeOverwritten()throws Exception{
        String first=submit(chen,chenAssignment);review(first,"RETURN");
        send("/api/v1/task-documents/"+caseId+"/segmentation-submissions",chen,VersionJson.uuid(),input(chenAssignment),null,409);
        var next=input(chenAssignment).put("supersedesSubmissionId",first);send("/api/v1/task-documents/"+caseId+"/segmentation-submissions",chen,VersionJson.uuid(),next,null,200);
        var second=data.find("segmentationSubmission","assignmentId",chenAssignment).stream().filter(s->s.path("attemptNumber").asInt()==2).findFirst().orElseThrow();assertThat(second.path("id").asText()).isNotEqualTo(first);assertThat(data.read("segmentationSubmission",first).path("status").asText()).isEqualTo("RETURNED");review(second.path("id").asText(),"ACCEPT");
        send("/api/v1/segmentation-submissions/"+second.path("id").asText()+"/reviews",arbiter,VersionJson.uuid(),mapper.createObjectNode().put("action","RETURN").put("reviewReason","不能覆盖原审核"),2,409);
    }
    @Test void receiptsStrictFieldsAndBlindScopeAreEnforced()throws Exception{
        String path="/api/v1/task-documents/"+caseId+"/segmentation-submissions",key=VersionJson.uuid();var body=input(chenAssignment);var response=send(path,chen,key,body,null,200);assertThat(send(path,chen,key,body,null,200).path("data")).isEqualTo(response.path("data"));assertThat(data.find("segmentationSubmission","taskDocumentId",caseId)).hasSize(1);
        var changed=body.deepCopy();((ObjectNode)changed.path("boundaries").get(0)).put("endOffset",19);send(path,chen,key,changed,null,409);send(path,liu,VersionJson.uuid(),body,null,403);send(path,liu,VersionJson.uuid(),input(liuAssignment).put("attemptNumber",99),null,400);
        var blind=mvc.perform(get(path).header("Authorization","Bearer "+liu).param("assignmentId",chenAssignment)).andReturn().getResponse();assertThat(blind.getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/integration/cases/"+caseId+"/segmentation").header("Authorization","Bearer "+creator)).andReturn().getResponse().getStatus()).isEqualTo(403);
        var own=mvc.perform(get(path).header("Authorization","Bearer "+chen)).andReturn().getResponse();assertThat(own.getStatus()).isEqualTo(200);assertThat(mapper.readTree(own.getContentAsString()).path("data").path("items").get(0).path("reviewedAt").isNull()).isTrue();
    }
    @Test void membershipChangeInvalidatesCalculatedInputsAndTrialCannotMixSources()throws Exception{
        String c=submit(chen,chenAssignment),l=submit(liu,liuAssignment);review(c,"ACCEPT");review(l,"ACCEPT");var calculation=calculate();
        var m=data.read("member",data.read("assignment",liuAssignment).path("taskMemberId").asText()).put("status","INACTIVE");data.replace("member",m);
        assertThatThrownBy(()->trial.confirm(identity.authenticate("Bearer "+arbiter),caseId,VersionJson.uuid(),confirmation(calculation))).isInstanceOf(com.argument.common.exception.BusinessException.class);
        assertThatThrownBy(()->trial.submit(identity.authenticate("Bearer "+chen),caseId,VersionJson.uuid(),mapper.createObjectNode().set("boundaries",input(chenAssignment).get("boundaries")))).isInstanceOf(com.argument.common.exception.BusinessException.class);
        assertThat(data.find("checkpoint","taskDocumentId",caseId)).isEmpty();
    }
    @Test void strictOffsetsAndStaleReviewReturnStructuredErrors()throws Exception{
        String path="/api/v1/task-documents/"+caseId+"/segmentation-submissions";var bad=input(chenAssignment);((ObjectNode)bad.path("boundaries").get(0)).put("startOffset",5.5);send(path,chen,VersionJson.uuid(),bad,null,400);assertThat(data.find("segmentationSubmission","taskDocumentId",caseId)).isEmpty();
        String id=submit(chen,chenAssignment);var body=mapper.createObjectNode().put("action","ACCEPT").put("reviewReason","检查边界");var stale=send("/api/v1/segmentation-submissions/"+id+"/reviews",arbiter,VersionJson.uuid(),body,2,409);assertThat(stale.path("error").path("details").get(0).path("currentRecordVersion").asInt()).isEqualTo(1);assertThat(data.read("segmentationSubmission",id).path("status").asText()).isEqualTo("SUBMITTED");
        send("/api/v1/segmentation-submissions/"+id+"/reviews",chen,VersionJson.uuid(),body,1,403);
    }

}
