package com.notemind.security;

import com.notemind.entity.Enums.Role;
import java.util.UUID;

/** Principal placed in the security context once a JWT has been verified against the database. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
