package com.dtb.heyyou.authentication.dto;

public record LoginRequest(
        String email,
        String password
) {
}
