#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""成员 C：教学评分冒烟测试（分工.md §四-7 五类练习）。

用法：
    python smoke-teaching.py

前置：后端已启动（mvn -o spring-boot:run），data.sql 已种入 5 道练习（ex-s-01 … ex-graph-01）。

与 smoke-relations.py / smoke-adjudication.py 的**共存性**：那两个脚本会写 md-1 / us-1，
彼此不能同时跑；本脚本是**纯只读**的（教学接口只算分不落库，且只读 mock_proposition），
因此可以和任何脚本共用同一个后端进程，不需要 require_pristine_seed 守卫。

本脚本的价值在于「题库种子 + 编解码器 + 评分器」三者的**端到端口径一致**：
把 data.sql 里那份标准答案原样提交上去必须得 100 分——任何一处口径漂移都会立刻变成失败。

退出码 0 = 全绿，1 = 有断言失败。
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"

PASSED = []
FAILED = []


def check(label, ok, detail=""):
    (PASSED if ok else FAILED).append(label)
    print("  %s %s%s" % ("PASS" if ok else "FAIL", label,
                         ("  <- " + str(detail)) if detail and not ok else ""))


def call(method, path, body=None):
    """返回 (http 状态, 解包后的 JSON)。信封由后端统一给出，这里不做二次包装。"""
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    payload = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, payload) as resp:
            return resp.status, json.load(resp)
    except urllib.error.HTTPError as exc:
        return exc.code, json.load(exc)


# ── 答题模板构造 ────────────────────────────────────────────────────────

def el(ordinal, role, target_id):
    return {"ordinal": ordinal, "role": role, "targetKind": "ELEMENT", "targetId": target_id}


def ref(ordinal, role, target_ref):
    return {"ordinal": ordinal, "role": role, "targetKind": "RELATION", "targetRef": target_ref}


def relation(rid, code, *participants):
    return {"ref": rid, "code": code, "participants": list(participants)}


def s(rid, source, target):
    return relation(rid, "S", el(1, "SOURCE", source), el(2, "TARGET", target))


def attack(rid, source, target):
    return relation(rid, "A", el(1, "SOURCE", source), el(2, "TARGET", target))


def match(rid, source, target):
    return relation(rid, "M", el(1, "SOURCE", source), el(2, "TARGET", target))


def join(rid, *members):
    return relation(rid, "J", *[el(n + 1, "MEMBER", m) for n, m in enumerate(members)])


def identify(rid, *members):
    return relation(rid, "I", *[el(n + 1, "MEMBER", m) for n, m in enumerate(members)])


def support_by_relation(rid, inner_ref, target):
    """S(J(...), P)：以组合整体为端点的支持。"""
    return relation(rid, "S", ref(1, "SOURCE", inner_ref), el(2, "TARGET", target))


def score(exercise_id, relations):
    return call("POST", "/teaching/exercises/%s/score" % exercise_id, {"relations": relations})


def dim(result, key):
    return next(d for d in result["dimensions"] if d["key"] == key)


def codes(result):
    return [f["code"] for f in result["feedback"]]


# ── 题库与详情 ──────────────────────────────────────────────────────────

