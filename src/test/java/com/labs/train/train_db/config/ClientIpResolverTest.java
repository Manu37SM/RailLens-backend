package com.labs.train.train_db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

        private final ClientIpResolver behindProxy = new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES, "");
        private final ClientIpResolver behindCloudflare =
                        new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES, "True-Client-IP");

        private static MockHttpServletRequest request(String peer, String xff) {
                MockHttpServletRequest r = new MockHttpServletRequest();
                r.setRemoteAddr(peer);
                if (xff != null) {
                        r.addHeader("X-Forwarded-For", xff);
                }
                return r;
        }

        @Test
        void aForgedLeftmostForwardedForIsIgnored() {
                // Render appends the address it saw to whatever the client sent.
                assertThat(behindProxy.resolve(request("10.1.2.3", "6.6.6.6, 203.0.113.5"))).isEqualTo("203.0.113.5");
        }

        @Test
        void rotatingFakeAddressesAllMapToTheRealClient() {
                // The bypass: a new fake IP per request used to get a fresh rate-limit bucket each time.
                for (int i = 1; i <= 50; i++) {
                        String forged = "198.51.100." + i;
                        assertThat(behindProxy.resolve(request("10.1.2.3", forged + ", 203.0.113.5")))
                                        .isEqualTo("203.0.113.5");
                }
        }

        @Test
        void aDirectClientCannotUseForwardingHeadersAtAll() {
                MockHttpServletRequest r = request("203.0.113.9", "1.1.1.1");
                r.addHeader("True-Client-IP", "8.8.8.8");
                assertThat(behindCloudflare.resolve(r)).isEqualTo("203.0.113.9");
        }

        @Test
        void trustedProxiesInTheChainAreSkippedRightToLeft() {
                assertThat(behindProxy.resolve(request("10.0.0.1", "6.6.6.6, 203.0.113.5, 10.0.0.7, 192.168.1.1")))
                                .isEqualTo("203.0.113.5");
        }

        @Test
        void theConfiguredCdnHeaderWinsWhenItComesThroughATrustedProxy() {
                MockHttpServletRequest r = request("10.0.0.1", "6.6.6.6, 172.70.1.1");   // CDN edge appended last
                r.addHeader("True-Client-IP", "203.0.113.5");
                assertThat(behindCloudflare.resolve(r)).isEqualTo("203.0.113.5");
        }

        @Test
        void anUnparseableCdnHeaderFallsBackToTheForwardedForWalk() {
                MockHttpServletRequest r = request("10.0.0.1", "203.0.113.5");
                r.addHeader("True-Client-IP", "not-an-ip");
                assertThat(behindCloudflare.resolve(r)).isEqualTo("203.0.113.5");
        }

        @Test
        void garbageAndHostNamesAreNeverResolved() {
                assertThat(behindProxy.resolve(request("10.0.0.1", "evil.example.com"))).isEqualTo("10.0.0.1");
                assertThat(ClientIpResolver.parse("localhost")).isNull();
                assertThat(ClientIpResolver.parse("1.2.3.4.5")).isNull();
                assertThat(ClientIpResolver.parse("x".repeat(500))).isNull();
        }

        @Test
        void ipv6ClientsAndRangesWork() {
                assertThat(behindProxy.resolve(request("::1", "2001:db8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
                assertThat(behindProxy.resolve(request("fd00::5", "2001:db8::1, fd00::9"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        }

        @Test
        void cidrBoundariesAreExact() {
                ClientIpResolver.Cidr r = ClientIpResolver.Cidr.parse("172.16.0.0/12");
                assertThat(r.contains(ClientIpResolver.parse("172.16.0.0"))).isTrue();
                assertThat(r.contains(ClientIpResolver.parse("172.31.255.255"))).isTrue();
                assertThat(r.contains(ClientIpResolver.parse("172.32.0.0"))).isFalse();
                assertThat(r.contains(ClientIpResolver.parse("172.15.255.255"))).isFalse();
                assertThat(r.contains(ClientIpResolver.parse("::ffff:0:0"))).isFalse();
                assertThatThrownBy(() -> ClientIpResolver.Cidr.parse("10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void withNoTrustedProxiesTheTcpPeerIsTheClient() {
                assertThat(ClientIpResolver.direct().resolve(request("10.0.0.1", "203.0.113.5"))).isEqualTo("10.0.0.1");
        }

        private static final String SECRET = "a-proxy-secret-that-is-at-least-32-characters";
        private final ClientIpResolver withWebsiteProxy =
                        new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED_PROXIES, "True-Client-IP", SECRET);

        private static MockHttpServletRequest fromWebsite(String secret, String clientIp) {
                // The website server reaches Render like any client: through Cloudflare, with its own address.
                MockHttpServletRequest r = request("10.0.0.1", "76.76.21.21");
                r.addHeader("True-Client-IP", "76.76.21.21");
                if (secret != null) {
                        r.addHeader("X-RailLens-Proxy-Secret", secret);
                }
                r.addHeader("X-RailLens-Client-IP", clientIp);
                return r;
        }

        @Test
        void theWebsiteServerCanVouchForTheVisitorIpWithTheSharedSecret() {
                assertThat(withWebsiteProxy.resolve(fromWebsite(SECRET, "203.0.113.77"))).isEqualTo("203.0.113.77");
        }

        @Test
        void withoutTheRightSecretTheVouchedIpIsIgnored() {
                // Anyone can send the header; only the website server knows the secret.
                assertThat(withWebsiteProxy.resolve(fromWebsite(null, "203.0.113.77"))).isEqualTo("76.76.21.21");
                assertThat(withWebsiteProxy.resolve(fromWebsite("wrong-secret-of-a-similar-length-123456", "203.0.113.77")))
                                .isEqualTo("76.76.21.21");
                assertThat(behindCloudflare.resolve(fromWebsite(SECRET, "203.0.113.77")))   // secret not configured here
                                .isEqualTo("76.76.21.21");
        }

        @Test
        void aVouchedValueMustBeAnIpLiteral() {
                assertThat(withWebsiteProxy.resolve(fromWebsite(SECRET, "evil.example.com"))).isEqualTo("76.76.21.21");
                assertThat(withWebsiteProxy.resolve(fromWebsite(SECRET, "2001:db8::5"))).isEqualTo("2001:db8:0:0:0:0:0:5");
        }

        @Test
        void aShortProxySecretIsRefusedAtStartup() {
                assertThatThrownBy(() -> new ClientIpResolver("", "", "short"))
                                .isInstanceOf(IllegalStateException.class);
        }
}
