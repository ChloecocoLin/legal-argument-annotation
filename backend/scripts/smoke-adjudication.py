#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""成员 C：关系差异与裁定的冒烟测试（J06 + 关系裁定结果试算）。

用法：
    python smoke-adjudication.py                      # 跑断言
    python smoke-adjudication.py --fixture out.json   # 顺手导出前端用的 mock fixture

前置：后端已启动（mvn -o spring-boot:run），data.sql 已种入演示会话 ses-1 / ses-2。
退出码 0 = 全绿，1 = 有断言失败。
"""

import argparse
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api/v1"
SESSION = "ses-1"
SESSION_GATED = "ses-2"          # 命题阶段未冻结，用于验证 PROPOSITIONS_NOT_COMPLETE
UNIFIED = "us-1"

PASSED = []
FAILED = []


def check(label, ok, detail=""):
    (PASSED if ok else FAILED).append(label)
    print("  %s %s%s" % ("PASS" if ok else "FAIL", label, ("  <- " + detail) if detail and not ok else ""))


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
    """返回 (http 状态, 解包后的 body)。信封由后端统一给出，这里不做二次包装。"""
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + TOKEN)
    payload = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, payload) as resp:
            return resp.status, json.load(resp)
    except urllib.error.HTTPError as exc:
        return exc.code, json.load(exc)


def items():
    status, body = call("GET", "/adjudication-sessions/%s/relation-items?size=50" % SESSION)
    assert status == 200, "J06 未返回 200：%s" % status
    return body["data"]["items"]


def item_id(rows, conflict_type, dimension):
    return next(r["id"] for r in rows if r["conflictType"] == conflict_type and r.get("dimension") == dimension)


def reason_id(rows, fragment):
    return next(r["id"] for r in rows if fragment in r.get("reason", ""))


def resolve_for(session, decisions):
    status, body = call("POST", "/adjudication-sessions/%s/relation-result" % session,
                        {"decisions": decisions})
    return status, body


def resolve(decisions):
    return resolve_for(SESSION, decisions)


# ── 1. J06：差异比较算法的分类结果 ──────────────────────────────────────

def test_j06_classification():
    print("\n[1] J06 差异分类（种子 ses-1 设计上覆盖 5 类 conflictType 与全部维度）")
    rows = items()
    check("总数为 8（7 条分歧 + 1 条一致）", len(rows) == 8, "实际 %d" % len(rows))

    got = sorted((r["conflictType"], r.get("dimension")) for r in rows)
    want = sorted([
        ("AGREEMENT", "一致项"),
        ("DIRECTION", "方向"),
        ("TYPE", "关系类型"),
        ("PARTICIPANTS", "组合成员"),
        ("PARTICIPANTS", "嵌套层级"),
        ("EXISTENCE", "一方遗漏"),
        ("EXISTENCE", "一方新增"),
        ("EXISTENCE", "一方新增"),
    ])
    check("八条的 (conflictType, 维度) 组合符合预期", got == want, "实际 %s" % got)

    pairs = sorted(tuple(c["expression"] for c in r["candidates"]) for r in rows if len(r["candidates"]) == 2)
    check("方向分歧配到了反向对 S(P2,P3) vs S(P3,P2)",
          ("S(P2,P3)", "S(P3,P2)") in pairs, "实际 %s" % pairs)
    check("组合成员分歧配到了 J(P2,P3) vs J(P2,P3,P4)（而非被类型分歧抢走）",
          ("J(P2,P3)", "J(P2,P3,P4)") in pairs, "实际 %s" % pairs)
    check("嵌套层级分歧两侧确实一深一浅",
          any("S(J(P2,P3),P5)" in c["expression"] for c in
              next(r for r in rows if r.get("dimension") == "嵌套层级")["candidates"]))
    check("一致项两侧表达式相同",
          all(len({c["expression"] for c in r["candidates"]}) == 1
              for r in rows if r["conflictType"] == "AGREEMENT"))
    check("所有候选都标注了版本标签 V1/V2",
          all(c["versionLabel"] in ("V1", "V2") for r in rows for c in r["candidates"]))
    check("无悬空端点（全部可映射到统一命题集合）",
          not any(c["unmapped"] for r in rows for c in r["candidates"]))


# ── 2. J06：门禁、分页与过滤 ────────────────────────────────────────────

def test_j06_gates():
    print("\n[2] J06 门禁 / 分页 / 过滤")

    status, body = call("GET", "/adjudication-sessions/%s/relation-items" % SESSION_GATED)
    check("命题阶段未冻结 → 409", status == 409, "实际 %s" % status)
    check("错误码为 PROPOSITIONS_NOT_COMPLETE",
          body.get("error", {}).get("code") == "PROPOSITIONS_NOT_COMPLETE",
          "实际 %s" % body.get("error", {}).get("code"))

    status, body = call("GET", "/adjudication-sessions/no-such-session/relation-items")
    check("会话不存在 → 404", status == 404, "实际 %s" % status)

    status, body = call("GET", "/adjudication-sessions/%s/relation-items?conflictType=EXISTENCE" % SESSION)
    check("按 conflictType=EXISTENCE 过滤出 3 条",
          status == 200 and body["meta"]["total"] == 3,
          "实际 %s" % body.get("meta", {}).get("total"))

    status, body = call("GET", "/adjudication-sessions/%s/relation-items?size=3" % SESSION)
    check("size=3 只返回 3 条且 total 仍为 8",
          len(body["data"]["items"]) == 3 and body["meta"]["total"] == 8,
          "items=%d total=%s" % (len(body["data"]["items"]), body["meta"].get("total")))

    status, body = call("GET", "/adjudication-sessions/%s/relation-items?status=OPEN" % SESSION)
    check("初始状态全部为 OPEN", body["meta"]["total"] == 8)


# ── 3. 关系裁定结果：正向闭环 ───────────────────────────────────────────

def full_decisions(rows):
    return [
        {"itemId": item_id(rows, "PARTICIPANTS", "组合成员"), "action": "ADOPT_VERSION",
         "sourceObjectId": "v2-j1", "reason": "V2 多列的 P4 有依据"},
        {"itemId": item_id(rows, "PARTICIPANTS", "嵌套层级"), "action": "ADOPT_VERSION",
         "sourceObjectId": "v1-s1", "reason": "整体作为支持来源更贴合原文"},
        {"itemId": item_id(rows, "DIRECTION", "方向"), "action": "ADOPT_VERSION",
         "sourceVersionId": "av-2", "reason": "P3 才是被支持项"},
        {"itemId": item_id(rows, "AGREEMENT", "一致项"), "action": "CONFIRM_AGREEMENT",
         "sourceObjectId": "v2-m1"},
        {"itemId": item_id(rows, "TYPE", "关系类型"), "action": "CUSTOM", "reason": "按 S 支持处理",
         "customRelation": {"relationCode": "S", "participants": [
             {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "us-p6"},
             {"ordinal": 2, "role": "TARGET", "targetKind": "ELEMENT", "targetId": "us-p8"}]}},
        {"itemId": reason_id(rows, "S(P1,P5)"), "action": "ADOPT_VERSION", "sourceObjectId": "v1-s3"},
        {"itemId": reason_id(rows, "S(P3,P5)"), "action": "DELETE",
         "reason": "两条规范并列，未形成直接支持关系。"},
        {"itemId": reason_id(rows, "A(P8,P7)"), "action": "ADOPT_VERSION", "sourceObjectId": "v2-a1",
         "reason": "V2 的攻击关系成立"},
    ]


def test_resolve_positive(rows):
    print("\n[3] 关系裁定结果：八项全部裁定 → COMPLETE + 正式论证图")
    status, body = resolve(full_decisions(rows))
    check("HTTP 200", status == 200, "实际 %s" % status)
    data = body["data"]
    check("status=COMPLETE", data["status"] == "COMPLETE", "实际 %s" % data["status"])
    check("8 项全部计入已裁", data["decidedCount"] == 8, "实际 %s" % data["decidedCount"])
    check("无待裁、无需重裁",
          not data["pendingItemIds"] and not data["reAdjudicationItemIds"])
    check("合并出 8 条关系", len(data["relations"]) == 8, "实际 %d" % len(data["relations"]))
    check("关系全部落在确定性交付物 ownerType=FINAL_VERSION 上",
          all(r["ownerType"] == "FINAL_VERSION" and r["ownerId"] == UNIFIED for r in data["relations"]))
    check("端点全部是统一命题集合里的命题（无源版本残留）",
          all(p["targetId"].startswith("us-") or p["targetKind"] == "RELATION"
              for r in data["relations"] for p in r["participants"]))
    check("嵌套不被压平：S(J(P2,P3),P5) 仍在，且其 SOURCE 指向一条关系节点",
          any("S(J(P2,P3),P5)" == r["expression"] and
              any(p["targetKind"] == "RELATION" for p in r["participants"])
              for r in data["relations"]))
    # 被采用的关系带溯源；自定义关系按设计没有来源关系（它的出处是 D 的裁定记录里那条 CUSTOM 决定）。
    custom_expression = "S(P6,P8)"
    check("被采用的关系都留下溯源 sourceRelationIds",
          all(r["sourceRelationIds"] for r in data["relations"]
              if r["expression"] != custom_expression))
    check("自定义关系不带来源关系（避免与「采用」混淆）",
          not next(r for r in data["relations"] if r["expression"] == custom_expression)["sourceRelationIds"])
    graph = data["graph"]
    check("论证图 id 形如 session:<会话>", graph["graphId"] == "session:" + SESSION)
    check("无 ERROR 级问题", not [i for i in data["issues"] if i["level"] == "ERROR"],
          str([i["code"] for i in data["issues"] if i["level"] == "ERROR"]))
    check("无孤立命题", not graph["isolatedNodeIds"], str(graph["isolatedNodeIds"]))
    check("识别出 2 个独立论证图", graph["componentCount"] == 2, "实际 %s" % graph["componentCount"])
    check("有向关系描出两段带箭头的边（SOURCE→关系节点→TARGET）",
          any(e["relationCode"] == "S" and e["arrow"] for e in graph["edges"]))
    check("无向关系（J）的边不带箭头",
          all(not e["arrow"] for e in graph["edges"] if e["relationCode"] == "J"))
    return data


# ── 4. 关系裁定结果：边界与非法输入 ─────────────────────────────────────

def test_resolve_edges(rows):
    print("\n[4] 关系裁定结果：边界与非法输入")

    # 4.1 未裁定
    status, body = resolve([])
    data = body["data"]
    check("未做任何裁定 → INCOMPLETE", data["status"] == "INCOMPLETE", "实际 %s" % data["status"])
    check("全部 8 项进待裁清单", len(data["pendingItemIds"]) == 8)
    check("不产出任何关系", not data["relations"])

    # 4.2 RETURN / ESCALATE_EXPERT 不消费裁定项
    status, body = resolve([
        {"itemId": item_id(rows, "TYPE", "关系类型"), "action": "RETURN", "reason": "退回重标"},
        {"itemId": reason_id(rows, "A(P8,P7)"), "action": "ESCALATE_EXPERT", "reason": "提交专家"}])
    data = body["data"]
    check("退回/提交专家 → 仍为 INCOMPLETE 且留在待裁",
          data["status"] == "INCOMPLETE" and data["decidedCount"] == 0
          and len(data["pendingItemIds"]) == 8)

    # 4.3 删除内层后，引用它的外层必须重新裁定
    status, body = resolve([
        {"itemId": item_id(rows, "PARTICIPANTS", "组合成员"), "action": "DELETE"},
        {"itemId": item_id(rows, "PARTICIPANTS", "嵌套层级"), "action": "ADOPT_VERSION",
         "sourceObjectId": "v1-s1"}])
    data = body["data"]
    check("删内层后外层决定不成立 → 进需重裁清单",
          data["reAdjudicationItemIds"] == [item_id(rows, "PARTICIPANTS", "嵌套层级")],
          str(data["reAdjudicationItemIds"]))
    check("抛出 NESTED_REFERENCE_EXISTS",
          any(i["code"] == "NESTED_REFERENCE_EXISTS" for i in data["issues"]))
    check("结果为 INVALID（有 ERROR 级问题）", data["status"] == "INVALID", "实际 %s" % data["status"])

    # 4.4 自定义关系的三种非法写法
    bad_customs = [
        ("端点在统一命题集合之外", {"relationCode": "S", "participants": [
            {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "us-p6"},
            {"ordinal": 2, "role": "TARGET", "targetKind": "ELEMENT", "targetId": "no-such-p"}]},
         "INVALID_RELATION_MAPPING"),
        ("S 缺 TARGET", {"relationCode": "S", "participants": [
            {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "us-p6"}]},
         "INVALID_PARTICIPANT"),
        ("关系类型未知", {"relationCode": "X", "participants": [
            {"ordinal": 1, "role": "SOURCE", "targetKind": "ELEMENT", "targetId": "us-p6"},
            {"ordinal": 2, "role": "TARGET", "targetKind": "ELEMENT", "targetId": "us-p8"}]},
         "INVALID_PARTICIPANT"),
        ("同一目标重复引用", {"relationCode": "I", "participants": [
            {"ordinal": 1, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "us-p6"},
            {"ordinal": 2, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "us-p6"}]},
         "INVALID_PARTICIPANT"),
    ]
    for label, custom, code in bad_customs:
        status, body = resolve([{"itemId": item_id(rows, "TYPE", "关系类型"),
                                 "action": "CUSTOM", "customRelation": custom}])
        data = body["data"]
        check("自定义关系「%s」被拒（%s）" % (label, code),
              data["status"] == "INVALID" and any(i["code"] == code for i in data["issues"]),
              str([i["code"] for i in data["issues"]]))

    # 4.5 未知动作
    status, body = resolve([{"itemId": reason_id(rows, "A(P8,P7)"), "action": "VIBE_CHECK"}])
    check("未知裁定动作 → 400 INVALID_RELATION_MAPPING",
          status == 400 and body["error"]["code"] == "INVALID_RELATION_MAPPING",
          "%s %s" % (status, body.get("error", {}).get("code")))

    # 4.6 采用不属于该项的候选：必须拒绝，不能退而取第一条
    status, body = resolve([{"itemId": reason_id(rows, "S(P3,P5)"), "action": "ADOPT_VERSION",
                             "sourceObjectId": "v2-a1"}])
    check("采用不在候选内的源关系 → 400（不静默改采第一条）",
          status == 400 and body["error"]["code"] == "INVALID_RELATION_MAPPING",
          "%s %s" % (status, body.get("error", {}).get("code")))

    # 4.7 指定了一个不存在的版本
    status, body = resolve([{"itemId": reason_id(rows, "S(P3,P5)"), "action": "ADOPT_VERSION",
                             "sourceVersionId": "av-99"}])
    check("指定了没有候选的版本 → 400",
          status == 400 and body["error"]["code"] == "INVALID_RELATION_MAPPING",
          "%s" % status)


# ── 5. 悬空端点（ses-3：av-4 的 P9 在统一命题集合里不存在） ──────────────

def test_unmapped_endpoint():
    print("\n[5] 悬空端点：端点在统一命题集合里不存在时")

    status, body = call("GET", "/adjudication-sessions/ses-3/relation-items?size=50")
    check("J06 返回 200", status == 200, "实际 %s" % status)
    rows = body["data"]["items"]
    check("两版只有一条可比关系 → 2 条裁定项（1 一致 + 1 新增）", len(rows) == 2,
          "实际 %d" % len(rows))

    unmappable = next(r for r in rows if any(c["unmapped"] for c in r["candidates"]))
    check("含悬空端点的候选被标 unmapped", any(c["unmapped"] for c in unmappable["candidates"]))
    check("该候选仍返回可读表达式（P9 用展示编号，不因映射不上而丢失）",
          all(c["expression"] for c in unmappable["candidates"]))
    check("差异理由带人工核查提示",
          "无法映射到统一命题集合的端点 v4-p9" in unmappable["reason"],
          unmappable["reason"])
    check("判为存在性差异而非「参与命题分歧」（未映射端点不得参与重合度计算）",
          unmappable["conflictType"] == "EXISTENCE"
          and unmappable["dimension"] == "一方新增",
          "%s/%s" % (unmappable["conflictType"], unmappable["dimension"]))
    check("可比的那条仍判为一致项",
          any(r["conflictType"] == "AGREEMENT" for r in rows))

    # 采用这条含悬空端点的关系 → 决定不成立
    status, body = resolve_for("ses-3", [
        {"itemId": unmappable["id"], "action": "ADOPT_VERSION",
         "sourceObjectId": unmappable["candidateRelationIds"][0]}])
    check("采用含悬空端点的关系 → 决定不成立，进需重裁清单",
          status == 200 and body["data"]["reAdjudicationItemIds"] == [unmappable["id"]],
          str(body["data"].get("reAdjudicationItemIds")))
    check("报 INVALID_RELATION_MAPPING（提示先完成该命题的裁定）",
          any(i["code"] == "INVALID_RELATION_MAPPING" and "先完成该命题的裁定" in i["message"]
              for i in body["data"]["issues"]),
          str([i["code"] for i in body["data"]["issues"] if i["level"] == "ERROR"]))
    check("会话无法定稿（INVALID）", body["data"]["status"] == "INVALID")


# ── fixture 导出 ────────────────────────────────────────────────────────

def dump_fixture(path, rows, result):
    """导出前端并行开发用的 mock fixture：裁定项 + 统一命题 + 一次完整裁定的结果。"""
    _, unified = call("GET", "/annotation-drafts/%s/mock-propositions" % UNIFIED)
    fixture = {
        "_note": "成员 C 关系裁定页的 mock fixture。由 backend/scripts/smoke-adjudication.py --fixture 生成，"
                 "非契约数据；B 的命题裁定（J03/J04）与 D 的裁定记录（J05/J07/J08）就绪后作废。",
        "session": {"id": SESSION, "status": "RELATION_REVIEW", "sourceVersionIds": ["av-1", "av-2"],
                    "unifiedSnapshotId": UNIFIED, "propositionCompletedAt": "2026-08-16T10:00:00"},
        "sourceVersions": [{"id": "av-1", "label": "V1"}, {"id": "av-2", "label": "V2"}],
        "unifiedPropositions": unified["data"]["items"],
        "items": rows,
        "sampleDecisions": full_decisions(rows),
        "sampleResult": result,
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(fixture, handle, ensure_ascii=False, indent=2)
    print("\nfixture 已写入 %s" % path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", help="顺手导出前端 mock fixture 的路径")
    args = parser.parse_args()

    print("关系裁定冒烟测试 → %s" % BASE)
    rows = items()
    test_j06_classification()
    test_j06_gates()
    result = test_resolve_positive(rows)
    test_resolve_edges(rows)
    test_unmapped_endpoint()

    if args.fixture:
        dump_fixture(args.fixture, rows, result)

    print("\n结果：%d 项通过，%d 项失败" % (len(PASSED), len(FAILED)))
    for label in FAILED:
        print("  FAILED: %s" % label)
    return 1 if FAILED else 0


if __name__ == "__main__":
    sys.exit(main())
