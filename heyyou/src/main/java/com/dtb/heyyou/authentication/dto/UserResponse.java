package com.dtb.heyyou.authentication.dto;

import com.dtb.heyyou.authentication.domain.User;
import com.dtb.heyyou.authentication.domain.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        UserStatus status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getStatus()
        );
    }
}
