package com.iflytek.skillhub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.wechatwork.WechatWorkAuthException;
import com.iflytek.skillhub.auth.wechatwork.WechatWorkLoginFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WechatWorkAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WechatWorkLoginFlowService loginFlowService;

    @Test
    void authorize_redirectsToWechatWorkUrl() throws Exception {
        given(loginFlowService.buildAuthorizationRedirect(any(), eq("/dashboard")))
                .willReturn("https://open.work.weixin.qq.com/wwopen/sso/qrConnect?appid=corp");

        mockMvc.perform(get("/api/v1/auth/wechatwork/authorize").param("returnTo", "/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        "https://open.work.weixin.qq.com/wwopen/sso/qrConnect?appid=corp"));
    }

    @Test
    void authorize_redirectsToLoginWhenWechatWorkAuthFails() throws Exception {
        given(loginFlowService.buildAuthorizationRedirect(any(), eq("/dashboard")))
                .willThrow(new WechatWorkAuthException("authorize failed"));

        mockMvc.perform(get("/api/v1/auth/wechatwork/authorize").param("returnTo", "/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?reason=ssoFailed"));
    }

    @Test
    void callback_redirectsToResolvedTargetAfterSuccess() throws Exception {
        given(loginFlowService.completeCallback(any(), eq("state-1"), eq("code-1")))
                .willReturn("/dashboard/review");

        mockMvc.perform(get("/api/v1/auth/wechatwork/callback")
                        .param("state", "state-1")
                        .param("code", "code-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard/review"));
    }

    @Test
    void callback_redirectsToLoginWhenWechatWorkAuthFails() throws Exception {
        String sensitiveCode = "code-sensitive-20260530";
        given(loginFlowService.completeCallback(any(), eq("state-2"), eq(sensitiveCode)))
                .willThrow(new WechatWorkAuthException("Failed to request WechatWork user info"));

        mockMvc.perform(get("/api/v1/auth/wechatwork/callback")
                        .param("state", "state-2")
                        .param("code", sensitiveCode))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?reason=ssoFailed"));
    }
}
