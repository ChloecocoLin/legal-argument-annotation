package com.argument.security.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 登录账号。归成员 C（公共基础工作：登录 / Spring Security / RBAC，分工.md §六）。
 *
 * <p>表在 S0 就建好了，本轮才启用。角色走 {@code app_user_role} 关联表，不放在本表上——
 * 一个账号可以同时是标注员与裁定员，落到一列上迟早要改。
 *
 * <p>{@code passwordHash} 用 Spring Security 的 {@code {id}hash} 前缀格式（种子是 {@code {noop}demo}），
 * 由 {@code DelegatingPasswordEncoder} 识别；换成 bcrypt 只需把种子前缀改成 {@code {bcrypt}}，
 * 不必改任何代码——这正是用带前缀编码器的原因。
 *
 * <p>{@code status} 为 {@code ACTIVE} 才允许登录。锁定/停用不做额外状态机（本轮范围之外）。
 */
@Data
@TableName("app_user")
public class AppUser {

    @TableId
    private String id;

    private String username;

    private String passwordHash;

    private String displayName;

    /** ACTIVE / DISABLED。只有 ACTIVE 能登录。 */
    private String status;

    private Integer recordVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
