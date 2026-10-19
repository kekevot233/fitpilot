package com.fitpilot.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Component
@Validated
@ConfigurationProperties("fitpilot.security.login-protection")
public class LoginProtectionProperties {
    @Min(1)
    private int maxFailures = 5;
    @Min(1)
    private int windowSeconds = 900;
    @Min(1)
    private int attemptLeaseSeconds = 30;
    private List<String> trustedProxies = List.of();

    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int value) { maxFailures = value; }
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int value) { windowSeconds = value; }
    public int getAttemptLeaseSeconds() { return attemptLeaseSeconds; }
    public void setAttemptLeaseSeconds(int value) { attemptLeaseSeconds = value; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> value) { trustedProxies = value; }
}
