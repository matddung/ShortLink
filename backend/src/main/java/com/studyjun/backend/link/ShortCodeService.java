package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ShortCodeService {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 6;

    private final ShortLinkRepository shortLinkRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShortCodeService(ShortLinkRepository shortLinkRepository) {
        this.shortLinkRepository = shortLinkRepository;
    }

    public String resolveShortCode(String customCode) {
        if (customCode != null && !customCode.isBlank()) {
            if (shortLinkRepository.existsByShortCode(customCode)) {
                throw new BusinessException("SHORT_CODE_ALREADY_EXISTS", "이미 사용 중인 커스텀 코드입니다.", HttpStatus.CONFLICT);
            }
            return customCode;
        }

        return generateUniqueShortCode();
    }

    public String generateUniqueShortCode() {
        for (int i = 0; i < 10; i++) {
            String code = randomCode();
            if (!shortLinkRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new BusinessException("SHORT_CODE_GENERATION_FAILED", "짧은 링크 생성에 실패했습니다. 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            builder.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return builder.toString();
    }
}