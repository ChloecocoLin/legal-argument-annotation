package com.argument.graph.dto;

/**
 * 论证图节点（**逻辑节点，不含坐标**；坐标在布局里）。
 *
 * @param id 命题 id 或关系 id
 * @param kind ELEMENT（命题）/ RELATION（关系）
 * @param displayNo 命题展示编号，如 P1；关系节点为 null
 * @param labelCode 命题的标签代码（SF/GF/…）；关系节点为 null
 * @param text 命题原文；关系节点为 null
 * @param relationCode 关系代码 S/A/J/M/I；命题节点为 null
 * @param directed 关系是否有向；命题节点为 null
 * @param expression 关系的展示表达式，如 M(J(P1,P2),P6)；命题节点为 null
 * @param representativeId I 关系的代表节点（组内最大展示编号命题）；其余为 null
 */
public record GraphNode(
        String id,
        String kind,
        String displayNo,
        String labelCode,
        String text,
        String relationCode,
        Boolean directed,
        String expression,
        String representativeId) {}
