package com.argument.draft;

import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Structural validation projected from frozen READY schemas; business checks remain in DraftService. */
@Component
public class StructureCommandValidator {
    private final JsonNode schemas;
    public StructureCommandValidator(ObjectMapper mapper)throws java.io.IOException{try(var input=new ClassPathResource("contracts/structure-commands.json").getInputStream()){schemas=mapper.readTree(input);}}
    public void validate(String operation,JsonNode body){check(body,schemas.path(operation));}
    private void check(JsonNode value,JsonNode rule){
        if(rule.isMissingNode())fail();
        if(rule.path("type").isArray()){if(value.isNull()&&rule.path("type").toString().contains("null"))return;if(!value.isTextual())fail();}
        else switch(rule.path("type").asText()){
            case "object" -> {if(!value.isObject())fail();var names=new HashSet<String>();value.fieldNames().forEachRemaining(names::add);for(var f:rule.path("required"))if(!names.contains(f.asText()))fail();if(rule.has("minProperties")&&names.size()<rule.path("minProperties").asInt())fail();for(String name:names){if(!rule.path("properties").has(name))fail();check(value.path(name),rule.path("properties").path(name));}}
            case "array" -> {if(!value.isArray())fail();for(var row:value)check(row,rule.path("items"));}
            case "integer" -> {if(!value.isIntegralNumber()||!value.canConvertToInt()||rule.has("minimum")&&value.asInt()<rule.path("minimum").asInt())fail();}
            case "string" -> {if(!value.isTextual()||rule.has("minLength")&&value.asText().length()<rule.path("minLength").asInt())fail();}
            default -> fail();
        }
        if("uuid".equals(rule.path("format").asText()))VersionJson.uuid(value.asText());
        if(rule.has("enum")){boolean valid=false;for(var candidate:rule.path("enum"))if(candidate.equals(value))valid=true;if(!valid)fail();}
    }
    private static void fail(){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"请求字段或类型不符合关系/图接口约定");}
}
