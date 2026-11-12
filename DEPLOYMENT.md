# How to deploy RailLens and RailOps (all repos)

**Last updated:** 12 Nov 2026, after Phase 3A (RailOps staff app restyled to look like RailLens).

This guide is the same file in every repo: `train-db`, `train-db-frontend`, `train-db-mobile` and `railops`. Open whichever one you have. It is updated after every phase. The newest version is always the one in `train-db`.

It is written for the day you decide to go live. Until then you can keep merging pull requests, **as long as you do Section 3 first**. Otherwise a merge *is* a deploy.

---

## 1. What runs where

| Piece | Repo | Runs on | Address |
|---|---|---|---|
| RailLens backend (the API) | `train-db` | **Render** web service `raillens-train-db` (free plan) | `https://raillens-train-db.onrender.com` |
| RailLens database | (none) | **Neon** (PostgreSQL) | in your Neon dashboard |
| RailLens website | `train-db-frontend` | **Vercel** | `https://raillens.vercel.app` |
| RailLens Android app | `train-db-mobile` | People's phones (you share an `.apk` file) | (none) |
| RailOps (booking system) | `railops` | Render + Neon + Confluent Cloud | not live yet, see Section 9 |

The website and the app both talk to the backend. **The backend always goes live first.**

---

## 2. Release tracker: what's merged, what's live