def test_catalog():
    print("题库列表与详情（不泄题）")
    status, body = call("GET", "/teaching/exercises")
    rows = body["data"]["items"]
    check("题库返回 5 道练习", status == 200 and len(rows) == 5, rows)
    check("按 sortOrder 排序", [r["sortOrder"] for r in rows] == [1, 2, 3, 4, 5])
    check("五类练习各一道",
          sorted(r["exerciseType"] for r in rows) == sorted(
              ["SUPPORT", "JOIN_VS_MATCH", "ATTACK_VS_IDENTIFY", "NESTING", "GRAPH_COMPARE"]))
    check("列表不含标准答案计数（不泄题）",
          all("standardRelations" not in r and "relationCount" not in r for r in rows))

    status, body = call("GET", "/teaching/exercises?exerciseType=NESTING")
    check("按类型过滤", status == 200 and len(body["data"]["items"]) == 1
          and body["data"]["items"][0]["id"] == "ex-nest-01", body)

    status, body = call("GET", "/teaching/exercises?exerciseType=NOPE")
    check("未知练习类型 -> 400 VALIDATION_ERROR",
          status == 400 and body["error"]["code"] == "VALIDATION_ERROR", body)

    status, body = call("GET", "/teaching/exercises/ex-nest-01")
    detail = body["data"]
    check("详情 200 且带题干与命题范围",
          status == 200 and len(detail["elements"]) == 4 and detail["exerciseType"] == "NESTING", detail)
    check("详情按 P2..P5 给出命题",
          [e["displayNo"] for e in detail["elements"]] == ["P2", "P3", "P4", "P5"],
          [e["displayNo"] for e in detail["elements"]])
    check("详情权重之和为 100", sum(d["weight"] for d in detail["dimensions"]) == 100,
          detail["dimensions"])
    raw = json.dumps(detail, ensure_ascii=False)
    check("详情不带标准答案与讲评要点",
          "standardRelations" not in raw and "explanation" not in raw
          and "S(J(" not in raw, raw)

    status, body = call("GET", "/teaching/exercises/nope")
    check("练习不存在 -> 404 EXERCISE_NOT_FOUND",
          status == 404 and body["error"]["code"] == "EXERCISE_NOT_FOUND", body)


# ── 逐类练习的评分 ──────────────────────────────────────────────────────

def test_support():
    print("S 专项练习（ex-s-01）")
    standard = [s("r1", "mp-1", "mp-5"), s("r2", "mp-3", "mp-5"), s("r3", "mp-4", "mp-5")]
    status, body = score("ex-s-01", standard)
    result = body["data"]
    check("标准答案原样提交 -> 100 分", status == 200 and result["score"] == 100, result)
    check("三个维度全部适用且满分",
          all(d["applicable"] and d["value"] == 1.0 for d in result["dimensions"]), result["dimensions"])
    check("统计：3 条配对一致、0 遗漏、0 多标",
          (result["statistics"]["matched"], result["statistics"]["missing"],
           result["statistics"]["extra"]) == (3, 0, 0), result["statistics"])
    check("评分后回显标准答案与讲评要点",
          len(result["standardAnswer"]["relations"]) == 3 and result["explanation"], result)

    # 方向写反
    reversed_one = [s("r1", "mp-5", "mp-1"), s("r2", "mp-3", "mp-5"), s("r3", "mp-4", "mp-5")]
    result = score("ex-s-01", reversed_one)[1]["data"]
    check("一条 S 写反 -> 85 分（只扣方向与配对）", result["score"] == 85, result["score"])
    check("方向维度 = 2/3", abs(dim(result, "DIRECTION")["value"] - 0.6667) < 1e-9, result["dimensions"])
    check("类型维度不被方向错牵连",
          dim(result, "TYPE_CHOICE")["value"] == 1.0 and result["statistics"]["wrongType"] == 0)
    check("反馈恰好一条 WRONG_DIRECTION", codes(result) == ["WRONG_DIRECTION"], codes(result))

    # 类型选错：端点一样，S 写成 J
    typed_wrong = [join("r1", "mp-1", "mp-5"), s("r2", "mp-3", "mp-5"), s("r3", "mp-4", "mp-5")]
    result = score("ex-s-01", typed_wrong)[1]["data"]
    check("S 写成 J -> 反馈一条 WRONG_TYPE",
          codes(result) == ["WRONG_TYPE"] and result["statistics"]["wrongType"] == 1, codes(result))
    check("类型选错时该条关系不计入方向判定",
          dim(result, "DIRECTION")["value"] == 1.0, result["dimensions"])


