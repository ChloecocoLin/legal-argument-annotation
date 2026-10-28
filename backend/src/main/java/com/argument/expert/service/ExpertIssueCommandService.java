package com.argument.expert.service;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.expert.entity.ExpertIssue;
import com.argument.expert.entity.ExpertReview;
import com.argument.expert.mapper.ExpertIssueMapper;
import com.argument.expert.mapper.ExpertReviewMapper;
import com.argument.expert.provider.CurrentUserProvider;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * X04 专家提交问题结论与指南依据。OPEN -> RESOLVED，带 If-Match 乐观锁。
 */
// Standalone adapter superseded by the shared workflow; not component-scanned.
public class ExpertIssueCommandService {

    private final ExpertIssueMapper issueMapper;
    private final ExpertReviewMapper reviewMapper;
    private final CurrentUserProvider currentUserProvider;

    public ExpertIssueCommandService(
            ExpertIssueMapper issueMapper,
            ExpertReviewMapper reviewMapper,
            CurrentUserProvider currentUserProvider) {
        this.issueMapper = issueMapper;
        this.reviewMapper = reviewMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public ExpertIssue resolveIssue(String issueId, String resolution, String guideBasis, String ifMatch) {
        ExpertIssue issue = issueMapper.selectById(issueId);
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问题不存在");
        }
        if (!StringUtils.hasText(guideBasis)) {
            throw new BusinessException(ErrorCode.GUIDE_BASIS_REQUIRED, "必须提供指南依据");
        }
        if (!StringUtils.hasText(resolution)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "resolution 不能为空");
        }
        if (!"OPEN".equals(issue.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "只有 OPEN 状态的问题可提交结论");
        }

        ExpertReview review = reviewMapper.selectById(issue.getExpertReviewId());
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "复核单不存在");
        }
        String current = currentUserProvider.getCurrentUserId();
        if (!current.equals(review.getExpertTaskMemberId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该问题不属于当前专家");
        }

        if (!StringUtils.hasText(ifMatch)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少 If-Match 请求头");
        }
        int expected;
        try {
            expected = Integer.parseInt(ifMatch.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match 必须是数字版本号");
        }
        if (issue.getRecordVersion() == null || expected != issue.getRecordVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT, "问题记录版本已变更，请刷新后重试");
        }

        issue.setResolution(resolution.trim());
        issue.setGuideBasis(guideBasis.trim());
        issue.setStatus("RESOLVED");
        issue.setResolvedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        return issue;
    }
}
