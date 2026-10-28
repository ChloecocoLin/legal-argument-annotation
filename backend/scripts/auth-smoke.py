#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""成员 C：登录 / Spring Security / RBAC 冒烟测试（分工.md §六）。

用法：
    python backend/scripts/auth-smoke.py [base_url]

前置：后端已启动。本脚本**不写业务数据**（唯一的写操作是登录/登出，改的是进程内的 TokenStore），
因此可以和任何其他脚本共用同一个后端进程，不需要「干净种子」守卫。

它验的不是「登录功能能用」，而是这几件容易出错的事：
  1. 该拦的拦住了没有 —— 没令牌访问受保护接口必须 401（而不是 200，也不该是 500）；
  2. 该放的行不行   —— 白名单（登录/健康检查/接口文档）与「登录即可读」的接口；
  3. 角色矩阵对不对 —— 5 个账号 × 代表接口，逐个断言 200 / 403；
  4. 越权与信息泄露 —— 学生不能写关系；口令错与账号不存在的**提示必须一样**（否则能枚举账号）；
  5. 令牌的边界     —— 乱令牌无效、登出后立刻失效、重复登出不算错误、同一账号两次登录各自有效。

退出码 0 = 全绿，1 = 有断言失败。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
PASS = 0
FAIL = 0

DEMO_PASSWORD = "demo"
# 与 data.sql 的 app_user 种子一致
ACCOUNTS = {
    "admin": ("legal.researcher", "王慧"),
    "annotator": ("ann.li", "李铭"),
    "arbiter": ("arb.zhang", "张宁"),
    "student": ("stu.chen", "陈默"),
    "teacher": ("tea.wu", "吴敏"),
}


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print("  PASS %s" % name)
    else:
        FAIL += 1
        print("  FAIL %s%s" % (name, ("  <- " + str(detail)) if detail else ""))


def raw(method, path, body=None, token=None, headers=None):
    """发请求，返回 (状态码, 解包后的 body)。**不做断言**，也不自动登录。"""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        try:
            return exc.code, json.loads(exc.read().decode())
        except ValueError:
            return exc.code, {"raw": exc.read().decode("utf-8", "replace")}


def error_code(body):
    return ((body or {}).get("error") or {}).get("code")


def message_of(body):
    return ((body or {}).get("error") or {}).get("message") or ""


def root_status(path):
    """探一个**不带 /api/v1 前缀**的路径（健康检查、接口文档挂在根上）。
    这些路径在白名单里，所以用裸 urllib 而不是 raw()——raw() 会拼上 BASE 前缀。"""
    req = urllib.request.Request(BASE.replace("/api/v1", "") + path)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status
    except urllib.error.HTTPError as exc:
        return exc.code
    except urllib.error.URLError as exc:
        raise SystemExit("连不上后端 %s：%s" % (BASE, exc))


def login(username, password=DEMO_PASSWORD):
    """返回 (状态码, body)。故意不抛异常：本脚本要断言登录**失败**的样子。"""
    return raw("POST", "/auth/login", {"username": username, "password": password})


def token_of(username, password=DEMO_PASSWORD):
    status, body = login(username, password)
    assert status == 200, "前置失败：%s 登录返回 %s %s" % (username, status, body)
    return body["data"]["token"]


# 代表接口：每个角色对这些路径的期望结果
PROTECTED_READ = "/annotation-drafts/md-1/relations"             # R01 读，登录即可
RELATION_ITEMS = "/adjudication-sessions/ses-1/relation-items"   # admin / arbiter
RELATION_WRITE = "POST /annotation-drafts/md-1/relations"        # admin / annotator
GRAPH_VALIDATE = "POST /annotation-drafts/md-1/graph/validate"   # admin / annotator / arbiter
TEACHING = "/teaching/exercises"                                 # admin / student / teacher

# ⚠️ 下面这两个请求体必须**格式合法**，这不是小事：
# `@Valid @RequestBody` 的参数解析与校验发生在方法调用**之前**，而 @PreAuthorize 的方法是
# 在方法调用时生效的。所以给一个不合法的体，未授权的人会先拿到 400 VALIDATION_ERROR，
# 根本轮不到 403——「403 没生效」这类问题会被 400 盖住，看不出真相。
# （400 而不是 403 只是提示不精确，不构成越权：两种情况都没进业务代码。）
VALID_RELATION_BODY = {
    "relationCode": "J",
    "participants": [
        {"ordinal": 1, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "p2"},
        {"ordinal": 2, "role": "MEMBER", "targetKind": "ELEMENT", "targetId": "p3"},
    ],
}

VALID_GRAPH_BODY = {"elementIds": ["p2", "p3"], "relationIds": []}


def send(spec, token):
    """把 "POST /path" 形式转成一次真实请求（读用裸路径）。"""
    if spec.startswith("POST "):
        path = spec[5:]
        body = VALID_RELATION_BODY if path.endswith("/relations") else VALID_GRAPH_BODY
        return raw("POST", path, body, token=token)
    return raw("GET", spec, token=token)


