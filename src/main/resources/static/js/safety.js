/** Participant anti-fraud controls; all permissions and state transitions are enforced by the server. */
(function () {
  'use strict';
  var LF = window.LF, e = LF.escapeHtml;
  var safety = { busy: false };
  safety.multipart = function (url, body) {
    return fetch(url, { method: 'POST', body: body }).then(function (response) {
      return response.json().then(function (data) { if (!response.ok) throw data; return data; });
    });
  };
  safety.evidence = function (claim) {
    return '<section class="card p-4"><h2 class="text-lg text-heading">Private ownership evidence</h2>' +
      '<p class="mt-2 whitespace-pre-line text-sm text-body" data-evidence-description>' + e(claim.safety.proof) + '</p>' +
      (claim.safety.hasEvidencePhoto ? '<a target="_blank" rel="noopener" href="/api/claims/' + encodeURIComponent(claim.reference) + '/evidence">' +
      '<img class="mt-3 w-full rounded-xl" style="max-height:240px;object-fit:contain" alt="Private ownership evidence" src="/api/claims/' + encodeURIComponent(claim.reference) + '/evidence"></a>' : '<p class="mt-2 text-xs text-muted">No image attached. The private description is still evidence for human review.</p>') +
      '<p class="mt-3 text-xs text-muted">Only the claimant, poster and authorized staff can view this. Evidence may be forged: compare concealed details, not just the public photo. Never upload passwords or a full student-ID image.</p></section>';
  };
  safety.mount = function (aside, claim, render) {
    var data = claim.safety, active = claim.status === 'OPEN' || claim.status === 'APPROVED';
    aside.insertAdjacentHTML('afterbegin', safety.evidence(claim));
    var section = document.createElement('section'); section.className = 'card p-4';
    var confirmed = claim.viewerRole === 'POSTER' ? data.posterHandoverAt : data.claimantHandoverAt;
    section.innerHTML = '<h2 class="text-lg text-heading">Safe return checklist</h2>' +
      '<p class="mt-3 text-sm ' + (data.handoverFrozen ? 'text-lost' : 'text-muted') + '" data-handover-state>' +
      (data.handoverFrozen ? 'Handover frozen — campus staff must resolve all disputes for this item.' : 'No active handover freeze.') + '</p>' +
      '<p class="mt-2 text-sm text-muted">Campus desk: <strong>' + e(data.deskReviewStatus) + '</strong>' +
      (data.studentIdCheckedAt ? ' · Staff recorded an in-person student-ID check.' : '') + '</p>' +
      (claim.status === 'APPROVED' ? '<div class="mt-4 rounded-xl border border-line p-3"><p class="text-sm font-semibold text-heading">Approved — awaiting pickup</p>' +
      '<p class="mt-2 text-xs text-muted">Poster: ' + (data.posterHandoverAt ? 'confirmed' : 'waiting') + ' · Claimant: ' + (data.claimantHandoverAt ? 'confirmed' : 'waiting') + '</p>' +
      '<p class="mt-2 text-xs text-muted">Confirm only after the physical item has changed hands. Approval alone is not a return.</p>' +
      '<button class="btn btn-primary mt-3 w-full" type="button" data-handover ' + (confirmed || data.handoverFrozen || data.deskReviewStatus === 'PENDING' ? 'disabled' : '') + '>' +
      (confirmed ? 'Your handover confirmation saved' : 'Confirm actual handover') + '</button>' +
      (claim.viewerRole === 'POSTER' ? '<button class="btn btn-secondary mt-2 w-full" type="button" data-decline>Cancel pickup approval</button>' : '') + '</div>' : '') +
      (claim.status === 'OPEN' && claim.viewerRole === 'CLAIMANT' && !data.hasEvidencePhoto ? '<form class="mt-4" data-evidence-upload>' +
      '<label class="label" for="extra-evidence">Add private evidence image</label><input id="extra-evidence" name="evidence" type="file" accept="image/jpeg,image/png,image/webp,image/gif" class="field mt-2" required>' +
      '<p class="mt-2 text-xs text-muted">Older photo or redacted receipt image; up to 5 MB. Adding evidence requires a new review.</p><button class="btn btn-secondary mt-2" type="submit">Upload evidence</button></form>' : '') +
      (active ? '<form class="mt-4 border-t border-line pt-4" data-safety-report><label for="safety-reason" class="label">Concern or request for staff review</label>' +
      '<textarea id="safety-reason" name="reason" maxlength="1000" minlength="10" rows="3" class="field mt-2" required placeholder="Explain the concern (at least 10 characters). Do not include full ID numbers."></textarea>' +
      '<div class="mt-3 flex flex-wrap gap-2"><button type="submit" name="action" value="desk-review" class="btn btn-secondary btn-sm">Request campus desk</button>' +
      '<button type="submit" name="action" value="flag" class="btn btn-secondary btn-sm text-lost">Report fraud & freeze</button></div></form>' : '') +
      '<p class="mt-3 text-sm text-lost" data-safety-error role="alert"></p>' +
      '<details class="mt-4" data-audit-details><summary class="text-sm text-brand-text cursor-pointer">Claim audit trail</summary><div class="mt-2 text-xs text-muted" data-audit>Open to load history.</div></details>';
    aside.appendChild(section);
    function run(button, request, message) {
      if (safety.busy || button.disabled) return;
      safety.busy = true; button.disabled = true;
      section.querySelector('[data-safety-error]').textContent = '';
      request().then(function (updated) { render(updated); LF.toast.success(message); })
        .catch(function (error) { section.querySelector('[data-safety-error]').textContent = error.message || 'Could not save. Try again.'; button.disabled = false; })
        .finally(function () { safety.busy = false; });
    }
    var handover = section.querySelector('[data-handover]');
    if (handover) handover.addEventListener('click', function () {
      if (!window.confirm('Has the physical item actually been handed over? Confirm only after pickup.')) return;
      run(handover, function () { return LF.api.post('/api/claims/' + encodeURIComponent(claim.reference) + '/handover'); }, 'Your handover confirmation was saved.');
    });
    var report = section.querySelector('[data-safety-report]');
    if (report) report.addEventListener('submit', function (ev) {
      ev.preventDefault(); var button = ev.submitter;
      var reason = report.elements.reason.value.trim();
      if (reason.length < 10) { section.querySelector('[data-safety-error]').textContent = 'Give at least 10 characters of detail.'; return; }
      if (button.value === 'flag' && !window.confirm('Report this concern to staff and freeze all handovers for this item?')) return;
      run(button, function () { return LF.api.post('/api/claims/' + encodeURIComponent(claim.reference) + '/' + button.value, { reason: reason }); }, 'Campus staff review requested.');
    });
    var upload = section.querySelector('[data-evidence-upload]');
    if (upload) upload.addEventListener('submit', function (ev) {
      ev.preventDefault(); var file = upload.elements.evidence.files[0]; if (!file) return;
      if (file.size > 5 * 1024 * 1024) { section.querySelector('[data-safety-error]').textContent = 'Evidence must be 5 MB or smaller.'; return; }
      var body = new FormData(); body.append('evidence', file);
      run(upload.querySelector('button'), function () { return safety.multipart('/api/claims/' + encodeURIComponent(claim.reference) + '/evidence', body); }, 'Private evidence uploaded.');
    });
    var history = section.querySelector('[data-audit-details]');
    history.addEventListener('toggle', function () {
      if (!history.open) return;
      LF.api.get('/api/claims/' + encodeURIComponent(claim.reference) + '/audit').then(function (events) {
        section.querySelector('[data-audit]').innerHTML = events.map(function (event) {
          return '<p class="border-b border-line py-2"><strong>' + e(event.action.replaceAll('_', ' ')) + '</strong><br>' + e(event.actorName) + ' · ' + e(new Date(event.createdAt).toLocaleString()) + '<br>' + e(event.note) + '</p>';
        }).join('');
      }).catch(function (err) { section.querySelector('[data-audit]').textContent = err.message; });
    });
  };
  LF.safety = safety;
})();
