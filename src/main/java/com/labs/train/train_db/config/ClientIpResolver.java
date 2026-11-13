package com.labs.train.train_db.config;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Works out the real client IP for rate limiting, without trusting anything the client can forge.
 *
 * <p>Render's proxy APPENDS to an incoming X-Forwarded-For instead of replacing it, so the LEFTMOST entry is
 * whatever the client sent. Using it (the previous behaviour) let anyone send a fresh fake IP per request and
 * bypass every per-IP limit, including the 10/min login limit.
 *
 * <p>Order of trust:
 * <ol>
 *   <li>Our own website's server (the sign-in proxy on Vercel, Phase 2B) sends the visitor's IP in
 *       {@code X-RailLens-Client-IP} together with {@code X-RailLens-Proxy-Secret}. The header is believed only when
 *       the secret matches {@code raillens.client-ip.proxy-secret} (constant-time). Without this, every visitor signing
 *       in through the website would share the website server's IP and one rate-limit bucket.</li>
 *   <li>If the TCP peer is not a trusted proxy, it IS the client: forwarding headers are ignored.</li>
 *   <li>If a trusted single-value header is configured (on Render: {@code True-Client-IP}, set by Cloudflare,
 *       which fronts every Render service) and holds an IP literal, use it.</li>
 *   <li>Otherwise walk X-Forwarded-For right to left, skipping trusted proxies; the first untrusted entry is the
 *       client. Entries to its left were written by the client and are ignored.</li>
 * </ol>
 * Nothing here does DNS: every value is parsed as an IP literal or rejected.
 */
@Slf4j
@Component
public class ClientIpResolver {

        static final String DEFAULT_TRUSTED_PROXIES =
                        "127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128,fc00::/7";

        static final String PROXY_CLIENT_IP_HEADER = "X-RailLens-Client-IP";
        static final String PROXY_SECRET_HEADER = "X-RailLens-Proxy-Secret";

        private final List<Cidr> trustedProxies;
        private final String trustedHeader;
        private final byte[] proxySecret;
        private final AtomicBoolean warnedUntrustedPeer = new AtomicBoolean();
        private final AtomicBoolean warnedBadProxySecret = new AtomicBoolean();

        public ClientIpResolver(String trustedProxies, String trustedHeader) {
                this(trustedProxies, trustedHeader, "");
        }

        @Autowired
        public ClientIpResolver(
                        @Value("${raillens.client-ip.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}") String trustedProxies,
                        @Value("${raillens.client-ip.trusted-header:}") String trustedHeader,
                        @Value("${raillens.client-ip.proxy-secret:}") String proxySecret) {
                this.trustedProxies = new ArrayList<>();
                for (String cidr : trustedProxies.split(",")) {
                        if (!cidr.isBlank()) {
                                this.trustedProxies.add(Cidr.parse(cidr.trim()));
                        }
                }
                this.trustedHeader = trustedHeader == null ? "" : trustedHeader.trim();
                String secret = proxySecret == null ? "" : proxySecret.trim();
                if (!secret.isEmpty() && secret.length() < 32) {
                        throw new IllegalStateException("raillens.client-ip.proxy-secret must be at least 32 characters "
                                        + "(openssl rand -base64 32)");
                }
                this.proxySecret = secret.isEmpty() ? null : secret.getBytes(StandardCharsets.UTF_8);
        }

        /** Remote address only: for tests and for deployments with no proxy in front. */
        public static ClientIpResolver direct() {
                return new ClientIpResolver("", "");
        }

