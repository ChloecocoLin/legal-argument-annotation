package com.argument.adjudication.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.argument.adjudication.dto.J03View;
import com.argument.adjudication.dto.PropositionItemView;
import com.argument.adjudication.dto.VersionElementGroup;
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
 * J03 命题裁定项只读查询。按源标注版本组织候选要素。
 */
@Service
public class ReviewQueryService {

    private final AdjudicationSessionMapper sessionMapper;
    private final PropositionAdjudicationItemMapper itemMapper;
    private final AnnotationElementMapper elementMapper;
    private final AnnotationVersionMapper annotationVersionMapper;

    public ReviewQueryService(
            AdjudicationSessionMapper sessionMapper,
            PropositionAdjudicationItemMapper itemMapper,
            AnnotationElementMapper elementMapper,
            AnnotationVersionMapper annotationVersionMapper) {
        this.sessionMapper = sessionMapper;
        this.itemMapper = itemMapper;
        this.elementMapper = elementMapper;
        this.annotationVersionMapper = annotationVersionMapper;
    }

    public J03View listPropositionItems(String sessionId, String status, String conflictType) {
        AdjudicationSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "裁定会话不存在");
        }

        LambdaQueryWrapper<PropositionAdjudicationItem> qw = new LambdaQueryWrapper<>();
        qw.eq(PropositionAdjudicationItem::getSessionId, sessionId);
        if (StringUtils.hasText(status)) {
            qw.eq(PropositionAdjudicationItem::getStatus, status);
        }
        if (StringUtils.hasText(conflictType)) {
            qw.eq(PropositionAdjudicationItem::getConflictType, conflictType);
        }
        qw.orderByAsc(PropositionAdjudicationItem::getCreatedAt);
        List<PropositionAdjudicationItem> items = itemMapper.selectList(qw);

        // 收集全部候选要素 id，一次查出
        Set<String> elementIds = new HashSet<>();
        for (PropositionAdjudicationItem item : items) {
            if (item.getCandidateElementIds() != null) {
                elementIds.addAll(item.getCandidateElementIds());
            }
        }
        Map<String, AnnotationElement> elemMap = new HashMap<>();
        if (!elementIds.isEmpty()) {
            for (AnnotationElement e : elementMapper.selectBatchIds(elementIds)) {
                elemMap.put(e.getId(), e);
            }
        }

        // 源标注版本 id -> displayCode
        Map<String, String> versionCodeMap = new HashMap<>();
        if (session.getSourceVersionIds() != null && !session.getSourceVersionIds().isEmpty()) {
            for (AnnotationVersion v : annotationVersionMapper.selectBatchIds(session.getSourceVersionIds())) {
                versionCodeMap.put(v.getId(), v.getDisplayCode());
            }
        }

        List<PropositionItemView> views = new ArrayList<>();
        for (PropositionAdjudicationItem item : items) {
            Map<String, List<AnnotationElement>> byVersion = new LinkedHashMap<>();
            if (item.getCandidateElementIds() != null) {
                for (String cid : item.getCandidateElementIds()) {
                    AnnotationElement e = elemMap.get(cid);
                    if (e != null) {
                        byVersion.computeIfAbsent(e.getOwnerId(), k -> new ArrayList<>()).add(e);
                    }
                }
            }
            List<VersionElementGroup> groups = new ArrayList<>();
            for (Map.Entry<String, List<AnnotationElement>> entry : byVersion.entrySet()) {
                String vid = entry.getKey();
                String code = versionCodeMap.getOrDefault(vid, vid);
                groups.add(new VersionElementGroup(vid, code, entry.getValue()));
            }
            // currentDecision 独立于 item.status，J04 接入前为 null。
            views.add(new PropositionItemView(item, groups, null));
        }

        return new J03View(session, views);
    }
}
