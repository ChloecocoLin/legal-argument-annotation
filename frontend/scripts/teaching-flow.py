#!/usr/bin/env python
"""教学练习页（ExercisePractice.vue）实际请求序列的联调自查。

按页面自己的调用顺序打真接口，并**照抄页面的构造规则**拼作答，断言页面渲染所依赖的字段：
  listExercises（GET /teaching/exercises）→ getExercise（单题详情）
  → addRelation 内存拼模板 → scoreExercise（POST …/score）

覆盖的页面行为（每节对应界面上的一个可见结论）：
  [1] 左栏题库：5 题、五类各一、列表不含任何标准答案线索
  [2] 中栏题干与命题清单：详情不返回标准答案；维度权重和为 100；
      ex-s-01 的要素里有一个非命题（页面「非命题」徽章的唯一来源）
  [3] 五类练习按页面的构造路径给出标准答案 → 各自满分
  [4] 右栏渲染依赖的字段：dimensions[].detail / applicable（决定显示「不适用」还是 0%）
  [5] 嵌套必须用 targetRef：页面的 participantOf() 把它写进 targetRef 是对的
  [6] 无向成员按展示编号排序（页面对 J/I 成员排序）不影响判分
  [7] GRAPH_COMPARE 的学生图与标准图对照（页面第七节的两张图）
  [8] 非命题：页面把非命题置灰，后端也把它拦在「结构问题」里
  [9] 只读试算：同一份作答反复提交分数不变（页面可以改完再提交）
  [10] 空作答与未知关系代码：得 0 分而不是 400

用法（先起后端，H2 为内存库，每次启动种子一致）：
  1) JAVA_HOME=<JBR21> mvn -o spring-boot:run
  2) python -I frontend/scripts/teaching-flow.py
"""
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"
PASS = 0
FAIL = 0

# 详情接口必须隐藏的三样东西：标准答案、讲评要点、以及渲染后的关系表达式
ANSWER_KEYS = ("standardRelations", "explanation", "standardAnswer")


# ── 登录（分工.md §六，成员 C）──────────────────────────────────────────
# C 启用 Spring Security 后，未带令牌的请求一律 401。这里走的就是浏览器那条路：
# 先登录换令牌、再带 Authorization 头——**没有**任何「跳过鉴权」的开关。
# 令牌拿不到就立刻退出：否则后面每条断言都会以 401 的形式红一片，
# 真正的原因（后端没起 / 种子没进 / 口令不对）反而被埋在中间。
import pathlib
import sys

_p = pathlib.Path(__file__).resolve().parent
while not (_p / ".git").exists():
    _p = _p.parent
sys.path.insert(0, str(_p / "scripts"))
from verify_auth import login_token  # noqa: E402

TOKEN = login_token(base=BASE)

def call(method, path, body=None, idem="teaching-flow"):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json", "Idempotency-Key": idem,
                 "Authorization": "Bearer " + TOKEN})
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode())


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print("  PASS  " + name)
    else:
        FAIL += 1
        print("  FAIL  " + name + "   " + str(detail))


def data_of(payload):
    return (payload or {}).get("data") or {}


def error_code(payload):
    return ((payload or {}).get("error") or {}).get("code")


def leaks_answer(payload):
    """响应里是否出现了标准答案/讲评要点；列表与详情都不允许有。"""
    text = json.dumps(payload, ensure_ascii=False)
    return [key for key in ANSWER_KEYS if key in text]


# ── 页面的作答构造（照抄 ExercisePractice.vue）──────────────────────────
#
# picks 里的每一项是 (展示编号, 目标种类, 目标 ID)：
#   命题 → ("P2", "ELEMENT", "mp-2")；已有关系 → (None, "RELATION", "r1")
# 有向关系取前 2 个槽位并给 SOURCE/TARGET；无向关系的成员按展示编号排序
# （sortKey：关系端点排最前，对应 sortKey() 返回 -1）。

DIRECTED = ("S", "A", "M")


def display_no(display_no):
    digits = "".join(c for c in (display_no or "") if c.isdigit())
    return int(digits) if digits else -1


def sort_key(pick):
    return -1 if pick[1] == "RELATION" else display_no(pick[0])


