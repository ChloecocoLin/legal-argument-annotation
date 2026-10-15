package com.argument.expert.service;

import com.argument.adjudication.entity.AdjudicationSession;
import com.argument.adjudication.entity.PropositionAdjudicationItem;
import com.argument.adjudication.mapper.AdjudicationSessionMapper;
import com.argument.adjudication.mapper.PropositionAdjudicationItemMapper;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.expert.dto.ExpertDtos.J12Request;
import com.argument.expert.dto.ExpertDtos.J12ResultView;
import com.argument.expert.entity.ExpertIssue;
import com.argument.expert.entity.ExpertReview;
import com.argument.expert.mapper.ExpertIssueMapper;
import com.argument.expert.mapper.ExpertReviewMapper;
import com.argument.expert.provider.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * J12 提交疑难问题；X03 接受复核；X05 确认完成。写操作都带 If-Match 乐观锁。
 */
@Service
public class ExpertReviewCommandService {

    private final ExpertReviewMapper reviewMapper;
    private final ExpertIssueMapper issueMapper;
    private final AdjudicationSessionMapper sessionMapper;
    private final PropositionAdjudicationItemMapper propositionItemMapper;
    private final ExpertAssignmentService assignmentService;
    private final CurrentUserProvider currentUserProvider;

    public ExpertReviewCommandService(
            ExpertReviewMapper reviewMapper,
            ExpertIssueMapper issueMapper,
            AdjudicationSessionMapper sessionMapper,
            PropositionAdjudicationItemMapper propositionItemMapper,
            ExpertAssignmentService assignmentService,
            CurrentUserProvider currentUserProvider) {
        this.reviewMapper = reviewMapper;
        this.issueMapper = issueMapper;
        this.sessionMapper = sessionMapper;
        this.propositionItemMapper = propositionItemMapper;
        this.assignmentService = assignmentService;
        this.currentUserProvider = currentUserProvider;
    }

