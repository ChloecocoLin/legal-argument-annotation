#!/usr/bin/env python
"""成员 C 冒烟脚本：关系定义 G07/G15/G16、关系实例 R01–R04、论证图 GR01–GR03。

用法（先起服务，H2 为内存库，每次启动种子一致）：
    1) JAVA_HOME=<JBR21> mvn -o spring-boot:run
    2) python backend/scripts/smoke-relations.py [base_url]
只读 + 少量写入；写入落在 md-1 上，因此**必须对着刚启动的后端跑一次**。
脚本开头会先预检 md-1 是否为干净种子（7 条关系），脏库直接退出并提示重启，
不会把「库被写过」伪装成回归。同理，别和 workbench-flow.py 共用同一个后端进程。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
PASS = 0
FAIL = 0


# ── 登录（分工.md §六，成员 C）──────────────────────────────────────────
# C 启用 Spring Security 后，未带令牌的请求一律 401。这里走的就是浏览器那条路：
# 先登录换令牌、再带 Authorization 头——**没有**任何「跳过鉴权」的开关。
# 令牌拿不到就立刻退出：否则后面每条断言都会以 401 的形式红一片，
# 真正的原因（后端没起 / 种子没进 / 口令不对）反而被埋在中间。
import pathlib

_p = pathlib.Path(__file__).resolve().parent
while not (_p / ".git").exists():
    _p = _p.parent
sys.path.insert(0, str(_p / "scripts"))
from verify_auth import login_token  # noqa: E402

TOKEN = login_token(base=BASE)


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json", "Idempotency-Key": "smoke-relations",
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


def code_of(payload):
    return ((payload or {}).get("error") or {}).get("code")


def data_of(payload):
    return (payload or {}).get("data") or {}


def participants(*triples):
    return [{"ordinal": i + 1, "role": role, "targetKind": kind, "targetId": tid}
            for i, (role, kind, tid) in enumerate(triples)]


def require_pristine_seed():
    """本脚本会往 md-1 写关系，不能和同样写 md-1 的脚本共用后端进程。

    H2 是内存库、每次启动种子一致；但同一次启动内，前面的脚本留下的关系会让
    后面的断言成片报出与实际行为无关的失败（条数对不上、layoutVersion 错位、
    种子 id 已不存在）。与其让它伪装成回归，这里先探一次，直接给出可执行的结论。
    """
    status, body = call("GET", "/annotation-drafts/md-1/relations?page=1&size=100")
    n = len(data_of(body).get("items") or []) if status == 200 else -1
    if n != 7:
        print(f"预检失败：md-1 当前有 {n} 条关系，干净种子应为 7 条。")
        print("该库已被其它脚本写过（workbench-flow.py 也写 md-1）。")
        print("请重启后端清空内存库后重跑，不要在同一进程上先后运行这两个脚本。")
        raise SystemExit(2)


require_pristine_seed()

print("== G07 读取关系定义 ==")
status, body = call("GET", "/guide-versions/v-g-12/relations")
items = data_of(body).get("items") or []
check("G07 返回 data.items", status == 200 and len(items) == 5, f"{status} {len(items)}")
check("G07 方向与上下限与字典一致",
      [(i["code"], i["directed"], i["minParticipants"], i["maxParticipants"]) for i in items]
      == [("S", True, 2, 2), ("A", True, 2, 2), ("J", False, 2, None),
          ("M", True, 2, 2), ("I", False, 2, None)],
      items)
check("G07 带 directionRule / sortOrder", all(i.get("directionRule") and i.get("sortOrder")
                                              for i in items))

print("== G15/G16 指南版本锁定 ==")
status, body = call("POST", "/guide-versions/v-g-12/relations",
                    {"code": "S", "definition": "x", "directionRule": "x",
                     "expressionTemplate": "x", "participantRule": "x", "diagramRule": "x"})
check("G15 已发布版本 → GUIDE_VERSION_LOCKED",
      status == 409 and code_of(body) == "GUIDE_VERSION_LOCKED", f"{status} {code_of(body)}")
status, body = call("PATCH", "/relation-definitions/r12-s", {"definition": "x"})
check("G16 已发布版本 → GUIDE_VERSION_LOCKED",
      status == 409 and code_of(body) == "GUIDE_VERSION_LOCKED", f"{status} {code_of(body)}")

print("== G15/G16 草稿版本 ==")
_, body = call("POST", "/guides", {"title": "C 冒烟指南", "description": "smoke"})
guide_id = data_of(body).get("id")
_, body = call("POST", f"/guides/{guide_id}/versions", {"versionCode": "0.1"})
version_id = data_of(body).get("id")
check("G04 建立草稿版本", bool(version_id), body)

payload = {"code": "S", "definition": "冒烟定义", "directionRule": "从支持者指向被支持命题。",
           "expressionTemplate": "S(x, y)", "participantRule": "二元有序。", "diagramRule": "实心圆有向。"}
status, body = call("POST", f"/guide-versions/{version_id}/relations", payload)
created_def = data_of(body)
check("G15 草稿新增成功且派生 directed/min/max/sort",
      status == 200 and created_def.get("directed") is True
      and created_def.get("maxParticipants") == 2 and created_def.get("sortOrder") == 1,
      body)
status, body = call("POST", f"/guide-versions/{version_id}/relations", payload)
check("G15 同版本同 code 重复 → CONFLICT",
      status == 409 and code_of(body) == "CONFLICT", f"{status} {code_of(body)}")
status, body = call("POST", f"/guide-versions/{version_id}/relations",
                    dict(payload, code="Z"))
check("G15 非法 code → VALIDATION_ERROR",
      status == 400 and code_of(body) == "VALIDATION_ERROR", f"{status} {code_of(body)}")
status, body = call("PATCH", f"/relation-definitions/{created_def.get('id')}",
                    {"diagramRule": "冒烟改动"})
check("G16 局部更新成功", status == 200 and data_of(body).get("diagramRule") == "冒烟改动", body)
status, body = call("PATCH", f"/relation-definitions/{created_def.get('id')}", {})
check("G16 空补丁 → VALIDATION_ERROR",
      status == 400 and code_of(body) == "VALIDATION_ERROR", f"{status} {code_of(body)}")

print("== R01/R02 关系实例 ==")
status, body = call("GET", "/annotation-drafts/md-1/relations")
check("R01 分页返回 7 条种子关系", status == 200 and len(data_of(body).get("items") or []) == 7,
      f"{status} {len(data_of(body).get('items') or [])}")
check("R01 meta 为 PageMeta",
      (body.get("meta") or {}).get("total") == 7, body.get("meta"))
first = (data_of(body).get("items") or [{}])[0]
check("R01 首条按 S 优先且带参与项与表达式",
      first.get("relationCode") == "S" and first.get("participants") and first.get("expression"),
      first)

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "J", "participants": participants(
                        ("MEMBER", "ELEMENT", "mp-9"), ("MEMBER", "ELEMENT", "mp-1"))})
check("R02 非命题参与 → INVALID_PARTICIPANT",
      status == 400 and code_of(body) == "INVALID_PARTICIPANT", f"{status} {code_of(body)}")

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "S", "participants": participants(
                        ("SOURCE", "ELEMENT", "mp-1"))})
check("R02 S 只有一个参与项 → INVALID_PARTICIPANT",
      status == 400 and code_of(body) == "INVALID_PARTICIPANT", f"{status} {code_of(body)}")

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "I", "participants": participants(
                        ("MEMBER", "ELEMENT", "mp-1"), ("MEMBER", "RELATION", "rel-1"))})
check("R02 I 的成员传关系 → INVALID_PARTICIPANT（后续关系用代表命题）",
      status == 400 and code_of(body) == "INVALID_PARTICIPANT", f"{status} {code_of(body)}")

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "M", "participants": participants(
                        ("SOURCE", "ELEMENT", "mp-1"), ("TARGET", "ELEMENT", "mp-2"))})
check("R02 M 的来源不是个别判断 → INVALID_PARTICIPANT",
      status == 400 and code_of(body) == "INVALID_PARTICIPANT", f"{status} {code_of(body)}")

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "I", "participants": participants(
                        ("MEMBER", "ELEMENT", "mp-1"), ("MEMBER", "ELEMENT", "mp-5"))})
new_i = data_of(body).get("id")
check("R02 I(P1,P5) 建立成功且 directed=false",
      status == 200 and new_i and data_of(body).get("directed") is False, body)

status, body = call("POST", "/annotation-drafts/md-1/relations",
                    {"relationCode": "M", "participants": participants(
                        ("SOURCE", "ELEMENT", "mp-2"), ("TARGET", "ELEMENT", "mp-1"))})
new_m = data_of(body).get("id")
check("R02 M(P2,P1) 建立成功且 directed=true",
      status == 200 and new_m and data_of(body).get("directed") is True, body)

print("== R03 整集替换与环检测 ==")
status, body = call("PUT", "/relations/rel-4/participants",
                    {"participants": participants(("SOURCE", "ELEMENT", "mp-4"),
                                                  ("TARGET", "ELEMENT", "mp-3"))})
check("R03 整集替换成功", status == 200 and data_of(body).get("participantCount") == 2, body)

status, body = call("PUT", "/relations/rel-1/participants",
                    {"participants": participants(("MEMBER", "ELEMENT", "mp-2"),
                                                  ("MEMBER", "ELEMENT", "mp-3"),
                                                  ("MEMBER", "RELATION", "rel-2"))})
check("R03 制造嵌套环 rel-1 → rel-2 → rel-1 → RELATION_CYCLE",
      status == 400 and code_of(body) == "RELATION_CYCLE", f"{status} {code_of(body)}")

status, body = call("PUT", "/relations/rel-1/participants",
                    {"participants": participants(("MEMBER", "ELEMENT", "mp-2"),
                                                  ("MEMBER", "RELATION", "rel-1"))})
check("R03 自引用 → INVALID_PARTICIPANT",
      status == 400 and code_of(body) == "INVALID_PARTICIPANT", f"{status} {code_of(body)}")

print("== R04 删除 ==")
status, body = call("DELETE", "/relations/rel-1", {"reason": "冒烟：被 rel-2 嵌套引用"})
check("R04 被嵌套引用 → NESTED_REFERENCE_EXISTS",
      status == 409 and code_of(body) == "NESTED_REFERENCE_EXISTS", f"{status} {code_of(body)}")
status, body = call("DELETE", "/relations/rel-7", {"reason": "冒烟：删除演示关系"})
check("R04 无引用 → status=REMOVED",
      status == 200 and data_of(body).get("status") == "REMOVED", body)
status, body = call("GET", "/annotation-drafts/md-1/relations")
codes = [i["id"] for i in (data_of(body).get("items") or [])]
check("R01 删除后不再出现", "rel-7" not in codes, codes)

print("== GR01 论证图 ==")
status, body = call("GET", "/annotation-owners/DRAFT/md-1/graph")
graph = data_of(body)
node_ids = [n["id"] for n in (graph.get("nodes") or [])]
element_ids = [n["id"] for n in (graph.get("nodes") or []) if n["kind"] == "ELEMENT"]
relation_ids = [n["id"] for n in (graph.get("nodes") or []) if n["kind"] == "RELATION"]
check("GR01 返回 graphId/status/layoutVersion",
      status == 200 and graph.get("graphId") and graph.get("status") == "VALID"
      and graph.get("layoutVersion") == 0, f"{status} {graph.get('status')}")
check("GR01 覆盖 8 个命题与 8 条关系（含新建两条），非命题不入图",
      len(element_ids) == 8 and len(relation_ids) == 8,
      f"elements={len(element_ids)} relations={len(relation_ids)}")
check("GR01 识别两个独立论证图",
      graph.get("componentCount") == 2, graph.get("componentCount"))
check("GR01 无孤立命题（每个命题都参与关系）",
      graph.get("isolatedNodeIds") == [], graph.get("isolatedNodeIds"))
edges = graph.get("edges") or []
check("GR01 嵌套不压平：rel-2 的 SOURCE 是关系 rel-1 的节点",
      any(e["source"] == "rel-1" and e["target"] == "rel-2" for e in edges),
      [e for e in edges if e["relationId"] == "rel-2"])
check("GR01 J 连线无箭头、I 连线虚线无箭头",
      all(e["arrow"] is False for e in edges if e["relationCode"] in ("J", "I"))
      and all(e["dashed"] is True for e in edges if e["relationCode"] == "I"),
      [e for e in edges if e["relationCode"] in ("J", "I")])
check("GR01 S/A/M 连线带箭头且指向目标",
      all(e["arrow"] is True for e in edges if e["relationCode"] in ("S", "A", "M"))
      and any(e["source"] == "rel-3" and e["target"] == "mp-5" and e["arrow"] is True
              for e in edges),
      [e for e in edges if e["relationId"] == "rel-3"])
i_node = next((n for n in graph.get("nodes") or [] if n["id"] == new_i), None)
check("GR01 I 节点带代表命题（组内最大展示编号）",
      bool(i_node) and i_node.get("representativeId") == "mp-5", i_node)

print("== GR02 图示审查 ==")
status, body = call("POST", "/annotation-drafts/md-1/graph/validate",
                    {"elementIds": element_ids, "relationIds": relation_ids})
result = data_of(body)
check("GR02 全量图示 → VALID，无 ERROR",
      status == 200 and result.get("status") == "VALID"
      and not [i for i in (result.get("issues") or []) if i["level"] == "ERROR"], body)

status, body = call("POST", "/annotation-drafts/md-1/graph/validate",
                    {"elementIds": [i for i in element_ids if i != "mp-5"],
                     "relationIds": ["rel-2"]})
result = data_of(body)
check("GR02 缺参与项 → INVALID + GRAPH_INVALID",
      status == 200 and result.get("status") == "INVALID"
      and result.get("issues") and result["issues"][0]["code"] == "GRAPH_INVALID", body)

status, body = call("POST", "/annotation-drafts/md-1/graph/validate",
                    {"elementIds": ["mp-9"], "relationIds": []})
result = data_of(body)
check("GR02 非命题要素（IS）→ INVALID + GRAPH_INVALID",
      status == 200 and result.get("status") == "INVALID"
      and any(i["code"] == "GRAPH_INVALID" for i in result.get("issues") or []), body)

print("== GR03 布局 ==")
graph_id = graph.get("graphId")
positions = [{"targetKind": "ELEMENT", "targetId": e, "x": 100 + 10 * i, "y": 100}
             for i, e in enumerate(element_ids)] + \
            [{"targetKind": "RELATION", "targetId": r, "x": 100 + 10 * i, "y": 200}
             for i, r in enumerate(relation_ids)]
status, body = call("POST", f"/graphs/{graph_id}/layouts",
                    {"canvasWidth": 1200, "canvasHeight": 800, "nodePositions": positions})
check("GR03 保存布局 → layoutVersion=1",
      status == 200 and data_of(body).get("layoutVersion") == 1, body)
status, body = call("POST", f"/graphs/{graph_id}/layouts",
                    {"canvasWidth": 1200, "canvasHeight": 800, "nodePositions": positions})
check("GR03 再次保存 → layoutVersion=2",
      status == 200 and data_of(body).get("layoutVersion") == 2, body)
status, body = call("POST", f"/graphs/{graph_id}/layouts",
                    {"canvasWidth": 1200, "canvasHeight": 800,
                     "nodePositions": [{"targetKind": "ELEMENT", "targetId": "mp-不存在",
                                        "x": 1, "y": 2}]})
check("GR03 未知节点 → LAYOUT_NODE_UNKNOWN",
      status == 400 and code_of(body) == "LAYOUT_NODE_UNKNOWN", f"{status} {code_of(body)}")
status, body = call("POST", f"/graphs/{graph_id}/layouts",
                    {"canvasWidth": 0, "canvasHeight": 800, "nodePositions": []})
check("GR03 画布宽为 0 → VALIDATION_ERROR",
      status == 400 and code_of(body) == "VALIDATION_ERROR", f"{status} {code_of(body)}")

status, body = call("GET", "/annotation-owners/DRAFT/md-1/graph?layoutVersion=1")
check("GR01 指定 layoutVersion 取回坐标",
      status == 200 and data_of(body).get("layoutVersion") == 1
      and len(data_of(body).get("positions") or []) == len(positions), body)

print("== 临时桥接：模拟命题 ==")
status, body = call("GET", "/annotation-drafts/md-1/mock-propositions")
items = data_of(body).get("items") or []
check("临时命题接口返回 10 项且区分命题/非命题",
      status == 200 and len(items) == 10
      and sum(1 for i in items if i["proposition"]) == 8, f"{status} {len(items)}")

print()
print(f"通过 {PASS}，失败 {FAIL}")
sys.exit(1 if FAIL else 0)