def main():
    print("[1] 白名单：不登录也能访问的接口")
    status, body = raw("GET", "/auth/me")
    check("无令牌访问 /auth/me 是 401", status == 401, "%s %s" % (status, body))
    check("401 的信封错误码是 UNAUTHENTICATED", error_code(body) == "UNAUTHENTICATED", body)
    check("401 的信封 data 为 null", body.get("data") is None, body)
    check("401 的信封带 requestId", bool(body.get("requestId")), body)

    status, body = login(ACCOUNTS["admin"][0])
    check("登录接口本身不需要令牌", status == 200, "%s %s" % (status, body))

    check("白名单：健康检查免登录", root_status("/actuator/health") == 200,
          root_status("/actuator/health"))

    # 接口文档（knife4j / springdoc）。挂在根路径上，不在 /api/v1 之下——
    # 一开始把这里写成了 raw("GET", "/v3/api-docs")，实际打到 /api/v1/v3/api-docs 上，
    # 于是「白名单没生效」的假象。白名单里的路径和探的路径必须是同一串。
    check("白名单：/v3/api-docs 免登录", root_status("/v3/api-docs") == 200,
          root_status("/v3/api-docs"))
    check("白名单：/doc.html 免登录", root_status("/doc.html") == 200, root_status("/doc.html"))
    # 反向确认：带 /api/v1 前缀的同名路径**不是**白名单项，该拦。
    # 说明白名单是逐条列举而不是按后缀匹配，将来加白名单时也不会误放。
    check("白名单是精确列举：/api/v1/v3/api-docs 仍要登录",
          raw("GET", "/v3/api-docs")[0] == 401, raw("GET", "/v3/api-docs")[0])

    print("\n[2] 登录成功的样子")
    status, body = login(ACCOUNTS["admin"][0])
    data = body.get("data") or {}
    check("登录返回 200", status == 200, status)
    check("返回 success=true", body.get("success") is True, body)
    check("带有令牌", bool(data.get("token")), data)
    check("令牌是 URL 安全的不透明串（无点号，不是 JWT）",
          data.get("token") and "." not in data["token"], data.get("token"))
    check("有效期是 8 小时（28800 秒）", data.get("expiresInSeconds") == 28800,
          data.get("expiresInSeconds"))
    user = data.get("user") or {}
    check("用户 id 是种子里的 u-admin", user.get("id") == "u-admin", user)
    check("用户姓名是种子里的王慧", user.get("displayName") == "王慧", user)
    check("角色是 [admin]", user.get("roles") == ["admin"], user)
    check("**响应里没有任何口令字段**",
          not any("password" in k.lower() or "hash" in k.lower() for k in user), user)
    check("**登录响应里没有回显提交的口令**", DEMO_PASSWORD not in json.dumps(body), body)

    token = data["token"]
    print("\n[3] 带令牌访问")
    status, body = raw("GET", "/auth/me", token=token)
    check("/auth/me 带令牌返回 200", status == 200, "%s %s" % (status, body))
    check("/auth/me 返回的是同一个账号", (body.get("data") or {}).get("id") == "u-admin", body)

    status, body = raw("GET", "/auth/me", token="garbage-token-should-not-work")
    check("伪造令牌被拒（401）", status == 401, "%s %s" % (status, body))

    # 不带 Bearer 前缀
    status, body = raw("GET", "/auth/me", headers={"Authorization": token})
    check("Authorization 头缺 Bearer 前缀时被拒（401）", status == 401, "%s %s" % (status, body))

    print("\n[4] 登录失败的口径")
    _, wrong_pwd = login(ACCOUNTS["admin"][0], "wrong-password")
    status, unknown = login("no.such.user")
    check("口令错 → 401", error_code(wrong_pwd) == "UNAUTHENTICATED", wrong_pwd)
    check("账号不存在 → 401", status == 401, "%s %s" % (status, unknown))
    # 这两条必须一样，否则可以逐个试账号名，判断哪个存在（用户枚举）
    check("**口令错与账号不存在的提示完全相同**（防用户枚举）",
          message_of(wrong_pwd) == message_of(unknown),
          "%r vs %r" % (message_of(wrong_pwd), message_of(unknown)))
    check("提示是中文且不透露哪个字段错了",
          "不正确" in message_of(unknown) and "不存在" not in message_of(unknown),
          message_of(unknown))

    status, body = login("")
    check("空登录名 → 400 VALIDATION_ERROR", status == 400 and error_code(body) == "VALIDATION_ERROR",
          "%s %s" % (status, body))
    details = (body.get("error") or {}).get("details") or []
    fields = sorted(d.get("field") for d in details)
    check("校验详情点出 username（口令字段是这个用例里唯一合法的，不该被报）",
          fields == ["username"], details)
    check("校验详情是中文且带字段名", (details[0].get("rule") or "") != "", details)

    # 两个字段都不给，才应该同时报出来
    status, body = raw("POST", "/auth/login", {})
    details = (body.get("error") or {}).get("details") or []
    fields = sorted(d.get("field") for d in details)
    check("两个字段都为空时同时报 username 与 password",
          status == 400 and fields == ["password", "username"], "%s %s" % (status, details))

    print("\n[5] RBAC 矩阵：5 个账号 × 代表接口")
    tokens = {}
    for role, (username, display_name) in ACCOUNTS.items():
        status, body = login(username)
        check("%s（%s）能登录" % (display_name, role), status == 200, "%s %s" % (status, body))
        tokens[role] = (body.get("data") or {}).get("token")
        roles = ((body.get("data") or {}).get("user") or {}).get("roles")
        check("  角色由服务端给出且是 [%s]" % role, roles == [role], roles)

    # 期望：True = 允许。这张表就是 SecurityConfig javadoc 里那份矩阵的可执行版本，
    # 改了一边忘了另一边时，这里会红。
    matrix = {
        PROTECTED_READ: {"admin": True, "annotator": True, "arbiter": True,
                         "student": True, "teacher": True},
        RELATION_ITEMS: {"admin": True, "arbiter": True,
                         "annotator": False, "student": False, "teacher": False},
        RELATION_WRITE: {"admin": True, "annotator": True,
                         "arbiter": False, "student": False, "teacher": False},
        GRAPH_VALIDATE: {"admin": True, "annotator": True, "arbiter": True,
                         "student": False, "teacher": False},
        # 教学练习是**整组**限 admin/student/teacher，连题库读也不给标注员/裁定员：
        # 学生题库和学生作答对标注员没有用途，放开只会多一个要维护的口子。
        TEACHING: {"admin": True, "student": True, "teacher": True,
                   "annotator": False, "arbiter": False},
    }
    for endpoint, expectations in matrix.items():
        for role, allowed in expectations.items():
            status, body = send(endpoint, tokens[role])
            if allowed:
                # 允许的场合：**不能是 401/403**。业务上可能是 400/404/409（比如 md-1 被前一个
                # 脚本写脏了），那属于别的脚本的事，不该在这里判失败。
                check("%s 允许 %s（%s）" % (endpoint, role, "写" if endpoint.startswith("POST") else "读"),
                      status not in (401, 403), "%s %s" % (status, body))
            else:
                check("%s 拒绝 %s" % (endpoint, role),
                      status == 403 and error_code(body) == "FORBIDDEN",
                      "%s %s" % (status, body))

    print("\n[6] 令牌的边界")
    t1 = token_of(ACCOUNTS["admin"][0])
    t2 = token_of(ACCOUNTS["admin"][0])
    check("同一账号两次登录得到不同令牌（多会话并存）", t1 != t2, "两次都是同一个令牌")
    check("第一个令牌仍然有效（登录新会话不会踢掉旧的）",
          raw("GET", "/auth/me", token=t1)[0] == 200, "旧令牌被顶掉了")

    status, body = raw("POST", "/auth/logout", token=t2)
    check("登出返回 200", status == 200, "%s %s" % (status, body))
    check("登出报告已作废 revoked=true", (body.get("data") or {}).get("revoked") is True, body)

    status, body = raw("GET", "/auth/me", token=t2)
    check("**登出后该令牌立刻失效（401）**", status == 401, "%s %s" % (status, body))
    check("登出只影响自己的令牌，另一个会话不受影响",
          raw("GET", "/auth/me", token=t1)[0] == 200, "登出把别人的会话也踢了")

    status, body = raw("POST", "/auth/logout", token=t2)
    check("重复登出不是错误，只是 revoked=false",
          status == 200 and (body.get("data") or {}).get("revoked") is False,
          "%s %s" % (status, body))

    status, body = raw("POST", "/auth/logout")
    check("无令牌登出也返回 200（登出永远能走完，不把人卡在登录态）",
          status == 200, "%s %s" % (status, body))

    print("\n[7] 越权不是 500")
    # 403 曾经被 GlobalExceptionHandler 的兜底 Exception 分支吃成 500 INTERNAL_ERROR，
    # 前端只显示「服务器错误」。这条断言就是钉住那个 bug 不再回来。
    status, body = send(RELATION_ITEMS, tokens["student"])
    check("角色不足返回 403 而不是 500", status == 403, "%s %s" % (status, body))
    check("403 的信封错误码是 FORBIDDEN", error_code(body) == "FORBIDDEN", body)
    check("403 的信封里没有堆栈/异常类名",
          "Exception" not in json.dumps(body) and "at com." not in json.dumps(body), body)

    print("\n" + "=" * 66)
    print("PASS %d / FAIL %d" % (PASS, FAIL))
    if FAIL:
        print("有断言失败，见上面的 FAIL 行。")
    return 1 if FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
