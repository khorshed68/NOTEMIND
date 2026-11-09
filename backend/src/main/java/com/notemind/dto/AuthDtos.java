package com.notemind.dto;

import com.notemind.entity.Enums.Role;
import com.notemind.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    private static final String PASSWORD_MESSAGE = "Password must contain at least one letter and one number";

    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 254) String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Password is required") @Size(max = 72) String password) {
    }

    public record UpdateProfileRequest(@NotBlank(message = "Name is required") @Size(max = 120) String name) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") @Size(max = 72) String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record UserDto(UUID id, String name, String email, Role role, boolean enabled, Instant createdAt) {
        public static UserDto from(User user) {
            return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEnabled(),
                    user.getCreatedAt());
        }
    }

    public record AuthResponse(String accessToken, long expiresInSeconds, UserDto user) {
    }

    /** Service-level result; the raw refresh token goes into a cookie, never into a JSON body. */
    public record IssuedSession(AuthResponse response, String refreshToken) {
    }
}
