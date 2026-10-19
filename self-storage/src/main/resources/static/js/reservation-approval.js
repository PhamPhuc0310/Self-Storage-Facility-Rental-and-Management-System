(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => value == null ? '—' : new Intl.NumberFormat('vi-VN', {style:'currency',currency:'VND'}).format(value);
  const date = value => value ? SafeBoxUc12Time.formatDateVN(value) : '—';
  const dateTime = value => value ? SafeBoxUc12Time.formatDateTimeVN(value) : '—';
  const badge = document.querySelector('aside [data-path="yeu-cau-dat-kho"] > span');
  const headerName = document.querySelector('header span.font-headline-sm');
  const headerInfo = document.querySelector('header span.font-body-sm');
  const headerUser = document.querySelector('header .border-l span.font-label-button');
  document.querySelectorAll('header button').forEach(button => button.hidden = true);
  const routes = {
    'tong-quan':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    'co-so-cua-toi':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html#manager-facilities',
    'loai-kho':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'kho-vat-ly':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html'
  };
  document.querySelectorAll('aside a[data-path]').forEach(link => {
    if (routes[link.dataset.path]) link.href = routes[link.dataset.path];
    if (link.dataset.path === 'dang-nhap') link.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
  });
  let facilities = [];
  let pending = [];
  let rejectId = null;
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(body?.message || 'Không thể xử lý yêu cầu.');
      error.status = response.status;
      throw error;
    }
    return body;
  }
  function showError(error) {
    $('approval-error-status').textContent = error.status ? `${error.status} ${error.status === 409 ? 'CONFLICT' : 'ERROR'}` : 'LỖI KẾT NỐI';
    $('approval-error-message').textContent = error.message;
    $('approval-error').hidden = false;
    $('approval-success').hidden = true;
  }
  function render() {
    const id = $('approval-facility').value;
    const chosen = id ? facilities.filter(f => f.facilityId === id) : facilities;
    const allowed = new Set(facilities.map(f => f.facilityId));
    const scoped = pending.filter(r => allowed.has(r.facilityId) && (!id || r.facilityId === id));
    const query = $('approval-search').value.trim().toLocaleLowerCase('vi');
    const rows = scoped.filter(r => !query || [r.reservationId, r.customerName, r.facilityName, r.typeName].some(value => String(value || '').toLocaleLowerCase('vi').includes(query)));
    const name = chosen.length === 1 ? chosen[0].name : chosen.length ? `${chosen.length} cơ sở được phân công` : 'Chưa được phân công cơ sở';
    headerName.textContent = name;
    headerInfo.textContent = chosen.length === 1 ? chosen[0].address : name;
    badge.textContent = `${pending.filter(r => allowed.has(r.facilityId)).length} MỚI`;
    $('approval-count').textContent = scoped.length;
    $('approval-page-count').textContent = `Đang hiển thị ${rows.length} trong tổng số ${scoped.length} yêu cầu đang chờ`;
    $('approval-rows').innerHTML = rows.map(r => `<tr class="hover:bg-surface-container-low/60 transition-colors"><td class="p-4 align-top text-secondary font-semibold">${escape(r.reservationId)}</td><td class="p-4 align-top">${escape(r.customerName)}</td><td class="p-4 align-top">${escape(r.typeName)}<br><span class="text-on-surface-variant">${escape(r.facilityName)}</span></td><td class="p-4 align-top">${date(r.startDate)} – ${date(r.endDate)}</td><td class="p-4 align-top text-right">${money(r.monthlyPrice)}<br><span class="text-on-surface-variant">Cọc: ${money(r.depositAmount)}</span></td><td class="p-4 align-top">${dateTime(r.createdAt)}</td><td class="p-4 align-top"><span class="px-2.5 py-1 rounded-full bg-secondary-fixed text-on-secondary-fixed">Chờ xác nhận</span></td><td class="p-4 align-top"><div class="flex gap-2 justify-center"><button class="approval-confirm px-3 py-1.5 rounded-lg bg-primary text-on-primary disabled:opacity-50" data-id="${escape(r.reservationId)}">Xác nhận</button><button class="approval-reject px-3 py-1.5 rounded-lg bg-error-container text-on-error-container disabled:opacity-50" data-id="${escape(r.reservationId)}">Từ chối</button></div></td></tr>`).join('');
    $('approval-empty').hidden = rows.length > 0;
    $('approval-empty-message').textContent = !facilities.length ? 'Chưa được phân công cơ sở. Vui lòng liên hệ quản trị viên.' : query ? 'Không có yêu cầu đang chờ khớp với từ khóa tìm kiếm tại cơ sở đã chọn.' : 'Không có yêu cầu nào đang chờ tại cơ sở đã chọn.';
    $('approval-clear').hidden = !query;
  }
  async function load() {
    $('approval-loading').hidden = false;
    try {
      const [assigned, rows] = await Promise.all([api('/api/manager/facilities'), api('/api/manager/reservations/pending')]);
      facilities = assigned;
      pending = rows;
      const filter = $('approval-facility');
      const previous = filter.value;
      filter.innerHTML = '<option value="">Tất cả cơ sở</option>' + facilities.map(f => `<option value="${escape(f.facilityId)}">${escape(f.name)}</option>`).join('');
      filter.value = facilities.some(f => f.facilityId === previous) ? previous : '';
      filter.disabled = facilities.length < 2;
      $('approval-error').hidden = true;
      render();
      return true;
    } catch (error) {
      $('approval-rows').replaceChildren();
      $('approval-empty').hidden = true;
      showError(error);
      return false;
    } finally { $('approval-loading').hidden = true; }
  }
  function closeReject() {
    $('approval-reject-modal').hidden = true;
    rejectId = null;
  }
  $('approval-facility').addEventListener('change', render);
  $('approval-search').addEventListener('input', render);
  $('approval-refresh').addEventListener('click', load);
  $('approval-error-refresh').addEventListener('click', load);
  $('approval-clear').addEventListener('click', () => { $('approval-search').value = ''; render(); });
  $('approval-reject-cancel').addEventListener('click', closeReject);
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeReject(); });
  $('approval-rows').addEventListener('click', async event => {
    const reject = event.target.closest('.approval-reject');
    if (reject) {
      rejectId = reject.dataset.id;
      $('approval-reject-id').textContent = rejectId;
      $('approval-reason').value = '';
      $('approval-reason-error').hidden = true;
      $('approval-reject-modal').hidden = false;
      $('approval-reason').focus();
      return;
    }
    const confirmButton = event.target.closest('.approval-confirm');
    if (!confirmButton || confirmButton.disabled || !confirm('Xác nhận yêu cầu và tạo hợp đồng cùng các khoản phải thu?')) return;
    confirmButton.disabled = true;
    try {
      const contract = await api(`/api/manager/reservations/${encodeURIComponent(confirmButton.dataset.id)}/confirm`, {method:'POST'});
      if (!await load()) return;
      $('approval-success').textContent = `Đã tạo hợp đồng ${contract.contractNumber || contract.contractId}.`;
      $('approval-success').hidden = false;
    } catch (error) { showError(error); confirmButton.disabled = false; }
  });
  $('approval-reject-submit').addEventListener('click', async () => {
    const reason = $('approval-reason').value.trim();
    if (!reason) { $('approval-reason-error').hidden = false; return; }
    const button = $('approval-reject-submit');
    button.disabled = true;
    try {
      await api(`/api/manager/reservations/${encodeURIComponent(rejectId)}/reject`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({rejectionReason:reason})});
      closeReject();
      if (!await load()) return;
      $('approval-success').textContent = 'Đã từ chối yêu cầu.';
      $('approval-success').hidden = false;
    } catch (error) { closeReject(); showError(error); }
    finally { button.disabled = false; }
  });
  SafeBoxAuth.requireRole('FACILITY_MANAGER').then(ok => {
    if (!ok) return;
    headerUser.textContent = SafeBoxAuth.getCurrentUser()?.fullName || '';
    load();
  });
})();
