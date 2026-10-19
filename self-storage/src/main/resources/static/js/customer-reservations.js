(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => value == null ? '—' : new Intl.NumberFormat('vi-VN', {style:'currency',currency:'VND'}).format(value);
  const date = value => SafeBoxUc12Time.formatDateVN(value);
  const dateTime = value => SafeBoxUc12Time.formatDateTimeVN(value);
  const placeholder = '/images/demo/standard-storage.png';
  const statuses = ['ALL', 'PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'];
  const styles = {
    PENDING: {badge:'bg-amber-500 text-white', label:'bg-amber-50 text-amber-800', icon:'hourglass_top'},
    CONFIRMED: {badge:'bg-emerald-600 text-white', label:'bg-emerald-50 text-emerald-800', icon:'check_circle'},
    REJECTED: {badge:'bg-rose-600 text-white', label:'bg-rose-50 text-rose-800', icon:'block'},
    CANCELLED: {badge:'bg-outline text-white', label:'bg-surface-container text-on-surface-variant', icon:'cancel'}
  };
  let all = [];
  let active = 'ALL';
  let selected = null;
  let requestNumber = 0;
  let searchTimer;
  const search = $('filter-search');
  const modal = $('cancel-modal');
  const confirmButton = modal.querySelector('.bg-error');

  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(body?.message || `Yêu cầu thất bại (HTTP ${response.status}).`);
      error.status = response.status;
      throw error;
    }
    return body;
  }
  function message(value, error = false) {
    const box = $('reservations-message');
    box.textContent = value;
    box.className = `rounded-xl p-4 ${error ? 'bg-error-container text-on-error-container' : 'bg-tertiary-fixed text-on-tertiary-fixed'}`;
    box.hidden = !value;
  }
  function imagePath(value) {
    return typeof value === 'string' && (value.startsWith('/') && !value.startsWith('//') || value.startsWith('https://')) ? value : placeholder;
  }
  function card(r) {
    const status = styles[r.status] || {badge:'bg-outline text-white',label:'bg-surface-container text-on-surface-variant',icon:'info'};
    const area = r.area != null ? `${escape(r.area)} m²` : (r.sizeName ? escape(r.sizeName) : '');
    const facility = r.facilityName || 'Cơ sở không còn hiển thị';
    const details = [r.typeName, r.sizeName && r.sizeName !== String(r.area) ? r.sizeName : null].filter(Boolean).join(' · ');
    const canCancel = r.status === 'PENDING';
    return `<article class="reservation-card bg-surface-container-lowest rounded-xl p-6 shadow-sm hover:shadow-md transition-all flex flex-col xl:flex-row gap-6">
      <div class="xl:w-72 w-full h-48 xl:h-auto rounded-lg overflow-hidden relative flex-shrink-0 bg-surface-container">
        <img class="reservation-image w-full h-full object-cover" src="${escape(imagePath(r.unitTypeImage))}" alt="${escape(r.typeName || 'Hình ảnh kho lưu trữ')}">
        <span class="absolute top-3 left-3 px-2.5 py-1 rounded font-label-badge shadow flex items-center gap-1 ${status.badge}"><span class="material-symbols-outlined text-sm">${status.icon}</span>${escape(r.statusLabel || r.status)}</span>
        ${area ? `<span class="absolute bottom-3 left-3 bg-primary/80 backdrop-blur-sm px-2.5 py-1.5 rounded text-white text-body-sm">${area}</span>` : ''}
      </div>
      <div class="flex-1 flex flex-col justify-between space-y-4"><div>
        <div class="flex flex-wrap items-center justify-between gap-2 mb-2"><div class="flex items-center gap-2.5"><strong class="px-2.5 py-1 rounded-md bg-surface-container text-primary font-mono font-headline-sm text-headline-sm">${escape(r.requestCode || r.reservationId)}</strong><span class="px-2.5 py-1 rounded-full font-label-badge ${status.label}">${escape(r.statusLabel || r.status)}</span></div><span class="text-body-sm text-on-surface-variant">Gửi lúc: ${dateTime(r.createdAt)}</span></div>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-3"><div class="space-y-2"><div class="flex items-start gap-2"><span class="material-symbols-outlined text-secondary text-lg">warehouse</span><div><strong class="block">${escape(facility)}</strong><span class="text-body-sm text-on-surface-variant">${escape(r.facilityAddress || '')}</span></div></div><p class="text-body-md text-on-surface">Loại kho: <strong>${escape(details || r.typeName || 'Chưa có thông tin')}</strong></p></div>
        <div class="bg-surface-container-low p-3.5 rounded-lg space-y-2"><div class="flex justify-between gap-3"><span>Thời hạn thuê:</span><strong>${r.rentalMonths == null ? '—' : `${escape(r.rentalMonths)} tháng`}</strong></div><div class="flex justify-between gap-3"><span>Bắt đầu:</span><strong>${date(r.startDate)}</strong></div><div class="flex justify-between gap-3"><span>Kết thúc:</span><strong>${date(r.endDate)}</strong></div></div></div></div>
        <div class="pt-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4"><div><span class="text-body-sm text-on-surface-variant block">Ước tính tổng chi phí</span><strong class="font-label-numeric-price text-label-numeric-price text-primary">${money(r.estimatedRentalAmount)}</strong><span class="text-body-sm text-on-surface-variant block">Giá tháng: ${money(r.monthlyPrice)} · Tiền cọc: ${money(r.estimatedDepositAmount)}</span></div><div class="flex flex-wrap items-center gap-3">${r.facilityPhone ? `<a class="px-4 py-2 rounded-lg bg-surface-container text-on-surface font-label-button" href="tel:${escape(r.facilityPhone)}">Hỗ trợ cơ sở</a>` : ''}${r.status === 'CONFIRMED' ? `<a class="px-4 py-2 rounded-lg bg-secondary text-white font-label-button" href="/uc10-customer-contract.html?reservationId=${encodeURIComponent(r.reservationId)}">Xem hợp đồng</a>` : ''}${canCancel ? `<button class="cancel-reservation px-4 py-2 rounded-lg bg-error-container text-error font-label-button" data-id="${escape(r.reservationId)}">Hủy yêu cầu</button>` : ''}</div></div>
      </div></article>`;
  }
  function updateCounts() {
    for (const status of statuses) {
      const button = $('tab-btn-' + status);
      const count = status === 'ALL' ? all.length : all.filter(r => r.status === status).length;
      button.querySelector('span:last-child').textContent = count;
      button.classList.toggle('bg-surface-container-lowest', active === status);
      button.classList.toggle('text-secondary', active === status);
      button.classList.toggle('shadow-sm', active === status);
      button.classList.toggle('text-on-surface-variant', active !== status);
      button.setAttribute('aria-pressed', String(active === status));
    }
    $('pending-kpi').textContent = `${all.filter(r => r.status === 'PENDING').length} Yêu cầu`;
  }
  function render(rows) {
    const list = $('reservations-list');
    list.innerHTML = rows.map(card).join('');
    list.querySelectorAll('img.reservation-image').forEach(img => {
      img.addEventListener('error', () => { if (!img.src.endsWith(placeholder)) img.src = placeholder; }, {once:true});
    });
    const empty = $('empty-state-view');
    empty.classList.toggle('hidden', rows.length > 0);
    const noReservations = all.length === 0;
    $('empty-state-title').textContent = noReservations ? 'Bạn chưa có yêu cầu thuê kho nào.' : 'Không tìm thấy yêu cầu đặt kho phù hợp';
    $('empty-state-description').textContent = noReservations ? 'Khám phá các cơ sở và chọn kho phù hợp với nhu cầu của bạn.' : 'Không có yêu cầu nào khớp với bộ lọc hoặc từ khóa hiện tại.';
    $('reset-reservation-filters').hidden = noReservations || (active === 'ALL' && !search.value.trim());
  }
  async function loadFiltered() {
    const current = ++requestNumber;
    $('reservations-loading').hidden = false;
    try {
      const params = new URLSearchParams();
      if (active !== 'ALL') params.set('status', active);
      if (search.value.trim()) params.set('search', search.value.trim());
      const rows = params.size ? await api('/api/reservations/my?' + params) : all;
      if (current === requestNumber) render(rows);
      return true;
    } catch (error) {
      if (current === requestNumber) { $('reservations-list').replaceChildren(); $('empty-state-view').classList.add('hidden'); message(error.message, true); }
      return false;
    } finally { if (current === requestNumber) $('reservations-loading').hidden = true; }
  }
  async function refresh() {
    ++requestNumber;
    $('reservations-loading').hidden = false;
    message('');
    try {
      all = await api('/api/reservations/my');
      updateCounts();
      return await loadFiltered();
    } catch (error) {
      $('reservations-list').replaceChildren();
      $('empty-state-view').classList.add('hidden');
      message(error.message, true);
      return false;
    } finally { $('reservations-loading').hidden = true; }
  }
  function closeModal() { modal.classList.add('hidden'); selected = null; }
  $('status-tabs-container').addEventListener('click', event => {
    const button = event.target.closest('button.tab-btn');
    if (!button) return;
    active = button.id.slice('tab-btn-'.length);
    updateCounts();
    loadFiltered();
  });
  search.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(loadFiltered, 250); });
  $('reset-reservation-filters').addEventListener('click', () => { active = 'ALL'; search.value = ''; updateCounts(); loadFiltered(); });
  $('reservations-list').addEventListener('click', async event => {
    const button = event.target.closest('.cancel-reservation');
    if (!button || button.disabled) return;
    button.disabled = true;
    try {
      const detail = await api(`/api/reservations/my/${encodeURIComponent(button.dataset.id)}`);
      if (detail.status !== 'PENDING') { await refresh(); throw new Error('Yêu cầu đã đổi trạng thái. Vui lòng xem dữ liệu mới.'); }
      selected = detail;
      $('modal-req-code').textContent = detail.requestCode || detail.reservationId;
      $('modal-reservation-context').textContent = ` tại ${detail.facilityName || 'cơ sở đã chọn'} · ${detail.typeName || 'loại kho đã chọn'}`;
      modal.classList.remove('hidden');
    } catch (error) { message(error.message, true); }
    finally { button.disabled = false; }
  });
  modal.querySelectorAll('button').forEach(button => {
    if (button !== confirmButton) button.addEventListener('click', closeModal);
  });
  modal.addEventListener('click', event => { if (event.target === modal) closeModal(); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });
  confirmButton.addEventListener('click', async () => {
    if (!selected || confirmButton.disabled) return;
    const id = selected.reservationId;
    confirmButton.disabled = true;
    try {
      const result = await api(`/api/reservations/my/${encodeURIComponent(id)}/cancel`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({cancellationReason:'Khách hàng yêu cầu hủy'})});
      if (result?.status !== 'CANCELLED') throw new Error('Máy chủ chưa xác nhận hủy yêu cầu.');
      closeModal();
      if (await refresh()) message('Yêu cầu đã được hủy.');
    } catch (error) { message(error.message, true); closeModal(); }
    finally { confirmButton.disabled = false; }
  });
  document.querySelectorAll('a[data-path="tim-kho"]').forEach(link => link.href = '/stitch/t_m_kho_l_u_tr_safebox_storage.html');
  SafeBoxAuth.requireRole('CUSTOMER').then(ok => { if (ok) refresh(); });
})();
