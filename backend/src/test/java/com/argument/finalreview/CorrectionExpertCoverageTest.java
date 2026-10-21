package com.argument.finalreview;
import com.argument.adjudication.AdjudicationRepository;
import com.argument.version.port.AnnotationDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CorrectionExpertCoverageTest {
 @Test void changedGroupingRequiresFreshExpertCoverageOfEveryOldCandidate(){
  var mapper=new ObjectMapper();var data=mock(AnnotationDependencies.class);var sessions=mock(AdjudicationRepository.class);var session=mapper.createObjectNode().put("id","new").put("previousSessionId","old");var oldIssue=mapper.createObjectNode().put("itemKind","RELATION").put("itemId","old-item");when(data.find("expertIssue","sessionId","old")).thenReturn(List.of(oldIssue));var oldItem=mapper.createObjectNode();oldItem.putArray("candidateRelationIds").add("r1").add("r2");when(sessions.item("old-item","RELATION")).thenReturn(oldItem);var issues=new ArrayList<ObjectNode>();
  for(int index=1;index<=2;index++){var issue=mapper.createObjectNode().put("itemKind","RELATION").put("itemId","new-item"+index).put("expertReviewId","review").put("status","RESOLVED").put("resolution","重新核查").put("guideBasis","指南关系章节");issues.add(issue);var item=mapper.createObjectNode();item.putArray("candidateRelationIds").add("r"+index);when(sessions.item("new-item"+index,"RELATION")).thenReturn(item);}
  when(data.read("expertReview","review")).thenReturn(mapper.createObjectNode().put("status","CONFIRMED"));when(data.find("expertIssue","sessionId","new")).thenReturn(List.of(issues.get(0)));assertThatThrownBy(()->CorrectionExpertChecks.check(data,sessions,session)).isInstanceOf(com.argument.common.exception.BusinessException.class);when(data.find("expertIssue","sessionId","new")).thenReturn(issues);assertThatCode(()->CorrectionExpertChecks.check(data,sessions,session)).doesNotThrowAnyException();
 }
}
