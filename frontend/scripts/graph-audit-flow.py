#!/usr/bin/env python
"""图示审查页（前端）实际请求序列的联调自查。

按 GraphAudit.vue 的调用顺序打真接口，断言页面渲染与归组所依赖的字段与不变量：
  getGraph(DRAFT/md-1) → validateGraph(md-1)（草稿才走 GR02）→ getGraph(FINAL_VERSION/us-1)

覆盖的页面行为（每节对应界面上的一个可见结论）：
  [1] 左栏四个统计数与七类清单的分母：图元、连线、独立论证图、孤立命题
  [2] 草稿全量审查 → VALID 且无发现（七类全部「未发现」）
  [3] 局部图示 → 遗漏关系（MISSING_RELATION）与孤立命题（ISOLATED_PROPOSITION）两类各自归位
  [4] 分类字段的完整性：后端下发的每条问题都归入七类之一或为空（页面据此落「其他结构问题」）
  [5] 统一定稿的图示：只有 GR01 可信，8 个孤立命题说明关系还没写入（D 的 J07/J08 未就绪）
  [6] 「图元错误」不由后端下发（页面按 nodes/edges 自查），故 issues 里不应出现它

用法（先起后端，H2 为内存库，每次启动种子一致）：
  1) JAVA_HOME=<JBR21> mvn -o spring-boot:run
  2) python -I frontend/scripts/graph-audit-flow.py
本脚本只读，但断言的是干净种子下的结论：必须排在写 md-1 的
smoke-relations.py / workbench-flow.py 之前（脚本会先探一次，脏库直接退 2）。
[5] 对 us-1 只断言不变式而非固定条数，故不挑剔裁定结果是否已写入。
"""
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"
DRAFT = "md-1"
FINAL = "us-1"
# 与后端 com.argument.relation.rules.AuditCategory 同名；前端 shared/graphAudit.ts 也照这份
SEVEN = ["MISSING_RELATION", "WRONG_DIRECTION", "JM_CONFUSION", "ILLEGAL_NESTING",
         "ISOLATED_PROPOSITION", "GLYPH_ERROR", "MULTIPLE_STRUCTURES"]
PASS = 0
FAIL = 0


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


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json", "Idempotency-Key": "graph-audit-flow",
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


def graph_of(owner_type, owner_id):
    status, body = call("GET", f"/annotation-owners/{owner_type}/{owner_id}/graph")
    return status, data_of(body)


def validate(elements, relations):
    return call("POST", f"/annotation-drafts/{DRAFT}/graph/validate",
                {"elementIds": elements, "relationIds": relations})


def by_category(issues, category):
    return [i for i in issues if i.get("category") == category]


def require_pristine_seed():
    """本脚本只读，但断言的是干净种子下的结论，故仍须在写脚本之前跑。

    它自己不污染任何人；反过来 smoke-relations.py 与 workbench-flow.py 都往 md-1
    写关系，跑过之后再进来，[1][2][3] 的图元/连线/分量数就全对不上了。与其让它
    伪装成回归，这里先探一次，直接给出可执行的结论。
    """
    status, body = call("GET", f"/annotation-drafts/{DRAFT}/relations?page=1&size=100")
    n = len(data_of(body).get("items") or []) if status == 200 else -1
    if n != 7:
        print(f"预检失败：{DRAFT} 当前有 {n} 条关系，干净种子应为 7 条。")
        print("该库已被写脚本（smoke-relations.py / workbench-flow.py）写过。")
        print("请重启后端清空内存库后重跑，并让本脚本排在它们之前。")
        raise SystemExit(2)


require_pristine_seed()

print("== [1] 左栏统计数：图元 / 连线 / 独立论证图 / 孤立命题 ==")
status, view = graph_of("DRAFT", DRAFT)
nodes, edges = view.get("nodes") or [], view.get("edges") or []
check("GR01 返回 200 且带 nodes/edges/componentCount/isolatedNodeIds",
      status == 200 and nodes and "componentCount" in view and "isolatedNodeIds" in view,
      [status, sorted(view.keys())[:8]])
check("图元与连线成对（连线两端都能在图元里找到）",
      all(e["source"] in {n["id"] for n in nodes} and e["target"] in {n["id"] for n in nodes}
          for e in edges),
      [e for e in edges if e["source"] not in {n["id"] for n in nodes}])
check("命题与关系在 nodes 里用 kind 区分（页面据此分左右统计）",
      {n["kind"] for n in nodes} <= {"ELEMENT", "RELATION"} and len(nodes) == 15,
      [(n["kind"], n["id"]) for n in nodes][:3])
check("独立论证图 2 个（分工.md §四.6 的「多个论证结构识别」取自这个数）",
      view.get("componentCount") == 2, view.get("componentCount"))
check("种子草稿无孤立命题（每个命题都参与了关系）",
      (view.get("isolatedNodeIds") or []) == [], view.get("isolatedNodeIds"))

elements = [n["id"] for n in nodes if n["kind"] == "ELEMENT"]
relations = [n["id"] for n in nodes if n["kind"] == "RELATION"]

print("== [2] 草稿全量审查：七类全部「未发现」 ==")
status, body = validate(elements, relations)
result = data_of(body)
check("GR02 返回 200 且 status=VALID", status == 200 and result.get("status") == "VALID",
      [status, result.get("status")])
check("全量图示无任何发现（页面七类全绿）", (result.get("issues") or []) == [],
      result.get("issues"))
check("全量图示的独立论证图数与 GR01 一致",
      result.get("componentCount") == view.get("componentCount"),
      [result.get("componentCount"), view.get("componentCount")])
check("全量图示无孤立命题", (result.get("isolatedNodeIds") or []) == [],
      result.get("isolatedNodeIds"))

