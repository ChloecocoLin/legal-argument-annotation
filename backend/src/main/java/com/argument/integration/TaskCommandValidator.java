package com.argument.integration;
import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
/** Structural validation projected from the unchanged READY T request schemas. */
@Component
public class TaskCommandValidator {
 private final ObjectMapper mapper;private final JsonNode schemas;
 public TaskCommandValidator(ObjectMapper mapper)throws java.io.IOException{this.mapper=mapper;try(var input=new ClassPathResource("contracts/task-commands.json").getInputStream()){schemas=mapper.readTree(input);}}
 public void validate(String code,Object value){var n=mapper.valueToTree(value);var schema=schemas.path(code);if(schema.isMissingNode())throw new BusinessException(ErrorCode.VALIDATION_ERROR,"未知任务命令");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);var expected=new HashSet<String>();schema.path("required").forEach(v->expected.add(v.asText()));if(!n.isObject()||!actual.equals(expected))fail();for(String field:expected)check(n.path(field),schema.path("properties").path(field));}
 private void check(JsonNode n,JsonNode rule){boolean valid=switch(rule.path("type").asText()){case "string"->n.isTextual();case "integer"->n.isIntegralNumber()&&n.canConvertToInt();case "boolean"->n.isBoolean();case "array"->n.isArray();default->false;};if(!valid)fail();if(rule.has("enum")){boolean found=false;for(var v:rule.path("enum"))if(v.equals(n))found=true;if(!found)fail();}if("uuid".equals(rule.path("format").asText()))VersionJson.uuid(n.asText());if("date-time".equals(rule.path("format").asText())){try{if(!n.asText().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"))fail();java.time.Instant.parse(n.asText());}catch(java.time.DateTimeException e){fail();}}if(n.isArray())for(var child:n)check(child,rule.path("items"));}
 private void fail(){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"请求字段或类型不符合任务接口约定");}
}
