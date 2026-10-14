package com.argument.auth.dto;

import com.argument.security.AppUserPrincipal;
import java.util.List;

/**
 * 当前登录用户的对外形态。归成员 C（分工.md §六）。
 *
 * <p>**不带 token 字段**：它与 token 在登录响应里是并列的两件事，前端也只把 token 单独存
 * （见 {@code stores/user.ts}）。塞进用户对象会让「用户变了」和「令牌变了」混在一次赋值里。
 *
 * @param id 账号 ID（审计、界面头像都用它）
 * @param username 登录名
 * @param displayName 中文姓名
 * @param roles 角色代码：admin / annotator / arbiter / expert / student / teacher
 */
public record AuthUser(String id, String username, String displayName, List<String> roles) {

    public static AuthUser of(AppUserPrincipal principal) {
        return new AuthUser(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRoles());
    }
}
