package com.argument.dashboard;

import com.argument.common.exception.*;
import com.argument.dashboard.local.LocalDashboardData;
import com.argument.exportpreview.LocalExportPreviewController;
import com.argument.finalreview.FinalReviewRepository;
import com.argument.version.VersionRepository;
import com.argument.version.local.LocalVersionIdentity;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class DashboardAndExportPreviewTest {
    static final String TASK="f102c523-b4ed-4323-81d3-0c564c4141a0", CASE="3cbb140f-0274-4871-8248-194271101a14";
    final ObjectMapper mapper=new ObjectMapper();
    AnnotationDependencies data;VersionRepository versions;FinalReviewRepository finals;MockMvc mvc;
    final Map<String,List<ObjectNode>> records=new HashMap<>();
    @BeforeEach void setup() throws Exception {
        data=mock(AnnotationDependencies.class);versions=mock(VersionRepository.class);finals=mock(FinalReviewRepository.class);records.clear();
        try(var input=getClass().getResourceAsStream("/fixtures/member-d-ready-submission.json")) {
            for(var e:mapper.readTree(input).path("records"))records.computeIfAbsent(e.path("kind").asText(),k->new ArrayList<>()).add((ObjectNode)e.path("value"));
        }
        when(data.find(anyString(),nullable(String.class),nullable(String.class))).thenAnswer(i->records.getOrDefault(i.getArgument(0),List.of()).stream().filter(n->i.getArgument(1)==null || n.path(i.<String>getArgument(1)).asText().equals(i.<String>getArgument(2))).toList());
        when(data.read(anyString(),anyString())).thenAnswer(i->record(i.getArgument(0),i.getArgument(1)));
        when(versions.versions(anyString())).thenReturn(List.of());when(finals.versions(anyString())).thenReturn(List.of());
        when(versions.effective(any())).thenAnswer(i->!"WITHDRAWN".equals(((ObjectNode)i.getArgument(0)).path("status").asText()));
        var identity=new LocalVersionIdentity(data);
        mvc=MockMvcBuilders.standaloneSetup(new DashboardController(new LocalDashboardData(data,versions,finals),identity),new LocalExportPreviewController(data,finals,identity,mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    ObjectNode record(String kind,String id) {return records.getOrDefault(kind,List.of()).stream().filter(n->id.equals(n.path("id").asText())).findFirst().orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND));}
    void add(String kind,ObjectNode n) {records.computeIfAbsent(kind,k->new ArrayList<>()).add(n);}
    ObjectNode response(String token,String path,int expected) throws Exception {
        var req=get(path);if(token!=null)req.header("Authorization","Bearer "+token);
        var r=mvc.perform(req).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(expected);return mapper.readValue(r.getContentAsString(),ObjectNode.class);
    }
    @Test void summaryKeepsFrozenShapeAndCountsGuidesNotVersions() throws Exception {
        var another=records.get("guideVersion").get(0).deepCopy().put("id",UUID.randomUUID().toString());add("guideVersion",another);
        var r=response("dev-admin-wang","/api/v1/dashboard/summary?page=2",200);
        assertThat(r.fieldNames()).toIterable().containsExactlyInAnyOrder("success","data","meta","error","requestId","timestamp");
        assertThat(r.path("data").fieldNames()).toIterable().containsExactlyInAnyOrder("guideCount","documentCount","taskCount","pendingReviews","autoQueueCount");
        assertThat(r.path("data").path("guideCount").asInt()).isEqualTo(1);assertThat(r.path("data").path("taskCount").asInt()).isEqualTo(1);
        assertThat(r.path("data").path("documentCount").asInt()).isEqualTo(1);assertThat(r.path("meta").isNull()).isTrue();
    }
    @Test void creatorCountsCannotIncludeAnotherOwnersTasksOrDocuments() throws Exception {
        add("task",records.get("task").get(0).deepCopy().put("id","foreign-task").put("ownerUserId","someone-else"));
        add("taskDocument",records.get("taskDocument").get(0).deepCopy().put("id","foreign-case").put("taskId","foreign-task").put("documentId","foreign-document"));
        add("document",records.get("document").get(0).deepCopy().put("id","foreign-document"));
        var own=response("dev-creator-li","/api/v1/dashboard/summary",200).path("data");var admin=response("dev-admin-wang","/api/v1/dashboard/summary",200).path("data");
        assertThat(own.path("taskCount").asInt()).isEqualTo(1);assertThat(own.path("documentCount").asInt()).isEqualTo(1);
        assertThat(admin.path("taskCount").asInt()).isEqualTo(2);assertThat(admin.path("documentCount").asInt()).isEqualTo(2);
    }
    @Test void pendingCasesCountDistinctEffectiveAuthorsAndExcludeCommittedResults() throws Exception {
        var a=mapper.createObjectNode().put("submittedById","a").put("status","SUBMITTED");
        var b=mapper.createObjectNode().put("submittedById","b").put("status","WITHDRAWN");
        when(versions.versions(CASE)).thenReturn(List.of(a,a.deepCopy(),b));
        assertThat(response("dev-admin-wang","/api/v1/dashboard/summary",200).path("data").path("pendingReviews").asInt()).isZero();
        b.put("status","ADJUDICATED");
        assertThat(response("dev-admin-wang","/api/v1/dashboard/summary",200).path("data").path("pendingReviews").asInt()).isEqualTo(1);
        when(finals.versions(CASE)).thenReturn(List.of(mapper.createObjectNode().put("status","IN_DATABASE")));
        assertThat(response("dev-admin-wang","/api/v1/dashboard/summary",200).path("data").path("pendingReviews").asInt()).isZero();
    }
    @Test void queueCountsOnlyQueuedAndRunningWithinOwnedCases() throws Exception {
        for(String status:List.of("QUEUED","RUNNING","COMPLETED","FAILED","CANCELLED"))add("automationTask",mapper.createObjectNode().put("taskDocumentId",CASE).put("status",status));
        add("automationTask",mapper.createObjectNode().put("taskDocumentId","other-case").put("status","QUEUED"));
        assertThat(response("dev-creator-li","/api/v1/dashboard/summary",200).path("data").path("autoQueueCount").asInt()).isEqualTo(2);
    }
    @Test void authenticationPermissionsAndUndefinedScopeFailClosed() throws Exception {
        response(null,"/api/v1/dashboard/summary",401);response("dev-annotator-chen","/api/v1/dashboard/summary",403);
        response("dev-creator-li","/api/v1/dashboard/summary?scope=all",400);
        for(String query:List.of("page=0","page=abc","page=999999999999999999","ownerId=someone"))response("dev-admin-wang","/api/v1/dashboard/summary?"+query,400);
        records.get("role").stream().filter(r->r.path("code").asText().equals("TASK_CREATOR")).findFirst().orElseThrow().putArray("permissionCodes");
        response("dev-creator-li","/api/v1/dashboard/summary",403);
    }
    @Test void exportPreviewNeverReadsUncommittedContentAndLimitsPreviewToFive() throws Exception {
        var committed=mapper.createObjectNode().put("id","committed").put("status","IN_DATABASE").put("versionNumber",4).put("guideVersionId",records.get("guideVersion").get(0).path("id").asText());
        when(finals.versions(CASE)).thenReturn(List.of(committed.deepCopy().put("id","draft").put("status","DRAFT"),committed.deepCopy().put("id","review").put("status","IN_REVIEW"),committed.deepCopy().put("id","approved").put("status","APPROVED"),committed));
        var snapshot=mapper.createObjectNode();var elements=snapshot.putArray("elements");snapshot.putArray("relations");
        for(int i=1;i<=7;i++)elements.add(records.get("element").get(0).deepCopy().put("displayNo","P"+i));
        when(finals.snapshot("committed")).thenReturn(snapshot);
        var r=response("dev-creator-li","/api/local/member-d/export-preview",200).path("data");assertThat(r.path("exportAvailable").asBoolean()).isFalse();
        assertThat(r.path("items").size()).isEqualTo(1);var item=r.path("items").get(0);
        assertThat(item.path("elementCount").asInt()).isEqualTo(7);assertThat(item.path("preview").size()).isEqualTo(5);
        assertThat(item.path("preview").get(0).path("type").asText()).isEqualTo("GM-L");
        verify(finals).snapshot("committed");verify(finals,never()).snapshot("approved");verify(finals,never()).snapshot("review");verify(finals,never()).snapshot("draft");
        verify(data,never()).replace(anyString(),any());verify(finals,never()).saveVersion(any());
    }
    @Test void exportPreviewDoesNotGrantArbitersOrOtherOwnersExportAccess() throws Exception {
        response("dev-arbiter-zhao","/api/local/member-d/export-preview",403);response("dev-annotator-chen","/api/local/member-d/export-preview",403);
        records.get("task").get(0).put("ownerUserId","someone-else");
        assertThat(response("dev-creator-li","/api/local/member-d/export-preview",200).path("data").path("items").isEmpty()).isTrue();
        verify(finals,never()).versions(anyString());
    }
}
