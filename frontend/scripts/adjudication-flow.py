#!/usr/bin/env python
"""关系裁定页（前端）实际请求序列的联调自查。

按 RelationAdjudication.vue 的调用顺序打真接口，断言页面渲染与交互所依赖的字段与不变量：
  listRelationItems（J06，size=100）→ listMockPropositions(us-1)（自定义弹窗的候选命题）
  → 逐项决定 → resolveRelationResult（生成关系裁定结果）

覆盖的页面行为（每节对应界面上的一个可见结论）：
  [1] 左栏列表 8 条与五类徽章、嵌套提示
  [2] 自定义弹窗的候选来源：统一命题集合 us-1
  [3] 「采用 V1/V2」逐项裁完 → COMPLETE，合并关系与所采候选一一对应
  [4] 一致项不确认就进不了最终审查（未决项不凭空补全）
  [5] 「修改方向」「修改嵌套」→ CUSTOM，端点用 us-pN 与已保留的内层关系
  [6] 悬空端点：候选被标 unmapped、强行采用则该决定不成立并报 INVALID_RELATION_MAPPING
  [7] 门禁：命题未冻结的会话打不开关系裁定（页面显示 gateHint）

用法（先起后端，H2 为内存库，每次启动种子一致）：
  1) JAVA_HOME=<JBR21> mvn -o spring-boot:run
  2) python -I frontend/scripts/adjudication-flow.py
"""
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"
SESSION = "ses-1"          # 主演示：8 条，覆盖全部 5 类
UNMAPPED_SESSION = "ses-3"  # 悬空端点演示
GATED_SESSION = "ses-2"     # 命题阶段未冻结
UNIFIED = "us-1"            # 页面上写死的统一命题集合（D 的 J05 就绪后由会话带出）
PASS = 0
FAIL = 0


def call(method, path, body=None, idem="adjudication-flow"):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json", "Idempotency-Key": idem})
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


def items_of(session):
    """页面的加载动作：J06 一次取满（PAGE_SIZE=100），过滤在本地做。"""
    status, body = call("GET", f"/adjudication-sessions/{session}/relation-items?page=1&size=100")
    return status, body, data_of(body).get("items") or []


def item_by(items, conflict_type=None, dimension=None):
    for item in items:
        if conflict_type and item["conflictType"] != conflict_type:
            continue
        if dimension and item["dimension"] != dimension:
            continue
        return item
    raise AssertionError(f"找不到裁定项 conflictType={conflict_type} dimension={dimension}")


def resolve(session, decisions):
    """页面的提交动作：不传 unifiedSnapshotId，由后端取会话声明的统一命题集合。"""
    return call("POST", f"/adjudication-sessions/{session}/relation-result",
                {"decisions": decisions}, idem="adjudication-flow-result")


def adopt(item):
    """「采用 V1/V2」：页面点候选头上的按钮，带上该候选的 sourceObjectId 与 sourceVersionId。"""
    candidate = item["candidates"][0]
    return {"itemId": item["id"], "action": "ADOPT_VERSION",
            "sourceVersionId": candidate["versionId"], "sourceObjectId": candidate["relationId"],
            "reason": "页面自查：采用第一候选"}


print("== [1] 左栏：关系裁定项列表（listRelationItems）==")
status, body, items = items_of(SESSION)
check("J06 返回 200 且信封带 meta.total", status == 200 and (body.get("meta") or {}).get("total") == 8,
      f"{status} {body.get('meta')}")
check("items 8 条（前端用 meta 之外还直接用 items.length 画进度）", len(items) == 8, len(items))
check("每条都带页面读取的字段 id/conflictType/dimension/reason/candidates/nestedFirst",
      all({"id", "conflictType", "dimension", "reason", "candidates", "nestedFirst", "status"}
          <= set(i.keys()) for i in items), items[0] if items else None)
check("五类 conflictType 齐备（CONFLICT_META 的五种徽章都画得出来）",
      {i["conflictType"] for i in items} == {"EXISTENCE", "TYPE", "DIRECTION", "PARTICIPANTS", "AGREEMENT"},
      sorted({i["conflictType"] for i in items}))
check("每个候选都有 versionLabel 与 expression（列表摘要 `V1 S(P2,P3) ≠ V2 S(P3,P2)` 靠它们）",
      all(c.get("versionLabel") and c.get("expression") for i in items for c in i["candidates"]),
      [c for i in items for c in i["candidates"] if not c.get("expression")])
check("存在性差异的候选只有一侧（页面据此只画一个「采用」按钮）",
      all(len(i["candidates"]) == 1 for i in items if i["conflictType"] == "EXISTENCE"),
      [(i["dimension"], len(i["candidates"])) for i in items if i["conflictType"] == "EXISTENCE"])
