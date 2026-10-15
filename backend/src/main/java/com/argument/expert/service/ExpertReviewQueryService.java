package com.argument.expert.service;

import com.argument.adjudication.entity.AdjudicationSession;
import com.argument.adjudication.entity.AnnotationElement;
import com.argument.adjudication.entity.AnnotationVersion;
import com.argument.adjudication.entity.PropositionAdjudicationItem;
import com.argument.adjudication.mapper.AdjudicationSessionMapper;
import com.argument.adjudication.mapper.AnnotationElementMapper;
import com.argument.adjudication.mapper.AnnotationVersionMapper;
import com.argument.adjudication.mapper.PropositionAdjudicationItemMapper;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.expert.dto.ExpertDtos.CandidateGroup;
import com.argument.expert.dto.ExpertDtos.ExpertIssueContextView;
import com.argument.expert.dto.ExpertDtos.ExpertReviewDetailView;
import com.argument.expert.dto.ExpertDtos.ExpertReviewSummaryView;
import com.argument.expert.entity.ExpertIssue;
import com.argument.expert.entity.ExpertReview;
import com.argument.expert.mapper.ExpertIssueMapper;
import com.argument.expert.mapper.ExpertReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * X01 专家待办分页；X02 复核单详情（含 issues 与 contexts）。
 */
@Service
public class ExpertReviewQueryService {

    private final ExpertReviewMapper reviewMapper;
    private final ExpertIssueMapper issueMapper;
    private final AdjudicationSessionMapper sessionMapper;
    private final PropositionAdjudicationItemMapper propositionItemMapper;
    private final AnnotationElementMapper elementMapper;
    private final AnnotationVersionMapper annotationVersionMapper;

    public ExpertReviewQueryService(
            ExpertReviewMapper reviewMapper,
            ExpertIssueMapper issueMapper,
            AdjudicationSessionMapper sessionMapper,
            PropositionAdjudicationItemMapper propositionItemMapper,
            AnnotationElementMapper elementMapper,
            AnnotationVersionMapper annotationVersionMapper) {
        this.reviewMapper = reviewMapper;
        this.issueMapper = issueMapper;
        this.sessionMapper = sessionMapper;
        this.propositionItemMapper = propositionItemMapper;
        this.elementMapper = elementMapper;
        this.annotationVersionMapper = annotationVersionMapper;
    }

    public Map<String, Object> listMyReviews(String currentUserId, String status, String q, int page, int size) {
        LambdaQueryWrapper<ExpertReview> qw = new LambdaQueryWrapper<>();
        qw.eq(ExpertReview::getExpertTaskMemberId, currentUserId);
        if (StringUtils.hasText(status)) {
            qw.eq(ExpertReview::getStatus, status);
        }
        if (StringUtils.hasText(q)) {
            qw.and(w -> w.like(ExpertReview::getId, q.trim()).or().like(ExpertReview::getSessionId, q.trim()));
        }
        qw.orderByDesc(ExpertReview::getCreatedAt);

        Long totalLong = reviewMapper.selectCount(qw);
        int total = totalLong == null ? 0 : totalLong.intValue();

        List<ExpertReview> all = reviewMapper.selectList(qw);
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<ExpertReview> pageList = all.subList(from, to);

        List<ExpertReviewSummaryView> items = new ArrayList<>();
        for (ExpertReview review : pageList) {
            List<ExpertIssue> issues =
                    issueMapper.selectList(
                            new LambdaQueryWrapper<ExpertIssue>()
                                    .eq(ExpertIssue::getExpertReviewId, review.getId()));
            int open = (int) issues.stream().filter(i -> "OPEN".equals(i.getStatus())).count();
            items.add(
                    new ExpertReviewSummaryView(
                            review,
                            review.getSessionId(),
                            "演示裁定任务",
                            review.getSessionId(),
                            "演示法律文书",
                            null,
                            issues.size(),
                            open));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("total", total);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", data);
        result.put("meta", meta);
        return result;
    }

    public ExpertReviewDetailView getDetail(String reviewId, String currentUserId) {
        ExpertReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "复核单不存在");
        }
        if (!currentUserId.equals(review.getExpertTaskMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该复核单不属于当前专家");
        }
        List<ExpertIssue> issues =
                issueMapper.selectList(
                        new LambdaQueryWrapper<ExpertIssue>()
                                .eq(ExpertIssue::getExpertReviewId, reviewId)
                                .orderByAsc(ExpertIssue::getCreatedAt));

        AdjudicationSession session = sessionMapper.selectById(review.getSessionId());

        List<ExpertIssueContextView> contexts = new ArrayList<>();
        for (ExpertIssue issue : issues) {
            contexts.add(buildContext(issue, session));
        }
        return new ExpertReviewDetailView(review, null, issues, contexts);
    }

    private ExpertIssueContextView buildContext(ExpertIssue issue, AdjudicationSession session) {
        if (!"PROPOSITION".equals(issue.getItemKind()) || !StringUtils.hasText(issue.getItemId())) {
            return new ExpertIssueContextView(issue.getId(), issue.getItemKind(), null, List.of(), List.of());
        }
        PropositionAdjudicationItem item = propositionItemMapper.selectById(issue.getItemId());
        if (item == null) {
            return new ExpertIssueContextView(issue.getId(), issue.getItemKind(), null, List.of(), List.of());
        }

        Map<String, AnnotationElement> elemMap = new HashMap<>();
        Set<String> elementIds = new HashSet<>();
        if (item.getCandidateElementIds() != null) {
            elementIds.addAll(item.getCandidateElementIds());
        }
        if (!elementIds.isEmpty()) {
            for (AnnotationElement e : elementMapper.selectBatchIds(elementIds)) {
                elemMap.put(e.getId(), e);
            }
        }

        Map<String, String> versionCodeMap = new HashMap<>();
        if (session != null && session.getSourceVersionIds() != null) {
            for (AnnotationVersion v : annotationVersionMapper.selectBatchIds(session.getSourceVersionIds())) {
                versionCodeMap.put(v.getId(), v.getDisplayCode());
            }
        }

        Map<String, List<AnnotationElement>> byVersion = new LinkedHashMap<>();
        if (item.getCandidateElementIds() != null) {
            for (String cid : item.getCandidateElementIds()) {
                AnnotationElement e = elemMap.get(cid);
                if (e != null) {
                    byVersion.computeIfAbsent(e.getOwnerId(), k -> new ArrayList<>()).add(e);
                }
            }
        }
        List<CandidateGroup> groups = new ArrayList<>();
        List<String> excerpts = new ArrayList<>();
        for (Map.Entry<String, List<AnnotationElement>> entry : byVersion.entrySet()) {
            String vid = entry.getKey();
            String code = versionCodeMap.getOrDefault(vid, vid);
            List<String> texts = new ArrayList<>();
            for (AnnotationElement e : entry.getValue()) {
                texts.add(e.getText());
                excerpts.add(e.getText());
            }
            groups.add(new CandidateGroup(vid, code, texts));
        }
        return new ExpertIssueContextView(issue.getId(), issue.getItemKind(), item, excerpts, groups);
    }
}