def test_join_vs_match():
    print("J/M 区分练习（ex-jm-01）")
    standard = [join("r1", "mp-2", "mp-3"), match("r2", "mp-7", "mp-6"), match("r3", "mp-8", "mp-6")]
    status, body = score("ex-jm-01", standard)
    result = body["data"]
    check("标准答案原样提交 -> 100 分", status == 200 and result["score"] == 100, result)
    check("类型维度权重最高（50）", dim(result, "TYPE_CHOICE")["weight"] == 50, result["dimensions"])
    check("方向维度只数有向的 M，不因 J 而失真",
          dim(result, "DIRECTION")["applicable"] and dim(result, "DIRECTION")["value"] == 1.0,
          result["dimensions"])

    # 把两条 M 写成 J：端点一样、类型不同
    confused = [join("r1", "mp-2", "mp-3"), join("r2", "mp-7", "mp-6"), join("r3", "mp-8", "mp-6")]
    result = score("ex-jm-01", confused)[1]["data"]
    check("M 写成 J -> 两条 WRONG_TYPE", codes(result).count("WRONG_TYPE") == 2, codes(result))
    check("类型维度 1/3", abs(dim(result, "TYPE_CHOICE")["value"] - 0.3333) < 1e-9,
          result["dimensions"])
    # 两条 M 都写成了 J，配对时已经「没有一条是有向关系可判方向」——方向维度整块不适用，
    # 它的 20 分权重被剔除后按 TYPE 50 + MATCH 30 归一。配对维度仍给这两条各 0.3 的「部分分」：
    # 端点找对了（只是类型选错）本身有价值。故总分 = (50*0.3333 + 30*0.5333) / 80 ≈ 41。
    check("类型错导致方向维度整块不适用（权重退还，不按 0 分误罚）",
          not dim(result, "DIRECTION")["applicable"], result["dimensions"])
    check("总分 41", result["score"] == 41, result["score"])


def test_attack_vs_identify():
    print("A/I 练习（ex-ai-01）")
    standard = [attack("r1", "mp-8", "mp-7"), identify("r2", "mp-5", "mp-7")]
    status, body = score("ex-ai-01", standard)
    result = body["data"]
    check("标准答案原样提交 -> 100 分", status == 200 and result["score"] == 100, result)
    check("I 是无向的，不参与方向判定",
          dim(result, "DIRECTION")["value"] == 1.0, result["dimensions"])

    # 反对方向写反
    reversed_one = [attack("r1", "mp-7", "mp-8"), identify("r2", "mp-5", "mp-7")]
    result = score("ex-ai-01", reversed_one)[1]["data"]
    check("A 反了 -> WRONG_DIRECTION", codes(result) == ["WRONG_DIRECTION"], codes(result))

    # 把 I 当 A 用：无向写成有向
    as_attack = [attack("r1", "mp-8", "mp-7"), attack("r2", "mp-5", "mp-7")]
    result = score("ex-ai-01", as_attack)[1]["data"]
    check("I 写成 A -> WRONG_TYPE", codes(result) == ["WRONG_TYPE"], codes(result))

    # I 的成员塞进关系整体：契约禁止，必须被校验器拦下
    illegal = [attack("r1", "mp-8", "mp-7"),
               relation("r2", "I", el(1, "MEMBER", "mp-5"), ref(2, "MEMBER", "r1"))]
    result = score("ex-ai-01", illegal)[1]["data"]
    check("I 的成员是关系 -> 校验问题进 validationIssues",
          any("I 的成员只能是命题" in i["message"] for i in result["validationIssues"]),
          result["validationIssues"])


