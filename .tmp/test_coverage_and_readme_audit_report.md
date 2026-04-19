# Test Coverage Audit

## Project Type Detection
- README explicitly declares: **fullstack web application**.
- Evidence: `repo/README.md:3`.

## Backend Endpoint Inventory
- Static inventory source: `repo/src/main/java/**/**Controller.java`.
- Unique controller endpoints (`METHOD + PATH`): **146**.

## API Test Mapping Table
- Primary endpoint sweep artifact: `repo/src/test/java/com/lexibridge/operations/integration/FullEndpointCoverageTest.java`.
- Evidence of real no-mock HTTP path:
  - Activated by env-gate: `@EnabledIfEnvironmentVariable(APP_BASE_URL)` (`FullEndpointCoverageTest.java:56`).
  - Real transport calls with `HttpURLConnection` helpers:
    - `getWithSession(...)` (`:500`)
    - `postJson(...)` (`:514`)
    - `postForm(...)` (`:532`)
    - `multipart(...)` (`:561`)
  - Real HMAC callback path:
    - `deviceHmac_POST_paymentsCallback_withRealHmacSignature` (`:425-449`)
- Mocked HTTP tests still present (secondary):
  - `repo/src/test/java/com/lexibridge/operations/web/ApiAuthorizationWebMvcTest.java`
  - `repo/src/test/java/com/lexibridge/operations/web/PortalSecurityWebMvcTest.java`
  - `repo/src/test/java/com/lexibridge/operations/web/ActuatorSecurityIntegrationTest.java`

## API Test Classification
1. True No-Mock HTTP
- `FullEndpointCoverageTest` (real HTTP requests against running app, no DI overrides).
- `TracePersistenceIntegrationTest` (`TestRestTemplate` random-port HTTP).
- `tests/e2e/major-flows.spec.ts` (browser-driven HTTP navigation).

2. HTTP with Mocking
- WebMvc tests using `@WebMvcTest` + `@MockBean`.

3. Non-HTTP
- Unit/integration tests invoking services/controllers directly.

## Mock Detection Rules Findings
- `@MockBean` detected:
  - `ApiAuthorizationWebMvcTest.java:73-103`
  - `PortalSecurityWebMvcTest.java:73-101`
- Direct controller call bypassing HTTP:
  - `modules/content/web/ContentControllerTest.java`

## Coverage Summary
- Total endpoints: **146**
- Endpoints with HTTP test evidence: **146** (route-group evidence via `FullEndpointCoverageTest`)
- Endpoints with true no-mock HTTP evidence: **146** (request-level evidence)
- HTTP coverage: **100%**
- True API coverage: **100%** (request-level)

## API Observability Check
- Improved from earlier revisions:
  - full-route suite now requires `status >= 200` and `< 500`.
  - Evidence: `FullEndpointCoverageTest.java:472-473`.
- Remaining strict caveat:
  - assertions are largely reachability-oriented, not endpoint-specific response contract checks.
  - strict handler correctness remains partially unproven for some routes.

## Unit Test Analysis
### Backend Unit Tests
- Present across service/security/privacy/governance/scheduler modules.

### Frontend Unit Tests (STRICT)
- Frontend unit tests: **PRESENT**
- Files:
  - `repo/tests/unit/content-preview.spec.js`
  - `repo/tests/unit/portal-validation.spec.js`
  - `repo/tests/unit/portal-format.spec.js`
- Framework/tooling:
  - Vitest + jsdom (`repo/vitest.config.js`, `repo/package.json`)
- Direct frontend module imports:
  - `src/main/resources/static/js/content-preview.js`
  - `src/main/resources/static/js/portal-validation.js`
  - `src/main/resources/static/js/portal-format.js`
- Important frontend modules still not deeply unit-tested:
  - broader page/template behavior in `repo/src/main/resources/templates/portal/*.html`.

### Cross-Layer Observation
- Balance improved significantly: backend + frontend unit + E2E layers now all present.
- Still utility-heavy on frontend unit side; page-flow contract assertions remain limited.

## Tests Check
- `repo/run_test.sh` executes Dockerized phases: Maven, Vitest, Playwright.
- Phase selection supported via `PHASES=...`.
- Minor consistency issue:
  - comment mentions `FullEndpointCoverageIT` while class is `FullEndpointCoverageTest`.
  - Evidence: `repo/run_test.sh:8`, `repo/src/test/java/com/lexibridge/operations/integration/FullEndpointCoverageTest.java`.

## Test Quality & Sufficiency
- Success-path breadth: high.
- Negative/authorization-path specificity: moderate.
- Edge/validation depth: moderate-to-strong (especially unit layer).
- Over-mocking risk: reduced by true no-mock full-route suite, but still present in WebMvc tests.

## Test Coverage Score (0-100)
- **91/100**

## Score Rationale
- Strong positives:
  - end-to-end no-mock HTTP route sweep
  - Dockerized deterministic test orchestration
  - multiple frontend unit suites with real module imports
- Deductions preventing 90+:
  - full-route suite mostly checks status bands rather than per-endpoint contract assertions
  - limited explicit response payload assertions across critical API routes

## Key Gaps
- Upgrade `FullEndpointCoverageTest` to endpoint-specific expected status/body assertions.
- Add response schema assertions for critical API commands.
- Expand frontend tests from utility functions to page/workflow integration behavior.

## Confidence & Assumptions
- Confidence: **high** on static evidence of coverage architecture.
- Confidence: **medium-high** on strict handler/business correctness due generic assertion style.

# README Audit

## High Priority Issues
- None.

## Medium Priority Issues
- README claim “exercises every controller route” is broadly supported, but strict sufficiency is still limited by generalized reachability assertions.
  - Evidence: `repo/README.md:113`, `FullEndpointCoverageTest.java:472-473`.

## Low Priority Issues
- Comment/class naming mismatch (`IT` vs `Test`) in test runner comments.

## Hard Gate Failures
- PASS: project type declared at top.
- PASS: required startup instruction includes `docker-compose up`.
- PASS: access method includes URL + port.
- PASS: verification method present (curl + UI flow checks).
- PASS: Docker-contained environment rules documented.
- PASS: demo credentials include all enforced roles, including `DEVICE_SERVICE` HMAC secret.

## README Verdict (PASS / PARTIAL PASS / FAIL)
- **PASS**
