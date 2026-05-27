package com.dtb.heyyou.authentication.dto;

public record RegisterRequest(
        String email,
        String password,
        String displayName,
        String avatarUrl
) {
}
