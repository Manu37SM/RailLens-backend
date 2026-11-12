package com.labs.train.train_db.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Deny-by-default check for an allow-by-default design. Auth here is applied per path pattern (interceptors), so a
 * new endpoint is PUBLIC unless someone remembers to add its path. This asks Spring for every registered handler
 * and proves that no state-changing endpoint is reachable without a credential, except the deliberate public
 * auth entry points. It found POST /api/v1/trains, /stations and /schedules open to anyone.
 */
@SpringBootTest(properties = "raillens.admin.api-key=test-admin-key-for-endpoint-authorization")
@AutoConfigureMockMvc
class EndpointAuthorizationTest {

        /** Public on purpose: you can't hold a credential before you have registered or logged in. */
        private static final Set<String> PUBLIC_WRITES = Set.of(
                        "POST /api/v1/auth/register",
                        "POST /api/v1/auth/login",
                        "POST /api/v1/auth/refresh",
                        "POST /api/v1/auth/logout");

        private static final Set<RequestMethod> READS = Set.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS);

        @Autowired
        private MockMvc mvc;

        @Autowired
        @Qualifier("requestMappingHandlerMapping")
        private RequestMappingHandlerMapping mappings;

        record Endpoint(RequestMethod method, String pattern) {
                String key() {
                        return method + " " + pattern;
                }

                String concretePath() {
                        return pattern.replaceAll("\\{[^}]+}", "1");
                }
        }

        private List<Endpoint> writeEndpoints() {
                List<Endpoint> out = new ArrayList<>();
                mappings.getHandlerMethods().forEach((info, handler) -> {
                        for (String pattern : info.getPatternValues()) {
                                if (!pattern.startsWith("/api/")) {
                                        continue;
                                }
                                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                                        if (!READS.contains(m)) {
                                                out.add(new Endpoint(m, pattern));
                                        }
                                }
                        }
                });
                return out;
        }

        @Test
        void noStateChangingEndpointAcceptsAnAnonymousCaller() throws Exception {
                List<Endpoint> writes = writeEndpoints();
                assertThat(writes).as("the scan must find the write endpoints").hasSizeGreaterThanOrEqualTo(8);

                List<String> open = new ArrayList<>();
                for (Endpoint e : writes) {
                        if (PUBLIC_WRITES.contains(e.key())) {
                                continue;
                        }
                        int status = mvc.perform(request(HttpMethod.valueOf(e.method().name()), e.concretePath())
                                                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                        .andReturn().getResponse().getStatus();
                        // 401 (no key / no token) or 503 (admin API unconfigured) are both "refused before the
                        // controller". Anything else - 2xx, 400 validation, 404 lookup - means the body reached it.
                        if (status != 401 && status != 403 && status != 503) {
                                open.add(e.key() + " -> " + status);
                        }
                }
                assertThat(open).as("write endpoints reachable without a credential").isEmpty();
        }

        @Test
        void theAdminKeyUnlocksCatalogueWritesAndReadsStayPublic() throws Exception {
                // With the key the request reaches the controller: an empty body then fails validation (400).
                int withKey = mvc.perform(post("/api/v1/stations").header("X-Admin-Key", "test-admin-key-for-endpoint-authorization")
                                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
                assertThat(withKey).isEqualTo(400);

                int wrongKey = mvc.perform(post("/api/v1/stations").header("X-Admin-Key", "nope")
                                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
                assertThat(wrongKey).isEqualTo(401);

                assertThat(mvc.perform(get("/api/v1/trains")).andReturn().getResponse().getStatus())
                                .as("listing trains stays public").isEqualTo(200);
                assertThat(mvc.perform(get("/api/v1/stations")).andReturn().getResponse().getStatus())
                                .as("listing stations stays public").isEqualTo(200);
        }
}
