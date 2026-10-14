#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""验证脚本的登录辅助（分工.md §六，成员 C）。

C 里的 M3 起启用了 Spring Security（`anyRequest().authenticated()`），原先那些直接打接口的
验证脚本会**全部收到 401**。与其在 8 个脚本里各抄一遍登录代码，不如放这一份。

各脚本这样用（放在 import 段之后）：

    import pathlib, sys
    _p = pathlib.Path(__file__).resolve().parent
    while not (_p / ".git").exists():
        _p = _p.parent
    sys.path.insert(0, str(_p / "scripts"))
    from verify_auth import login_token

    TOKEN = login_token()

再在发请求的地方加上请求头：

    req.add_header("Authorization", "Bearer " + TOKEN)

为什么不放在 backend/ 或 frontend/ 下：两个目录的脚本都要用，而且它们分属不同成员，
放在仓库根目录的 scripts/ 才不会被当成某一个人的私有工具。

**这里没有做任何「跳过鉴权」的开关**：脚本走的就是浏览器走的那条路（登录 → 拿令牌 → 带头上）。
如果登录本身坏了，脚本应该跟着红，而不是绕过去假装没事。
"""

import json
import os
import urllib.error
import urllib.request

DEFAULT_BASE = os.environ.get("ARG_BASE", "http://localhost:8080/api/v1")
# 演示账号（backend/src/main/resources/data.sql）。用 admin 是因为这些脚本要覆盖
# 各角色的接口（关系写、裁定、教学），而只有 admin 全都能过。
DEFAULT_USERNAME = os.environ.get("ARG_USER", "legal.researcher")
DEFAULT_PASSWORD = os.environ.get("ARG_PASSWORD", "demo")


def login_token(
    username=DEFAULT_USERNAME, password=DEFAULT_PASSWORD, base=DEFAULT_BASE
):
    """登录并返回令牌串。

    失败时**直接抛异常并让脚本以非 0 退出**，不返回空串：
    拿不到令牌的话后面每条断言都会以「401」的形式失败，打印一大片红字，
    真正的原因（后端没起 / 种子没进 / 口令不对）反而被埋在中间。
    """
    body = json.dumps({"username": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(base + "/auth/login", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as resp:
            envelope = json.load(resp)
    except urllib.error.HTTPError as exc:
        raise SystemExit(
            "登录失败（HTTP %s）：%s\n"
            "  · 后端起来了吗？  %s/actuator/health\n"
            "  · 用的是演示账号吗？%s / %s" % (exc.code, exc.read().decode("utf-8", "replace"),
                                            base, username, password)
        )
    except urllib.error.URLError as exc:
        raise SystemExit("连不上后端 %s：%s" % (base, exc))

    if not envelope.get("success"):
        raise SystemExit("登录被拒：%s" % json.dumps(envelope.get("error"), ensure_ascii=False))
    return envelope["data"]["token"]


def auth_headers(token, extra=None):
    """拼请求头。给用 `requests` 或手工 `urllib` 的脚本都能用。"""
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
    if extra:
        headers.update(extra)
    return headers
