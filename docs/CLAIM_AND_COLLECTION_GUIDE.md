# Claim and collection guide

Open http://localhost:8080/help.html for the user guide. Staff reviews are at
http://localhost:8080/desk.html.

## Local testing accounts

Use separate browser profiles for the finder, claimant and staff.
Tabs in the same profile share a login session.

| Role | Username | Password |
| --- | --- | --- |
| Finder / report poster | `finder` | `campus123` |
| Owner / claimant | `owner` | `campus123` |
| Staff | `admin` | `admin123` |

These credentials are for local testing only. Never enable them in production.
Account approval does not verify institutional email or connect to UIU records.

Run `node scripts/add-claim-reports.mjs` to add missing reports and claims to a
local server. It preserves existing progress. The initial reports are Spiral
notebook, Black smartphone and Navy backpack. Their security answer is
`DP 4821`. Use `node scripts/add-map-reports.mjs` for the six additional pinned
reports; their security answer is `blue star`. The scripts use `SEED_BASE_URL`
(default `http://localhost:8080`) and reject non-local hosts. Map seeding accepts
`SEED_USERNAME` and `SEED_PASSWORD`; claim seeding accepts
`SEED_ADMIN_USERNAME` and `SEED_ADMIN_PASSWORD`.

## Private evidence and collection

1. Open My items → My claims as the owner. Supply a concealed detail, older
   photo, redacted receipt or partial serial number. Images support
   JPEG/PNG/WebP/GIF up to 5 MB, not PDF. Never upload a full ID card or password.
2. As the finder, open Incoming claims and inspect the private evidence. Click
   “I reviewed the private evidence” before “Approve for pickup”. Updating the
   evidence invalidates an earlier review; image updates stop after approval.
3. Approval changes the claim to “Approved — awaiting pickup”. The item stays
   pending and the private chat remains available for pickup arrangements.
4. After the physical exchange, both participants independently confirm the
   handover. One person's repeated confirmation cannot replace the other.
   Only both confirmations mark the item returned and close other open claims.
5. Check the private history for evidence review, approval and both confirmation
   actors. Unrelated accounts cannot view the evidence or history. Approval
   alone does not reveal the poster's private identifying photo to a claimant.

## Campus-desk review

Electronics, jewellery and other items marked as valuable require staff review.
The Black smartphone claim starts with desk review pending.

1. The finder reviews evidence but cannot approve until staff clear the review.
2. Staff open Campus desk and inspect the evidence, conversation and history.
3. Staff record review notes and check the claimant's student ID in person.
   Clearance requires the ID-check attestation. Do not enter full ID numbers or
   ID photos in notes. Staff cannot review their own report or claim.
4. Staff clearance does not replace finder approval or both handover confirmations.

## Suspicious claims

The Navy backpack claim starts approved with pickup paused for a reported concern.

1. Neither participant can complete handover while a dispute is unresolved.
   The restriction is enforced by the server, not only the interface.
2. Staff investigate the report and history, then reject the claim with notes or
   clear it after reviewing evidence and checking the claimant's ID in person.
3. Rejection does not mark the item returned. Clearance preserves pickup approval,
   but both participants must submit fresh handover confirmations.
4. The freeze covers all claims on the item. Every unresolved dispute must be
   cleared; withdrawal or decline cannot erase a concern or its history.

Only participants can flag a claim. Staff check the queue for reviews; there are
no email or push notifications.

## Automated checks

Run `./mvnw.cmd test` with Java 21+ from the repository root. The 21 H2
integration tests cover privacy, chat, map data, private evidence, staff review,
fraud freezes, audit access, two-party handover and concurrent requests.

Start an isolated test server in a separate terminal:

```powershell
./scripts/start-browser-test.ps1
```

Then run:

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run test:fraud
```

The browser suite uses port 18080, separate sessions and generated test evidence.
It creates reports, so run it against the isolated server, not the main application.
Screenshots are under `target/`. Stop that test server with Ctrl+C afterwards.
Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` when using an already installed Chromium.

For read-only interface and wording checks on the main application:

```powershell
cd frontend
npm run test:content
```

## Existing database upgrade

Back up the database before applying the matching `APPROVED` enum migration in
`docs/migrations/`. H2 schema update does not extend native enums automatically.
Existing completed claims retain their historical state; do not invent handover
confirmations. Production should use reviewed, explicit migrations.

## Security boundaries

Ownership evidence can be forged and human reviews can be mistaken. Student-ID
checks are staff attestations, not automated verification. Questions are
category-based, not AI fraud detection. Audit history is append-only through the
application, not tamper-proof against database administrators. Notifications,
image-forgery detection, antivirus scanning and general fraud-report rate
limiting are not implemented. Private evidence requires durable, access-controlled
storage, backups and a retention policy.
