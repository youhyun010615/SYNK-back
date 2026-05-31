// 프로필 수정 시 이름, 프로필 이미지를 받는다.

package com.synk.dto.request;

import lombok.Getter;

@Getter
public class UpdateProfileRequest {

    private String name;
    private String profileImage;
}

