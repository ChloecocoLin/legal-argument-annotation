package com.argument.adjudication.service.impl;

import com.argument.adjudication.dto.*;
import com.argument.adjudication.entity.*;
import com.argument.adjudication.mapper.*;
import com.argument.adjudication.service.CustomAnnotationService;
import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.guide.entity.GuideVersion;
import com.argument.guide.entity.LabelDefinition;
import com.argument.guide.mapper.GuideVersionMapper;
import com.argument.guide.mapper.LabelDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomAnnotationServiceImpl implements CustomAnnotationService {

    private final AdjudicationSessionMapper sessionMapper;
    private final PropositionAdjudicationItemMapper itemMapper;
    private final AnnotationVersionMapper versionMapper;
    private final AnnotationElementMapper elementMapper;
    private final DocumentTextVersionMapper documentMapper;
    private final AnnotationScopeMapper scopeMapper;
    private final GuideVersionMapper guideVersionMapper;
    private final LabelDefinitionMapper labelMapper;
    private final ArbiterCustomAnnotationMapper customAnnotationMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public CustomContextResponse getContext(String sessionId, String itemKind, String itemId) {
        requirePropositionKind(itemKind);
        AdjudicationSession session = requireSession(sessionId);
        PropositionAdjudicationItem item = requireItem(sessionId, itemId);
        requireEditableSession(session);

        List<AnnotationVersion> sourceVersions = loadInOrder(
                session.getSourceVersionIds(), versionMapper::selectById, "源标注版本不存在");
        if (sourceVersions.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_VERSIONS, "裁定会话缺少源标注版本");
        }

        AnnotationVersion basis = sourceVersions.get(0);
        ensureSameBasis(sourceVersions, basis);
        DocumentTextVersion document = documentMapper.selectById(basis.getDocumentTextVersionId());
        AnnotationScope scope = scopeMapper.selectById(basis.getAnnotationScopeId());
        GuideVersion guideVersion = guideVersionMapper.selectById(basis.getGuideVersionId());
        if (document == null || scope == null || guideVersion == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "裁定上下文依赖的正文、范围或指南版本不存在");
        }
        validateScope(document, scope);
        requireLockedGuideVersion(guideVersion);

        List<AnnotationElement> candidates = loadInOrder(
                item.getCandidateElementIds(), elementMapper::selectById, "候选要素不存在");
        Set<String> sourceVersionIds = new HashSet<>(session.getSourceVersionIds());
        for (AnnotationElement candidate : candidates) {
            if (!"ANNOTATION_VERSION".equals(candidate.getOwnerType())
                    || !sourceVersionIds.contains(candidate.getOwnerId())
                    || !basis.getDocumentTextVersionId().equals(candidate.getDocumentTextVersionId())
                    || !basis.getAnnotationScopeId().equals(candidate.getAnnotationScopeId())
                    || !Boolean.TRUE.equals(candidate.getIsProposition())
                    || candidate.getStartOffset() < scope.getStartOffset()
                    || candidate.getEndOffset() > scope.getEndOffset()
                    || candidate.getStartOffset() >= candidate.getEndOffset()) {
                throw new BusinessException(ErrorCode.CONFLICT, "候选要素与裁定会话的数据基础不一致");
            }
        }

        return new CustomContextResponse(
                session,
                "PROPOSITION",
                item,
                sourceVersions,
                candidates,
                List.of(),
                List.of(),
                document,
                scope,
                guideVersion,
                List.of("CUSTOM_ANNOTATION", "ESCALATE_EXPERT"));
    }

    @Override
    @Transactional
    public CustomAnnotationResult save(
            String sessionId, String idempotencyKey, CustomAnnotationRequest request) {
        String requestHash = hashRequest(request);
        ArbiterCustomAnnotation existing = customAnnotationMapper.selectOne(
                new LambdaQueryWrapper<ArbiterCustomAnnotation>()
                        .eq(ArbiterCustomAnnotation::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)
                    || !existing.getSessionId().equals(sessionId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "同一幂等键不能用于不同请求");
            }
            return toResult(existing);
        }

        requirePropositionKind(request.itemKind());
        AdjudicationSession session = requireSession(sessionId);
        requireEditableSession(session);
        PropositionAdjudicationItem item = requireItem(sessionId, request.itemId());
        if (!request.customRelations().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CUSTOM_GRAPH, "命题自定义快照不能包含关系数据");
        }

        List<AnnotationVersion> sourceVersions = loadInOrder(
                session.getSourceVersionIds(), versionMapper::selectById, "源标注版本不存在");
        if (sourceVersions.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_VERSIONS, "裁定会话缺少源标注版本");
        }
        AnnotationVersion basis = sourceVersions.get(0);
        ensureSameBasis(sourceVersions, basis);
        DocumentTextVersion document = documentMapper.selectById(basis.getDocumentTextVersionId());
        AnnotationScope scope = scopeMapper.selectById(basis.getAnnotationScopeId());
        GuideVersion guideVersion = guideVersionMapper.selectById(basis.getGuideVersionId());
        if (document == null || scope == null || guideVersion == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "裁定上下文缺少正文、标注范围或指南版本");
        }
        validateScope(document, scope);
        requireLockedGuideVersion(guideVersion);

        List<CustomElementInput> inputs = new ArrayList<>(request.customElements());
        inputs.sort(Comparator.comparing(CustomElementInput::startOffset));
        validateClientKeys(inputs);
        validateNoOverlap(inputs);

        Set<String> allowedSourceIds = new HashSet<>(item.getCandidateElementIds());
        Set<String> codes = inputs.stream().map(CustomElementInput::labelCode).collect(Collectors.toSet());
        Map<String, LabelDefinition> labels = labelMapper.selectList(
                        new LambdaQueryWrapper<LabelDefinition>()
                                .eq(LabelDefinition::getGuideVersionId, basis.getGuideVersionId())
                                .in(LabelDefinition::getCode, codes))
                .stream().collect(Collectors.toMap(LabelDefinition::getCode, Function.identity()));

        ArbiterCustomAnnotation annotation = new ArbiterCustomAnnotation();
        annotation.setId(UUID.randomUUID().toString());
        annotation.setSessionId(sessionId);
        annotation.setItemKind("PROPOSITION");
        annotation.setItemId(item.getId());
        annotation.setElementIds(new ArrayList<>());
        annotation.setRelationIds(List.of());
        annotation.setReason(request.reason().trim());
        annotation.setIdempotencyKey(idempotencyKey);
        annotation.setRequestHash(requestHash);
        annotation.setRecordVersion(0);
        customAnnotationMapper.insert(annotation);

        List<String> elementIds = new ArrayList<>();
        List<ClientIdMapping> idMap = new ArrayList<>();
        for (CustomElementInput input : inputs) {
            LabelDefinition label = labels.get(input.labelCode());
            validateElementInput(input, label, allowedSourceIds, document, scope);

            AnnotationElement element = new AnnotationElement();
            element.setId(UUID.randomUUID().toString());
            element.setOwnerType("ARBITER_CUSTOM");
            element.setOwnerId(annotation.getId());
            element.setClientKey(input.clientKey());
            element.setDocumentTextVersionId(document.getId());
            element.setAnnotationScopeId(scope.getId());
            element.setStartOffset(input.startOffset());
            element.setEndOffset(input.endOffset());
            element.setText(document.getNormalizedText().substring(input.startOffset(), input.endOffset()));
            element.setLabelDefinitionId(label.getId());
            element.setLabelCode(label.getCode());
            element.setIsProposition(true);
            element.setDisplayNo(null);
            element.setSourceElementIds(List.copyOf(input.sourceElementIds()));
            element.setRecordVersion(0);
            elementMapper.insert(element);

            elementIds.add(element.getId());
            idMap.add(new ClientIdMapping(input.clientKey(), "ELEMENT", element.getId()));
        }
        annotation.setElementIds(elementIds);
        customAnnotationMapper.updateById(annotation);
        return new CustomAnnotationResult(
                annotation.getId(), "PROPOSITION", elementIds, List.of(), idMap, annotation.getReason());
    }

    private AdjudicationSession requireSession(String sessionId) {
        AdjudicationSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.NOT_FOUND, "裁定会话不存在");
        return session;
    }

    private PropositionAdjudicationItem requireItem(String sessionId, String itemId) {
        PropositionAdjudicationItem item = itemMapper.selectById(itemId);
        if (item == null) throw new BusinessException(ErrorCode.NOT_FOUND, "命题裁定项不存在");
        if (!sessionId.equals(item.getSessionId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "裁定项不属于当前会话");
        }
        return item;
    }

    private void requirePropositionKind(String itemKind) {
        if (!"PROPOSITION".equalsIgnoreCase(itemKind)) {
            throw new BusinessException(
                    ErrorCode.INVALID_CUSTOM_GRAPH, "M3 当前仅支持 PROPOSITION 自定义要素；关系模块待接入");
        }
    }

    private void requireEditableSession(AdjudicationSession session) {
        if (!"PROPOSITION_REVIEW".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REVIEW_STATE, "当前会话不处于命题裁定阶段");
        }
    }

    private void ensureSameBasis(List<AnnotationVersion> versions, AnnotationVersion basis) {
        for (AnnotationVersion version : versions) {
            if (!basis.getDocumentTextVersionId().equals(version.getDocumentTextVersionId())
                    || !basis.getAnnotationScopeId().equals(version.getAnnotationScopeId())
                    || !basis.getGuideVersionId().equals(version.getGuideVersionId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "源标注版本的正文、范围或指南版本不一致");
            }
        }
    }

    private void validateScope(DocumentTextVersion document, AnnotationScope scope) {
        if (!document.getId().equals(scope.getDocumentTextVersionId())
                || scope.getStartOffset() < 0
                || scope.getEndOffset() <= scope.getStartOffset()
                || scope.getEndOffset() > document.getNormalizedText().length()) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "标注范围不合法");
        }
    }

    private void requireLockedGuideVersion(GuideVersion guideVersion) {
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(guideVersion.getStatus())) {
            throw new BusinessException(ErrorCode.GUIDE_NOT_READY, "裁定只能使用已发布且不可编辑的指南版本");
        }
    }

    private void validateClientKeys(List<CustomElementInput> inputs) {
        Set<String> keys = new HashSet<>();
        for (CustomElementInput input : inputs) {
            if (!keys.add(input.clientKey())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "clientKey 必须唯一");
            }
        }
    }

    private void validateNoOverlap(List<CustomElementInput> inputs) {
        for (int i = 1; i < inputs.size(); i++) {
            if (inputs.get(i).startOffset() < inputs.get(i - 1).endOffset()) {
                throw new BusinessException(ErrorCode.OFFSET_INVALID, "自定义要素范围不能重叠");
            }
        }
    }

    private void validateElementInput(
            CustomElementInput input,
            LabelDefinition label,
            Set<String> allowedSourceIds,
            DocumentTextVersion document,
            AnnotationScope scope) {
        if (input.startOffset() < scope.getStartOffset()
                || input.endOffset() > scope.getEndOffset()
                || input.startOffset() >= input.endOffset()) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "要素范围超出当前标注范围");
        }
        if (label == null || !"PROPOSITION".equals(label.getCategory())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "标签不存在或不是命题标签");
        }
        if (!allowedSourceIds.containsAll(input.sourceElementIds())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sourceElementIds 包含非当前裁定项候选");
        }
        String text = document.getNormalizedText().substring(input.startOffset(), input.endOffset());
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.OFFSET_INVALID, "要素文本不能为空白");
        }
    }

    private CustomAnnotationResult toResult(ArbiterCustomAnnotation annotation) {
        List<AnnotationElement> elements = elementMapper.selectList(
                new LambdaQueryWrapper<AnnotationElement>()
                        .eq(AnnotationElement::getOwnerType, "ARBITER_CUSTOM")
                        .eq(AnnotationElement::getOwnerId, annotation.getId()));
        Map<String, AnnotationElement> byId = elements.stream()
                .collect(Collectors.toMap(AnnotationElement::getId, Function.identity()));
        List<ClientIdMapping> mappings = annotation.getElementIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(element -> new ClientIdMapping(element.getClientKey(), "ELEMENT", element.getId()))
                .toList();
        return new CustomAnnotationResult(
                annotation.getId(), annotation.getItemKind(), annotation.getElementIds(),
                annotation.getRelationIds(), mappings, annotation.getReason());
    }

    private String hashRequest(CustomAnnotationRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算请求摘要", ex);
        }
    }

    private <T> List<T> loadInOrder(
            List<String> ids, Function<String, T> loader, String missingMessage) {
        if (ids == null) return List.of();
        List<T> result = new ArrayList<>();
        for (String id : ids) {
            T value = loader.apply(id);
            if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, missingMessage + ": " + id);
            result.add(value);
        }
        return result;
    }
}
