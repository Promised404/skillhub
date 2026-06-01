package com.iflytek.skillhub.auth.privatesso;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class PrivateSsoClient {

    private final WebClient webClient;

    public PrivateSsoClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public SsoUser authenticate(String username, String encryptedPassword, String twoFactorCode) {
        SsoAuthenticateRequest request = new SsoAuthenticateRequest(username, encryptedPassword, twoFactorCode);
        try {
            ResponseEntity<SsoUser> response = webClient.post()
                    .uri("/api/sso/authenticate")
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
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            String body = e.getResponseBodyAsString();
            if (body.contains("INVALID_2FA_CODE")) {
                return new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.invalid2faCode");
            }
            return new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.invalidCredentials");
        }
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
            return new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.accountDisabled");
        }
        return new AuthFlowException(HttpStatus.BAD_GATEWAY, "error.auth.ssoUnavailable");
    }
}