def build(ref, code, picks):
    """镜像 addRelation()：拼出页面会 POST 的 participants。"""
    directed = code in DIRECTED
    ordered = picks[:2] if directed else sorted(picks, key=sort_key)
    participants = []
    for index, (_, kind, target) in enumerate(ordered):
        if directed:
            role = "SOURCE" if index == 0 else "TARGET"
        else:
            role = "MEMBER"
        nested = kind == "RELATION"
        participants.append({
            "ordinal": index + 1,
            "role": role,
            "targetKind": kind,
            # 页面把关系端点写进 targetRef、把 targetId 留空（participantOf()）
            "targetId": None if nested else target,
            "targetRef": target if nested else None,
        })
    return {"ref": ref, "code": code, "participants": participants, "note": None}


def p(display_no_value, element_id):
    return (display_no_value, "ELEMENT", element_id)


def r(ref_name):
    return (None, "RELATION", ref_name)


def list_exercises(exercise_type=None):
    path = "/teaching/exercises"
    if exercise_type:
        path += "?exerciseType=" + exercise_type
    status, body = call("GET", path)
    return status, body, data_of(body).get("items") or []


def detail_of(exercise_id):
    return call("GET", "/teaching/exercises/" + exercise_id)


def score(exercise_id, relations):
    return call("POST", "/teaching/exercises/%s/score" % exercise_id, {"relations": relations})


def dimension(result, key):
    for dim in result["dimensions"]:
        if dim["key"] == key:
            return dim
    return None


def codes(result):
    return [f["code"] for f in result["feedback"]]


def perfect_answer(exercise_id):
    """五类练习各自的标准答案，用页面的构造路径拼出来（顺序与 data.sql 的种子一致）。"""
    if exercise_id == "ex-s-01":
        return [
            build("r1", "S", [p("P1", "mp-1"), p("P5", "mp-5")]),
            build("r2", "S", [p("P3", "mp-3"), p("P5", "mp-5")]),
            build("r3", "S", [p("P4", "mp-4"), p("P5", "mp-5")]),
        ]
    if exercise_id == "ex-jm-01":
        return [
            build("r1", "J", [p("P2", "mp-2"), p("P3", "mp-3")]),
            build("r2", "M", [p("P7", "mp-7"), p("P6", "mp-6")]),
            build("r3", "M", [p("P8", "mp-8"), p("P6", "mp-6")]),
        ]
    if exercise_id == "ex-ai-01":
        return [
            build("r1", "A", [p("P8", "mp-8"), p("P7", "mp-7")]),
            build("r2", "I", [p("P5", "mp-5"), p("P7", "mp-7")]),
        ]
    if exercise_id == "ex-nest-01":
        return [
            build("r1", "J", [p("P2", "mp-2"), p("P3", "mp-3")]),
            build("r2", "S", [r("r1"), p("P5", "mp-5")]),
            build("r3", "S", [p("P4", "mp-4"), p("P5", "mp-5")]),
        ]
    if exercise_id == "ex-graph-01":
        return [
            build("r1", "J", [p("P2", "mp-2"), p("P3", "mp-3")]),
            build("r2", "S", [r("r1"), p("P5", "mp-5")]),
            build("r3", "S", [p("P1", "mp-1"), p("P5", "mp-5")]),
            build("r4", "S", [p("P4", "mp-4"), p("P5", "mp-5")]),
            build("r5", "A", [p("P8", "mp-8"), p("P7", "mp-7")]),
            build("r6", "S", [p("P6", "mp-6"), p("P8", "mp-8")]),
            build("r7", "M", [p("P7", "mp-7"), p("P6", "mp-6")]),
        ]
    return None


# ── [1] 左栏题库 ────────────────────────────────────────────────────────

def test_catalog():
    print("\n[1] 左栏题库")
    status, body, items = list_exercises()
    check("题库 200 且 5 题", status == 200 and len(items) == 5, (status, len(items)))
    types = [i["exerciseType"] for i in items]
    check("五类练习各一题",
          sorted(types) == ["ATTACK_VS_IDENTIFY", "GRAPH_COMPARE", "JOIN_VS_MATCH",
                            "NESTING", "SUPPORT"], types)
    check("按 sortOrder 排好序（页面直接照序渲染）",
          [i["sortOrder"] for i in items] == [1, 2, 3, 4, 5],
          [i["sortOrder"] for i in items])
    check("每项都有中文类型名与标题（el-no / el-text 两行）",
          all(i["exerciseTypeLabel"] and i["title"] for i in items))
    check("列表不含标准答案线索（标题里也不含关系表达式）",
          not leaks_answer(body) and "S(" not in json.dumps(body, ensure_ascii=False),
          leaks_answer(body))

    status, _, one = list_exercises("SUPPORT")
    check("按类型筛选只剩 1 题", status == 200 and len(one) == 1
          and one[0]["exerciseType"] == "SUPPORT", (status, len(one)))

    status, body, _ = list_exercises("NOT_A_TYPE")
    check("未知类型 → 400 VALIDATION_ERROR",
          status == 400 and error_code(body) == "VALIDATION_ERROR",
          (status, error_code(body)))


