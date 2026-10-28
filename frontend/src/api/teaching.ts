// 教学练习 API（分工.md §四-7，归成员C）。
//
// 这三个路径**不在** `接口契约/openapi-ready.yaml` 里：教学评分是 C 按 §四-7 自己划的边界
// （C 只出「关系、图评分」，学生成绩与班级统计归 D）。若要并进公共契约，需 A/B/C/D 共同确认。
//
// 拦截器已解包信封，因此这里直接返回 data 类型，调用方**绝不写 `.data`**。
import { get, post } from './request'
import type { ListResult } from '@/types/common'
import type {
  ExerciseDetail,
  ExerciseSummary,
  RelationTemplate,
  TeachingScoreResult
} from '@/types/teaching'

/** 题库列表。exerciseType 为空取全部；传未知类型后端返回 VALIDATION_ERROR。 */
export async function listExercises(exerciseType?: string): Promise<ExerciseSummary[]> {
  const r = await get<ListResult<ExerciseSummary>>('/teaching/exercises', {
    exerciseType: exerciseType || undefined
  })
  return r.items
}

/** 单题详情。**响应里没有标准答案**，这是接口的硬约束。 */
export function getExercise(exerciseId: string): Promise<ExerciseDetail> {
  return get<ExerciseDetail>(`/teaching/exercises/${exerciseId}`)
}

/**
 * 提交作答并评分。**只算分不落库**，因此可以反复提交同一题做「改一改再看看」。
 * relations 可以为空数组（答案就是「不建立任何关系」，得 0 分而不是 400）。
 */
export function scoreExercise(
  exerciseId: string,
  relations: RelationTemplate[]
): Promise<TeachingScoreResult> {
  return post<TeachingScoreResult>(`/teaching/exercises/${exerciseId}/score`, { relations })
}
