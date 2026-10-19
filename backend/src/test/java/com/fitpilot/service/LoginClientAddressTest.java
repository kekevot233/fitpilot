package com.fitpilot.service;

import com.fitpilot.config.LoginProtectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LoginClientAddressTest {
    private final LoginProtectionProperties properties = new LoginProtectionProperties();
    private final LoginClientAddress resolver = new LoginClientAddress(properties);

    @Test
    void ignoresSpoofedHeadersFromAnUntrustedPeer() {
        var request = request("203.0.113.1", "198.51.100.9");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void readsTheRightmostUntrustedHopInsteadOfTheForgedFirstHop() {
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        assertThat(resolver.resolve(request("10.0.0.3", "192.0.2.99, 198.51.100.8, 10.0.0.2")))
                .isEqualTo("198.51.100.8");
    }

    @Test
    void malformedForwardingFallsBackToThePeerWithoutResolvingClientHostnames() {
        properties.setTrustedProxies(List.of("127.0.0.1"));
        assertThat(resolver.resolve(request("127.0.0.1", "attacker.example"))).isEqualTo("127.0.0.1");
        assertThat(resolver.resolve(request("127.0.0.1", "deadbeef"))).isEqualTo("127.0.0.1");
    }

    @Test
    void blankProxyConfigurationDoesNotTrustLoopback() {
        properties.setTrustedProxies(List.of(""));
        assertThat(resolver.resolve(request("127.0.0.1", "192.0.2.99"))).isEqualTo("127.0.0.1");
    }

    private MockHttpServletRequest request(String peer, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
