#!/usr/bin/env python
"""标注工作台（前端）实际请求序列的联调自查。

按 AnnotateWorkbench.vue 的调用顺序打真接口，断言前端渲染所依赖的字段与不变量：
  listMockPropositions → listRelations → getGraph → validateGraph → 建关系（含嵌套 A(po,S) 与 I）
  → getGraph（图元方向/虚线/代表节点）→ saveGraphLayout（全量整数坐标）→ getGraph?layoutVersion

用法（先起后端，H2 为内存库，每次启动种子一致）：
  1) JAVA_HOME=<JBR21> mvn -o spring-boot:run
  2) python -I frontend/scripts/workbench-flow.py
写入落在 md-1 上，因此**必须对着刚启动的后端跑一次**。脚本开头会先预检 md-1
是否为干净种子（7 条关系），脏库直接退出并提示重启，不会把「库被写过」伪装成回归。
同理，别和 smoke-relations.py 共用同一个后端进程。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"
DRAFT = "md-1"
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

def call(method, path, body=None, idem="workbench-flow"):
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


def require_pristine_seed():
    """本脚本会往 md-1 写关系与布局，不能和同样写 md-1 的脚本共用后端进程。

    H2 是内存库、每次启动种子一致；但同一次启动内，前面的脚本留下的关系会让
    后面的断言成片报出与实际行为无关的失败（条数对不上、layoutVersion 错位、
    种子 id 已不存在）。与其让它伪装成回归，这里先探一次，直接给出可执行的结论。
    """
    status, body = call("GET", f"/annotation-drafts/{DRAFT}/relations?page=1&size=100")
    n = len(data_of(body).get("items") or []) if status == 200 else -1
    if n != 7:
        print(f"预检失败：md-1 当前有 {n} 条关系，干净种子应为 7 条。")
        print("该库已被其它脚本写过（smoke-relations.py 也写 md-1）。")
        print("请重启后端清空内存库后重跑，不要在同一进程上先后运行这两个脚本。")
        raise SystemExit(2)


require_pristine_seed()

print("== 左栏：模拟命题（listMockPropositions）==")
status, body = call("GET", f"/annotation-drafts/{DRAFT}/mock-propositions")
items = data_of(body).get("items") or []
check("返回 data.items（前端读 r.items）", status == 200 and len(items) == 10, f"{status} {len(items)}")
check("每条都带前端渲染字段 id/text/labelCode/displayNo/proposition",
      all(set(["id", "text", "labelCode", "displayNo", "proposition"]) <= set(i.keys())
          for i in items), items[0] if items else None)
check("命题 8 条带 P 编号、非命题 2 条 displayNo 为 null",
      sum(1 for i in items if i["proposition"]) == 8
      and all(i["displayNo"] is None for i in items if not i["proposition"]), items)

print("== 中栏：关系列表（listRelations）==")
status, body = call("GET", f"/annotation-drafts/{DRAFT}/relations?size=100")
relations = data_of(body).get("items") or []
check("分页信封 meta.total 与 items 一致",
      (body.get("meta") or {}).get("total") == len(relations) == 7, f"{len(relations)}")
check("每条带 expression（.rel-item .formula 直接渲染）",
      all(r.get("expression") for r in relations), relations[0] if relations else None)
check("参与项带 ordinal/role/targetKind/targetId 与显示辅助字段",
      all(p.get("ordinal") and p.get("role") and p.get("targetKind") and p.get("targetId")
          for r in relations for p in r["participants"]), relations[0]["participants"])
check("无向关系 directed=false、有向 true",
      all((r["relationCode"] in ("J", "I")) == (r["directed"] is False) for r in relations),
      [(r["relationCode"], r["directed"]) for r in relations])
check("列表顺序按 S → J → M → A → I（工作台标注顺序）",
      [r["relationCode"] for r in relations] == sorted(
          [r["relationCode"] for r in relations],
          key=lambda c: ["S", "J", "M", "A", "I"].index(c)),
      [r["relationCode"] for r in relations])

print("== 右栏：论证图（getGraph DRAFT/md-1）==")
status, body = call("GET", f"/annotation-owners/DRAFT/{DRAFT}/graph")
graph = data_of(body)
nodes = graph.get("nodes") or []
edges = graph.get("edges") or []
by_id = {n["id"]: n for n in nodes}
check("graphId/status/layoutVersion 齐备", bool(graph.get("graphId")) and graph.get("status"), graph.get("status"))
check("首屏无布局时 positions 为空（前端走自动布局）", graph.get("positions") == [], graph.get("positions"))
check("节点字段满足绘图：kind/displayNo/relationCode/expression/representativeId",
      all(set(["id", "kind", "displayNo", "relationCode", "expression", "representativeId"]) <= set(n.keys())
          for n in nodes), nodes[0] if nodes else None)
check("边字段满足绘图：source/target/relationCode/arrow/dashed",
      all(set(["source", "target", "relationCode", "arrow", "dashed"]) <= set(e.keys())
          for e in edges), edges[0] if edges else None)
check("识别 2 个独立论证图", graph.get("componentCount") == 2, graph.get("componentCount"))
check("嵌套不压平：rel-1 到 rel-2 的边存在（关系节点可作参与项）",
      any(e["source"] == "rel-1" and e["target"] == "rel-2" for e in edges))

print("== 加载时的图示校验（validateGraph 全量）==")
all_elements = [n["id"] for n in nodes if n["kind"] == "ELEMENT"]
all_relations = [n["id"] for n in nodes if n["kind"] == "RELATION"]
status, body = call("POST", f"/annotation-drafts/{DRAFT}/graph/validate",
                    {"elementIds": all_elements, "relationIds": all_relations})
validation = data_of(body)
check("全量图示 → VALID 且无 ERROR",
      validation.get("status") == "VALID"
      and not [i for i in (validation.get("issues") or []) if i["level"] == "ERROR"], validation)

print("== 建关系：嵌套 A(po, S(...))（前端 orderedInputs 的出参形状）==")
s_rel = next(r for r in relations if r["relationCode"] == "S")
status, body = call("POST", f"/annotation-drafts/{DRAFT}/relations", {
    "relationCode": "A",
    "participants": [
        {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "mp-4"},
        {"ordinal": 2, "role": "TARGET", "targetKind": "RELATION", "targetId": s_rel["id"]}]})
new_a = data_of(body).get("id")
check("A(P4, S 关系整体) 建立成功", status == 200 and bool(new_a), f"{status} {body}")

status, body = call("GET", f"/annotation-drafts/{DRAFT}/relations?size=100")
new_a_row = next((r for r in data_of(body).get("items") or [] if r["id"] == new_a), None)
check("新关系表达式形如 A(P4,S(...))（中文界面用它做公式）",
      bool(new_a_row) and new_a_row["expression"].startswith("A(P4,S("), new_a_row)

print("== 建关系：I（成员只能是命题，前端传 MEMBER）==")
status, body = call("POST", f"/annotation-drafts/{DRAFT}/relations", {
    "relationCode": "I",
    "participants": [
        {"ordinal": 1, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "mp-5"},
        {"ordinal": 2, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "mp-1"}]})
new_i = data_of(body).get("id")
check("I(P5,P1) 建立成功且 directed=false",
      status == 200 and bool(new_i) and data_of(body).get("directed") is False, f"{status} {body}")

status, body = call("GET", f"/annotation-owners/DRAFT/{DRAFT}/graph")
graph2 = data_of(body)
nodes2 = {n["id"]: n for n in graph2.get("nodes") or []}
edges2 = graph2.get("edges") or []
check("新 A 的两条边：来源→关系节点、关系节点→被反对对象，均带箭头",
      any(e["source"] == "mp-4" and e["target"] == new_a and e["arrow"] for e in edges2)
      and any(e["source"] == new_a and e["arrow"] for e in edges2),
      [e for e in edges2 if e["relationId"] == new_a])
check("A 的攻击对象是 S 关系整体（嵌套边指向关系节点）",
      any(e["source"] == new_a and e["target"] == s_rel["id"] for e in edges2),
      [e for e in edges2 if e["relationId"] == new_a])
i_node = nodes2.get(new_i)
check("I 节点带代表命题 mp-5（组内最大展示编号）",
      bool(i_node) and i_node.get("representativeId") == "mp-5", i_node)
check("I 的边虚线、无箭头（框内“/”不另画圆节点）",
      all(e["dashed"] is True and e["arrow"] is False
          for e in edges2 if e["relationId"] == new_i),
      [e for e in edges2 if e["relationId"] == new_i])
check("新增关系后仍是 2 个独立论证图",
      graph2.get("componentCount") == 2, graph2.get("componentCount"))

print("== 保存布局（saveGraphLayout 全量整数坐标）==")
ids = [(n["id"], n["kind"]) for n in graph2.get("nodes") or []]
positions = [{"targetKind": kind, "targetId": nid, "x": 100 + 30 * i, "y": 80 + 20 * i}
             for i, (nid, kind) in enumerate(ids)]
status, body = call("POST", f"/graphs/{graph2['graphId']}/layouts",
                    {"canvasWidth": 1200, "canvasHeight": 800, "nodePositions": positions})
check("保存成功且返回 layoutVersion=1", status == 200 and data_of(body).get("layoutVersion") == 1, body)

status, body = call("GET", f"/annotation-owners/DRAFT/{DRAFT}/graph?layoutVersion=1")
restored = data_of(body)
check("取回布局完整覆盖全部节点（前端 applyPositions 命中数 == 节点数，不触发自动布局）",
      len(restored.get("positions") or []) == len(ids) == len(restored.get("nodes") or []),
      f"positions={len(restored.get('positions') or [])} nodes={len(restored.get('nodes') or [])}")
check("坐标为保存时写入的整数值",
      all(p["x"] % 1 == 0 and p["y"] % 1 == 0 for p in restored.get("positions") or []),
      (restored.get("positions") or [])[:2])

print("== 非法操作的中文反馈（前端把 error.message 显示在操作区）==")
status, body = call("POST", f"/annotation-drafts/{DRAFT}/relations", {
    "relationCode": "I",
    "participants": [
        {"ordinal": 1, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "mp-9"},
        {"ordinal": 2, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "mp-1"}]})
message = ((body or {}).get("error") or {}).get("message")
check("非命题参与 I → INVALID_PARTICIPANT 且带中文原因",
      status == 400 and ((body or {}).get("error") or {}).get("code") == "INVALID_PARTICIPANT"
      and "非命题" in (message or ""), f"{status} {message}")

status, body = call("DELETE", f"/relations/{s_rel['id']}", {"reason": "工作台联调"})
check("删除被嵌套引用的 S 关系 → NESTED_REFERENCE_EXISTS（前端弹窗保留）",
      status == 409 and ((body or {}).get("error") or {}).get("code") == "NESTED_REFERENCE_EXISTS",
      f"{status} {body}")

print()
print(f"通过 {PASS}，失败 {FAIL}")
sys.exit(1 if FAIL else 0)
