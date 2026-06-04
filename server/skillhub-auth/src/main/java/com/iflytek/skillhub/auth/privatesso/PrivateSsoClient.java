package com.iflytek.skillhub.auth.privatesso;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class PrivateSsoClient {

    private static final Logger log = LoggerFactory.getLogger(PrivateSsoClient.class);

    private final WebClient webClient;

    public PrivateSsoClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public SsoUser authenticate(String username, String encryptedPassword, String twoFactorCode) {
        SsoAuthenticateRequest request = new SsoAuthenticateRequest(username, encryptedPassword, twoFactorCode);
        try {
            ResponseEntity<SsoUser> response = webClient.post()
                    .uri("/api/sso/authenticate.do")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .toEntity(SsoUser.class)
                    .block();

            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthFlowException(HttpStatus.BAD_GATEWAY, "error.auth.ssoUnavailable");
            }
            return response.getBody();
        } catch (WebClientResponseException e) {
            throw mapSsoError(e);
        } catch (AuthFlowException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthFlowException(HttpStatus.BAD_GATEWAY, "error.auth.ssoUnavailable");
        }
    }

    private AuthFlowException mapSsoError(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        log.warn("SSO error response: status={}, body={}", e.getStatusCode(), body);
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            if (body.contains("INVALID_2FA_CODE")) {
                return new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.invalid2faCode");
            }
            return new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials");
        }
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
            return new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.accountDisabled");
        }
        if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return new AuthFlowException(HttpStatus.BAD_REQUEST, "error.auth.ssoBadRequest");
        }
        return new AuthFlowException(HttpStatus.BAD_GATEWAY, "error.auth.ssoUnavailable");
    }
}
