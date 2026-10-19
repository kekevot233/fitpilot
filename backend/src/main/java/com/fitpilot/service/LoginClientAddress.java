package com.fitpilot.service;

import com.fitpilot.config.LoginProtectionProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@Component
public class LoginClientAddress {
    private final LoginProtectionProperties properties;

    public LoginClientAddress(LoginProtectionProperties properties) { this.properties = properties; }

    public String resolve(HttpServletRequest request) {
        String peer = literal(request.getRemoteAddr());
        if (peer == null) return "unknown";
        String current = peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return peer;
        String[] hops = forwarded.split(",", -1);
        for (int index = hops.length - 1; index >= 0; index--) {
            if (!trusted(current)) break;
            String next = literal(hops[index].trim());
            if (next == null) return peer;
            current = next;
        }
        return current;
    }

    private boolean trusted(String address) {
        for (String rule : properties.getTrustedProxies()) {
            if (rule == null || rule.isBlank()) continue;
            try {
                if (rule.contains("/")) {
                    if (new IpAddressMatcher(rule).matches(address)) return true;
                } else {
                    // Configured Docker service names are resolved locally, never client-supplied names.
                    InetAddress peer = InetAddress.getByName(address);
                    if (Arrays.asList(InetAddress.getAllByName(rule)).contains(peer)) return true;
                }
            } catch (UnknownHostException | IllegalArgumentException ignored) {
                // An unavailable proxy name is not trusted.
            }
        }
        return false;
    }

    private String literal(String value) {
        if (value == null) return null;
        boolean ipv4 = value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6 = value.contains(":") && value.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException exception) { return null; }
    }
}