check("嵌套层级项标了 nestedFirst（页面显示「含嵌套，先裁内层」）",
      item_by(items, "PARTICIPANTS", "嵌套层级")["nestedFirst"] is True)
check("dimension 比 conflictType 细一档，两类 PARTICIPANTS 分得开",
      {item_by(items, "PARTICIPANTS", "组合成员")["dimension"],
       item_by(items, "PARTICIPANTS", "嵌套层级")["dimension"]} == {"组合成员", "嵌套层级"})
check("reason 是可直接显示的中文理由（页面在详情区整段渲染）",
      all(len(i["reason"]) > 10 and any("一" <= ch <= "鿿" for ch in i["reason"])
          for i in items), items[0]["reason"])

print("== [2] 自定义弹窗的候选来源：统一命题集合 ==")
status, body = call("GET", f"/annotation-drafts/{UNIFIED}/mock-propositions")
unified = data_of(body).get("items") or []
check("us-1 返回 8 条命题（弹窗「候选命题」一栏）",
      status == 200 and sum(1 for p in unified if p["proposition"]) == 8, len(unified))
check("命题 id 形如 us-pN（CUSTOM 的端点就填它）",
      all(p["id"].startswith("us-p") for p in unified if p["proposition"]),
      [p["id"] for p in unified][:3])
check("displayNo 为 P1…P8（弹窗按数值序排列，与 relationRules.parseDisplayNo 一致）",
      [p["displayNo"] for p in unified if p["proposition"]] == [f"P{i}" for i in range(1, 9)],
      [p["displayNo"] for p in unified if p["proposition"]])

print("== [3] 逐项「采用 V1/V2」→ 生成关系裁定结果 ==")
all_adopt = [adopt(i) for i in items]
status, body = resolve(SESSION, all_adopt)
result = data_of(body)
check("POST relation-result 返回 200（非契约接口，只读试算）", status == 200, status)
check("全部采用后 status=COMPLETE", result.get("status") == "COMPLETE", result.get("status"))
check("decidedCount=8、无未决、无重裁",
      result.get("decidedCount") == 8 and result.get("pendingItemIds") == []
      and result.get("reAdjudicationItemIds") == [],
      [result.get("decidedCount"), result.get("pendingItemIds")])
check("conflictCount + agreementCount = 项数（右栏两个统计数）",
      result.get("conflictCount") + result.get("agreementCount") == 8,
      [result.get("conflictCount"), result.get("agreementCount")])
check("合并关系 8 条，ownerType=FINAL_VERSION、ownerId=统一命题集合",
      len(result.get("relations") or []) == 8
      and all(r["ownerType"] == "FINAL_VERSION" and r["ownerId"] == UNIFIED
              for r in result["relations"]), len(result.get("relations") or []))
check("合并关系带展示表达式（页面「合并后的关系」列表直接渲染）",
      all(r.get("expression") for r in result.get("relations") or []),
      [r.get("expression") for r in result.get("relations") or []])
check("每条合并关系记录被采用的来源（sourceRelationIds）",
      all(len(r.get("sourceRelationIds") or []) == 1 for r in result.get("relations") or []),
      [r.get("sourceRelationIds") for r in result.get("relations") or []])
check("正式论证图可渲染：nodes/edges/componentCount 齐备",
      (result.get("graph") or {}).get("nodes") and "edges" in result["graph"]
      and isinstance(result["graph"].get("componentCount"), int), result.get("graph", {}).keys())
check("没有 ERROR 级 issue 时才可能 COMPLETE",
      all(i["level"] != "ERROR" for i in result.get("issues") or []),
      [i for i in result.get("issues") or [] if i["level"] == "ERROR"])

print("== [4] 一致项不确认就进不了最终审查 ==")
without_agreement = [d for d in all_adopt
                     if d["itemId"] != item_by(items, "AGREEMENT")["id"]]
status, body = resolve(SESSION, without_agreement)
result = data_of(body)
check("漏掉一致项 → INCOMPLETE（页面显示未决提示）", result.get("status") == "INCOMPLETE",
      result.get("status"))
check("pendingItemIds 恰为那条一致项（未决项不凭空补全）",
      result.get("pendingItemIds") == [item_by(items, "AGREEMENT")["id"]],
      result.get("pendingItemIds"))
check("命中的是「一致项」那条（页面用 conflictType 标出第几项）",
      item_by(items, "AGREEMENT")["dimension"] == "一致项")

status, body = resolve(SESSION, [])
result = data_of(body)
check("一个决定都不给 → INCOMPLETE 且 8 项全待办、合并关系为空",
      result.get("status") == "INCOMPLETE" and len(result.get("pendingItemIds") or []) == 8
      and (result.get("relations") or []) == [],
      [result.get("status"), len(result.get("pendingItemIds") or [])])