    // J12
    public J12ResultView escalate(String sessionId, J12Request req) {
        AdjudicationSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "裁定会话不存在");
        }
        if (!StringUtils.hasText(req.expertUserId())
                || !assignmentService.isAssigned(sessionId, req.expertUserId())) {
            throw new BusinessException(ErrorCode.EXPERT_NOT_ASSIGNED, "该专家未被指派给此会话");
        }

        String itemKind = req.itemKind();
        String itemId = req.itemId();
        String question = req.question() == null ? "" : req.question().trim();
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "question 不能为空");
        }
        // 校验挂载项。GENERAL 必须无 itemId；RELATION 现阶段未建模，直接拒绝。
        if ("GENERAL".equals(itemKind)) {
            if (itemId != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "GENERAL 问题的 itemId 必须为 null");
            }
        } else if ("PROPOSITION".equals(itemKind)) {
            if (!StringUtils.hasText(itemId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PROPOSITION 问题必须给出 itemId");
            }
            PropositionAdjudicationItem item = propositionItemMapper.selectById(itemId);
            if (item == null || !sessionId.equals(item.getSessionId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "命题项不属于本会话");
            }
        } else if ("RELATION".equals(itemKind)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "RELATION 问题暂未支持");
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知 itemKind");
        }

        // 同一会话、同一专家，只复用一张未完成（REQUESTED/IN_REVIEW）复核单。
        LambdaQueryWrapper<ExpertReview> activeQw = new LambdaQueryWrapper<>();
        activeQw.eq(ExpertReview::getSessionId, sessionId);
        activeQw.eq(ExpertReview::getExpertTaskMemberId, req.expertUserId());
        activeQw.in(ExpertReview::getStatus, List.of("REQUESTED", "IN_REVIEW"));
        activeQw.orderByDesc(ExpertReview::getCreatedAt);
        List<ExpertReview> actives = reviewMapper.selectList(activeQw);

        ExpertReview review;
        if (actives.isEmpty()) {
            review = new ExpertReview();
            review.setSessionId(sessionId);
            review.setExpertTaskMemberId(req.expertUserId());
            review.setRequestedById(currentUserProvider.getCurrentUserId());
            review.setStatus("REQUESTED");
            review.setRecordVersion(0);
            reviewMapper.insert(review);
        } else {
            review = actives.get(0);
        }

        // 在该复核单下按 (itemKind, itemId, 标准化 question) 去重。
        List<ExpertIssue> issues =
                issueMapper.selectList(
                        new LambdaQueryWrapper<ExpertIssue>()
                                .eq(ExpertIssue::getExpertReviewId, review.getId())
                                .orderByAsc(ExpertIssue::getCreatedAt));
        for (ExpertIssue existing : issues) {
            boolean sameKind = java.util.Objects.equals(existing.getItemKind(), itemKind);
            boolean sameItem = java.util.Objects.equals(existing.getItemId(), itemId);
            boolean sameQuestion =
                    existing.getQuestion() != null && existing.getQuestion().trim().equals(question);
            if (sameKind && sameItem && sameQuestion) {
                // 安全重试：直接返回已有复核单与问题。
                return new J12ResultView(review, issues, session.getRecordVersion());
            }
        }

        // 同裁定项但问题文本不同：在已有复核单下新增问题。
        ExpertIssue issue = new ExpertIssue();
        issue.setExpertReviewId(review.getId());
        issue.setItemKind(itemKind);
        issue.setItemId(itemId);
        issue.setQuestion(question);
        issue.setStatus("OPEN");
        issue.setRecordVersion(0);
        issueMapper.insert(issue);

        List<ExpertIssue> all =
                issueMapper.selectList(
                        new LambdaQueryWrapper<ExpertIssue>()
                                .eq(ExpertIssue::getExpertReviewId, review.getId())
                                .orderByAsc(ExpertIssue::getCreatedAt));
        return new J12ResultView(review, all, session.getRecordVersion());
    }

    // X03 接受复核申请
    public ExpertReview acceptReview(String reviewId, String ifMatch) {
        ExpertReview review = mustFindReview(reviewId);
        checkOwnership(review);
        requireVersion(ifMatch, review.getRecordVersion());
        if (!"REQUESTED".equals(review.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "只有 REQUESTED 状态可接受");
        }
        review.setStatus("IN_REVIEW");
        review.setOpenedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        return review;
    }

    // X05 确认完成
    public ExpertReview confirmReview(String reviewId, Boolean confirmed, String ifMatch) {
        ExpertReview review = mustFindReview(reviewId);
        checkOwnership(review);
        if (confirmed == null || !confirmed) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "confirmed 必须为 true");
        }
        if (!"IN_REVIEW".equals(review.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "只有 IN_REVIEW 状态可确认");
        }
        List<ExpertIssue> issues =
                issueMapper.selectList(
                        new LambdaQueryWrapper<ExpertIssue>()
                                .eq(ExpertIssue::getExpertReviewId, reviewId));
        boolean hasOpen = issues.stream().anyMatch(i -> "OPEN".equals(i.getStatus()));
        if (hasOpen) {
            throw new BusinessException(ErrorCode.OPEN_EXPERT_ISSUES, "仍有未解决的问题");
        }
        requireVersion(ifMatch, review.getRecordVersion());
        review.setStatus("CONFIRMED");
        review.setConfirmedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        return review;
    }

    private ExpertReview mustFindReview(String reviewId) {
        ExpertReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "复核单不存在");
        }
        return review;
    }

    private void checkOwnership(ExpertReview review) {
        String current = currentUserProvider.getCurrentUserId();
        if (!current.equals(review.getExpertTaskMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该复核单不属于当前专家");
        }
    }

    private void requireVersion(String ifMatch, Integer currentVersion) {
        if (!StringUtils.hasText(ifMatch)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少 If-Match 请求头");
        }
        int expected;
        try {
            expected = Integer.parseInt(ifMatch.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match 必须是数字版本号");
        }
        if (currentVersion == null || expected != currentVersion) {
            throw new BusinessException(ErrorCode.CONFLICT, "记录版本已变更，请刷新后重试");
        }
    }
}
