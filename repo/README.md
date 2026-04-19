# LexiBridge Operations Suite

**Project type: fullstack web application** (Spring Boot API + Thymeleaf server-rendered portal + browser-side JavaScript).

Operations platform for content, moderation, bookings, leave workflows, payments, and admin controls.

## Quick Start (Docker only)

All runtime and test workflows run inside Docker. Do not install Java, Maven, Node, MySQL, or Playwright on the host - they are supplied by container images.

Bring the stack up:

```bash
docker-compose up --build
```

Alias spelling also accepted:

```bash
docker compose up --build
```

Open:

- App: `http://localhost:8081`
- Login: `http://localhost:8081/login`

Bring the stack down:

```bash
docker compose down
```

Full reset (containers + DB volume + local image):

```bash
docker compose down -v --remove-orphans --rmi local
```

## Demo Credentials (local Docker only)

Auth is strictly role-scoped. The bootstrap service seeds one account for every enforced role on first boot. All accounts below are for local/demo use only and must be rotated in any non-local environment.

| Role            | Username          | Credential                            |
|-----------------|-------------------|---------------------------------------|
| ADMIN           | `admin`           | password `AdminPass2026!`             |
| CONTENT_EDITOR  | `content_editor`  | password `ContentPass2026!`           |
| MODERATOR       | `moderator`       | password `ModeratorPass2026!`         |
| FRONT_DESK      | `front_desk`      | password `FrontDeskPass2026!`         |
| EMPLOYEE        | `employee`        | password `EmployeePass2026!`          |
| MANAGER         | `manager`         | password `ManagerPass2026!`           |
| HR_APPROVER     | `hr_approver`     | password `HrApproverPass2026!`        |
| SUPERVISOR      | `supervisor`      | password `SupervisorPass2026!`        |
| DEVICE_SERVICE  | `demo-device`     | HMAC shared secret `local-dev-shared-secret-2026` (header `X-Client-Key: demo-device`, key version `1`) |

DEVICE_SERVICE authenticates via HMAC, not form login. The `X-Client-Key`, `X-Key-Version`, `X-Timestamp`, `X-Nonce`, and `X-Signature` headers must accompany every signed request. The shared secret above matches the default `BOOTSTRAP_DEVICE_SHARED_SECRET` in `docker-compose.yml`.

## Architecture at a glance

- Spring Boot (`Java 21`) + Thymeleaf portal + MySQL 8
- Flyway-managed schema and policy controls
- Module domains: content, moderation, booking/attendance, leave, payments, admin

## Security controls implemented

- Location and actor scope checks across portal and API endpoints
- Device HMAC auth with admin key inventory/rotation/cutover workflows
- PII encryption at rest and masked list/review display patterns
- DB-level immutability triggers for audit, booking transition, and reconciliation records
- 7-year retention enforcement at DB level for audit and reconciliation tables, with retention-hold override support
- Moderation enforcement: users with active suspension cannot create post/comment/Q&A targets
- Attendance scan enforcement: decoded booking ID is scope-checked before scan persistence

## Verification (after `docker-compose up`)

Run these from any shell on the host. The app port is `8081`.

1. Health check (expect HTTP 200 and `{"status":"UP"...}`):

   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/actuator/health
   ```

2. Login page loads (expect HTTP 200):

   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/login
   ```

3. Unauthenticated admin API is blocked (expect HTTP 401):

   ```bash
   curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/v1/admin/status
   ```

4. Admin login succeeds and lands on `/portal` (expect HTTP 200 after login redirect):

   Open `http://localhost:8081/login`, log in as `admin` / `AdminPass2026!`, and confirm the portal home renders.

5. Role-scoped portal access: log in as `front_desk` / `FrontDeskPass2026!` and visit `http://localhost:8081/portal/bookings` (expect 200). Visiting `/portal/admin` with the same user should return 403.

Any other expected-response pair is documented inline in `docs/api-spec.md`.

## Run Tests (Docker)

```bash
./run_test.sh
```

This script:

- starts `docker compose` in the background
- runs all Maven tests in a Dockerized Maven container (includes the true no-mock HTTP endpoint coverage suite that exercises every controller route)
- runs the frontend unit test suite (Vitest) in a Dockerized Node container
- runs the Playwright end-to-end suite inside the official Playwright container
- stops the compose stack after tests

Keep the stack running after tests:

```bash
KEEP_STACK_UP=true ./run_test.sh
```

Run only a single test phase:

```bash
PHASES=maven ./run_test.sh            # backend only
PHASES=vitest ./run_test.sh           # frontend unit only
PHASES=playwright ./run_test.sh       # e2e only
PHASES=maven,vitest ./run_test.sh     # multiple phases
```

## Troubleshooting

- Invalid credentials repeated: verify the password exactly (`AdminPass2026!`, no trailing `#`).
- Temporary lockout after failed attempts: run the full reset command above, or wait for lockout expiry.
- UI changes not visible: rebuild the app image and hard-refresh the browser.
  - macOS: `Cmd+Shift+R`
  - Windows/Linux: `Ctrl+Shift+R` (or `Ctrl+F5`)
- Port 8081 in use: set `APP_PORT` in the environment before `docker compose up`.

## Docs

- Design: `docs/design.md`
- API overview: `docs/api-spec.md`
