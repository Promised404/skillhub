package com.iflytek.skillhub.auth.wechatwork;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.wechatwork")
public class WechatWorkAuthProperties {

    private boolean enabled = false;
    private String corpId;
    private String agentId;
    private String corpSecret;
    private String callbackBaseUrl;
    private String displayName = "WeCom";
    private boolean employeeLoginOnly = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getCorpSecret() {
        return corpSecret;
    }

    public void setCorpSecret(String corpSecret) {
        this.corpSecret = corpSecret;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEmployeeLoginOnly() {
        return employeeLoginOnly;
    }

    public void setEmployeeLoginOnly(boolean employeeLoginOnly) {
        this.employeeLoginOnly = employeeLoginOnly;
    }
}
