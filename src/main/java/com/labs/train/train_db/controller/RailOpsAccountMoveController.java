package com.labs.train.train_db.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.exception.AuthFlowException;
import com.labs.train.train_db.service.RailOpsAccountMoveService;
import com.labs.train.train_db.service.RailOpsAccountMoveService.AccountToMove;
import com.labs.train.train_db.service.RailOpsAccountMoveService.Link;
import com.labs.train.train_db.service.RailOpsAccountMoveService.LinkResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Called by RailOps' identity service while moving accounts (Phase 4C), with the RailOps admin's own token.
 * Admin-only by SecurityConfig (/api/v1/admin/**). Because the export carries password hashes, the legacy shared
 * X-Admin-Key (no MFA) is refused here: only an admin who signed in with a one-time code gets through.
 */
@RestController
@RequestMapping("/api/v1/admin/railops")
@RequiredArgsConstructor
public class RailOpsAccountMoveController {

        static final String LEGACY_KEY_PRINCIPAL = "admin-api-key";

        public record LinksRequest(@NotNull @Size(max = RailOpsAccountMoveService.MAX_PAGE) List<@Valid @NotNull Link> links) {
        }

        private final RailOpsAccountMoveService moves;

        @GetMapping("/accounts")
        public ResponseEntity<List<AccountToMove>> pending(@RequestParam(defaultValue = "0") long afterId,
                        @RequestParam(defaultValue = "200") int limit, Authentication auth) {
                requireMfaAdmin(auth);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moves.pending(afterId, limit));
        }

        @PostMapping("/links")
        public LinkResult link(@Valid @RequestBody LinksRequest req, Authentication auth) {
                requireMfaAdmin(auth);
                return moves.link(req.links());
        }

        private static void requireMfaAdmin(Authentication auth) {
                if (auth == null || LEGACY_KEY_PRINCIPAL.equals(auth.getName())) {
                        throw new AuthFlowException(HttpStatus.FORBIDDEN, "MFA_ADMIN_REQUIRED",
                                        "Account moves need an admin signed in with a one-time code, not the shared admin key");
                }
        }
}
