package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.wechatwork.WechatWorkAuthException;
import com.iflytek.skillhub.auth.wechatwork.WechatWorkLoginFlowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Browser redirects for WeChat Work authorization and callback completion.
 */
@Controller
@RequestMapping("/api/v1/auth/wechatwork")
public class WechatWorkAuthController {

    private final WechatWorkLoginFlowService loginFlowService;

    public WechatWorkAuthController(WechatWorkLoginFlowService loginFlowService) {
        this.loginFlowService = loginFlowService;
    }

    @GetMapping("/authorize")
    public String authorize(HttpServletRequest request,
                            @RequestParam(name = "returnTo", required = false) String returnTo) {
        try {
            return "redirect:" + loginFlowService.buildAuthorizationRedirect(request, returnTo);
        } catch (WechatWorkAuthException ex) {
            return "redirect:/login?reason=ssoFailed";
        }
    }

    @GetMapping("/callback")
    public String callback(HttpServletRequest request,
                           @RequestParam(name = "state", required = false) String state,
                           @RequestParam(name = "code", required = false) String code) {
        try {
            return "redirect:" + loginFlowService.completeCallback(request, state, code);
        } catch (WechatWorkAuthException ex) {
            return "redirect:/login?reason=ssoFailed";
        }
    }
}
