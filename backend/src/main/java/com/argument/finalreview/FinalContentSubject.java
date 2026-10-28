package com.argument.finalreview;
import com.argument.common.exception.ErrorCode;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.argument.adjudication.AdjudicationService.require;
/** A content subject independent of regenerated object IDs, reasons and source bookkeeping. */
public final class FinalContentSubject {
 private FinalContentSubject(){}
 public static String digest(ObjectNode snapshot){var elements=new HashMap<String,String>();var rows=new ArrayList<String>();for(var e:snapshot.path("elements")){String token=e.path("startOffset")+":"+e.path("endOffset")+":"+e.path("labelCode")+":"+e.path("isProposition")+":"+e.path("text");elements.put(e.path("id").asText(),token);rows.add("E:"+token);}var relations=new HashMap<String,JsonNode>();for(var r:snapshot.path("relations"))relations.put(r.path("id").asText(),r);for(String id:relations.keySet())rows.add("R:"+relation(id,snapshot,elements,relations,new HashSet<>()));Collections.sort(rows);return VersionJson.sha256(rows.toString());}
 private static String relation(String id,ObjectNode snapshot,Map<String,String> elements,Map<String,JsonNode> relations,Set<String> path){require(path.add(id)&&relations.containsKey(id),ErrorCode.INVALID_RELATION_MAPPING,"补正内容存在循环或悬空关系");var r=relations.get(id);var parts=new ArrayList<JsonNode>();for(var p:snapshot.path("participants"))if(id.equals(p.path("relationId").asText()))parts.add(p);parts.sort(Comparator.comparingInt(p->p.path("ordinal").asInt()));var tokens=new ArrayList<String>();for(var p:parts){String target="RELATION".equals(p.path("targetKind").asText())?relation(p.path("targetId").asText(),snapshot,elements,relations,path):elements.get(p.path("targetId").asText());require(target!=null,ErrorCode.INVALID_RELATION_MAPPING,"补正端点不存在");tokens.add(p.path("role").asText()+":"+target);}if(!r.path("directed").asBoolean())Collections.sort(tokens);path.remove(id);return r.path("relationCode")+":"+r.path("directed")+":"+tokens;}
}
