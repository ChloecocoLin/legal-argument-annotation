package com.argument.adjudication;

import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** A/B/C adapter. All mutations join the caller's transaction. No production fallback.
 * Item output contains conflictType and candidate IDs, including every agreement/non-proposition.
 * freezePropositions must validate complete coverage and persist the approved M13 mapping.
 * validateRelations rechecks the whole effective graph (including deleted inner relations).
 */
public interface AdjudicationDependencies {
    List<ObjectNode> comparePropositions(List<ObjectNode> snapshots);
    List<ObjectNode> compareRelations(ObjectNode session,List<ObjectNode> snapshots);
    void freezePropositions(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions);
    void validateRelations(ObjectNode session,List<ObjectNode> snapshots,List<ObjectNode> items,List<ObjectNode> decisions);
    ObjectNode custom(String id);
    boolean expertAccess(Actor actor,ObjectNode session);
    void checkExperts(ObjectNode session);
    void returnDraft(ObjectNode version,Actor actor);
}
