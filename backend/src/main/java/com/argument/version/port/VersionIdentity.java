package com.argument.version.port;

import java.util.Set;

/** C supplies authentication. Authorization is re-evaluated on every request, including retries. */
public interface VersionIdentity {
    Actor authenticate(String authorization);
    record Actor(String id, String name, Set<String> roles, Set<String> permissions) {
        public boolean role(String role) { return roles.contains(role); }
        public boolean permission(String permission) { return permissions.contains(permission); }
    }
}
