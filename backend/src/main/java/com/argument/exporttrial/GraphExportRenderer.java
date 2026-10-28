package com.argument.exporttrial;

import com.argument.common.exception.ErrorCode;
import com.argument.finalreview.FrozenGraphValidation;
import com.argument.version.VersionJson;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import static com.argument.adjudication.AdjudicationService.require;

/** C GraphAssembler and graphGeometry rules, rendered entirely from frozen server data.
 * This equivalent Java renderer is a D-branch trial, not a production render certificate. */
@Component
public class GraphExportRenderer {
    public static final String VERSION="C-geometry+D-java2d-trial-1";
    private final FrozenGraphValidation validation;
    public GraphExportRenderer(FrozenGraphValidation validation){this.validation=validation;}
    public void prepare(Actor actor,ObjectNode record,ObjectNode input,ExportBundleBuilder bundles){
        var all=input.putArray("renderGraphs");int count=0;
        for(var id:record.path("finalVersionIds")){
            var g=validation.inspect(bundles.source(actor,id.asText()));
            require("VALID".equals(g.path("status").asText()),ErrorCode.SOURCE_INCOMPLETE,"冻结图验证不通过，不能生成图示");
            count+=g.path("nodes").size();require(count<=1000,ErrorCode.VALIDATION_ERROR,"图示节点超过 1000，请缩小导出范围");all.add(g);
        }
        require(count>0,ErrorCode.SOURCE_INCOMPLETE,"冻结图没有节点");
    }
    private record Node(String id,String label,String code,boolean proposition,double x,double y,double hw,double hh){}
    private record Shape(String kind,double x,double y,double x2,double y2,String text,String color,boolean dashed,boolean fill){}
    public record Rendered(byte[] bytes,ArrayNode components,int width,int height){}
    public Rendered render(ObjectNode record,ObjectNode input){
        if(!VERSION.equals(record.path("rendererVersion").asText())||!VersionJson.canonicalHash(input.path("renderGraphs")).equals(record.path("renderInputDigest").asText()))throw new IllegalStateException("Frozen render input mismatch");
        var shapes=new ArrayList<Shape>();var manifest=JsonNodeFactory.instance.arrayNode();double top=75,width=700;
        shapes.add(new Shape("text",24,30,0,0,"D BRANCH TRIAL / "+VERSION,"#475569",false,false));
        int total=0;
        for(var graph:input.path("renderGraphs")){
            var nodes=new LinkedHashMap<String,JsonNode>();graph.path("nodes").forEach(n->{if(nodes.put(n.path("id").asText(),n)!=null)throw new IllegalStateException("Duplicate node");});
            total+=nodes.size();if(total>1000)throw new IllegalStateException("Too many nodes");
            var adjacent=new LinkedHashMap<String,Set<String>>();nodes.keySet().forEach(id->adjacent.put(id,new TreeSet<>()));
            for(var edge:graph.path("edges")){String a=edge.path("source").asText(),b=edge.path("target").asText();if(!nodes.containsKey(a)||!nodes.containsKey(b))throw new IllegalStateException("Unknown edge endpoint");adjacent.get(a).add(b);adjacent.get(b).add(a);}
            var remaining=new LinkedHashSet<>(nodes.keySet());int component=0;
            while(!remaining.isEmpty()){
                var group=new LinkedHashSet<String>();var queue=new ArrayDeque<String>();queue.add(remaining.iterator().next());
                while(!queue.isEmpty()){String id=queue.remove();if(!group.add(id))continue;remaining.remove(id);queue.addAll(adjacent.get(id).stream().filter(x->!group.contains(x)).toList());}
                var edges=new ArrayList<JsonNode>();graph.path("edges").forEach(e->{if(group.contains(e.path("source").asText()))edges.add(e);});
                var depths=new LinkedHashMap<String,Integer>();var incoming=new HashSet<String>();edges.forEach(e->incoming.add(e.path("target").asText()));
                group.stream().filter(id->!incoming.contains(id)).forEach(id->{depths.put(id,0);queue.add(id);});if(queue.isEmpty()){String first=group.iterator().next();depths.put(first,0);queue.add(first);}
                while(!queue.isEmpty()){String id=queue.remove();for(var e:edges)if(id.equals(e.path("source").asText())){String next=e.path("target").asText();if(!depths.containsKey(next)){depths.put(next,depths.get(id)+1);queue.add(next);}}}
                int last=depths.values().stream().mapToInt(Integer::intValue).max().orElse(0)+1;group.forEach(id->depths.putIfAbsent(id,last));
                var levels=new TreeMap<Integer,java.util.List<String>>();group.forEach(id->levels.computeIfAbsent(depths.get(id),k->new ArrayList<>()).add(id));
                int maxRows=levels.values().stream().mapToInt(java.util.List::size).max().orElse(1);double h=maxRows*94+80;
                shapes.add(new Shape("text",24,top,0,0,"Final "+graph.path("ownerId").asText()+" / component "+(++component),"#475569",false,false));
                var placed=new LinkedHashMap<String,Node>();
                for(var level:levels.entrySet()){var ids=level.getValue();ids.sort(Comparator.comparingInt((String id)->number(nodes.get(id).path("displayNo").asText())).thenComparing(id->id));int row=0;
                    for(String id:ids){var n=nodes.get(id);boolean prop="ELEMENT".equals(n.path("kind").asText());String code=n.path("relationCode").asText(),label=prop?n.path("displayNo").asText():code;
                        if(code.equals("I")){var labels=new ArrayList<String>();for(var e:edges)if(id.equals(e.path("relationId").asText()))labels.add(nodes.get(e.path("source").asText()).path("displayNo").asText());labels.sort(Comparator.comparingInt(GraphExportRenderer::number));label=String.join("/",labels);if(label.isEmpty())label="I";}
                        if(label.length()>120)throw new IllegalStateException("Graph label too wide");double hw=prop?29:code.equals("I")?Math.max(26,12+label.length()*4.2):12,hh=prop?19:code.equals("I")?15:12;
                        placed.put(id,new Node(id,label,code,prop,70+level.getKey()*154,top+55+(maxRows-ids.size())*47+row++*94,hw,hh));
                    }
                }
                for(var e:edges){var a=placed.get(e.path("source").asText());var b=placed.get(e.path("target").asText());double[] p=end(a,b),q=end(b,a);String color=color(e.path("relationCode").asText());
                    shapes.add(new Shape("line",p[0],p[1],q[0],q[1],"",color,e.path("dashed").asBoolean(),false));
                    if(e.path("arrow").asBoolean()){double angle=Math.atan2(q[1]-p[1],q[0]-p[0]);for(double sign:new double[]{-1,1})shapes.add(new Shape("line",q[0],q[1],q[0]-8*Math.cos(angle+sign*.45),q[1]-8*Math.sin(angle+sign*.45),"",color,false,false));}
                }
                for(var n:placed.values()){String color=n.proposition?"#334155":color(n.code);String kind=n.proposition||n.code.equals("I")?"rect":"circle";
                    shapes.add(new Shape(kind,n.x,n.y,n.hw,n.hh,"",color,false,n.code.equals("S")));
                    if(n.proposition||n.code.equals("I"))shapes.add(new Shape("label",n.x,n.y,0,0,n.label,color,false,false));
                    if(n.code.equals("J")||n.code.equals("M")){shapes.add(new Shape("line",n.x-6,n.y,n.x+6,n.y,"",color,false,false));shapes.add(new Shape("line",n.x,n.y-6,n.x,n.y+6,"",color,false,false));}
                    width=Math.max(width,n.x+n.hw+40);
                }
                var m=manifest.addObject().put("finalVersionId",graph.path("ownerId").asText()).put("graphId",graph.path("graphId").asText()).put("componentNumber",component).put("componentId",graph.path("graphId").asText()+":"+component).put("fileEntryName","legal-annotation-"+record.path("id").asText()+"."+record.path("format").asText().toLowerCase(java.util.Locale.ROOT)).put("isolated",edges.isEmpty()).put("contentDigest",graph.path("validationBinding").path("contentDigest").asText());
                var ns=m.putArray("nodeIds");group.forEach(ns::add);var es=m.putArray("edgeIds");edges.forEach(e->es.add(e.path("id").asText()));m.putObject("viewport").put("x",0).put("y",top-25).put("width",width).put("height",h);top+=h;
            }
            if(component!=graph.path("componentCount").asInt())throw new IllegalStateException("Incomplete components");
        }
        int w=(int)Math.ceil(width),h=(int)Math.ceil(top+20);if(w>8000||h>8000||(long)w*h>16_000_000)throw new IllegalStateException("Render dimensions exceed limit; narrow selection");
        String format=record.path("format").asText();byte[] bytes=switch(format){case "SVG"->svg(shapes,w,h).getBytes(StandardCharsets.UTF_8);case "PNG"->png(shapes,w,h);default->throw new IllegalArgumentException("Unsupported graph format");};return new Rendered(bytes,manifest,w,h);
    }
    private static int number(String label){var m=java.util.regex.Pattern.compile("[0-9]+").matcher(label);return m.find()?Integer.parseInt(m.group()):-1;}
    private static double[] end(Node a,Node b){double dx=b.x-a.x,dy=b.y-a.y,len=Math.max(1,Math.hypot(dx,dy)),sx=dx/len,sy=dy/len;double t=!a.proposition&&!a.code.equals("I")?a.hw:Math.min(a.hw/Math.max(Math.abs(sx),.000001),a.hh/Math.max(Math.abs(sy),.000001));return new double[]{a.x+sx*t,a.y+sy*t};}
    private static String color(String c){return switch(c){case "S"->"#1f7a4d";case "A"->"#c0392b";case "J"->"#b7791f";case "M"->"#0e7490";case "I"->"#6b7280";default->"#475569";};}
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String svg(java.util.List<Shape> shapes,int w,int h){var s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w).append("\" height=\"").append(h).append("\" viewBox=\"0 0 ").append(w).append(' ').append(h).append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><title>D branch trial frozen argument graphs</title>");
        for(var p:shapes){String style=" stroke=\""+p.color+"\" stroke-width=\"2\" fill=\""+(p.fill?p.color:"white")+"\""+(p.dashed?" stroke-dasharray=\"5 4\"":"");switch(p.kind){case "line"->s.append("<line x1=\"").append(p.x).append("\" y1=\"").append(p.y).append("\" x2=\"").append(p.x2).append("\" y2=\"").append(p.y2).append('\"').append(style).append("/>");case "rect"->s.append("<rect x=\"").append(p.x-p.x2).append("\" y=\"").append(p.y-p.y2).append("\" width=\"").append(p.x2*2).append("\" height=\"").append(p.y2*2).append('\"').append(style).append("/>");case "circle"->s.append("<ellipse cx=\"").append(p.x).append("\" cy=\"").append(p.y).append("\" rx=\"").append(p.x2).append("\" ry=\"").append(p.y2).append('\"').append(style).append("/>");default->s.append("<text x=\"").append(p.x).append("\" y=\"").append(p.y+(p.kind.equals("label")?5:0)).append("\" font-family=\"sans-serif\" font-size=\"13\" text-anchor=\"").append(p.kind.equals("label")?"middle":"start").append("\" fill=\"").append(p.color).append("\">").append(esc(p.text)).append("</text>");}}return s.append("</svg>").toString();}
    private static byte[] png(java.util.List<Shape> shapes,int w,int h){var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13));for(var p:shapes){g.setColor(Color.decode(p.color));g.setStroke(p.dashed?new BasicStroke(2,0,0,10,new float[]{5,4},0):new BasicStroke(2));switch(p.kind){case "line"->g.draw(new Line2D.Double(p.x,p.y,p.x2,p.y2));case "rect","circle"->{java.awt.Shape shape=p.kind.equals("rect")?new Rectangle2D.Double(p.x-p.x2,p.y-p.y2,p.x2*2,p.y2*2):new Ellipse2D.Double(p.x-p.x2,p.y-p.y2,p.x2*2,p.y2*2);if(p.fill)g.fill(shape);else{g.setColor(Color.WHITE);g.fill(shape);g.setColor(Color.decode(p.color));g.draw(shape);}}default->g.drawString(p.text,(float)(p.x-(p.kind.equals("label")?g.getFontMetrics().stringWidth(p.text)/2.0:0)),(float)(p.y+(p.kind.equals("label")?5:0)));}}var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("No PNG writer");return out.toByteArray();}catch(IOException e){throw new IllegalStateException("PNG render failed",e);}finally{g.dispose();}}
}
