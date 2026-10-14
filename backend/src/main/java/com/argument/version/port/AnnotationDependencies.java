package com.argument.version.port;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** A/B/C integration boundary. lock/replace must join D's JDBC transaction.
 * Production must supply an adapter; no mock adapter is selected in prod.
 */
public interface AnnotationDependencies {
    ObjectNode read(String kind, String id);
    List<ObjectNode> find(String kind, String field, String value);
    ObjectNode lock(String kind, String id);
    void replace(String kind, ObjectNode record);
    /** Must include retained sessions, including completed/returned sessions. */
    boolean referencedByAdjudication(String versionId);
}
