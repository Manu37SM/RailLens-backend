package com.labs.train.train_db.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.labs.train.train_db.audit.SecurityAuditService;
import com.labs.train.train_db.audit.SecurityAuditService.Entry;
import com.labs.train.train_db.audit.SecurityAuditService.Verification;
import com.labs.train.train_db.exception.AuthFlowException;

import lombok.RequiredArgsConstructor;

/**
 * Phase 6: read and check the security audit chain. Admin-only (/api/v1/admin/**) and, like account moves, refused
 * to the legacy shared key: reading who signed in from where needs an admin who signed in with a one-time code.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class SecurityAuditController {

        private final SecurityAuditService audit;

        @GetMapping
        public ResponseEntity<List<Entry>> page(@RequestParam(defaultValue = "0") long afterId,
                        @RequestParam(defaultValue = "100") int limit, Authentication auth) {
                requireMfaAdmin(auth);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.page(afterId, limit));
        }

        /** 200 either way; "intact": false and "firstBrokenId" say where the chain was changed. */
        @GetMapping("/verify")
        public ResponseEntity<Verification> verify(Authentication auth) {
                requireMfaAdmin(auth);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.verify());
        }

        private static void requireMfaAdmin(Authentication auth) {
                if (auth == null || RailOpsAccountMoveController.LEGACY_KEY_PRINCIPAL.equals(auth.getName())) {
                        throw new AuthFlowException(HttpStatus.FORBIDDEN, "MFA_ADMIN_REQUIRED",
                                        "The audit log needs an admin signed in with a one-time code, not the shared admin key");
                }
        }
}
