package com.argument.segmentationtrial;
import java.util.*;
/** Trial strategy boundary; an approved algorithm may replace the implementation. */
public interface SegmentationAgreement {
 BoundaryAgreement.Result calculate(List<Set<Integer>> inputs);
 String code();
 String version();
}
