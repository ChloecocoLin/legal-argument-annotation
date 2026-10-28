package com.argument.teaching.controller;

import com.argument.common.response.ApiResponse;
import com.argument.teaching.dto.*;
import com.argument.teaching.service.TeachingExerciseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教学接口（分工.md §四-7）：题库列表、单题详情（不含答案）、作答评分试算。归成员 C。
 *
 * <p>这三个接口**不在** {@code openapi-ready.yaml} 里——该契约未覆盖教学。因此：
 * <ul>
 *   <li>路径自成一组 {@code /api/v1/teaching/**}，不与契约路径混排，将来契约补齐时好对齐；</li>
 *   <li>错误码 {@code EXERCISE_NOT_FOUND} / {@code EXERCISE_KEY_INVALID} 为 C 侧新增，
 *       已在 {@code ErrorCode} 里注明无契约对应项。</li>
 * </ul>
 *
 * <p>评分**不落库**：作答留档、成绩册、班级统计与教师反馈归 D（§五-6）。D 汇总时调用
 * 同一个 {@code POST …/score}，因此 C 与 D 不会各算一套分数。
 *
 * <p>RBAC（分工.md §六）：整组接口限 admin / student / teacher。做题人与出题人是这两类角色，
 * 标注员与裁定员没有理由看到题库——尤其题库详情虽不含标准答案，但题目构造本身就是教学资源。
 */
@RestController
@RequestMapping("/api/v1/teaching")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('admin','student','teacher')")
public class TeachingController {

    private final TeachingExerciseService exerciseService;

    /** 题库列表。{@code exerciseType} 取 {@code TeachingExerciseType} 枚举名。 */
    @GetMapping("/exercises")
    public ApiResponse<Map<String, List<ExerciseSummary>>> listExercises(
            @RequestParam(required = false) String exerciseType) {
        return ApiResponse.ok(Map.of("items", exerciseService.listExercises(exerciseType)));
    }

    /** 单题详情。刻意不含标准答案与讲评要点。 */
    @GetMapping("/exercises/{exerciseId}")
    public ApiResponse<ExerciseDetail> getExercise(@PathVariable String exerciseId) {
        return ApiResponse.ok(exerciseService.getExercise(exerciseId));
    }

    /** 提交作答并评分（只读试算）。 */
    @PostMapping("/exercises/{exerciseId}/score")
    public ApiResponse<TeachingScoreResult> score(
            @PathVariable String exerciseId,
            @Valid @RequestBody ExerciseScoreRequest request) {
        return ApiResponse.ok(exerciseService.score(exerciseId, request));
    }
}