print("== [5] 自定义关系：修改方向 / 修改嵌套 ==")
direction = item_by(items, "DIRECTION")
custom_direction = {"itemId": direction["id"], "action": "CUSTOM",
                    "customRelation": {"relationCode": "S", "participants": [
                        {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "us-p3"},
                        {"ordinal": 2, "role": "TARGET", "targetKind": "ELEMENT", "targetId": "us-p2"}]},
                    "reason": "页面自查：按方向差异改写"}
# 组合成员项先采用其 V1（J(P2,P3)），它成为「已保留的内层关系」，供外层嵌套引用
join_item = item_by(items, "PARTICIPANTS", "组合成员")
join_adopt = adopt(join_item)
join_source_id = join_adopt["sourceObjectId"]
nested_item = item_by(items, "PARTICIPANTS", "嵌套层级")
custom_nested = {"itemId": nested_item["id"], "action": "CUSTOM",
                 "customRelation": {"relationCode": "S", "participants": [
                     {"ordinal": 1, "role": "SOURCE", "targetKind": "RELATION",
                      "targetId": join_source_id},
                     {"ordinal": 2, "role": "TARGET", "targetKind": "ELEMENT", "targetId": "us-p5"}]},
                 "reason": "页面自查：保留嵌套结构"}

status, body = resolve(SESSION, [custom_direction, join_adopt, custom_nested])
result = data_of(body)
customs = [r for r in result.get("relations") or [] if not (r.get("sourceRelationIds") or [])]
check("两条自定义关系都计入已裁（decidedCount=3）", result.get("decidedCount") == 3,
      result.get("decidedCount"))
check("自定义关系在合并结果里（sourceRelationIds 为空即自定义，页面据此区分）",
      len(customs) == 2, [r["expression"] for r in customs])
check("「修改方向」的自定义关系端点在统一命题集合上：S(P3,P2)",
      any(r["expression"] == "S(P3,P2)" for r in customs),
      [r["expression"] for r in customs])
check("「修改嵌套」引用了已保留的内层关系，嵌套不压平：S(J(P2,P3),P5)",
      any(r["expression"] == "S(J(P2,P3),P5)" for r in customs),
      [r["expression"] for r in customs])
check("自定义关系同样过关系校验器（无 ERROR 级 issue）",
      all(i["level"] != "ERROR" for i in result.get("issues") or []),
      [i for i in result.get("issues") or [] if i["level"] == "ERROR"])

print("== [6] 悬空端点：拦在界面，也拦在后端 ==")
status, body, unmapped_items = items_of(UNMAPPED_SESSION)
check("ses-3 也能打开（命题已冻结，只是有一个端点映射不上）", status == 200, status)
dangling = [c for i in unmapped_items for c in i["candidates"] if c.get("unmapped")]
check("含悬空端点的候选被标 unmapped（页面据此禁用「采用」按钮）", len(dangling) == 1, len(dangling))
check("悬空候选仍带可读表达式（界面不因映射不上而丢信息）",
      dangling[0].get("expression") and "P9" in dangling[0]["expression"],
      dangling[0].get("expression") if dangling else None)
check("对应的差异理由带人工核查提示",
      any("无法映射" in i["reason"] for i in unmapped_items),
      [i["reason"] for i in unmapped_items if "无法映射" in i["reason"]])

dangling_item = next(i for i in unmapped_items if any(c.get("unmapped") for c in i["candidates"]))
forced = [{"itemId": dangling_item["id"], "action": "ADOPT_VERSION",
           "sourceVersionId": next(c for c in dangling_item["candidates"] if c["unmapped"])["versionId"],
           "reason": "页面自查：强行采用悬空候选"}]
status, body = resolve(UNMAPPED_SESSION, forced)
result = data_of(body)
check("强行采用悬空候选 → 该决定不成立，进 reAdjudicationItemIds",
      result.get("reAdjudicationItemIds") == [dangling_item["id"]],
      result.get("reAdjudicationItemIds"))
check("报 INVALID_RELATION_MAPPING 且文案是「请先完成该命题的裁定」",
      any(i["code"] == "INVALID_RELATION_MAPPING" and "请先完成该命题的裁定" in i["message"]
          for i in result.get("issues") or []),
      [i for i in result.get("issues") or []])
check("会话进不了定稿（status=INVALID）", result.get("status") == "INVALID", result.get("status"))

print("== [7] 门禁：命题未冻结的会话 ==")
status, body, _ = items_of(GATED_SESSION)
check("J06 返回 409 PROPOSITIONS_NOT_COMPLETE（页面显示 gateHint）",
      status == 409 and (body.get("error") or {}).get("code") == "PROPOSITIONS_NOT_COMPLETE",
      [status, (body.get("error") or {}).get("code")])

print()
print(f"结果：{PASS} 通过 / {FAIL} 失败")
raise SystemExit(1 if FAIL else 0)
