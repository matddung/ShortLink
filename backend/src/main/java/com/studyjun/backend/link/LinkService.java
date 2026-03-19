package com.studyjun.backend.link;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.link.clickevent.ClickEventPublisher;
import com.studyjun.backend.link.clickevent.RedirectClickEventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LinkService {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 6;

    private final ShortLinkRepository shortLinkRepository;
    private final LinkClickEventRepository linkClickEventRepository;
    private final ClickEventPublisher clickEventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String appBaseUrl;
    private final long anonymousExpirationDays;

    public LinkService(ShortLinkRepository shortLinkRepository,
                       LinkClickEventRepository linkClickEventRepository,
                       ClickEventPublisher clickEventPublisher,
                       @Value("${app.base-url:http://localhost:8080}") String appBaseUrl,
                       @Value("${app.anonymous.expiration-days:30}") long anonymousExpirationDays) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkClickEventRepository = linkClickEventRepository;
        this.clickEventPublisher = clickEventPublisher;
        this.appBaseUrl = appBaseUrl;
        this.anonymousExpirationDays = anonymousExpirationDays;
    }

    @Transactional
    public LinkResponse.ShortLinkResponse createAnonymous(String originalUrl, String ownerKey) {
        validateUrl(originalUrl);

        String shortCode = generateUniqueShortCode();
        Instant anonymousExpiresAt = Instant.now().plus(anonymousExpirationDays, ChronoUnit.DAYS);
        ShortLink saved = shortLinkRepository.save(new ShortLink(originalUrl, shortCode, ownerKey, anonymousExpiresAt));

        return toResponse(saved);
    }

    @Transactional
    public LinkResponse.ShortLinkResponse createForUser(String originalUrl, String customCode, Long userId) {
        validateUrl(originalUrl);

        String shortCode = resolveShortCode(customCode);
        ShortLink shortLink = new ShortLink(originalUrl, shortCode, null, null);
        shortLink.claimToUser(userId);

        ShortLink saved = shortLinkRepository.save(shortLink);
        return toResponse(saved);
    }

    @Transactional
    public List<LinkResponse.ShortLinkResponse> getAnonymousLinks(String ownerKey) {
        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        List<ShortLink> expired = links.stream()
                .filter(this::isAnonymousExpired)
                .toList();
        if (!expired.isEmpty()) {
            shortLinkRepository.deleteAll(expired);
        }

        return links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public int claimAnonymousLinks(String ownerKey, Long userId) {
        if (ownerKey == null || ownerKey.isBlank()) {
            return 0;
        }

        List<ShortLink> links = shortLinkRepository.findAllByOwnerKeyAndOwnerUserIdIsNullOrderByCreatedAtDesc(ownerKey);

        List<ShortLink> validLinks = links.stream()
                .filter(link -> !isAnonymousExpired(link))
                .toList();

        List<ShortLink> expiredLinks = links.stream()
                .filter(this::isAnonymousExpired)
                .toList();

        if (!expiredLinks.isEmpty()) {
            shortLinkRepository.deleteAll(expiredLinks);
        }

        validLinks.forEach(link -> link.claimToUser(userId));
        return validLinks.size();
    }

    @Transactional
    public long purgeExpiredAnonymousLinks() {
        return shortLinkRepository.deleteByOwnerUserIdIsNullAndAnonymousExpiresAtBefore(Instant.now());
    }

    @Transactional
    public List<LinkResponse.ShortLinkResponse> getUserLinks(Long userId) {
        return shortLinkRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LinkResponse.LinkStatsResponse getLinkStats(Long linkId, Long userId) {
        ShortLink link = shortLinkRepository.findByIdAndOwnerUserId(linkId, userId)
                .orElseThrow(() -> new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        List<LinkClickEvent> allEvents = linkClickEventRepository.findAllByShortLinkIdOrderByClickedAtDesc(linkId);

        long uniqueClicks = allEvents.stream()
                .map(LinkClickEvent::getVisitorKey)
                .filter(Objects::nonNull)
                .filter(key -> !key.isBlank())
                .distinct()
                .count();

        Instant lastClickedAt = allEvents.isEmpty() ? null : allEvents.get(0).getClickedAt();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(13);
        Instant startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<LocalDate, Long> clickByDate = linkClickEventRepository
                .findAllByShortLinkIdAndClickedAtGreaterThanEqualOrderByClickedAtAsc(linkId, startInstant)
                .stream()
                .collect(Collectors.groupingBy(
                        event -> event.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()
                ));

        List<LinkResponse.DailyClickStat> dailyClicks = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate date = startDate.plusDays(i);
            dailyClicks.add(new LinkResponse.DailyClickStat(date, clickByDate.getOrDefault(date, 0L)));
        }

        List<LinkResponse.CountryStat> topCountries = allEvents.stream()
                .map(LinkClickEvent::getCountryCode)
                .map(country -> country == null || country.isBlank() ? "Unknown" : country)
                .collect(Collectors.groupingBy(country -> country, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> new LinkResponse.CountryStat(entry.getKey(), entry.getValue()))
                .toList();

        long totalEvents = allEvents.size();
        List<LinkResponse.ReferrerStat> topReferrers = allEvents.stream()
                .map(LinkClickEvent::getReferrer)
                .map(ref -> ref == null || ref.isBlank() ? "Direct" : ref)
                .collect(Collectors.groupingBy(ref -> ref, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    double percentage = totalEvents == 0 ? 0 : (entry.getValue() * 100.0) / totalEvents;
                    return new LinkResponse.ReferrerStat(entry.getKey(), entry.getValue(), Math.round(percentage * 10.0) / 10.0);
                })
                .toList();

        return new LinkResponse.LinkStatsResponse(
                link.getTotalClicks(),
                uniqueClicks,
                lastClickedAt,
                topReferrers,
                dailyClicks,
                topCountries
        );
    }

    @Transactional
    public String resolveOriginalUrl(String shortCode, String countryCode, String referrer, String visitorKey, String requestId, String source) {
        ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (isAnonymousExpired(shortLink)) {
            shortLinkRepository.delete(shortLink);
            throw new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        Instant clickedAt = Instant.now();
        linkClickEventRepository.save(new LinkClickEvent(
                shortLink,
                clickedAt,
                countryCode,
                referrer,
                visitorKey
        ));
        shortLink.increaseClickCount();
        clickEventPublisher.publish(new RedirectClickEventMessage(
                UUID.randomUUID(),
                DateTimeFormatter.ISO_INSTANT.format(clickedAt),
                requestId,
                source,
                shortLink.getId(),
                shortLink.getShortCode(),
                shortLink.getOriginalUrl(),
                countryCode,
                referrer,
                visitorKey
        ));

        return shortLink.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public String resolveOriginalUrlSelectOnly(String shortCode) {
        ShortLink shortLink = shortLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (isAnonymousExpired(shortLink)) {
            throw new BusinessException("LINK_NOT_FOUND", "링크를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        return shortLink.getOriginalUrl();
    }

    private boolean isAnonymousExpired(ShortLink shortLink) {
        return shortLink.getOwnerUserId() == null
                && shortLink.getAnonymousExpiresAt() != null
                && shortLink.getAnonymousExpiresAt().isBefore(Instant.now());
    }

    private LinkResponse.ShortLinkResponse toResponse(ShortLink shortLink) {
        return new LinkResponse.ShortLinkResponse(
                String.valueOf(shortLink.getId()),
                shortLink.getOriginalUrl(),
                shortLink.getShortCode(),
                buildShortUrl(shortLink.getShortCode()),
                shortLink.getCreatedAt(),
                "active",
                shortLink.getTotalClicks(),
                shortLink.getOwnerUserId() == null ? "anonymous" : String.valueOf(shortLink.getOwnerUserId())
        );
    }

    private String buildShortUrl(String shortCode) {
        return appBaseUrl + "/s/" + shortCode;
    }

    private void validateUrl(String originalUrl) {
        try {
            URI uri = new URI(originalUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException("INVALID_URL", "올바른 URL을 입력해 주세요.", HttpStatus.BAD_REQUEST);
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException("INVALID_URL", "올바른 URL을 입력해 주세요.", HttpStatus.BAD_REQUEST);
            }
        } catch (URISyntaxException e) {
            throw new BusinessException("INVALID_URL", "올바른 URL을 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveShortCode(String customCode) {
        if (customCode != null && !customCode.isBlank()) {
            if (shortLinkRepository.existsByShortCode(customCode)) {
                throw new BusinessException("SHORT_CODE_ALREADY_EXISTS", "이미 사용 중인 커스텀 코드입니다.", HttpStatus.CONFLICT);
            }
            return customCode;
        }

        return generateUniqueShortCode();
    }

    private String generateUniqueShortCode() {
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