package com.argument.segmentationtrial;
import com.argument.common.exception.*;
import com.argument.version.*;
import com.argument.version.local.*;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test")
class SegmentationTrialWorkflowTest {
 static final String CASE="3cbb140f-0274-4871-8248-194271101a14";
 @Autowired SegmentationTrialService service;@Autowired SegmentationTrialRepository store;@Autowired LocalFixtureLoader loader;@Autowired LocalAnnotationDependencies data;@Autowired VersionIdentity identity;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired VersionService versions;
 @BeforeEach void setup()throws Exception{clean();loader.run(null);}
 @AfterEach void after(){clean();}
 void clean(){for(String t:List.of("trial_correction_rounds","trial_segmentation_records","trial_downloads","trial_files","trial_data_events","trial_exports","trial_export_receipts","trial_final_commitments","trial_audit_events","final_review_transitions","final_annotation_versions","adjudication_transitions","adjudication_returned_versions","adjudication_decisions","adjudication_items","adjudication_sessions","annotation_mutation_receipts","snapshot_object_owners","annotation_version_snapshots","annotation_versions","annotation_case_counters","workflow_records"))jdbc.update("DELETE FROM "+t);}
 VersionIdentity.Actor actor(String role){return identity.authenticate("Bearer dev-"+role);}
 ObjectNode input(int... ends){var n=mapper.createObjectNode();var a=n.putArray("boundaries");int start=5;for(int end:ends){a.addObject().put("startOffset",start).put("endOffset",end);start=end;}return n;}
 ObjectNode calculate(){service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(20,39));service.submit(actor("annotator-liu"),CASE,VersionJson.uuid(),input(20,39));return service.calculate(actor("arbiter-zhao"),CASE,VersionJson.uuid(),mapper.createObjectNode());}
 ObjectNode confirm(ObjectNode calc){var n=mapper.createObjectNode().put("calculationId",calc.path("id").asText()).put("reason","协商确认原文边界");n.set("unifiedBoundaries",input(20,39).get("boundaries"));return n;}
 @Test void realInputsCalculateConfirmBindAndSubmitWithoutFixtureCheckpoint(){String key=VersionJson.uuid();var calc=calculate();var command=confirm(calc);var cp=service.confirm(actor("arbiter-zhao"),CASE,key,command);assertThat(service.confirm(actor("arbiter-zhao"),CASE,key,command)).isEqualTo(cp);assertThat(cp.path("agreementScore").asDouble()).isEqualTo(1);String draft="f67848e1-a06d-4600-8454-19055e720d2c";service.bind(actor("annotator-chen"),CASE,VersionJson.uuid(),1,mapper.createObjectNode().put("draftId",draft).put("checkpointId",cp.path("id").asText()));var version=versions.submit(actor("annotator-chen"),draft,VersionJson.uuid(),2,true,"");assertThat(version.path("segmentationCheckpointId")).isEqualTo(cp.get("id"));assertThat(store.list("SUBMISSION",CASE)).hasSize(2);var doc=data.read("taskDocument",CASE).put("status","ADJUDICATING");data.replace("taskDocument",doc);assertThat(service.workspace(actor("arbiter-zhao"),CASE).path("canWrite").asBoolean()).isFalse();assertThat(service.confirm(actor("arbiter-zhao"),CASE,key,command)).isEqualTo(cp);assertThatThrownBy(()->service.submit(actor("annotator-liu"),CASE,VersionJson.uuid(),input(20,39))).isInstanceOf(BusinessException.class).hasMessageContaining("标注阶段");}
 @Test void latestSubmissionInvalidatesOldCalculationAndOldCheckpointStaysImmutable(){var calc=calculate();service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(19,39));assertThatThrownBy(()->service.confirm(actor("arbiter-zhao"),CASE,VersionJson.uuid(),confirm(calc))).isInstanceOf(BusinessException.class).hasMessageContaining("重新计算");assertThat(store.list("CALCULATION",CASE)).hasSize(1);assertThat(store.list("CHECKPOINT",CASE)).isEmpty();}
 @Test void insufficientDistinctPeopleAndBlindReadsAreEnforced(){service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(20,39));service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(20,39));assertThatThrownBy(()->service.calculate(actor("arbiter-zhao"),CASE,VersionJson.uuid(),mapper.createObjectNode())).isInstanceOf(BusinessException.class).hasMessageContaining("N 名");assertThat(service.workspace(actor("annotator-liu"),CASE).path("submissions")).isEmpty();assertThatThrownBy(()->service.workspace(actor("creator-li"),CASE)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->service.confirm(actor("annotator-chen"),CASE,VersionJson.uuid(),mapper.createObjectNode())).isInstanceOf(BusinessException.class);}
 @Test void malformedBoundariesAndInactiveMembershipAreRejected(){assertThatThrownBy(()->service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(4,39))).isInstanceOf(BusinessException.class);var calc=calculate();var assignment=data.find("assignment","userId",actor("annotator-liu").id()).get(0);assignment.put("status","INACTIVE");data.replace("assignment",assignment);assertThatThrownBy(()->service.confirm(actor("arbiter-zhao"),CASE,VersionJson.uuid(),confirm(calc))).isInstanceOf(BusinessException.class);}
 @Test void cannotCutInsideAnEmojiSurrogatePair(){var text=data.find("text",null,null).get(0);text.put("normalizedText","abcde😀xx").put("contentSha256",VersionJson.sha256("abcde😀xx"));data.replace("text",text);var scope=data.find("scope",null,null).get(0).put("startOffset",5).put("endOffset",9);data.replace("scope",scope);assertThatThrownBy(()->service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(6,9))).isInstanceOf(BusinessException.class).hasMessageContaining("代理对");assertThat(service.submit(actor("annotator-chen"),CASE,VersionJson.uuid(),input(7,9)).path("boundaries")).hasSize(2);}
}
