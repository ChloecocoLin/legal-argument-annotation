package com.argument.membera.support;

import com.argument.common.exception.BusinessException;
import com.argument.common.exception.ErrorCode;
import com.argument.membera.store.WorkflowStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class IdempotencySupport {

    private final WorkflowStore store;
    private final ObjectMapper objectMapper;

    public IdempotencySupport(WorkflowStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public Ticket begin(String userId, String operation, String key, Object body) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "写操作需要 Idempotency-Key");
        }
        String fingerprint = userId + "|" + operation + "|" + key;
        String incoming = write(body);
        String existing = store.idempotencyBodies.get(fingerprint);
        if (existing == null) {
            return new Ticket(fingerprint, incoming, null);
        }
        if (!existing.equals(incoming)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "同一幂等键不能用于不同请求体");
        }
        return new Ticket(fingerprint, incoming, store.idempotencyResults.get(fingerprint));
    }

    public void save(Ticket ticket, Object result) {
        store.idempotencyBodies.put(ticket.fingerprint(), ticket.bodyJson());
        store.idempotencyResults.put(ticket.fingerprint(), result);
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body == null ? "" : body);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "幂等校验失败");
        }
    }

    public record Ticket(String fingerprint, String bodyJson, Object cached) {
        public boolean replay() {
            return cached != null;
        }
    }
}
