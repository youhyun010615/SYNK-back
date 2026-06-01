package com.synk.dto.request;

import lombok.Getter;

@Getter
public class AuthRequest {

    private String code;
    private String redirectUri;
}
