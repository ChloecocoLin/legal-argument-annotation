package com.argument.exporttrial;
import com.argument.version.VersionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.assertj.core.api.Assertions.*;

class GraphExportRendererTest {
    final ObjectMapper mapper=new ObjectMapper();final GraphExportRenderer renderer=new GraphExportRenderer(null);
    ObjectNode input(){var input=mapper.createObjectNode();var graph=input.putArray("renderGraphs").addObject().put("ownerId",VersionJson.uuid()).put("graphId",VersionJson.uuid()).put("componentCount",2);graph.putObject("validationBinding").put("contentDigest","a".repeat(64));var nodes=graph.putArray("nodes");
        for(int i=1;i<=5;i++)nodes.addObject().put("id","p"+i).put("kind","ELEMENT").put("displayNo",i==5?"P5<&>":"P"+i);
        for(String code:List.of("S","A","J","M","I"))nodes.addObject().put("id",code).put("kind","RELATION").put("relationCode",code);
        var edges=graph.putArray("edges");edge(edges,"p1","S","S",true,false);edge(edges,"S","p2","S",true,false);edge(edges,"p2","A","A",true,false);edge(edges,"A","p3","A",true,false);edge(edges,"p1","J","J",false,false);edge(edges,"p3","J","J",false,false);edge(edges,"J","M","M",true,false);edge(edges,"M","p4","M",true,false);edge(edges,"p1","I","I",false,true);edge(edges,"p4","I","I",false,true);return input;}
    void edge(ArrayNode edges,String a,String b,String code,boolean arrow,boolean dashed){edges.addObject().put("id",a+"->"+b).put("source",a).put("target",b).put("relationId",code).put("relationCode",code).put("arrow",arrow).put("dashed",dashed);}
    ObjectNode record(ObjectNode input,String format){return mapper.createObjectNode().put("format",format).put("rendererVersion",GraphExportRenderer.VERSION).put("renderInputDigest",VersionJson.canonicalHash(input.path("renderGraphs")));}
    @Test void nestedAllSymbolsAndIsolatedPropositionsAreRenderedAndManifestHasExactCoverage()throws Exception{
        var input=input();var svg=renderer.render(record(input,"SVG"),input);assertThat(svg.components()).hasSize(2);var ids=new HashSet<String>();svg.components().forEach(c->c.path("nodeIds").forEach(id->assertThat(ids.add(id.asText())).isTrue()));assertThat(ids).hasSize(10);assertThat(svg.components().get(1).path("isolated").asBoolean()).isTrue();String xml=new String(svg.bytes(),java.nio.charset.StandardCharsets.UTF_8);assertThat(xml).contains("P5&lt;&amp;&gt;","stroke-dasharray","P1/P4");var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.bytes()));var png=renderer.render(record(input,"PNG"),input);var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(png.bytes()));assertThat(image.getWidth()).isEqualTo(svg.width());assertThat(image.getHeight()).isEqualTo(svg.height());var pngManifest=png.components().deepCopy();var svgManifest=svg.components().deepCopy();pngManifest.forEach(n->((ObjectNode)n).remove("fileEntryName"));svgManifest.forEach(n->((ObjectNode)n).remove("fileEntryName"));assertThat(pngManifest).isEqualTo(svgManifest);assertThat(renderer.render(record(input,"SVG"),input).bytes()).isEqualTo(svg.bytes());}
    @Test void frozenInputRendererMismatchAndExcessiveDimensionsFailWithoutTruncation(){var input=input();var record=record(input,"PNG");((ObjectNode)input.path("renderGraphs").get(0).path("nodes").get(0)).put("displayNo","changed");assertThatThrownBy(()->renderer.render(record,input)).hasMessageContaining("mismatch");record.put("renderInputDigest",VersionJson.canonicalHash(input.path("renderGraphs"))).put("rendererVersion","client-renderer");assertThatThrownBy(()->renderer.render(record,input)).hasMessageContaining("mismatch");var many=mapper.createObjectNode();var g=many.putArray("renderGraphs").addObject().put("componentCount",100);var ns=g.putArray("nodes");g.putArray("edges");for(int i=0;i<100;i++)ns.addObject().put("id","isolated"+i).put("kind","ELEMENT").put("displayNo","P"+i);assertThatThrownBy(()->renderer.render(record(many,"PNG"),many)).hasMessageContaining("dimensions");}
}
