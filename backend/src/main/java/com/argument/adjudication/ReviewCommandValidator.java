package com.argument.adjudication;
import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ReviewCommandValidator {
    private final JsonNode schemas;
    public ReviewCommandValidator(ObjectMapper mapper)throws java.io.IOException{try(var stream=new ClassPathResource("contracts/review-commands.json").getInputStream()){schemas=mapper.readTree(stream);}}
    public void validate(String op,JsonNode value){check(value,schemas.path(op));}
    private void check(JsonNode v,JsonNode r){
        if(r.isMissingNode())fail();if(r.has("const")){if(!r.get("const").equals(v))fail();return;}if(r.has("oneOf")){int matches=0;for(var option:r.path("oneOf"))try{check(v,option);matches++;}catch(BusinessException ignored){}if(matches!=1)fail();return;}
        if(r.path("type").isArray()){if(v.isNull())return;if(!v.isTextual())fail();}else switch(r.path("type").asText()){
            case "object"->{if(!v.isObject())fail();for(var f:r.path("required"))if(!v.has(f.asText()))fail();var names=new ArrayList<String>();v.fieldNames().forEachRemaining(names::add);for(String name:names){if(!r.path("properties").has(name))fail();check(v.path(name),r.path("properties").path(name));}}
            case "array"->{if(!v.isArray()||r.has("minItems")&&v.size()<r.path("minItems").asInt())fail();for(var row:v)check(row,r.path("items"));}
            case "integer"->{if(!v.isIntegralNumber()||!v.canConvertToInt()||r.has("minimum")&&v.asInt()<r.path("minimum").asInt())fail();}
            case "string"->{if(!v.isTextual()||r.has("minLength")&&v.asText().length()<r.path("minLength").asInt())fail();}
            case "boolean"->{if(!v.isBoolean())fail();}
            default->fail();
        }
        if("uuid".equals(r.path("format").asText()))VersionJson.uuid(v.asText());if(r.has("enum")){boolean found=false;for(var x:r.path("enum"))found|=x.equals(v);if(!found)fail();}
    }
    private void fail(){throw new BusinessException(ErrorCode.VALIDATION_ERROR,"请求不符合冻结的复核接口格式");}
}
