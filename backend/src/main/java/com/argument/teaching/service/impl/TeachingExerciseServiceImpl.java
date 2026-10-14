package com.argument.teaching.service.impl;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.proposition.PropositionRef;
import com.argument.proposition.PropositionSource;
import com.argument.relation.service.RelationValidator;
import com.argument.teaching.dto.*;
import com.argument.teaching.entity.TeachingExercise;
import com.argument.teaching.mapper.TeachingExerciseMapper;
import com.argument.teaching.rules.TeachingExerciseType;
import com.argument.teaching.service.RelationTemplateCodec;
import com.argument.teaching.service.TeachingExerciseService;
import com.argument.teaching.service.TeachingScorer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 教学练习服务实现。归成员 C。 */
@Service
@RequiredArgsConstructor
public class TeachingExerciseServiceImpl implements TeachingExerciseService {

    private final TeachingExerciseMapper exerciseMapper;
    private final RelationTemplateCodec codec;
    private final TeachingScorer scorer;
    private final PropositionSource propositions;

    @Override
    public List<ExerciseSummary> listExercises(String exerciseType) {
        if (exerciseType != null && !exerciseType.isBlank()
                && TeachingExerciseType.of(exerciseType) == null) {
            // 未知的练习类型只可能是调用方写错了枚举名，空列表会让它看起来像「题库没题」。
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知的练习类型：" + exerciseType);
        }
        LambdaQueryWrapper<TeachingExercise> query = new LambdaQueryWrapper<>();
        if (exerciseType != null && !exerciseType.isBlank()) {
            query.eq(TeachingExercise::getExerciseType, exerciseType);
        }
        query.orderByAsc(TeachingExercise::getSortOrder);
        return exerciseMapper.selectList(query).stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public ExerciseDetail getExercise(String exerciseId) {
        TeachingExercise exercise = require(exerciseId);
        TeachingExerciseType type = requireType(exercise);
        List<ExerciseElement> elements = propositions.findBySnapshot(exercise.getSnapshotId()).stream()
                .filter(p -> inScope(exercise, p))
                .sorted(Comparator
                        .comparing((PropositionRef p) -> p.proposition() ? 0 : 1)
                        .thenComparingInt(p -> RelationValidator.parseDisplayNo(p.displayNo())))
                .map(p -> new ExerciseElement(
                        p.id(), p.displayNo(), p.labelCode(), p.text(), p.proposition()))
                .collect(Collectors.toList());
        List<ExerciseDetail.DimensionWeight> dimensions = type.weights().entrySet().stream()
                .map(e -> ExerciseDetail.DimensionWeight.of(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new ExerciseDetail(
                exercise.getId(), exercise.getExerciseCode(), type.name(), type.label(),
                exercise.getTitle(), exercise.getPromptText(), exercise.getSnapshotId(),
                elements, dimensions);
    }

    @Override
    public TeachingScoreResult score(String exerciseId, ExerciseScoreRequest request) {
        TeachingExercise exercise = require(exerciseId);
        TeachingExerciseType type = requireType(exercise);
        Set<String> scope = scopeOf(exercise);
        // 两侧走同一个解析器：标准答案不合法时由 TeachingScorer 抛 EXERCISE_KEY_INVALID，
        // 而不是拿一份坏答案去"证明"学生错了。
        RelationTemplateCodec.Parsed standard =
                codec.parse(exercise.getStandardRelations(), exercise.getSnapshotId(), scope);
        RelationTemplateCodec.Parsed answer =
                codec.parse(request.relations(), exercise.getSnapshotId(), scope);
        return scorer.score(exercise.getId(), type, exercise.getSnapshotId(), scope,
                standard, answer, exercise.getExplanation());
    }

    // ── 装载与校验 ──────────────────────────────────────────────────────

    private TeachingExercise require(String exerciseId) {
        TeachingExercise exercise = exerciseId == null ? null : exerciseMapper.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND, "练习不存在：" + exerciseId);
        }
        return exercise;
    }

    /**
     * 练习类型必须是已知枚举。取值错误属于**题库数据**问题（种子写错或表被手改），
     * 不是学生的输入错误，因此报 500 而不是 400：它必须被出题人看见并修复。
     */
    private TeachingExerciseType requireType(TeachingExercise exercise) {
        TeachingExerciseType type = TeachingExerciseType.of(exercise.getExerciseType());
        if (type == null) {
            throw new BusinessException(ErrorCode.EXERCISE_KEY_INVALID,
                    "练习 " + exercise.getId() + " 的类型无法识别：" + exercise.getExerciseType());
        }
        return type;
    }

    /** 范围为空视为**不限制**（整快照都是范围），与 {@code RelationTemplateCodec} 的 null 语义一致。 */
    private Set<String> scopeOf(TeachingExercise exercise) {
        List<String> ids = exercise.getScopeIds();
        return ids == null || ids.isEmpty() ? null : new LinkedHashSet<>(ids);
    }

    private boolean inScope(TeachingExercise exercise, PropositionRef proposition) {
        Set<String> scope = scopeOf(exercise);
        return scope == null || scope.contains(proposition.id());
    }

    private ExerciseSummary toSummary(TeachingExercise exercise) {
        TeachingExerciseType type = TeachingExerciseType.of(exercise.getExerciseType());
        return new ExerciseSummary(
                exercise.getId(), exercise.getExerciseCode(), exercise.getExerciseType(),
                type == null ? exercise.getExerciseType() : type.label(),
                exercise.getTitle(),
                exercise.getSortOrder() == null ? 0 : exercise.getSortOrder());
    }
}
