package com.argument.integration;

import com.argument.common.exception.*;
import com.argument.security.*;
import com.argument.version.local.LocalVersionIdentity;
import com.argument.version.port.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** C issues opaque sessions; current workflow identities/permissions are re-read for every request. */
@Component @Primary
public class UnifiedVersionIdentity implements VersionIdentity {
    private final TokenStore tokens;private final AnnotationDependencies data;private final ObjectProvider<LocalVersionIdentity> development;
    public UnifiedVersionIdentity(TokenStore tokens,AnnotationDependencies data,ObjectProvider<LocalVersionIdentity> development){this.tokens=tokens;this.data=data;this.development=development;}
    public Actor actor(String id){
        var user=data.read("user",id);if(!"ACTIVE".equals(user.path("status").asText()))throw new BusinessException(ErrorCode.UNAUTHENTICATED,"账号已停用");
        var roles=new HashSet<String>();var permissions=new HashSet<String>();
        for(var idNode:user.path("roleIds")){var role=data.read("role",idNode.asText());if("ACTIVE".equals(role.path("status").asText())){roles.add(role.path("code").asText());role.path("permissionCodes").forEach(p->permissions.add(p.asText()));}}
        if(roles.isEmpty())throw new BusinessException(ErrorCode.FORBIDDEN,"账号未分配有效角色");return new Actor(id,user.path("displayName").asText(),Set.copyOf(roles),Set.copyOf(permissions));
    }
    public Actor authenticate(String header){
        if(header!=null&&header.startsWith("Bearer ")){
            var principal=tokens.resolve(header.substring(7)).orElse(null);
            if(principal!=null)return actor(principal.getUserId());
            var local=development.getIfAvailable();if(local!=null&&header.startsWith("Bearer dev-"))return local.authenticate(header);
        }
        throw new BusinessException(ErrorCode.UNAUTHENTICATED,"请先登录");
    }
    public static String uiRole(String role){return "TASK_CREATOR".equals(role)?"creator":role.toLowerCase(Locale.ROOT);}
    public static String domainRole(String role){return "creator".equals(role)?"TASK_CREATOR":role.toUpperCase(Locale.ROOT);}
}
