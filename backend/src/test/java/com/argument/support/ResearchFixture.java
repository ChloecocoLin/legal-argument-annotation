package com.argument.support;
import com.argument.adjudication.*;
import com.argument.finalreview.*;
import com.argument.finalreview.local.LocalFinalReviewDependencies;
import com.argument.committrial.FinalCommitTrialService;
import com.argument.version.*;
import com.argument.version.port.VersionIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Positive end-to-end research fixture; certificate replacement exists only on the test classpath. */
public class ResearchFixture {
    public static final String CASE="3cbb140f-0274-4871-8248-194271101a14";
    private final com.argument.version.port.AnnotationDependencies data;private final VersionService versions;private final VersionIdentity identity;private final AdjudicationService adjudication;private final AdjudicationRepository sessions;private final FinalReviewService finals;private final FinalCommitTrialService commits;private final LocalFinalReviewDependencies deps;private final ObjectMapper mapper;
    public ResearchFixture(com.argument.version.port.AnnotationDependencies data,VersionService versions,VersionIdentity identity,AdjudicationService adjudication,AdjudicationRepository sessions,FinalReviewService finals,FinalCommitTrialService commits,LocalFinalReviewDependencies deps,ObjectMapper mapper){this.data=data;this.versions=versions;this.identity=identity;this.adjudication=adjudication;this.sessions=sessions;this.finals=finals;this.commits=commits;this.deps=deps;this.mapper=mapper;}
    public ObjectNode create(boolean commit){return create(commit,true);}
    public ObjectNode create(boolean commit,boolean approve){var actor=identity.authenticate("Bearer dev-arbiter-zhao");var a=versions.submit(identity.authenticate("Bearer dev-annotator-chen"),"f67848e1-a06d-4600-8454-19055e720d2c",VersionJson.uuid(),data.read("draft","f67848e1-a06d-4600-8454-19055e720d2c").path("recordVersion").asInt(),true,"");var b=versions.submit(identity.authenticate("Bearer dev-annotator-liu"),"ef21c243-05a2-4837-92b4-d56f3fb77639",VersionJson.uuid(),data.read("draft","ef21c243-05a2-4837-92b4-d56f3fb77639").path("recordVersion").asInt(),true,"");var body=mapper.createObjectNode();body.putArray("sourceVersionIds").add(a.path("id").asText()).add(b.path("id").asText());var s=adjudication.create(actor,CASE,VersionJson.uuid(),body);String sid=s.path("id").asText();
        for(var item:sessions.items(sid,"PROPOSITION")){var cmd=mapper.createObjectNode().put("reason","来源检查");if("AGREEMENT".equals(item.path("conflictType").asText()))cmd.put("action","CONFIRM_AGREEMENT");else{String object=item.path("candidateElementIds").get(0).asText();cmd.put("action","ADOPT_VERSION").put("sourceObjectId",object);for(var source:adjudication.snapshots(s))for(var e:source.path("elements"))if(object.equals(e.path("id").asText()))cmd.put("sourceVersionId",source.path("version").path("id").asText());}adjudication.decideLocalProposition(actor,item.path("id").asText(),VersionJson.uuid(),1,cmd);}
        adjudication.complete(actor,sid,VersionJson.uuid(),sessions.session(sid,false).path("recordVersion").asInt(),mapper.createObjectNode().put("confirmed",true),"PROPOSITION");for(var item:sessions.items(sid,"RELATION")){adjudication.decideRelation(actor,item.path("id").asText(),VersionJson.uuid(),1,mapper.createObjectNode().put("action","ADOPT_VERSION").put("reason","关系检查").put("sourceObjectId",item.path("candidateRelationIds").get(0).asText()).put("sourceVersionId",a.path("id").asText()));}adjudication.complete(actor,sid,VersionJson.uuid(),sessions.session(sid,false).path("recordVersion").asInt(),mapper.createObjectNode().put("confirmed",true),"RELATION");var result=finals.create(actor,sid,VersionJson.uuid(),1);String id=result.path("id").asText();finals.transition(actor,id,VersionJson.uuid(),1,"FV03",mapper.createObjectNode().put("action","SUBMIT"));doAnswer(inv->{ObjectNode snapshot=inv.getArgument(0);return new FinalReviewDependencies.Evidence(true,"PRODUCTION",FinalReviewService.contentDigest(snapshot),snapshot.path("decisionDigest").asText(),snapshot.path("version").path("guideVersionId").asText(),List.of());}).when(deps).assess(any(),any());if(approve)finals.transition(actor,id,VersionJson.uuid(),2,"FV05",mapper.createObjectNode().put("confirmed",true));if(commit)commits.commit(actor,id,VersionJson.uuid(),3,mapper.createObjectNode().put("confirmed",true),VersionJson.uuid());return finals.read(actor,id);
    }
}
