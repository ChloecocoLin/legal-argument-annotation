package com.argument.version.local;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.AnnotationDependencies;
import com.argument.version.port.VersionIdentity;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !prod")
public class LocalVersionIdentity implements VersionIdentity {
    private final AnnotationDependencies dependencies;
    private static final Map<String,String> TOKENS=Map.of(
        "dev-admin-wang","bca3772c-2ec2-4001-9521-9f825c33a3f2",
        "dev-creator-li","9795b546-9838-4ef5-ab05-4ba73d88ea7e",
        "dev-annotator-chen","1068a77e-2a72-4f38-ae97-dcb88a45c7b3",
        "dev-annotator-liu","17e46666-aec6-4ed2-8e48-c044014f94a1",
        "dev-arbiter-zhao","9e76d32b-86e7-4d62-84e1-7f60e7c0b502",
        "dev-teacher-sun","2901c453-e8c4-4cfa-948c-7dedb4f2ab8a",
        "dev-student-zhou","52b8374c-4ad4-4aca-bed8-1b7bc6687577",
        "dev-expert-qian","4809fe74-569e-4906-a453-423c290b0525");
    public LocalVersionIdentity(AnnotationDependencies dependencies) { this.dependencies=dependencies; }
    public Actor authenticate(String authorization) {
        String userId=authorization!=null && authorization.startsWith("Bearer ") ? TOKENS.get(authorization.substring(7)) : null;
        if(userId==null) throw new BusinessException(ErrorCode.UNAUTHENTICATED,"请先登录");
        var user=dependencies.read("user",userId);
        if(!user.path("status").asText().equals("ACTIVE")) throw new BusinessException(ErrorCode.UNAUTHENTICATED,"账号已停用");
        var roles=new HashSet<String>(); var permissions=new HashSet<String>();
        for(var roleId:user.path("roleIds")) {
            var role=dependencies.read("role",roleId.asText());
            if(role.path("status").asText().equals("ACTIVE")) {
                roles.add(role.path("code").asText());
                role.path("permissionCodes").forEach(p->permissions.add(p.asText()));
            }
        }
        return new Actor(userId,user.path("displayName").asText(),Set.copyOf(roles),Set.copyOf(permissions));
    }
}