Update this table whenever you merge or deploy. (I'll update it at the end of every phase.)

| Change | Repo | Merged to main/master? | Live? | Needs before going live |
|---|---|---|---|---|
| Phase 0: IP detection behind Render, admin key needed for catalogue writes, HSTS | `train-db` | ✅ yes | ❓ check (see below) | env var `RAILLENS_CLIENT_IP_TRUSTED_HEADER` (Section 6) |
| Phase 1: Spring Security "deny by default", safer refresh tokens (DB migration **V6**) | `train-db` | ✅ yes | ❓ check | the database check in Section 5 |
| Phase 2A: admin accounts + one-time codes (DB migration **V7**) | `train-db` | ⏳ branch `security/phase2a-admin-mfa` | ❌ no | 2 new env vars (Section 6) |
| Phase 2A: admin sign-in screens | `train-db-frontend` | ⏳ branch `security/phase2a-admin-mfa` | ❌ no | backend Phase 2A live first |
| Phase 2A: admin sign-in screens | `train-db-mobile` | ⏳ branch `security/phase2a-admin-mfa` | ❌ no | backend Phase 2A live first, then build a new APK |
| Phase 2B: backend trusts the visitor IP sent by the website's server | `train-db` | ⏳ branch `security/phase2b-web-cookie` (built on 2A: **merge 2A first**) | ❌ no | env var `RAILLENS_CLIENT_IP_PROXY_SECRET` (Section 6) |
| Phase 2B: website keeps the refresh token in a secure cookie | `train-db-frontend` | ⏳ branch `security/phase2b-web-cookie` (built on 2A: **merge 2A first**) | ❌ no | backend 2B live first; env var `RAILLENS_PROXY_SECRET` (Section 7) |
| RailOps (whole system) | `railops` | n/a (no git yet) | ❌ never deployed | follow `railops/deploy/render/README.md` |
| Phase 3A: RailOps web app restyled like RailLens (colours, Geist font, light/dark theme) | `railops` (`railops-web/`) | n/a (in the zip) | ❌ never deployed | nothing extra: it ships with the normal RailOps web deploy |

**Merging the branches:** each 2B branch contains the 2A commits too. Merge `security/phase2a-admin-mfa` first, then `security/phase2b-web-cookie`, in each repo. The mobile app has no 2B changes.

**How to see what's live right now**
- **Backend:** Render → `raillens-train-db` → **Events**. The newest "Deploy live" line shows the commit. Compare it with `git log` in `train-db`.
- **Website:** Vercel → your project → **Deployments**. The one marked *Production* shows its commit.
- **App:** whatever APK you last shared.

---

## 3. Stop merges from going live (do this before merging anything)

By default, both services publish every commit automatically:
- **Render** redeploys the backend every time `main` changes.
- **Vercel** publishes the website every time `master` changes.

To merge now and deploy later:

**Render (backend)**
1. Open `raillens-train-db` → **Settings** → **Build & Deploy** → **Auto-Deploy**.
2. Set it to **Off** (it may be labelled **No**). Save.
3. When you want to go live: **Manual Deploy** → **Deploy latest commit**.

**Vercel (website)**
1. Open the project → **Settings** → **Git** (or **Build and Deployment**) → **Ignored Build Step**.
2. Choose **Run my Bash script / custom command** and enter `exit 0`. Save. (`exit 0` means "skip this build".)
3. When you want to go live, clear that setting. Then **Deployments** → newest production commit → **⋯** → **Redeploy**.

**Android app:** nothing to do. A merge never reaches anyone's phone until you build and share a new APK.

> Render's free plan sleeps after 15 minutes without visitors. The first request after that takes up to about a minute. That is normal, not a bug.

---

## 4. Golden rules

1. **Order: backend → website → app.** The new website and app need the new backend. The old website and app keep working with the new backend.
2. **Database migrations only go forward.** The backend upgrades the database by itself when it starts (Flyway, files `V1`…`V7` in `train-db/src/main/resources/db/migration`). Rolling back the code does *not* undo them. V6 and V7 only *add* things, so older code still runs on top of them.
3. **Never put secrets in git.** Secrets go in Render or Vercel's *Environment* settings only. Keep a copy of every secret in your password manager.
4. **Some keys can't be lost.**
   - `RAILLENS_MFA_ENCRYPTION_KEY`: if it's lost or changed, every admin must set up their authenticator app again.
   - `release.keystore` (Android signing key): if it's lost, phones won't accept updates to the app. Users would have to uninstall and reinstall.
5. **Deploy one thing at a time** and run the smoke test (Section 10) before the next.

---

## 5. One-time database check (before the first deploy of Phase 1 or later)

Why this matters: the backend's local settings file (`application.properties`) is **not in git**, so the version Render builds runs without it. In particular, it does **not** have `spring.flyway.baseline-on-migrate`. That is fine *if* the live database already has Flyway's history table, but the backend won't start if it doesn't.

Open **Neon → your project → SQL Editor** and run:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

- **You see a list ending at `5` (or `6`), all `success = true`:** good. The new migrations will run by themselves on deploy.
- **"relation flyway_schema_history does not exist":** **stop.** Don't deploy yet. Send me the result. The database predates Flyway and needs a one-off adoption step first.
- **Any row with `success = false`:** stop and send me the result.

If the list does **not** include `2`, also run this. Migration V2 adds "no empty values" and "no duplicate stops" rules to the timetable, and it will fail if bad rows exist:

```sql
-- both should return 0 rows / 0
SELECT count(*) FROM train_schedule
WHERE train_id IS NULL OR station_id IS NULL OR sequence_no IS NULL;

SELECT train_id, sequence_no, count(*) FROM train_schedule
GROUP BY train_id, sequence_no HAVING count(*) > 1 LIMIT 20;
```

If a migration fails, nothing is half-done: the database rolls that migration back, and Render keeps the previous version running because the health check fails.

---

## 6. Deploy the backend (`train-db`)

### 6.1 Environment variables on Render

Render → `raillens-train-db` → **Environment**. `render.yaml` lists them too, but Render only reads that file if the service was created as a *Blueprint*. If you created the service by hand, add them here yourself.

| Variable | What to put | Secret? | Since |
|---|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | from Neon's connection details (use the **direct** host, not `-pooler`) | no | before |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | from Neon | **yes** | before |
| `SPRING_JPA_OPEN_IN_VIEW` | `false` | no | before |
| `RAILLENS_JWT_SECRET` | 32+ random characters (`openssl rand -base64 32`). Changing it logs everyone out | **yes** | before |
| `RAILLENS_CORS_ALLOWED_ORIGINS` | `https://raillens.vercel.app` (comma-separate more sites) | no | before |
| `RAILLENS_ADMIN_API_KEY` | the old shared admin key (being retired) | **yes** | before |
| `RAILLENS_CLIENT_IP_TRUSTED_HEADER` | `True-Client-IP` | no | **Phase 0** |
| `RAILLENS_MFA_ENCRYPTION_KEY` | `openssl rand -base64 32`. **Save a copy now** | **yes** | **Phase 2A** |
| `RAILLENS_ADMIN_BOOTSTRAP_USERNAMES` | your own RailLens username (it must already be registered) | no | **Phase 2A** |
| `RAILLENS_ADMIN_LEGACY_KEY_ENABLED` | leave unset (means `true`). Set `false` only in Section 11 | no | **Phase 2A** |
| `RAILLENS_CLIENT_IP_PROXY_SECRET` | `openssl rand -base64 32`. **The same value** goes into Vercel as `RAILLENS_PROXY_SECRET` (Section 7) | **yes** | **Phase 2B** |

`openssl` comes with **Git Bash** on Windows.

### 6.2 Go live

1. Do Section 5 once.
2. Merge the backend branch into `main`.
3. Set any new environment variables (6.1). Saving them may restart the service; that's fine.
4. Render → **Manual Deploy** → **Deploy latest commit**.
5. Watch **Logs**. You want `Successfully applied … migration(s)` (or `Schema … is up to date`), then `Started TrainDbApplication`.
6. Run the backend part of Section 10.

### 6.3 After Phase 2A is live: become an admin

1. Make sure your username is in `RAILLENS_ADMIN_BOOTSTRAP_USERNAMES`. If you added it *after* the deploy, restart the service once (**Manual Deploy → Restart service**). The log says `Promoted <name> to ADMIN`.
2. Install an authenticator app on your phone (Google Authenticator, Microsoft Authenticator, Authy or 1Password).
3. Once the new website is live (Section 7), log in. It will ask you to set up the app: add the key it shows, then type the 6-digit code.
4. From then on, admin login is password **plus** the code from the app.

Do step 3 right after step 1. Until you finish it, anyone who knows your password could set up *their* phone for your admin account.

---

## 7. Deploy the website (`train-db-frontend`)

Only after the backend is live (Section 6).

1. Vercel → project → **Settings → Environment Variables** (Production):
   - `NEXT_PUBLIC_API_BASE_URL` = `https://raillens-train-db.onrender.com/api/v1`
   - `NEXT_PUBLIC_SITE_URL` = `https://raillens.vercel.app`
   - `RAILLENS_PROXY_SECRET` = **exactly** the value of `RAILLENS_CLIENT_IP_PROXY_SECRET` on Render (Phase 2B). Mark it *Sensitive*. Never give it a `NEXT_PUBLIC_` name: that would publish it in the website's JavaScript.

   Variables are read when the site is built. **After changing one, redeploy.**

   **What 2B changes (in plain words):** the website now signs people in through its own small server routes (`/api/auth/...`). Your long-lived sign-in token is kept in a cookie that page scripts can't read, instead of in the browser's `localStorage`. Vercel runs those routes for free as part of the same project; there's nothing extra to create.
2. Merge the branch into `master`.
3. Deploy: undo the Ignored Build Step from Section 3, or redeploy the newest commit.
4. Run the website part of Section 10.

---

## 8. Release the Android app (`train-db-mobile`)

Only after the backend is live. "Deploying" the app means building a new APK and sharing it.

1. Check `src/config/env.ts`: `LIVE_BASE_URL` must be `https://raillens-train-db.onrender.com/api/v1`, and `USE_LOCAL_BACKEND` must be `false`.
2. In `android/app/build.gradle`, raise `versionCode` by 1 (for example `1` → `2`) and set `versionName` (for example `"1.1"`). Phones refuse an "update" with the same `versionCode`.
3. Make sure `android/keystore.properties` and your `release.keystore` exist on this computer. They are not in git, so keep backups. Without them the APK is signed with the public debug key, and antivirus apps flag it.
4. Build it in PowerShell:
   ```powershell
   cd android
   .\gradlew.bat assembleRelease
   ```
5. The file is `android/app/build/outputs/apk/release/app-release.apk`. Share it via Drive, email, etc. People installing it allow "install unknown apps" once.
6. Install it on your own phone and run the app part of Section 10.

---

## 9. RailOps

**Follow RailOps' own guide: `railops/deploy/render/README.md`.** It covers everything step by step: Neon, Confluent Cloud (Kafka), Render Key Value (Redis), the five services, the Angular staff app, costs and troubleshooting.

Things to know first:
- RailOps payments are **simulated**. Nobody can be charged real money. Keep it that way until a real payment provider is built and reviewed.
- RailOps is separate from RailLens for now. Logging in to one does not log you in to the other until the "single login" phase is done. When it is, this guide will gain a section on the order to deploy both.
- RailOps uses its **own** Neon project. Don't point it at the RailLens database.
- Since Phase 3A the RailOps web app looks like RailLens (same colours, font and light/dark switch). There are no new settings to make.
- **The `railops` folder has no git history yet.** Before you start merging RailOps changes, make it a repo: `cd railops`, `git init`, `git add .`, `git commit -m "RailOps baseline"`. Check first that `deploy/render/render.env`, `deploy/render/out/` and `deploy/secrets/` are listed in `.gitignore` (they are), so secrets never get committed.

---

## 10. Smoke test after every deploy (5 minutes)

**Backend**
- [ ] `https://raillens-train-db.onrender.com/actuator/health` shows `{"status":"UP"}`
- [ ] `https://raillens-train-db.onrender.com/api/v1/stats` returns numbers, not an error
- [ ] Render logs show no `ERROR` lines since the deploy

**Website**
- [ ] Home page loads; searching for a train shows results
- [ ] Register a throwaway account, log out, log back in
- [ ] (Phase 2A+) Admin: log in with password + code → you land on `/admin` and the stats load
- [ ] (Phase 2A+) Log in as a normal user and open `/admin`: it must say you don't have an admin session
- [ ] (Phase 2B+) Log in, then **reload the page**: you're still signed in
- [ ] (Phase 2B+) Browser DevTools → **Application**:
  - **Local Storage** → `raillens-auth` shows only username, email and role, no token
  - **Cookies** → `__Secure-raillens_rt` is ticked **HttpOnly** and **Secure**
- [ ] (Phase 2B+) Render logs must **not** show `Ignoring X-RailLens-Client-IP`

**Android app**
- [ ] Opens; train search works
- [ ] Sign in / sign out works
- [ ] (Phase 2A+) Admin sign-in asks for the code; the Admin screen loads stats

---

## 11. Later: turn off the old shared admin key

Only when **all** of these are true: the Phase 2A website is live, every admin has signed in with a code, and nobody uses an old APK for admin work.

1. Render logs: search for `Legacy X-Admin-Key used`. It should not have appeared for a while.
2. Set `RAILLENS_ADMIN_LEGACY_KEY_ENABLED` = `false`. Render restarts.
3. You can then delete `RAILLENS_ADMIN_API_KEY`.

---

## 12. If something breaks

| Problem | What to do |
|---|---|
| Backend deploy failed | Render keeps the old version running. Read **Logs** for the first `ERROR`. A failed migration names the file (e.g. `V7__…`). Send me that line. |
| Backend is live but broken | Render → **Events** → previous "Deploy live" → **Rollback**. The database changes stay, which is safe for V6/V7. Afterwards, check that **Auto-Deploy** is still **Off**, so the broken commit isn't redeployed. |
| Website broken | Vercel → **Deployments** → previous production deployment → **⋯ → Promote / Instant Rollback**. |
| (2B) Logged out after every page reload | The cookie isn't sticking. The site must be on `https://`. Check Vercel → **Logs** for `/api/auth/refresh` errors. |
| (2B) Everyone gets "Too many requests" when logging in | The two proxy secrets differ or one is missing. Render's log says `Ignoring X-RailLens-Client-IP`. Make `RAILLENS_PROXY_SECRET` (Vercel) and `RAILLENS_CLIENT_IP_PROXY_SECRET` (Render) identical, then redeploy both. |
| (2B) Website rolled back to a pre-2B version | Safe. People who signed in after 2B look signed out (the old site can't use the cookie) and just log in again. |
| Website says "Unable to reach the server" | The backend is asleep (wait a minute) or down, or `NEXT_PUBLIC_API_BASE_URL` is wrong. Check Section 7, then redeploy. |
| Browser console shows a CORS error | `RAILLENS_CORS_ALLOWED_ORIGINS` doesn't exactly match the website address (`https://…`, no trailing `/`). |
| "Admin sign-in is not configured on this server" | `RAILLENS_MFA_ENCRYPTION_KEY` is missing on Render. |
| Admin lost their phone | Ask me. It's a one-line database change (`README.md` → Authentication → "Lost phone"), done only with your OK. |
| App update won't install | `versionCode` wasn't raised, or it was signed with a different key than last time. |

---

## 13. Changes to this guide

| Date | What changed |
|---|---|
| 12 Nov 2026 | First version: covers Phases 0, 1 and 2A, holding merges back from going live, the database check, and the Phase 2A admin setup. |
| 12 Nov 2026 | Phase 2B: two matching proxy secrets (Render + Vercel), merge order 2A → 2B, cookie checks in the smoke test, three new troubleshooting rows. |
| 12 Nov 2026 | Phase 3A: RailOps web restyle in the tracker (no new settings); note to put `railops` under git before merging. |