def test_nesting():
    print("嵌套练习（ex-nest-01）")
    standard = [join("r1", "mp-2", "mp-3"),
                support_by_relation("r2", "r1", "mp-5"),
                s("r3", "mp-4", "mp-5")]
    status, body = score("ex-nest-01", standard)
    result = body["data"]
    check("标准答案原样提交（含嵌套）-> 100 分", status == 200 and result["score"] == 100, result)
    check("嵌套维度 1/1", dim(result, "NESTING")["value"] == 1.0, result["dimensions"])
    check("标准答案表达式保留嵌套不压平",
          any(r["expression"] == "S(J(P2,P3),P5)" for r in result["standardAnswer"]["relations"]),
          result["standardAnswer"])

    # 把共同理由压平成两条直接支持
    flattened = [s("a1", "mp-2", "mp-5"), s("a2", "mp-3", "mp-5"), s("a3", "mp-4", "mp-5")]
    result = score("ex-nest-01", flattened)[1]["data"]
    check("压平 -> 嵌套维度 0", dim(result, "NESTING")["applicable"]
          and dim(result, "NESTING")["value"] == 0.0, result["dimensions"])
    check("压平 -> 总分 30", result["score"] == 30, result["score"])
    check("同时报出遗漏（组合）、多标与嵌套问题",
          set(codes(result)) == {"MISSING_RELATION", "EXTRA_RELATION", "WRONG_NESTING"},
          codes(result))
    check("嵌套反馈点明「接到了外层目标上」",
          any("直接接到了外层目标上" in f["message"] for f in result["feedback"]),
          [f["message"] for f in result["feedback"]])

    # 嵌套写成对端点的不合法引用
    dangling = [s("a1", "mp-2", "mp-5"), s("a2", "mp-3", "mp-5"),
                relation("a3", "S", ref(1, "SOURCE", "nope"), el(2, "TARGET", "mp-5"))]
    result = score("ex-nest-01", dangling)[1]["data"]
    check("引用了不存在的 ref -> 报错而不是静默丢掉",
          any("引用了本作答里不存在的关系 ref" in i["message"] for i in result["validationIssues"]),
          result["validationIssues"])


def test_graph_compare():
    print("图比较练习（ex-graph-01）")
    standard = [
        join("r1", "mp-2", "mp-3"),
        support_by_relation("r2", "r1", "mp-5"),
        s("r3", "mp-1", "mp-5"),
        s("r4", "mp-4", "mp-5"),
        attack("r5", "mp-8", "mp-7"),
        s("r6", "mp-6", "mp-8"),
        match("r7", "mp-7", "mp-6"),
    ]
    status, body = score("ex-graph-01", standard)
    result = body["data"]
    check("标准答案原样提交 -> 100 分", status == 200 and result["score"] == 100, result)
    check("识别出 2 个独立论证图",
          result["statistics"]["standardStructures"] == 2
          and result["statistics"]["studentStructures"] == 2, result["statistics"])
    check("无孤立命题", result["standardAnswer"]["isolatedDisplayNos"] == []
          and result["statistics"]["isolatedMismatch"] == 0, result["statistics"])
    check("图结构维度 1.0", dim(result, "GRAPH_STRUCTURE")["value"] == 1.0, result["dimensions"])

    # 多连一条把两组串起来的关系
    bridged = standard + [s("bridge", "mp-5", "mp-8")]
    result = score("ex-graph-01", bridged)[1]["data"]
    check("跨组多连一条 -> 独立论证图变成 1 个",
          result["statistics"]["studentStructures"] == 1, result["statistics"])
    check("报出 WRONG_STRUCTURE_COUNT（INFO 提示）",
          any(f["code"] == "WRONG_STRUCTURE_COUNT" and f["level"] == "INFO"
              for f in result["feedback"]), result["feedback"])
    check("图结构维度 0.75（分量分 0.5、孤立分 1.0）",
          dim(result, "GRAPH_STRUCTURE")["value"] == 0.75, result["dimensions"])

    # 漏掉 S(P4,P5) -> P4 成为孤立命题
    dropped = [r for r in standard if r["ref"] != "r4"]
    result = score("ex-graph-01", dropped)[1]["data"]
    check("漏一条 -> 报出孤立命题 P4",
          any("P4" in f["message"] for f in result["feedback"] if f["code"] == "WRONG_ISOLATED"),
          result["feedback"])
    check("孤立命题不一致计数为 1", result["statistics"]["isolatedMismatch"] == 1,
          result["statistics"])
    check("图结构维度 0.25（分量分 0.5、孤立分 0）",
          dim(result, "GRAPH_STRUCTURE")["value"] == 0.25, result["dimensions"])

    # 空作答
    result = score("ex-graph-01", [])[1]["data"]
    check("空作答 -> 0 分且不报错", result["score"] == 0
          and result["statistics"]["missing"] == 7, result["statistics"])


