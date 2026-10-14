package com.argument.relation.dto;

/**
 * 关系校验问题项。同一份问题清单同时服务三个出口：
 * R02/R03 直接取首条抛错、GR02「图示审查」收集为 issues、前端做「未完成关系提示」。
 *
 * @param code 与 ErrorCode 同名：INVALID_PARTICIPANT / RELATION_CYCLE / …
 * @param level ERROR（阻断保存）或 WARNING（仅提示，如孤立命题）
 */
public record RelationIssue(
        String code, String level, String message, String relationId, Integer participantOrdinal) {

    public static RelationIssue error(String code, String message) {
        return new RelationIssue(code, "ERROR", message, null, null);
    }

    public static RelationIssue warning(String code, String message) {
        return new RelationIssue(code, "WARNING", message, null, null);
    }

    public RelationIssue at(String relationId, Integer ordinal) {
        return new RelationIssue(code, level, message, relationId, ordinal);
    }
}
