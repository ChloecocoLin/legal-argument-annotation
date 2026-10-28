package com.argument.teachingtrial;
import com.argument.teaching.dto.*;
import com.argument.teaching.rules.TeachingExerciseType;
import com.argument.teaching.service.*;
import com.argument.integration.StructureEngine;
import com.argument.relation.service.*;
import com.argument.graph.service.GraphAssembler;
import com.argument.version.VersionJson;
import com.argument.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;
/** C's specialist weights apply only to that specialist. Course total remains null. */
@Component
public class TeachingScoring {
 public static final String VERSION="C-TeachingScorer-ab6f03d+D-snapshot-v2";
 public static String version(){return VERSION;}
 private final StructureEngine structures;private final ObjectMapper mapper;
 public TeachingScoring(StructureEngine structures,ObjectMapper mapper){this.structures=structures;this.mapper=mapper;}
 public List<RelationTemplate> templates(ObjectNode snapshot){var list=new ArrayList<RelationTemplate>();for(var r:snapshot.path("relations")){var parts=new ArrayList<TemplateParticipant>();for(var p:snapshot.path("participants"))if(p.path("relationId").equals(r.path("id")))parts.add(new TemplateParticipant(p.path("ordinal").asInt(),p.path("role").asText(),p.path("targetKind").asText(),"ELEMENT".equals(p.path("targetKind").asText())?p.path("targetId").asText():null,"RELATION".equals(p.path("targetKind").asText())?p.path("targetId").asText():null));parts.sort(Comparator.comparingInt(TemplateParticipant::ordinal));list.add(new RelationTemplate(r.path("id").asText(),r.path("relationCode").asText(),parts,null));}return list;}
 public RelationTemplateCodec.Parsed parse(ObjectNode basis,List<RelationTemplate> templates){var source=structures.source(TeachingSnapshot.rows(basis,"elements"));var validator=new RelationValidator(source);var ids=new HashSet<String>();basis.path("elements").forEach(e->{if(e.path("isProposition").asBoolean())ids.add(e.path("id").asText());});return new RelationTemplateCodec(source,validator).parse(templates,basis.path("ownerId").asText(),ids);}
 public ObjectNode score(String resultId,String type,ObjectNode standard,List<RelationTemplate> answer){var exercise=TeachingExerciseType.of(type);require(exercise!=null,ErrorCode.VALIDATION_ERROR,"未知专项评分类型");var source=structures.source(TeachingSnapshot.rows(standard,"elements"));var validator=new RelationValidator(source);var codec=new RelationTemplateCodec(source,validator);var ids=new HashSet<String>();standard.path("elements").forEach(e->{if(e.path("isProposition").asBoolean())ids.add(e.path("id").asText());});var key=codec.parse(templates(standard),standard.path("ownerId").asText(),ids);var actual=codec.parse(answer,standard.path("ownerId").asText(),ids);var c=new TeachingScorer(new RelationCanonicalizer(source),new GraphAssembler(validator),source).score(resultId,exercise,standard.path("ownerId").asText(),ids,key,actual,"");var result=mapper.createObjectNode().put("algorithmVersion",VERSION).put("relationSpecialistScore",c.score()).put("scoreStatus","SPECIALIST_ONLY").putNull("totalScore");result.set("specialist",mapper.valueToTree(c));return result;}
}