# ── 边界与只读性 ────────────────────────────────────────────────────────

def test_edges():
    print("边界与只读性")

    # 非命题不能参与关系：mp-9 是 IS、mp-10 是 JD
    illegal = [join("r1", "mp-2", "mp-9")]
    status, body = score("ex-s-01", illegal)
    result = body["data"]
    check("非命题参与关系 -> 校验问题进 validationIssues",
          status == 200 and any("非命题要素不能参与论证关系" in i["message"]
                                for i in result["validationIssues"]), result["validationIssues"])

    # 自引用 / 成环：J 的成员指向 S，而该 S 的来源又指向这个 J——角色组合本身都合法，
    # 唯一的违规就是环，因此这条用例确实是在考 DFS 环检测（换成别的写法会先因角色不合法被拦）。
    cyclic = [relation("j1", "J", ref(1, "MEMBER", "s1"), el(2, "MEMBER", "mp-2")),
              relation("s1", "S", ref(1, "SOURCE", "j1"), el(2, "TARGET", "mp-5"))]
    result = score("ex-nest-01", cyclic)[1]["data"]
    check("自造循环嵌套被拒",
          any("循环" in i["message"] or "环" in i["message"] for i in result["validationIssues"]),
          result["validationIssues"])

    # 自引用：S 的来源指向自己（角色也合法，只违反「不能自引用」）
    selfish = [relation("s1", "S", ref(1, "SOURCE", "s1"), el(2, "TARGET", "mp-5"))]
    result = score("ex-nest-01", selfish)[1]["data"]
    check("关系自引用被拒", result["validationIssues"], result["validationIssues"])

    # 未知关系类型
    bogus = [{"ref": "r1", "code": "X", "participants": [el(1, "SOURCE", "mp-1"),
                                                         el(2, "TARGET", "mp-5")]}]
    status, body = score("ex-s-01", bogus)
    check("未知关系类型 -> 仍 200 并在问题清单里报出（作答错误不是接口错误）",
          status == 200 and body["data"]["validationIssues"], body)

    # 空作答体
    status, body = call("POST", "/teaching/exercises/ex-s-01/score", {})
    check("缺少 relations 字段 -> 400", status == 400, body)

    # 只读性：反复评分不改变题库
    before = call("GET", "/teaching/exercises")[1]["data"]["items"]
    for _ in range(2):
        score("ex-s-01", [s("r1", "mp-1", "mp-5")])
    after = call("GET", "/teaching/exercises")[1]["data"]["items"]
    check("评分不落库（题库快照不变）", before == after)


def main():
    print("教学评分冒烟测试 → %s\n" % BASE)
    try:
        call("GET", "/teaching/exercises")
    except Exception as exc:  # noqa: BLE001
        print("后端不可达：%s\n请先启动 mvn -o spring-boot:run" % exc)
        return 2

    test_catalog()
    test_support()
    test_join_vs_match()
    test_attack_vs_identify()
    test_nesting()
    test_graph_compare()
    test_edges()

    print("\n结果：%d 项通过，%d 项失败" % (len(PASSED), len(FAILED)))
    for label in FAILED:
        print("  FAILED: %s" % label)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