print("== [3] 局部图示：遗漏关系与孤立命题各自归位 ==")
status, body = validate(elements, relations[:2])
partial = data_of(body)
issues = partial.get("issues") or []
missing = by_category(issues, "MISSING_RELATION")
isolated = by_category(issues, "ISOLATED_PROPOSITION")
check("局部图示报「遗漏关系」一类",
      len(missing) == 1 and missing[0]["code"] == "GRAPH_INCOMPLETE", missing)
check("遗漏关系是 WARNING（只提示，不阻断）", missing and missing[0]["level"] == "WARNING",
      missing[0]["level"] if missing else None)
check("局部图示报「孤立命题」一类，条数与 isolatedNodeIds 一致",
      len(isolated) == len(partial.get("isolatedNodeIds") or [])
      and len(isolated) == 5, [len(isolated), partial.get("isolatedNodeIds")])
check("孤立命题逐条点名到具体命题 id（页面点条目定位到图上）",
      all(any(nid in i["message"] for nid in partial["isolatedNodeIds"]) for i in isolated),
      [i["message"] for i in isolated])
check("两类互不串味（遗漏关系不混进孤立命题）",
      all(i["category"] == "MISSING_RELATION" for i in missing)
      and all(i["category"] == "ISOLATED_PROPOSITION" for i in isolated),
      [(i["code"], i["category"]) for i in issues])

print("== [4] 分类字段的完整性 ==")
check("后端下发的每条问题都带 category 字段（页面据此归组）",
      all("category" in i for i in issues), [i for i in issues if "category" not in i])
check("出现过的分类都在七类之内",
      {i["category"] for i in issues} <= set(SEVEN),
      {i["category"] for i in issues} - set(SEVEN))
check("未分类的问题 category 为空（页面把空值归入「其他结构问题」，不丢）",
      all(i.get("category") in SEVEN or not i.get("category") for i in issues),
      [i.get("category") for i in issues])
check("空白 category 与 null 都算未分类（前端 categoryOf 两种都兜住）",
      all(not i.get("category") for i in issues if i.get("category") not in SEVEN),
      [i.get("category") for i in issues if i.get("category") not in SEVEN])

print("== [5] 统一定稿的图示：只有 GR01 可信 ==")
status, final_view = graph_of("FINAL_VERSION", FINAL)
final_nodes = final_view.get("nodes") or []
final_edges = final_view.get("edges") or []
check("GR01 支持非草稿归属（FINAL_VERSION/us-1）返回 200", status == 200, status)
check("统一命题集合 8 条命题（数量由统一命题集合自身决定，与关系写入无关）",
      len(final_nodes) == 8, len(final_nodes))
check("非草稿归属不下发 completeness 类问题（GR02 是草稿接口，页面据此只做结构自查）",
      by_category(final_view.get("issues") or [], "MISSING_RELATION") == [],
      final_view.get("issues"))

# 不写死「关系数 = 0」：裁定结果（D 的 J07/J08）写入 us-1 之后这里本就会变多，
# adjudication-flow.py 正是那条写入路径。改断言页面真正依赖的两条不变式，
# 与写入状态无关：孤立命题 = 未接任何连线的命题；分量数 = 由图元与连线算出的分量数。
def isolated_by_edges(nodes, edges):
    touched = set()
    for e in edges:
        touched.add(e["source"])
        touched.add(e["target"])
    return sorted(n["id"] for n in nodes if n["kind"] == "ELEMENT" and n["id"] not in touched)


def components_by_edges(nodes, edges):
    parent = {n["id"]: n["id"] for n in nodes}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for e in edges:
        a, b = find(e["source"]), find(e["target"])
        if a != b:
            parent[a] = b
    return len({find(n["id"]) for n in nodes})


check("孤立命题集合与「未接任何连线的命题」逐一吻合（页面的孤立命题条目据此逐条点名）",
      sorted(final_view.get("isolatedNodeIds") or []) == isolated_by_edges(final_nodes, final_edges),
      [sorted(final_view.get("isolatedNodeIds") or []), isolated_by_edges(final_nodes, final_edges)])
check("独立论证图数与由图元/连线现算的分量数一致",
      final_view.get("componentCount") == components_by_edges(final_nodes, final_edges),
      [final_view.get("componentCount"), components_by_edges(final_nodes, final_edges)])
if not final_edges:
    check("（种子态）关系尚未由裁定结果写入：8 条命题各自成分量、全部孤立",
          final_view.get("componentCount") == 8
          and len(final_view.get("isolatedNodeIds") or []) == 8,
          [final_view.get("componentCount"), len(final_view.get("isolatedNodeIds") or [])])
    check("（种子态）上述 8 处孤立命题正是页面上可见的「孤立命题」结论",
          len(isolated_by_edges(final_nodes, final_edges)) == 8,
          isolated_by_edges(final_nodes, final_edges))
else:
    print("  SKIP  （种子态未满足：us-1 已被裁定结果写入 %d 条关系，跳过这两条）"
          % len(final_edges))

print("== [6] 「图元错误」不由后端下发 ==")
check("GR02 的 issues 里没有 GLYPH_ERROR（它由页面按 nodes/edges 自查）",
      by_category(issues, "GLYPH_ERROR") == [],
      by_category(issues, "GLYPH_ERROR"))
check("GR01 的 issues 里同样没有 GLYPH_ERROR",
      by_category(view.get("issues") or [], "GLYPH_ERROR") == [],
      view.get("issues"))
check("GR01 的 issues 已带关系级校验（方向/嵌套等在同一份清单里，前端不重算）",
      isinstance(view.get("issues"), list), type(view.get("issues")).__name__)

print()
print(f"结果：{PASS} 通过 / {FAIL} 失败")
raise SystemExit(1 if FAIL else 0)
