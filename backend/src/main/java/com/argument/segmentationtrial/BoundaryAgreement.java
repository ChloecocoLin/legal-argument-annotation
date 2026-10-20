package com.argument.segmentationtrial;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Replaceable trial algorithm. Exact rational comparison avoids threshold rounding. */
@Component @org.springframework.context.annotation.Profile("(local | test) & !prod")
public class BoundaryAgreement implements SegmentationAgreement {
 public String code(){return "PAIRWISE_INTERNAL_BOUNDARY_F1_MEAN";}
 public String version(){return "trial-1";}
 public record Pair(int left,int right,int common,int leftCount,int rightCount,double score){}
 public record Result(List<Pair> pairs,double score,boolean passed,String numerator,String denominator){}
 public Result calculate(List<Set<Integer>> inputs){
  if(inputs.size()<2)throw new IllegalArgumentException("At least two independent inputs required");
  var pairs=new ArrayList<Pair>();BigInteger numerator=BigInteger.ZERO,denominator=BigInteger.ONE;
  for(int i=0;i<inputs.size();i++)for(int j=i+1;j<inputs.size();j++){
   var a=inputs.get(i);var b=inputs.get(j);int common=(int)a.stream().filter(b::contains).count(),total=a.size()+b.size();
   BigInteger n=BigInteger.valueOf(total==0?1:2L*common),d=BigInteger.valueOf(total==0?1:total);
   pairs.add(new Pair(i,j,common,a.size(),b.size(),n.doubleValue()/d.doubleValue()));
   numerator=numerator.multiply(d).add(n.multiply(denominator));denominator=denominator.multiply(d);
   var gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);
  }
  denominator=denominator.multiply(BigInteger.valueOf(pairs.size()));var gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);
  return new Result(List.copyOf(pairs),new BigDecimal(numerator).divide(new BigDecimal(denominator),MathContext.DECIMAL64).doubleValue(),numerator.multiply(BigInteger.valueOf(5)).compareTo(denominator.multiply(BigInteger.valueOf(4)))>0,numerator.toString(),denominator.toString());
 }
}
