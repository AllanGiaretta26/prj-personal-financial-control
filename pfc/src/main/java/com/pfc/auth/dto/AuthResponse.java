package com.pfc.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Instant expiresAt;
}
