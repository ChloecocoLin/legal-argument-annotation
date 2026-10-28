package com.argument.teachingtrial;
import com.argument.common.exception.*;
import com.argument.integration.UnifiedVersionIdentity;
import com.argument.version.*;
import com.argument.version.port.*;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;
@Component
public class TeachingAccess {
 public final AnnotationDependencies data;public final TeachingRepository store;public final VersionRepository versions;private final UnifiedVersionIdentity identity;
 public TeachingAccess(AnnotationDependencies data,TeachingRepository store,VersionRepository versions,UnifiedVersionIdentity identity){this.data=data;this.store=store;this.versions=versions;this.identity=identity;}
 public Actor actor(Actor supplied,String role,String permission){var fresh=identity.actor(supplied.id());require(fresh.role(role)&&fresh.permission(permission),ErrorCode.FORBIDDEN,"当前角色没有本教学操作权限");return fresh;}
 public Actor fresh(Actor supplied){return identity.actor(supplied.id());}
 public void lock(Actor actor){data.lock("user",actor.id());}
 public ObjectNode course(Actor actor,String id,String permission){actor(actor,"TEACHER",permission);var c=data.read("teachingCourse",VersionJson.uuid(id));require(actor.id().equals(c.path("teacherUserId").asText()),ErrorCode.FORBIDDEN,"只能管理本人课程");return c;}
 public ObjectNode teacherAssignment(Actor actor,String id,String permission){var a=data.read("teachingAssignment",VersionJson.uuid(id));course(actor,a.path("courseId").asText(),permission);return a;}
 public ObjectNode studentAssignment(Actor actor,String id){actor(actor,"STUDENT","STUDENT_ANSWER_WORK");var a=data.read("teachingAssignment",VersionJson.uuid(id));var c=data.read("teachingCourse",a.path("courseId").asText());var cls=data.read("teachingClass",a.path("classId").asText());require("ACTIVE".equals(c.path("status").asText())&&"ACTIVE".equals(cls.path("status").asText())&&"PUBLISHED".equals(a.path("status").asText())&&data.find("teachingMembership","classId",a.path("classId").asText()).stream().anyMatch(m->actor.id().equals(m.path("studentUserId").asText())&&"ACTIVE".equals(m.path("status").asText())),ErrorCode.FORBIDDEN,"未加入此作业所属有效班级或作业尚未发布");return a;}
 public ObjectNode lockAssignment(Actor actor,String id,boolean teacher,String permission){lock(actor);var a=teacher?teacherAssignment(actor,id,permission):studentAssignment(actor,id);data.lock("teachingCourse",a.path("courseId").asText());data.lock("teachingClass",a.path("classId").asText());a=data.lock("teachingAssignment",id);if(teacher)teacherAssignment(actor,id,permission);else studentAssignment(actor,id);return a;}
 public ObjectNode receipt(Actor actor,String op,String key,ObjectNode body){VersionJson.uuid(key);return versions.receipt(actor.id(),"TEACHING:"+op,key,VersionJson.canonicalHash(body));}
 public void finish(Actor actor,String op,String key,ObjectNode body,ObjectNode result){store.event(actor.id(),op,result.path("id").asText(),VersionJson.canonicalHash(result));versions.receipt(actor.id(),"TEACHING:"+op,key,VersionJson.canonicalHash(body),result,result.objectNode(),result,"");}
 public static void fields(ObjectNode body,Set<String> required,Set<String> optional){require(body!=null,ErrorCode.VALIDATION_ERROR,"请求不能为空");var actual=new HashSet<String>();body.fieldNames().forEachRemaining(actual::add);var allowed=new HashSet<>(required);allowed.addAll(optional);require(actual.containsAll(required)&&allowed.containsAll(actual),ErrorCode.VALIDATION_ERROR,"教学请求字段不符合试行约定");}
 public static String text(ObjectNode body,String field,int max){require(body.path(field).isTextual()&&!body.path(field).asText().isBlank()&&body.path(field).asText().length()<=max,ErrorCode.VALIDATION_ERROR,field+"不能为空且不得超过 "+max+" 字符");return body.path(field).asText().trim();}
 public static Instant date(ObjectNode body,String field){String value=text(body,field,30);require(value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"),ErrorCode.VALIDATION_ERROR,"时间必须为 UTC 秒精度格式");try{return Instant.parse(value);}catch(java.time.DateTimeException e){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"无效时间");}}
 public static void match(ObjectNode n,int version){require(n.path("recordVersion").asInt()==version,ErrorCode.VERSION_CONFLICT,"教学资源已变化，请刷新");}
 public static void touch(ObjectNode n,Actor actor){n.put("recordVersion",n.path("recordVersion").asInt()+1).put("updatedById",actor.id()).put("updatedAt",VersionJson.now());}
}
