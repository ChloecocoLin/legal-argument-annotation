package com.argument.automation;
import com.fasterxml.jackson.databind.node.*;
/** Model boundary: server-owned frozen input; no HTTP endpoint accepts caller-supplied outputs. */
public interface AutomationModelAdapter {
    ObjectNode descriptor();
    ArrayNode generate(ObjectNode frozenInput);
}