# ── [2] 中栏题干与命题清单 ──────────────────────────────────────────────

def test_detail():
    print("\n[2] 中栏题干与命题清单")
    exercises = detail_catalog()
    for item in exercises:
        status, body = detail_of(item["id"])
        detail = data_of(body)
        check("%s 详情 200 且要素非空" % item["id"],
              status == 200 and len(detail.get("elements") or []) > 0, status)
        check("%s 详情不返回标准答案与讲评要点" % item["id"], not leaks_answer(body),
              leaks_answer(body))
        weights = sum(d["weight"] for d in detail["dimensions"])
        check("%s 维度权重和为 100（左栏「评分维度」一栏）" % item["id"], weights == 100, weights)

    status, body = detail_of("ex-s-01")
    elements = data_of(body)["elements"]
    check("ex-s-01 列出 6 个要素（含一个干扰项）", len(elements) == 6, len(elements))
    check("ex-s-01 最后一个是非命题（页面「非命题」徽章 + 置灰的唯一来源）",
          elements[-1]["proposition"] is False and elements[-1]["displayNo"] is None,
          elements[-1])
    check("命题排在非命题之前（页面按后端顺序渲染）",
          [e["proposition"] for e in elements] == [True] * 5 + [False],
          [e["proposition"] for e in elements])

    others = [i for i in exercises if i["id"] != "ex-s-01"]
    all_props = True
    for item in others:
        _, body = detail_of(item["id"])
        all_props = all_props and all(e["proposition"] for e in data_of(body)["elements"])
    check("其余四题范围内没有非命题（现状：非命题只出现在 S 专项题）", all_props)

    status, body = detail_of("ex-none")
    check("切到不存在的题 → 404 EXERCISE_NOT_FOUND",
          status == 404 and error_code(body) == "EXERCISE_NOT_FOUND",
          (status, error_code(body)))


def detail_catalog():
    _, _, items = list_exercises()
    return items


# ── [3] 五类练习的满分路径 ──────────────────────────────────────────────

def test_perfect_paths():
    print("\n[3] 五类练习按页面的构造路径作答")
    for item in detail_catalog():
        relations = perfect_answer(item["id"])
        status, body = score(item["id"], relations)
        result = data_of(body)
        check("%s 标准答案原样提交 → 100 分" % item["id"],
              status == 200 and result.get("score") == 100,
              (status, result.get("score")))
        check("%s 满分不产生任何反馈" % item["id"], result.get("feedback") == [],
              result.get("feedback"))

    _, body = score("ex-nest-01", perfect_answer("ex-nest-01"))
    result = data_of(body)
    check("嵌套题的作答确实是 S(J(P2,P3),P5)（页面的 targetRef 通道）",
          any(r["expression"] == "S(J(P2,P3),P5)" for r in result["standardAnswer"]["relations"]),
          [r["expression"] for r in result["standardAnswer"]["relations"]])
    check("满分时 matched 等于标准答案条数",
          result["statistics"]["matched"] == result["statistics"]["standardRelations"],
          result["statistics"])


# ── [4] 右栏渲染依赖的字段 ──────────────────────────────────────────────

