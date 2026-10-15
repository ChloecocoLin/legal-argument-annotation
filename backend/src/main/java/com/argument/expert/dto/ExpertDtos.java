package com.argument.expert.dto;

import com.argument.adjudication.entity.PropositionAdjudicationItem;
import com.argument.expert.entity.ExpertIssue;
import com.argument.expert.entity.ExpertReview;
import java.util.List;

/**
 * 专家复核相关请求与视图对象。集中放置，避免散落。
 */
public final class ExpertDtos {

    private ExpertDtos() {}

    // J12
    public record J12Request(String expertUserId, String itemKind, String itemId, String question) {}

    public record J12ResultView(ExpertReview review, List<ExpertIssue> issues, int sessionRecordVersion) {}

    // X01
    public record ExpertReviewSummaryView(
            ExpertReview review,
            String taskId,
            String taskName,
            String documentId,
            String documentTitle,
            String guideVersionId,
            int issueCount,
            int openIssueCount) {}

    // X02
    public record CandidateGroup(String versionId, String displayCode, List<String> texts) {}

    public record ExpertIssueContextView(
            String issueId,
            String itemKind,
            PropositionAdjudicationItem item,
            List<String> textExcerpts,
            List<CandidateGroup> candidateGroups) {}

    public record ExpertReviewDetailView(
            ExpertReview review,
            String guideVersionId,
            List<ExpertIssue> issues,
            List<ExpertIssueContextView> contexts) {}
}
