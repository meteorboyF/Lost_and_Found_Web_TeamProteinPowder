/**
 * report.js — the post form.
 *
 * Validation messages are written into a message row that is already in the
 * layout at its full height, so an error appearing never shifts the form
 * under the reader's cursor. Server-side field errors land in the same rows,
 * which means the client and the API report problems identically.
 */
(function () {
  'use strict';

  var LF = window.LF;

  var form = document.querySelector('[data-report]');
  var submitBtn = document.querySelector('[data-submit]');
  var successHost = document.querySelector('[data-success]');
  var fileInput = document.querySelector('input[name="photo"]');
  var preview = document.querySelector('[data-preview]');
  var MAX_BYTES = 5 * 1024 * 1024;

  /* ------------------------------------------------------------------
     Field errors
     ------------------------------------------------------------------ */

  function setError(field, message) {
    var slot = document.querySelector('[data-error="' + field + '"]');
    if (slot) slot.textContent = message || '';

    var input = form.elements[field] || document.querySelector('[name="' + field + '"]');
    if (input && input.setAttribute) {
      if (message) input.setAttribute('aria-invalid', 'true');
      else input.removeAttribute('aria-invalid');
    }
  }

  function clearErrors() {
    document.querySelectorAll('[data-error]').forEach(function (el) {
      el.textContent = '';
    });
    form.querySelectorAll('[aria-invalid]').forEach(function (el) {
      el.removeAttribute('aria-invalid');
    });
  }

  /* ------------------------------------------------------------------
     Client-side checks. These mirror the Bean Validation rules on
     ItemRequest — the server remains the authority, this is only to save
     a round trip.
     ------------------------------------------------------------------ */

  function validate(data, kind) {
    var errors = {};

    if (!kind) errors.kind = 'Choose whether this item was lost or found';
    if (!data.title.trim()) errors.title = 'Give the item a short title';
    if (!data.category) errors.category = 'Pick a category';
    if (!data.description.trim()) errors.description = 'Describe the item';
    if (!data.location.trim()) errors.location = 'Say roughly where it happened';
    if (!data.reporterName.trim()) errors.reporterName = 'Tell us your name';

    var email = data.reporterEmail.trim();
    if (!email) errors.reporterEmail = 'We need an email address to reach you';
    else if (!/^[^\s@]+@[^\s@]+\.[a-z]{2,}$/i.test(email)) {
      errors.reporterEmail = 'That does not look like a valid email address';
    }

    if (data.happenedOn) {
      var today = new Date();
      today.setHours(23, 59, 59, 999);
      if (new Date(data.happenedOn) > today) {
        errors.happenedOn = 'The date cannot be in the future';
      }
    }

    var file = fileInput.files && fileInput.files[0];
    if (file && file.size > MAX_BYTES) {
      errors.photo = 'That photo is ' + (file.size / 1048576).toFixed(1) + ' MB. The limit is 5 MB.';
    }

    return errors;
  }

  function showErrors(errors) {
    clearErrors();
    Object.keys(errors).forEach(function (field) {
      setError(field, errors[field]);
    });

    var first = document.querySelector('[aria-invalid="true"]') ||
      (errors.kind ? document.querySelector('input[name="kind"]') : null);
    if (first && first.focus) first.focus();
  }

  /* ------------------------------------------------------------------
     Photo preview
     ------------------------------------------------------------------ */

  function initPhoto() {
    fileInput.addEventListener('change', function () {
      var file = fileInput.files && fileInput.files[0];
      setError('photo', '');

      if (!file) {
        preview.classList.add('hidden');
        return;
      }

      if (file.size > MAX_BYTES) {
        setError('photo', 'That photo is ' + (file.size / 1048576).toFixed(1) + ' MB. The limit is 5 MB.');
      }

      var img = preview.querySelector('[data-preview-img]');
      /* Release the previous object URL rather than leaking one per change. */
      if (img.dataset.objectUrl) URL.revokeObjectURL(img.dataset.objectUrl);
      var url = URL.createObjectURL(file);
      img.dataset.objectUrl = url;
      img.src = url;

      preview.querySelector('[data-preview-name]').textContent = file.name;
      preview.querySelector('[data-preview-size]').textContent =
        (file.size / 1024).toFixed(0) + ' KB';
      preview.classList.remove('hidden');
    });

    preview.querySelector('[data-preview-remove]').addEventListener('click', function () {
      var img = preview.querySelector('[data-preview-img]');
      if (img.dataset.objectUrl) {
        URL.revokeObjectURL(img.dataset.objectUrl);
        delete img.dataset.objectUrl;
      }
      fileInput.value = '';
      preview.classList.add('hidden');
      setError('photo', '');
    });
  }

  /* ------------------------------------------------------------------
     Kind preselection from the query string, so "Report lost" in the nav
     lands on a form that is already on the right side of the board.
     ------------------------------------------------------------------ */

  function initKind() {
    var wanted = (LF.qs('kind') || '').toUpperCase();
    if (wanted === 'LOST' || wanted === 'FOUND') {
      var radio = document.querySelector('input[name="kind"][value="' + wanted + '"]');
      if (radio) radio.checked = true;
    }
    paintKind();

    document.querySelectorAll('input[name="kind"]').forEach(function (radio) {
      radio.addEventListener('change', function () {
        setError('kind', '');
        paintKind();
      });
    });
  }

  function paintKind() {
    var checked = document.querySelector('input[name="kind"]:checked');
    var heading = document.querySelector('[data-heading]');
    var sub = document.querySelector('[data-subheading]');
    var eyebrow = document.querySelector('[data-eyebrow]');

    if (!checked) {
      eyebrow.textContent = 'Report';
      heading.textContent = 'Post an item';
      sub.textContent = 'The more specific you are, the better the matching works.';
      return;
    }

    if (checked.value === 'LOST') {
      eyebrow.textContent = 'Lost something';
      heading.textContent = 'Tell us what you lost';
      sub.textContent = 'We will check it against everything handed in and let you know if something lines up.';
    } else {
      eyebrow.textContent = 'Found something';
      heading.textContent = 'Post what you found';
      sub.textContent = 'Describe it plainly. The owner will recognise it faster than you think.';
    }
  }

  /* ------------------------------------------------------------------
     Submit
     ------------------------------------------------------------------ */

  function submit(ev) {
    ev.preventDefault();

    var kindEl = document.querySelector('input[name="kind"]:checked');
    var data = {
      title: form.elements.title.value,
      category: form.elements.category.value,
      colour: form.elements.colour.value,
      description: form.elements.description.value,
      location: form.elements.location.value,
      happenedOn: form.elements.happenedOn.value,
      reporterName: form.elements.reporterName.value,
      reporterEmail: form.elements.reporterEmail.value
    };

    var errors = validate(data, kindEl && kindEl.value);
    if (Object.keys(errors).length) {
      showErrors(errors);
      LF.toast.error('Some fields need attention before this can be posted.');
      return;
    }

    clearErrors();

    var payload = {
      kind: kindEl.value,
      category: data.category,
      title: data.title.trim(),
      description: data.description.trim(),
      colour: data.colour.trim() || null,
      location: data.location.trim(),
      happenedOn: data.happenedOn || null,
      reporterName: data.reporterName.trim(),
      reporterEmail: data.reporterEmail.trim()
    };

    var body = new FormData();
    body.append('item', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    var file = fileInput.files && fileInput.files[0];
    if (file) body.append('photo', file);

    submitBtn.disabled = true;
    var originalLabel = submitBtn.textContent;
    submitBtn.textContent = 'Posting…';

    fetch('/api/items', { method: 'POST', body: body })
      .then(function (res) {
        return res.json().then(function (payload) {
          if (!res.ok) throw payload;
          return payload;
        });
      })
      .then(showSuccess)
      .catch(function (problem) {
        submitBtn.disabled = false;
        submitBtn.textContent = originalLabel;

        if (problem && problem.fields) {
          showErrors(problem.fields);
          LF.toast.error(problem.message || 'Some fields need attention.');
        } else {
          LF.toast.error(
            (problem && problem.message) || 'Could not reach the server. Is the application running?'
          );
        }
      });
  }

  function showSuccess(item) {
    form.classList.add('hidden');
    document.querySelector('fieldset').classList.add('hidden');
    document.querySelector('[data-heading]').parentElement.classList.add('hidden');

    var e = LF.escapeHtml;
    successHost.className = '';
    successHost.innerHTML =
      '<div class="rounded-sm border border-found bg-found-wash p-8 text-on-wash">' +
        '<svg class="icon h-10 w-10 text-found" aria-hidden="true"><use href="/assets/icons.svg#i-st-verified"></use></svg>' +
        '<h1 class="mt-4 text-3xl">Posted to the board</h1>' +
        '<p class="mt-3 max-w-xl">' +
          (item.kind === 'LOST'
            ? 'We will watch the found side and email you if something matches.'
            : 'Whoever lost this can now find it and start a conversation with you.') +
        '</p>' +
        '<dl class="mt-6 grid gap-2 border-t border-found/30 pt-6 sm:grid-cols-[8rem_1fr]">' +
          '<dt class="u-caps opacity-70">Reference</dt>' +
          '<dd class="font-mono text-lg font-bold">' + e(item.reference) + '</dd>' +
          '<dt class="u-caps opacity-70">Item</dt><dd>' + e(item.title) + '</dd>' +
          '<dt class="u-caps opacity-70">Where</dt><dd>' + e(item.location) + '</dd>' +
        '</dl>' +
        '<p class="mt-4 text-2xs opacity-80">Keep the reference code — it identifies this post in any conversation.</p>' +
        '<div class="mt-8 flex flex-wrap gap-3">' +
          '<a href="/item.html?ref=' + encodeURIComponent(item.reference) + '" ' +
            'class="rounded-sm bg-primary px-5 py-3 font-bold text-surface hover:opacity-90">View the post</a>' +
          '<a href="/browse.html" class="rounded-sm border border-current px-5 py-3 font-bold">Browse the board</a>' +
          '<a href="/report.html" class="rounded-sm px-5 py-3 font-bold underline underline-offset-4">Post another</a>' +
        '</div>' +
      '</div>';

    successHost.setAttribute('tabindex', '-1');
    successHost.focus();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /* ------------------------------------------------------------------
     Boot
     ------------------------------------------------------------------ */

  document.addEventListener('lf:ready', function () {
    initKind();
    initPhoto();

    LF.api
      .get('/api/items/categories')
      .then(function (categories) {
        var select = form.elements.category;
        categories.forEach(function (c) {
          var option = document.createElement('option');
          option.value = c.value;
          option.textContent = c.label;
          select.appendChild(option);
        });
      })
      .catch(function () {
        LF.toast.error('Could not load the category list. Reload the page to try again.');
      });

    var title = form.elements.title;
    var counter = document.querySelector('[data-count-for="title"]');
    title.addEventListener('input', function () {
      counter.textContent = String(title.value.length);
    });

    /* Re-check a field the moment the reader fixes it, but never before they
       have had a first go at it. */
    ['title', 'description', 'location', 'reporterName', 'reporterEmail', 'category'].forEach(
      function (name) {
        var el = form.elements[name];
        if (!el) return;
        el.addEventListener('blur', function () {
          if (el.getAttribute('aria-invalid') !== 'true') return;
          var kindEl = document.querySelector('input[name="kind"]:checked');
          var current = validate(
            {
              title: form.elements.title.value,
              category: form.elements.category.value,
              colour: form.elements.colour.value,
              description: form.elements.description.value,
              location: form.elements.location.value,
              happenedOn: form.elements.happenedOn.value,
              reporterName: form.elements.reporterName.value,
              reporterEmail: form.elements.reporterEmail.value
            },
            kindEl && kindEl.value
          );
          setError(name, current[name]);
        });
      }
    );

    form.addEventListener('submit', submit);
  });
})();
