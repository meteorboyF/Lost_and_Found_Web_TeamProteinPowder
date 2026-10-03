# Getting started

A campus lost-and-found application for UIU: report an item, pinpoint its location,
verify ownership, arrange pickup and confirm the return.

## Tech stack

| Area | Technology |
| --- | --- |
| Backend | Java 21+, Spring Boot 4.1.1, Spring MVC, Bean Validation |
| Data | Spring Data JPA / Hibernate; MySQL 8 for normal deployments |
| Frontend | Static HTML and vanilla JavaScript, served by Spring Boot; no React or separate frontend server |
| Styling | Tailwind CSS v4; Node.js 22+ and npm for builds and seeding |
| Maps | Leaflet 1.9.4 with OpenStreetMap tiles, centered on UIU |
| Tests | Spring Boot / JUnit integration tests with H2; Playwright browser tests |

Pages and `/api/*` share one origin. Authentication uses server-side sessions;
passwords and ownership answers use salted PBKDF2 hashes. Python 3 is only needed
when regenerating shared HTML pages.

## Fastest local start — Windows

Install JDK 21+ and Node.js 22+, and set `JAVA_HOME` to your JDK installation.
From the repository root:

```powershell
cd frontend
npm ci
npm run build
cd ..
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-browser-test.ps1
```

Open http://localhost:18080. This starts the full application with an isolated
H2 database, so no MySQL or Docker installation is needed. Keep the terminal
open; Ctrl+C stops it. Records reset on restart. It does not touch the existing
application or database on port 8080.

In a second terminal, from the repository root, add pinned reports and claims:

```powershell
$env:SEED_BASE_URL = 'http://localhost:18080'
node scripts/add-map-reports.mjs
node scripts/add-claim-reports.mjs
```

Local logins: `student` / `student123`, `admin` / `admin123`, and, after claim
seeding, `finder` or `owner` / `campus123`. Use separate browser profiles for
different roles. These credentials must never be used on a public deployment.
The scripts skip existing reports rather than resetting claim progress.

For persistent development, use MySQL via `run.sh` on Linux/macOS, or configure
`DB_URL`, `DB_USERNAME` and `DB_PASSWORD` before `./mvnw.cmd spring-boot:run`.
The Docker alternative is `docker compose up --build -d`. See the
[README](README.md#running-it-locally) for database setup. A fresh normal
deployment needs `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`
(12+ characters); the administrator approves student registrations.

## What is already implemented

- Lost/found reports, public photos, protected private photos, browsing, matching,
  public clues, account dashboards and moderation.
- Category-based ownership questions, incorrect-answer lockouts and private
  participant-only chat. Questions are rule-based, not AI-generated.
- Interactive UIU map: click/drag report pins, search-radius circles, filtering,
  walking directions and optional device location.
- Private claimant evidence: older photos, redacted receipts, partial serial
  numbers or concealed details. The finder must review it before approval.
- Campus-desk review for valuable/disputed items, in-person ID-check attestation,
  fraud reports, item-wide handover freezes and private audit history.
- Pickup approval is separate from return: claims move
  `OPEN → APPROVED → ACCEPTED`; only both participants' handover confirmations
  mark the item `RESOLVED`. Approval alone leaves the item `PENDING`.

Useful pages: `/map.html`, `/report.html`, `/dashboard.html`, `/help.html`,
`/admin.html` and `/desk.html` (staff only).

## Where to work

- `src/main/java/com/teamproteinpowder/lostfound/`: `domain/` entities, `repo/`
  persistence, `service/` business rules/access checks, `web/` controllers/DTOs.
- `src/main/resources/static/`: HTML, page-specific JavaScript and map assets.
- `frontend/genpages.py`: shared page/header/footer generator; most pages are
  generated here. `login.html` is maintained separately.
- `frontend/src/input.css`: Tailwind source; compiled output is
  `src/main/resources/static/css/app.css` and is committed.
- `src/main/resources/application.properties`: database, sessions and storage.
- `src/test/` and `frontend/tests/`: backend and browser regression tests.

After editing generated page layouts, run `python frontend/genpages.py` from
the root, then `npm run build` from `frontend/`. Java changes require a server
restart; local source-tree HTML/JS/CSS changes appear on refresh. Do not edit
only generated HTML or compiled CSS: the next build will overwrite it.

## Verify changes

Backend, from the repository root (no external database needed):

```powershell
./mvnw.cmd test
```

Browser checks, with the port-18080 server above running:

```powershell
cd frontend
npx playwright install chromium
$env:CONTENT_BASE_URL = 'http://localhost:18080'
npm run test:content
npm run test:fraud
```

Run content checks on the clean server before the fraud suite: the latter adds
explicitly labeled test fixtures. Never run the fraud suite against the main
database. Current coverage includes 21 backend tests plus real-browser privacy,
staff review, handover, fraud-freeze and responsive-layout checks.

## Important boundaries

Enforce permissions in backend services, not just by hiding buttons. Never expose
private evidence, security answers or credentials publicly. ID checks are staff
attestations, not a connection to university records; evidence can be forged.
Audit history is append-only through the API, not tamper-proof against database
administrators. Email/push review notifications and automated fraud/image-forgery
detection are not implemented. Back up databases/uploads before migrations; existing enum
databases need the `APPROVED` migration in `docs/migrations/`.

More detail: [claim and collection guide](docs/CLAIM_AND_COLLECTION_GUIDE.md),
[project review](docs/PROJECT_REVIEW.md), [full README](README.md).
