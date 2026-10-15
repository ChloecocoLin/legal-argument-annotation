package com.argument.dashboard.local;

import com.argument.dashboard.DashboardData;
import com.argument.version.VersionRepository;
import com.argument.finalreview.FinalReviewRepository;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity.Actor;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Local dependency store only; production requires A/B adapters for their authoritative stores. */
@Component
@Profile("(local | test) & !prod")
public class LocalDashboardData implements DashboardData {
    private final AnnotationDependencies data;
    private final VersionRepository versions;
    private final FinalReviewRepository finals;
    public LocalDashboardData(AnnotationDependencies data,VersionRepository versions,FinalReviewRepository finals) { this.data=data;this.versions=versions;this.finals=finals; }
    @Override @Transactional(readOnly=true)
    public Summary summary(Actor actor) {
        boolean admin=actor.role("ADMIN");
        var tasks=data.find("task",null,null).stream().filter(t->admin || actor.id().equals(t.path("ownerUserId").asText())).toList();
        var taskIds=tasks.stream().map(t->t.path("id").asText()).collect(Collectors.toSet());
        var cases=data.find("taskDocument",null,null).stream().filter(d->taskIds.contains(d.path("taskId").asText())).toList();
        var caseIds=cases.stream().map(d->d.path("id").asText()).collect(Collectors.toSet());
        var documentIds=cases.stream().map(d->d.path("documentId").asText()).collect(Collectors.toSet());
        var guideVersionIds=tasks.stream().map(t->t.path("guideVersionId").asText()).collect(Collectors.toSet());
        int guides=(int)data.find("guideVersion",null,null).stream()
                .filter(g->admin || guideVersionIds.contains(g.path("id").asText()))
                .map(g->g.path("guideId").asText()).filter(id->!id.isBlank()).distinct().count();
        int documents=admin ? data.find("document",null,null).size() : documentIds.size();
        int pending=(int)cases.stream().filter(d->!"COMPLETED".equals(d.path("status").asText()))
                .filter(d->finals.versions(d.path("id").asText()).stream().noneMatch(v->"IN_DATABASE".equals(v.path("status").asText())))
                .filter(d->versions.versions(d.path("id").asText()).stream().filter(versions::effective)
                        .map(v->v.path("submittedById").asText()).distinct().count()>=Math.max(2,d.path("requiredAnnotators").asInt())).count();
        int queue=(int)data.find("automationTask",null,null).stream()
                .filter(a->caseIds.contains(a.path("taskDocumentId").asText()))
                .filter(a->Set.of("QUEUED","RUNNING").contains(a.path("status").asText())).count();
        return new Summary(guides,documents,tasks.size(),pending,queue);
    }
}
