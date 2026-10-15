package com.argument.finalreview;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Integration port, not a public HTTP contract. Production MUST supply a real adapter.
 * resolve uses B's frozen M13 mapping and C's relation mapping; custom results require
 * immutable B snapshots. Output: elements,relations,participants,provenance (core model).
 * assess must bind its evidence to this exact snapshot/session/guide; readiness cannot
 * be supplied by a request, UI checkbox, environment flag, or development fixture.
 */
public interface FinalReviewDependencies {
    ObjectNode resolve(ObjectNode session,List<ObjectNode> sources,List<ObjectNode> propositions,List<ObjectNode> relations,List<ObjectNode> decisions);
    Evidence assess(ObjectNode snapshot,ObjectNode session);
    record Evidence(boolean formalReady,String mode,String contentDigest,String decisionDigest,String guideVersionId,List<Check> checks) {}
    record Check(String code,String message,boolean blocking) {}
}
