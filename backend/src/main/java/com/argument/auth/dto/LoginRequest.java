package com.argument.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。归成员 C（分工.md §六）。
 *
 * @param username 登录名
 * @param password 明文口令；服务端用带前缀的委托编码器校验，不落库、不记日志
 */
public record LoginRequest(
        @NotBlank(message = "请输入登录名") String username,
        @NotBlank(message = "请输入口令") @Size(max = 100, message = "口令过长") String password) {}
