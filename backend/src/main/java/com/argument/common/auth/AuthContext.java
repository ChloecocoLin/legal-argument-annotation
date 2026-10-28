package com.argument.common.auth;
import com.argument.common.exception.*;
import com.argument.security.AppUserPrincipal;
import com.argument.integration.UnifiedVersionIdentity;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.stream.Collectors;
/** A's compatibility API reads C's authenticated principal; never accepts X-User-Id or a default user. */
public final class AuthContext {
 private AuthContext(){}
 public static DevUser current(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof AppUserPrincipal p))return null;return new DevUser(p.getUserId(),p.getUsername(),p.getDisplayName(),p.getRoles().stream().map(UnifiedVersionIdentity::domainRole).collect(Collectors.toSet()));}
 public static DevUser require(){var user=current();if(user==null)throw new BusinessException(ErrorCode.UNAUTHENTICATED,"请先登录");return user;}
 public static DevUser requireAnyRole(String... roles){var user=require();if(!user.hasAnyRole(roles))throw new BusinessException(ErrorCode.FORBIDDEN,"当前角色无权访问");return user;}
}