def test_result_shape():
    print("\n[4] 右栏渲染依赖的字段")
    # 故意把第一条 S 写反（页面允许：有向关系按槽位给角色，选反了就是方向错）
    answer = perfect_answer("ex-s-01")
    answer[0] = build("r1", "S", [p("P5", "mp-5"), p("P1", "mp-1")])
    _, body = score("ex-s-01", answer)
    result = data_of(body)

    check("score 是 0..100 的整数", isinstance(result["score"], int) and 0 <= result["score"] <= 100,
          result["score"])
    dims = result["dimensions"]
    check("每个维度都有 key/label/weight/value/applicable/detail（进度条与说明文字）",
          all(set(d) >= {"key", "label", "weight", "value", "applicable", "detail"} for d in dims),
          dims)
    check("维度 key 与详情接口的维度一一对应",
          [d["key"] for d in dims] == [d["key"] for d in data_of(detail_of("ex-s-01")[1])["dimensions"]],
          [d["key"] for d in dims])
    check("applicable=true 的维度 value 在 0..1",
          all(0 <= d["value"] <= 1 for d in dims if d["applicable"]), dims)
    check("applicable=false 的维度 value 为 0（页面显示「不适用」而不是 0%）",
          all(d["value"] == 0 for d in dims if not d["applicable"]), dims)
    direction = dimension(result, "DIRECTION")
    check("写反一条 S 只扣方向维度", direction["value"] < 1.0, direction)
    check("反馈里的表达式与维度说明都可直接显示",
          all(f["message"] and f["level"] in ("ERROR", "INFO") for f in result["feedback"]),
          result["feedback"])
    check("方向错不报成类型错", "WRONG_TYPE" not in codes(result), codes(result))

    # 把仅有的那条 S 写成 J：配对强度降到「类型不同」，这条关系已没有可判的方向，
    # 方向维度整块不可判（页面显示「不适用」而不是 0%）
    confused = [build("r1", "J", [p("P1", "mp-1"), p("P5", "mp-5")])]
    _, body = score("ex-s-01", confused)
    result = data_of(body)
    check("S 写成 J → 方向维度 applicable=false（页面显示「不适用」）",
          dimension(result, "DIRECTION")["applicable"] is False,
          dimension(result, "DIRECTION"))
    check("S 写成 J → 报 WRONG_TYPE", "WRONG_TYPE" in codes(result), codes(result))
    check("方向维度不可判时权重退还：40 分的配对拿到 0.1 → 约 6 分",
          result["score"] == 6, result["score"])


# ── [5] 嵌套必须用 targetRef ────────────────────────────────────────────

def test_nested_reference():
    print("\n[5] 嵌套端点的字段通道")
    good = perfect_answer("ex-nest-01")
    status, body = score("ex-nest-01", good)
    check("targetRef 指向 r1 → 接受且满分",
          status == 200 and data_of(body).get("score") == 100,
          (status, data_of(body).get("score")))

    # 页面若图省事把引用名写进 targetId（targetKind 仍是 RELATION），必须被拦下
    bad = perfect_answer("ex-nest-01")
    bad[1]["participants"][0] = {
        "ordinal": 1, "role": "SOURCE", "targetKind": "RELATION",
        "targetId": "r1", "targetRef": None}
    status, body = score("ex-nest-01", bad)
    result = data_of(body)
    rejected = status != 200 or bool(result.get("validationIssues")) or result.get("score") != 100
    check("把 ref 写进 targetId 不会被当成合法满分（页面必须用 targetRef）",
          rejected, (status, error_code(body), result.get("score")))


# ── [6] 无向成员的顺序 ──────────────────────────────────────────────────

def test_undirected_order():
    print("\n[6] 无向成员按展示编号排序")
    ascending = [
        build("r1", "J", [p("P2", "mp-2"), p("P3", "mp-3")]),
        build("r2", "M", [p("P7", "mp-7"), p("P6", "mp-6")]),
        build("r3", "M", [p("P8", "mp-8"), p("P6", "mp-6")]),
    ]
    descending = [
        build("r1", "J", [p("P3", "mp-3"), p("P2", "mp-2")]),
        build("r2", "M", [p("P7", "mp-7"), p("P6", "mp-6")]),
        build("r3", "M", [p("P8", "mp-8"), p("P6", "mp-6")]),
    ]
    _, body_a = score("ex-jm-01", ascending)
    _, body_b = score("ex-jm-01", descending)
    check("J 成员写 P2,P3 还是 P3,P2 都不影响判分（页面按展示编号排序只是让显示稳定）",
          data_of(body_a)["score"] == data_of(body_b)["score"] == 100,
          (data_of(body_a)["score"], data_of(body_b)["score"]))


# ── [7] 学生图与标准图对照 ──────────────────────────────────────────────

