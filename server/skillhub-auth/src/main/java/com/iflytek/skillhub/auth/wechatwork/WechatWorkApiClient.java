package com.iflytek.skillhub.auth.wechatwork;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Minimal WeChat Work API client for resolving login callback codes to user identity.
 */
@Component
public class WechatWorkApiClient {

    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com";
    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 120;
    private static final int MIN_TOKEN_CACHE_SECONDS = 60;
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WechatWorkAuthProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final Object tokenLock = new Object();
    private volatile CachedAccessToken cachedAccessToken;

    public WechatWorkApiClient(WechatWorkAuthProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder, Clock.systemUTC());
    }

    WechatWorkApiClient(
            WechatWorkAuthProperties properties,
            RestClient.Builder restClientBuilder,
            Clock clock
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(API_BASE_URL)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.clock = clock;
    }

    public WechatWorkUserInfoResponse resolveUserInfo(String code) {
        if (code == null || code.isBlank()) {
            throw new WechatWorkAuthException("WechatWork authorization code must not be blank");
        }

        String accessToken = loadAccessToken();
        WechatWorkUserInfoResponse response = requestUserInfo(accessToken, code);

        if (response.errorCode() != 0) {
            throw new WechatWorkAuthException("WechatWork user info API failed, errcode="
                    + response.errorCode() + ", errmsg=" + response.errorMessage());
        }

        if (properties.isEmployeeLoginOnly() && isBlank(response.userId())) {
            throw new WechatWorkAuthException(
                    "WechatWork employee login is required but callback user is openid-only");
        }

        return response;
    }

    private String loadAccessToken() {
        Instant now = clock.instant();
        CachedAccessToken snapshot = cachedAccessToken;
        if (snapshot != null && snapshot.isValidAt(now)) {
            return snapshot.token();
        }

        synchronized (tokenLock) {
            now = clock.instant();
            snapshot = cachedAccessToken;
            if (snapshot != null && snapshot.isValidAt(now)) {
                return snapshot.token();
            }
            WechatWorkAccessTokenResponse tokenResponse = requestAccessToken();
            if (tokenResponse.errorCode() != 0) {
                throw new WechatWorkAuthException("WechatWork token API failed, errcode="
                        + tokenResponse.errorCode() + ", errmsg=" + tokenResponse.errorMessage());
            }
            if (isBlank(tokenResponse.accessToken())) {
                throw new WechatWorkAuthException("WechatWork token API returned empty access token");
            }
            if (tokenResponse.expiresIn() <= 0) {
                throw new WechatWorkAuthException("WechatWork token API returned invalid expires_in");
            }

            long ttlSeconds = Math.max(
                    MIN_TOKEN_CACHE_SECONDS,
                    tokenResponse.expiresIn() - TOKEN_REFRESH_BUFFER_SECONDS
            );
            cachedAccessToken = new CachedAccessToken(
                    tokenResponse.accessToken(),
                    now.plusSeconds(ttlSeconds)
            );
            return tokenResponse.accessToken();
        }
    }

    private WechatWorkAccessTokenResponse requestAccessToken() {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/gettoken")
                            .queryParam("corpid", properties.getCorpId())
                            .queryParam("corpsecret", properties.getCorpSecret())
                            .build())
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException exception) {
            throw new WechatWorkAuthException("Failed to request WechatWork access token", exception);
        }
        validateNonEmptyResponse(body, "token");
        return new WechatWorkAccessTokenResponse(
                readErrorCode(body, "token"),
                readString(body, "errmsg"),
                readString(body, "access_token"),
                readLong(body, "expires_in")
        );
    }

    private WechatWorkUserInfoResponse requestUserInfo(String accessToken, String code) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/auth/getuserinfo")
                            .queryParam("access_token", accessToken)
                            .queryParam("code", code)
                            .build())
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientException exception) {
            throw new WechatWorkAuthException("Failed to request WechatWork user info", exception);
        }

        validateNonEmptyResponse(body, "user info");
        return new WechatWorkUserInfoResponse(
                readErrorCode(body, "user info"),
                readString(body, "errmsg"),
                firstNonBlank(body, "UserId", "userid"),
                firstNonBlank(body, "OpenId", "openid"),
                firstNonBlank(body, "DeviceId", "deviceid"),
                body
        );
    }

    private void validateNonEmptyResponse(Map<String, Object> responseBody, String apiName) {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new WechatWorkAuthException("WechatWork " + apiName + " API returned empty response");
        }
    }

    private int readErrorCode(Map<String, Object> responseBody, String apiName) {
        Object raw = responseBody.get("errcode");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // Fall through to exception below.
            }
        }
        throw new WechatWorkAuthException("WechatWork " + apiName + " API returned invalid errcode");
    }

    private long readLong(Map<String, Object> responseBody, String key) {
        Object raw = responseBody.get(key);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // Fall through to exception below.
            }
        }
        return -1L;
    }

    private String readString(Map<String, Object> responseBody, String key) {
        Object raw = responseBody.get(key);
        return raw instanceof String text ? text : null;
    }

    private String firstNonBlank(Map<String, Object> responseBody, String... keys) {
        for (String key : keys) {
            String value = readString(responseBody, key);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedAccessToken(String token, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
