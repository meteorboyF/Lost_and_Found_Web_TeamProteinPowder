(function () {
  'use strict';
  var LF = window.LF, e = LF.escapeHtml, host = document.querySelector('[data-desk]');
  var state = { claims: [], events: [], selected: LF.qs('ref'), history: false };
  function load() {
    if (!LF.auth.require()) return;
    if (!LF.auth.isAdmin()) { host.innerHTML = LF.errorState('Only approved campus staff can access private reviews.'); return; }
    Promise.all([LF.api.get('/api/desk/claims'), LF.api.get('/api/desk/audit')]).then(function (results) {
      state.claims = results[0]; state.events = results[1]; render();
    }).catch(function (err) { host.innerHTML = LF.errorState(err.message); });
  }
  function render() {
    var queue = state.claims.filter(function (claim) { return state.history || claim.safety.deskReviewStatus === 'PENDING'; });
    host.innerHTML = '<div class="flex flex-wrap items-center gap-4 mb-5"><label class="text-sm text-muted"><input type="checkbox" data-desk-history ' + (state.history ? 'checked' : '') + '> Include all claims</label>' +
      '<button class="btn btn-secondary btn-sm" type="button" data-desk-refresh>Refresh reviews</button><span class="text-sm text-muted">' + queue.length + ' review' + (queue.length === 1 ? '' : 's') + '</span></div>' +
      '<div class="grid gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]"><section class="card p-4"><h2 class="text-xl text-heading">Review queue</h2><div class="mt-3 grid gap-2">' +
      (queue.length ? queue.map(function (claim) {
        return '<button type="button" class="rounded-xl border border-line p-3 text-left hover:bg-sunken" data-desk-claim="' + e(claim.reference) + '">' +
          '<strong class="text-sm text-heading">' + e(claim.item.title) + '</strong><p class="mt-1 text-xs text-muted">' + e(claim.claimantName) + ' · ' + e(claim.reference) + '</p>' +
          '<p class="mt-2 text-xs ' + (claim.safety.disputed ? 'text-lost' : 'text-brand-text') + '">' + (claim.safety.disputed ? 'Fraud report — handover frozen' : e(claim.safety.deskReviewStatus)) + '</p></button>';
      }).join('') : '<p class="text-sm text-muted">No reviews pending. You can request one from any active claim.</p>') +
      '</div></section><section data-desk-detail><p class="card p-6 text-muted">Select a claim to inspect its private evidence and review history.</p></section></div>' +
      '<details class="mt-8 card p-4"><summary class="text-heading cursor-pointer">Administrator audit log (latest 500 events)</summary>' +
      '<div class="mt-4 grid gap-3">' + state.events.map(function (event) {
        return '<div class="border-b border-line py-2 text-xs text-muted"><strong class="text-heading">' + e(event.action.replaceAll('_', ' ')) + '</strong> · ' + e(event.claimReference) + ' · ' + e(event.itemReference) + '<br>' +
          e(event.actorName) + ' · ' + e(event.createdAt) + '<p class="mt-1 whitespace-pre-line">' + e(event.note) + '</p></div>';
      }).join('') + '</div></details>';
    host.querySelector('[data-desk-history]').addEventListener('change', function (ev) { state.history = ev.target.checked; render(); });
    host.querySelector('[data-desk-refresh]').addEventListener('click', load);
    host.querySelectorAll('[data-desk-claim]').forEach(function (button) {
      button.addEventListener('click', function () { state.selected = button.dataset.deskClaim; detail(); });
    });
    if (state.selected) detail();
  }
  function detail() {
    var reference = state.selected;
    LF.api.get('/api/desk/claims/' + encodeURIComponent(reference)).then(function (claim) {
      if (reference !== state.selected) return;
      var target = host.querySelector('[data-desk-detail]'); if (!target) return;
      var events = state.events.filter(function (event) { return event.claimReference === reference; });
      target.innerHTML = '<div class="card p-5"><p class="font-mono text-xs text-muted">' + e(claim.reference) + '</p><h2 class="mt-2 text-2xl text-heading">' + e(claim.item.title) + '</h2>' +
        '<p class="mt-2 text-sm text-muted">Claimant: ' + e(claim.claimantName) + ' · Poster: ' + e(claim.posterName) + '</p><p class="mt-2 text-sm text-muted">Claim: ' + e(claim.status) + ' · Desk: ' + e(claim.safety.deskReviewStatus) + '</p>' +
        (claim.safety.handoverFrozen ? '<p class="mt-2 text-sm text-lost">All handovers for this item are frozen.</p>' : '') + '</div>' +
        '<div class="mt-4">' + LF.safety.evidence(claim) + '</div>' +
        '<section class="card p-5 mt-4"><h3 class="text-lg text-heading">Review history &amp; reported concerns</h3>' +
        events.map(function (event) { return '<p class="mt-3 text-xs text-muted"><strong>' + e(event.action.replaceAll('_', ' ')) + '</strong> · ' + e(event.actorName) + '<br>' + e(new Date(event.createdAt).toLocaleString()) + '<br>' + e(event.note) + '</p>'; }).join('') + '</section>' +
        (claim.safety.deskReviewStatus === 'PENDING' ? '<form class="card p-5 mt-4" data-desk-decision><h3 class="text-lg text-heading">Staff decision</h3>' +
        '<p class="mt-2 text-sm text-muted">Compare private evidence and concealed details. Clear only after checking the claimant’s student ID in person; approval and handover still belong to the participants. You cannot review your own claim or report.</p>' +
        '<label class="mt-4 block text-sm text-body"><input type="checkbox" name="studentIdChecked" data-id-checked> I checked the claimant’s student ID in person and matched the account</label>' +
        '<label class="label block mt-4" for="desk-note">Review notes (no full ID numbers)</label><textarea id="desk-note" name="note" required minlength="10" maxlength="1000" rows="3" class="field mt-2"></textarea>' +
        '<div class="mt-4 flex flex-wrap gap-3"><button class="btn btn-primary" type="submit" name="decision" value="clear">Clear desk review</button><button class="btn btn-secondary text-lost" type="submit" name="decision" value="reject">Reject suspicious claim</button></div>' +
        '<p class="mt-3 text-sm text-lost" data-desk-error role="alert"></p></form>' : '') +
        '<section class="card p-5 mt-4"><h3 class="text-lg text-heading">Private conversation</h3>' + claim.messages.map(function (message) {
          return '<p class="mt-3 text-sm text-body"><strong>' + e(message.authorName) + ':</strong> ' + e(message.body) + '</p>';
        }).join('') + '</section>';
      var form = target.querySelector('[data-desk-decision]');
      if (form) form.addEventListener('submit', function (ev) {
        ev.preventDefault(); var button = ev.submitter, clear = button.value === 'clear';
        var note = form.elements.note.value.trim(), idChecked = form.elements.studentIdChecked.checked;
        var error = form.querySelector('[data-desk-error]');
        if (note.length < 10) { error.textContent = 'Give at least 10 characters of review notes.'; return; }
        if (clear && !idChecked) { error.textContent = 'An in-person student-ID check is required before clearance.'; return; }
        form.querySelectorAll('button').forEach(function (control) { control.disabled = true; });
        LF.api.post('/api/desk/claims/' + encodeURIComponent(reference) + '/review', { clear: clear, studentIdChecked: idChecked, note: note })
          .then(function () { LF.toast.success(clear ? 'Desk review cleared. Participants still need to complete handover.' : 'Claim rejected. Review saved.'); load(); })
          .catch(function (err) { error.textContent = err.message; form.querySelectorAll('button').forEach(function (control) { control.disabled = false; }); });
      });
    }).catch(function (err) { var target = host.querySelector('[data-desk-detail]'); if (target) target.innerHTML = LF.errorState(err.message); });
  }
  document.addEventListener('lf:ready', load);
})();