def test_graph_compare():
    print("\n[7] 学生图与标准图对照（页面第七节）")
    _, body = score("ex-graph-01", perfect_answer("ex-graph-01"))
    result = data_of(body)
    graph = result["graph"]
    check("GRAPH_COMPARE 返回 graph 对照", graph is not None)
    check("标准图与学生图都有节点与连线",
          graph and all(graph[k] for k in
                        ("standardNodes", "standardEdges", "studentNodes", "studentEdges")),
          {k: len(graph[k]) for k in ("standardNodes", "standardEdges",
                                      "studentNodes", "studentEdges")} if graph else None)
    relation_nodes = [n for n in graph["standardNodes"] if n["kind"] == "RELATION"]
    check("标准图把每条关系都画成关系节点（7 条关系 + 8 个命题 = 15 个节点）",
          len(relation_nodes) == 7 and len(graph["standardNodes"]) == 15,
          (len(relation_nodes), len(graph["standardNodes"])))
    node_keys = set(graph)
    check("graph 只给节点与连线，不给页面上没用的额外结构",
          node_keys == {"standardNodes", "standardEdges", "studentNodes", "studentEdges"},
          sorted(node_keys))
    combo = [n for n in relation_nodes if n["relationCode"] == "J"]
    check("组合整体是独立节点，表达式可直接显示",
          len(combo) == 1 and combo[0]["expression"] == "J(P2,P3)", combo)
    check("嵌套是经关系节点串联的（S 连线落在 J 的节点上，没有把 J 压平）",
          any(e["source"] == "tpl:r1" and e["target"] == "tpl:r2" for e in graph["standardEdges"]),
          [e["id"] for e in graph["standardEdges"]])
    check("连线数 = 参与项总数（7 条关系 × 2）",
          len(graph["standardEdges"]) == 14, len(graph["standardEdges"]))
    check("满分时两边连线数相同", len(graph["studentEdges"]) == len(graph["standardEdges"]),
          (len(graph["studentEdges"]), len(graph["standardEdges"])))
    check("满分时独立论证图 2 个", result["statistics"]["standardStructures"] == 2
          and result["statistics"]["studentStructures"] == 2, result["statistics"])
    check("满分时没有孤立命题", result["standardAnswer"]["isolatedDisplayNos"] == [],
          result["standardAnswer"]["isolatedDisplayNos"])

    for item in detail_catalog():
        if item["id"] == "ex-graph-01":
            continue
        _, body = score(item["id"], perfect_answer(item["id"]))
        check("%s 不返回图对照（页面因此不画第七节）" % item["id"],
              data_of(body).get("graph") is None, data_of(body).get("graph"))


# ── [8] 非命题 ──────────────────────────────────────────────────────────

def test_non_proposition():
    print("\n[8] 非命题")
    answer = perfect_answer("ex-s-01") + [build("r9", "S", [p(None, "mp-9"), p("P5", "mp-5")])]
    status, body = score("ex-s-01", answer)
    result = data_of(body)
    check("拿非命题当理由 → 200 且被记为结构问题（页面「结构问题」一节）",
          status == 200 and bool(result.get("validationIssues")),
          (status, result.get("validationIssues")))
    check("结构问题里有 ERROR 级",
          any(i["level"] == "ERROR" for i in result.get("validationIssues") or []),
          result.get("validationIssues"))
    check("误用非命题不会还拿到满分", result.get("score") != 100, result.get("score"))


# ── [9] 只读试算 ────────────────────────────────────────────────────────

def test_readonly():
    print("\n[9] 只读试算（页面可以改完再提交）")
    answer = perfect_answer("ex-s-01")
    _, first = score("ex-s-01", answer)
    _, second = score("ex-s-01", answer)
    check("同一份作答连提两次分数相同", data_of(first)["score"] == data_of(second)["score"],
          (data_of(first)["score"], data_of(second)["score"]))
    _, body = detail_of("ex-s-01")
    check("评分后详情仍不返回标准答案（没有回写题库）", not leaks_answer(body), leaks_answer(body))


# ── [10] 空作答与未知关系代码 ───────────────────────────────────────────

def test_edges():
    print("\n[10] 空作答与未知关系代码")
    status, body = score("ex-s-01", [])
    result = data_of(body)
    check("清空作答后提交 → 200 且 0 分（页面显示 0 分而不是报错）",
          status == 200 and result.get("score") == 0, (status, result.get("score")))
    check("0 分时标准答案仍全部计入遗漏",
          result["statistics"]["missing"] == result["statistics"]["standardRelations"],
          result["statistics"])

    unknown = [build("r1", "X", [p("P1", "mp-1"), p("P5", "mp-5")])]
    status, body = score("ex-s-01", unknown)
    check("未知关系代码不被静默接受（页面只出 S/A/J/M/I，出现 X 必定是 bug）",
          status == 200 and bool(data_of(body).get("validationIssues")),
          (status, data_of(body).get("validationIssues")))


def main():
    test_catalog()
    test_detail()
    test_perfect_paths()
    test_result_shape()
    test_nested_reference()
    test_undirected_order()
    test_graph_compare()
    test_non_proposition()
    test_readonly()
    test_edges()
    print("\n%d passed, %d failed" % (PASS, FAIL))
    return 1 if FAIL else 0


if __name__ == "__main__":
    raise SystemExit(main())