        public String resolve(HttpServletRequest request) {
                String fromOurProxy = fromAuthenticatedProxy(request);
                if (fromOurProxy != null) {
                        return fromOurProxy;
                }
                String peer = request.getRemoteAddr();
                InetAddress peerAddress = parse(peer);
                if (peerAddress == null || !isTrusted(peerAddress)) {
                        if (!trustedHeader.isEmpty() && request.getHeader(trustedHeader) != null
                                        && warnedUntrustedPeer.compareAndSet(false, true)) {
                                // Most likely raillens.client-ip.trusted-proxies doesn't include the platform's proxy:
                                // every user would then share the proxy's bucket and hit the limit together.
                                log.warn("Request carries {} but its peer {} is not a trusted proxy; ignoring the header. "
                                                + "Check raillens.client-ip.trusted-proxies.", trustedHeader, peer);
                        }
                        return peer;
                }

                if (!trustedHeader.isEmpty()) {
                        InetAddress fromHeader = parse(request.getHeader(trustedHeader));
                        if (fromHeader != null) {
                                return fromHeader.getHostAddress();
                        }
                }

                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isBlank()) {
                        String[] hops = forwardedFor.split(",");
                        for (int i = hops.length - 1; i >= 0; i--) {
                                InetAddress hop = parse(hops[i]);
                                if (hop == null) {
                                        // Garbage can only come from the client's part of the header: stop here.
                                        return peer;
                                }
                                if (!isTrusted(hop)) {
                                        return hop.getHostAddress();
                                }
                        }
                }
                return peer;
        }

        /** The visitor IP our own website server vouches for, or null if the request isn't from it. */
        private String fromAuthenticatedProxy(HttpServletRequest request) {
                if (proxySecret == null) {
                        return null;   // feature off: the headers mean nothing
                }
                String presented = request.getHeader(PROXY_SECRET_HEADER);
                if (presented == null) {
                        return null;
                }
                if (!MessageDigest.isEqual(proxySecret, presented.getBytes(StandardCharsets.UTF_8))) {
                        if (warnedBadProxySecret.compareAndSet(false, true)) {
                                log.warn("Ignoring {}: the proxy secret does not match. Check that RAILLENS_PROXY_SECRET "
                                                + "(website) and RAILLENS_CLIENT_IP_PROXY_SECRET (backend) are the same.",
                                                PROXY_CLIENT_IP_HEADER);
                        }
                        return null;
                }
                InetAddress client = parse(request.getHeader(PROXY_CLIENT_IP_HEADER));
                return client == null ? null : client.getHostAddress();
        }

        private boolean isTrusted(InetAddress address) {
                for (Cidr cidr : trustedProxies) {
                        if (cidr.contains(address)) {
                                return true;
                        }
                }
                return false;
        }

        /** IP literal only (never a host name: no DNS lookups driven by request headers). */
        static InetAddress parse(String value) {
                if (value == null) {
                        return null;
                }
                String v = value.trim();
                if (v.isEmpty() || v.length() > 45) {
                        return null;
                }
                try {
                        return InetAddress.ofLiteral(v);
                } catch (IllegalArgumentException e) {
                        return null;
                }
        }

        record Cidr(byte[] network, int prefix) {

                static Cidr parse(String cidr) {
                        String[] parts = cidr.split("/");
                        InetAddress address = ClientIpResolver.parse(parts[0]);
                        if (address == null) {
                                throw new IllegalArgumentException("not an IP range: " + cidr);
                        }
                        int max = address.getAddress().length * 8;
                        int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : max;
                        if (prefix < 0 || prefix > max) {
                                throw new IllegalArgumentException("bad prefix length: " + cidr);
                        }
                        return new Cidr(address.getAddress(), prefix);
                }

                boolean contains(InetAddress address) {
                        byte[] candidate = address.getAddress();
                        if (candidate.length != network.length) {
                                return false;   // IPv4 range never matches an IPv6 address and vice versa
                        }
                        int fullBytes = prefix / 8;
                        for (int i = 0; i < fullBytes; i++) {
                                if (candidate[i] != network[i]) {
                                        return false;
                                }
                        }
                        int remainingBits = prefix % 8;
                        if (remainingBits == 0) {
                                return true;
                        }
                        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
                }
        }
}
