package com.studyjun.backend.link;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LinkRequest {

    public record CreateAnonymousRequest(
            @NotBlank(message = "URL을 입력해 주세요.")
            String originalUrl
    ) {
    }

    public record CreateLinkRequest(
            @NotBlank(message = "URL을 입력해 주세요.")
            String originalUrl,
            @Pattern(regexp = "^[a-zA-Z0-9-]{3,32}$", message = "커스텀 코드는 3~32자 영문, 숫자, -만 가능합니다.")
            String customCode
    ) {
    }
}