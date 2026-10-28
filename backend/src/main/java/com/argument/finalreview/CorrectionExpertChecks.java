package com.argument.finalreview;
import com.argument.adjudication.AdjudicationRepository;
import com.argument.common.exception.ErrorCode;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.argument.adjudication.AdjudicationService.require;
/** Expert conclusions from a superseded session are context, not inherited confirmation. */
public final class CorrectionExpertChecks {
 private CorrectionExpertChecks(){}
 public static void check(AnnotationDependencies data,AdjudicationRepository sessions,ObjectNode session){
  if(!session.hasNonNull("previousSessionId"))return;
  var current=data.find("expertIssue","sessionId",session.path("id").asText());
  for(var prior:data.find("expertIssue","sessionId",session.path("previousSessionId").asText())){
   String kind=prior.path("itemKind").asText();var expected="GENERAL".equals(kind)?Set.<String>of():candidates(sessions.item(prior.path("itemId").asText(),kind),kind);
   boolean general=false;var covered=new HashSet<String>();
   for(var issue:current)if(kind.equals(issue.path("itemKind").asText())&&"RESOLVED".equals(issue.path("status").asText())&&!issue.path("resolution").asText().isBlank()&&!issue.path("guideBasis").asText().isBlank()&&"CONFIRMED".equals(data.read("expertReview",issue.path("expertReviewId").asText()).path("status").asText())){
    if("GENERAL".equals(kind))general=true;else covered.addAll(candidates(sessions.item(issue.path("itemId").asText(),kind),kind));
   }
   require("GENERAL".equals(kind)?general:covered.containsAll(expected),ErrorCode.OPEN_EXPERT_ISSUES,"补正轮次须对原专家问题重新复核，不能继承旧会话确认");
  }
 }
 private static Set<String> candidates(JsonNode item,String kind){var ids=new HashSet<String>();for(var id:item.path("PROPOSITION".equals(kind)?"candidateElementIds":"candidateRelationIds"))ids.add(id.asText());return ids;}
}
