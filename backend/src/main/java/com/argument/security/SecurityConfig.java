package com.argument.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 鉴权配置。归成员 C（公共基础工作：登录 / Spring Security / RBAC，分工.md §六）。
 *
 * <p>三条决定，都是「换个写法也能跑，但后果不一样」的地方：
 *
 * <ol>
 *   <li><b>无状态 + Authorization 头</b>：令牌走请求头，不用 Cookie 会话，因此
 *       {@code csrf} 关闭（CSRF 针对的是浏览器自动携带的凭据，请求头里的令牌不会被自动带上）。
 *       这不等于「不需要管 CSRF」——如果将来改成 Cookie 会话，这里必须同时改回去。</li>
 *   <li><b>默认全站要登录</b>（{@code anyRequest().authenticated()}），只白名单三条：
 *       登录接口、健康检查、接口文档。白名单越短越好：漏掉一条大家的页面就打不开，
 *       多放一条就是永久的口子，两者都不好，所以要显式列举而不是按前缀一把放开。</li>
 *   <li><b>角色判定不写在这里</b>：本类只负责「有没有登录」，具体「谁能做什么」用
 *       {@code @PreAuthorize} 写在各自控制器上（{@link EnableMethodSecurity} 打开）。
 *       把角色矩阵堆在配置里，接口一多就没人能说清某个 URL 到底归谁。</li>
 * </ol>
 *
 * <p><b>一个已知的先后次序</b>：{@code @Valid @RequestBody} 的解析与校验发生在方法调用**之前**，
 * 而 {@code @PreAuthorize} 是在方法调用时生效的。所以一个角色不足的人提交**格式不合法**的请求体时，
 * 会先拿到 {@code 400 VALIDATION_ERROR}，而不是 {@code 403 FORBIDDEN}——他据此能看出「这个体合不合法」。
 * 没有越权（两种情况都没进业务代码），但如果将来要按接口隐藏存在性，就得把它挪到 URL 级
 * {@code authorizeHttpRequests} 里。自查脚本里的请求体刻意都是合法格式，就是为了让 403 不被 400 盖住。
 * （另：缺权限的 403 由 {@code GlobalExceptionHandler} 里的 {@code AccessDeniedException} 分支给出，
 * 不走下面的 JSON 处理器——原因见那个方法的注释。）
 *
 * <p>RBAC 矩阵（C 的三个模块，落在控制器上）：
 *
 * <pre>
 *   关系实例写（R02–R04）                      admin、annotator
 *   论证图写（GR02 审查 / GR03 布局）            admin、annotator、arbiter（图示审查是裁定员的活）
 *   关系裁定（J06、裁定结果）                   admin、arbiter
 *   教学练习（题库与评分）                      admin、student、teacher
 *   读接口（R01、GR01、指南、示例数据）          已登录即可
 * </pre>
 *
 * <p>其他成员模块（B 的指南管理、D 的任务与导出）本轮统一是「登录即可访问」——
 * 它们的角色矩阵应由各自的负责人补 {@code @PreAuthorize}，C 不代填。
 *
 * <p><b>没有「免登录开关」</b>：曾经想过加一个 {@code open} profile 让 A/D 点检时不用登录，
 * 但 {@code @PreAuthorize} 是方法级的，免掉 URL 鉴权也免不掉它，结果会变成「能读不能写」的半开状态——
 * 比没有更让人困惑。演示统一用种子账号登录（登录页上直接列了账号）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenStore tokenStore;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    /**
     * 口令编码器。用**带前缀**的委托编码器：{@code {noop}demo} 与 {@code {bcrypt}$2a$…} 都能校验，
     * 因此种子里的占位口令不必改代码就能用，改造成 bcrypt 也只是把种子前缀换掉。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 一个**永远不会成功**的 {@link UserDetailsService}。
     *
     * <p>存在的唯一理由是「让 Spring Boot 不要自己造一个」：容器里没有 UserDetailsService 时，
     * {@code UserDetailsServiceAutoConfiguration} 会塞一个内存用户 {@code user} + 随机口令，
     * 并在启动日志里打印出来。本项目的用户来自 {@code app_user} 表、认证走 {@link TokenStore}，
     * 那个内置用户既用不上、又往日志里丢一行像样的「密码」——留着只会让人以为系统有个后门账号。
     *
     * <p>所以这里显式声明一个拒绝一切的服务：认证完全由 {@code /auth/login} + 令牌过滤器完成，
     * 这条路径上不会有人来问它。
     */
    @Bean
    public UserDetailsService unusedUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("本系统不使用 UserDetailsService，请走 /api/v1/auth/login");
        };
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {        // 手工 new 而不是把过滤器声明成 @Component：Spring Boot 会把容器里的每个 Filter bean
        // 再注册到 servlet 容器一次，于是它会在 Spring Security 链路之外**再跑一遍**。
        // 这里只需要它在安全链路里工作，所以不进容器。
        TokenAuthenticationFilter tokenFilter = new TokenAuthenticationFilter(tokenStore);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // 登出允许匿名：令牌已过期时也要能走完「清掉本地登录态」这一步
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/doc.html",
                                "/webjars/**",
                                "/favicon.ico")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
