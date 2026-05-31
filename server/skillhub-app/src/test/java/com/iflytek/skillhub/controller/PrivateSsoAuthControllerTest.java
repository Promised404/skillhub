package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.privatesso.PrivateSsoClient;
import com.iflytek.skillhub.auth.privatesso.SsoUser;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.auth.oauth.OAuthClaims;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "skillhub.auth.direct.enabled=true",
    "skillhub.auth.private-sso.base-url=https://sso.test.local"
})
class PrivateSsoAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrivateSsoClient privateSsoClient;

    @MockBean
    private IdentityBindingService identityBindingService;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private UserRoleBindingRepository userRoleBindingRepository;

    @Test
    void directLogin_shouldAuthenticateViaPrivateSsoProvider() throws Exception {
        SsoUser ssoUser = new SsoUser("U10042", "zhangsan", "张三", "zhangsan@company.com", null);
        given(privateSsoClient.authenticate("zhangsan", "encrypted-pw", null)).willReturn(ssoUser);

        PlatformPrincipal principal = new PlatformPrincipal(
            "usr_sso_1", "zhangsan", "zhangsan@company.com", null, "private-sso", Set.of("USER")
        );
        given(identityBindingService.bindOrCreate(any(OAuthClaims.class), any(UserStatus.class)))
            .willReturn(principal);

        mockMvc.perform(post("/api/v1/auth/direct/login")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {"provider":"private-sso","username":"zhangsan","password":"encrypted-pw"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value("usr_sso_1"));
    }

    @Test
    void authMethods_shouldIncludeDirectPrivateSso() throws Exception {
        mockMvc.perform(get("/api/v1/auth/methods"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.methodType=='DIRECT_PASSWORD')]").exists());
    }
}
