package com.argument.membera.support;

import com.argument.common.auth.AuthContext;
import com.argument.common.exception.*;
import com.argument.integration.UnifiedVersionIdentity;
import com.argument.version.*;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A commands share D's parent locks, durable 24-hour receipts and one JDBC transaction. */
@Component
public class IdempotencySupport {
    private final com.argument.integration.WorkflowCommandHistory history;private final com.argument.integration.TaskCommandValidator validator;private final AnnotationDependencies data;private final UnifiedVersionIdentity identity;private final VersionRepository versions;private final ObjectMapper mapper;private final TransactionTemplate transaction;
    public IdempotencySupport(com.argument.integration.WorkflowCommandHistory history,com.argument.integration.TaskCommandValidator validator,AnnotationDependencies data,UnifiedVersionIdentity identity,VersionRepository versions,ObjectMapper mapper,PlatformTransactionManager manager){this.history=history;this.validator=validator;this.data=data;this.identity=identity;this.versions=versions;this.mapper=mapper;transaction=new TransactionTemplate(manager);transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);}
    public <T> T execute(String operation,String key,Object body,Supplier<T> action){
        VersionJson.uuid(key);
        return transaction.execute(status->{
            String actorId=AuthContext.require().userId();data.lock("user",actorId);var actor=identity.actor(actorId);
            String code=operation.split(":")[0];validator.validate(code,body);String permission=Set.of("T07","T08","T10","T11").contains(code)?"TASK_ASSIGN":"TASK_CREATE";
            if(!actor.role("TASK_CREATOR")||!actor.permission(permission))throw new BusinessException(ErrorCode.FORBIDDEN,"当前身份无任务管理权限");
            String taskId=null;var cases=new TreeSet<String>();
            if(operation.contains(":")){
                String id=VersionJson.uuid(operation.split(":")[1]);
                if(code.equals("T10")){var doc=data.read("taskDocument",id);taskId=doc.path("taskId").asText();cases.add(id);}
                else if(code.equals("T08")){String[] parts=operation.split(":");id=VersionJson.uuid(parts[1]);String memberId=VersionJson.uuid(parts[2]);if(!id.equals(data.read("member",memberId).path("taskId").asText()))throw new BusinessException(ErrorCode.FORBIDDEN,"成员不属于指定任务");taskId=id;}
                else taskId=id;
            }
            if(taskId!=null){data.find("taskDocument","taskId",taskId).forEach(d->cases.add(d.path("id").asText()));for(String id:cases)versions.lockCase(id);for(String id:cases)data.lock("taskDocument",id);var task=data.lock("task",taskId);if(!actorId.equals(task.path("ownerUserId").asText()))throw new BusinessException(ErrorCode.FORBIDDEN,"只能管理本人创建的任务");}
            var command=mapper.valueToTree(body);String hash=VersionJson.canonicalHash(command);var cached=versions.receipt(actorId,operation,key,hash);
            if(cached!=null)return mapper.convertValue(cached,new com.fasterxml.jackson.core.type.TypeReference<T>(){});
            T result=action.get();var node=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(result);history.append(actorId,operation,command,node);versions.receipt(actorId,operation,key,hash,node,mapper.createObjectNode(),node,"");return result;
        });
    }
}
