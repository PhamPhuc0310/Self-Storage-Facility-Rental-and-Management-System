(() => {
  const main = document.querySelector('main > div');
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value || 0);
  const {formatDateVN, formatDateTimeVN} = SafeBoxUc12Time;
  const routes = {
    'tong-quan':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    'loai-kho':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'kho-vat-ly':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'yeu-cau-dat-kho':'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html'
  };
  document.querySelectorAll('aside a[data-path]').forEach(link => {
    if (routes[link.dataset.path]) link.href = routes[link.dataset.path];
    if (link.dataset.path === 'co-so-cua-toi') link.hidden = true;
  });
  const header = document.querySelector('header');
  header.querySelectorAll('button').forEach(button => button.hidden = true);
  header.querySelectorAll('img[alt="Profile"]').forEach(img => img.hidden = true);
  const headerFacility = header.querySelector('span.font-headline-sm');
  const headerInfo = header.querySelector('span.font-body-sm');
  const headerUser = header.querySelector('.border-l span.font-label-button');
  headerFacility.textContent = 'Đang tải cơ sở...';
  headerInfo.textContent = 'Đang kiểm tra phân công cơ sở...';
  if (headerUser) headerUser.textContent = 'Quản lý cơ sở';
  main.innerHTML = `<section class="max-w-5xl mx-auto w-full px-4 sm:px-6 py-8 space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3"><div><h1 class="font-headline-lg text-headline-lg text-primary">Yêu cầu đặt kho</h1><p class="text-on-surface-variant">Duyệt yêu cầu tại cơ sở được phân công.</p></div><button id="refresh-reservations" class="px-5 py-2.5 rounded-lg bg-secondary text-white">Tải lại</button></div>
    <label class="block">Xem cơ sở <select id="approval-facility-filter" class="ml-2 p-2 rounded-lg bg-white border border-outline-variant"><option value="">Tất cả cơ sở</option></select></label>
    <p id="approval-message" class="rounded-lg p-3" role="status" hidden></p>
    <p id="approval-loading" class="bg-white rounded-xl p-6 shadow-sm">Đang tải yêu cầu…</p>
    <div id="approval-list" class="space-y-4"></div>
    <p id="approval-empty" class="bg-white rounded-xl p-8 text-center shadow-sm" hidden>Không có yêu cầu nào đang chờ.</p>
  </section>`;
  const $ = id => document.getElementById(id);
  function message(text, error = false) {
    $('approval-message').textContent = text;
    $('approval-message').className = `rounded-lg p-3 ${error ? 'bg-error-container text-error' : 'bg-tertiary-fixed text-on-tertiary-fixed'}`;
    $('approval-message').hidden = !text;
  }
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || 'Không thể xử lý yêu cầu.');
    return body;
  }
  async function load() {
    $('approval-loading').hidden = false;
    $('approval-empty').hidden = true;
    try {
      const [facilities, pending] = await Promise.all([
        api('/api/manager/facilities'), api('/api/manager/reservations/pending')
      ]);
      const allowed = new Set(facilities.map(f => f.facilityId));
      const filter = $('approval-facility-filter');
      const selected = allowed.has(filter.value) ? filter.value : '';
      filter.innerHTML = '<option value="">Tất cả cơ sở</option>' + facilities.map(f => `<option value="${escape(f.facilityId)}">${escape(f.name)}</option>`).join('');
      filter.value = selected;
      const shownFacilities = selected ? facilities.filter(f => f.facilityId === selected) : facilities;
      const rows = pending.filter(r => allowed.has(r.facilityId) && (!selected || r.facilityId === selected));
      headerFacility.textContent = shownFacilities.map(f => f.name).join(', ') || 'Chưa được phân công cơ sở';
      headerInfo.textContent = shownFacilities.length === 1 ? shownFacilities[0].address : `${shownFacilities.length} cơ sở được phân công`;
      const badge = document.querySelector('aside [data-path="yeu-cau-dat-kho"] > span');
      if (badge) badge.textContent = `${rows.length} MỚI`;
      $('approval-list').innerHTML = rows.map(r => `<article class="bg-white rounded-xl p-5 sm:p-6 shadow-sm space-y-3">
        <div class="flex flex-wrap justify-between gap-3"><div><h2 class="font-headline-sm text-headline-sm text-primary">${escape(r.customerName)}</h2><p class="text-on-surface-variant">${escape(r.facilityName)} · ${escape(r.typeName)}</p></div><span class="text-secondary">Chờ xác nhận</span></div>
        <p>${formatDateVN(r.startDate)} → ${formatDateVN(r.endDate)} · Tạo: ${formatDateTimeVN(r.createdAt)}</p><p>Tiền thuê tháng: <strong>${money(r.monthlyPrice)}</strong> · Tiền cọc: <strong>${money(r.depositAmount)}</strong></p>
        <div class="flex flex-wrap gap-2"><button class="confirm-reservation px-5 py-2.5 rounded-lg bg-secondary text-white disabled:opacity-50" data-id="${escape(r.reservationId)}">Xác nhận và tạo hợp đồng</button><button class="reject-reservation px-5 py-2.5 rounded-lg bg-error-container text-error disabled:opacity-50" data-id="${escape(r.reservationId)}">Từ chối</button></div>
      </article>`).join('');
      $('approval-empty').textContent = facilities.length ? 'Không có yêu cầu nào đang chờ.' : 'Chưa được phân công cơ sở. Vui lòng liên hệ quản trị viên.';
      $('approval-empty').hidden = rows.length > 0;
    } catch (error) {
      $('approval-list').replaceChildren();
      $('approval-empty').hidden = true;
      message(error.message, true);
    } finally { $('approval-loading').hidden = true; }
  }
  $('approval-facility-filter').addEventListener('change', () => load());
  $('refresh-reservations').addEventListener('click', () => load().catch(e => message(e.message, true)));
  $('approval-list').addEventListener('click', async event => {
    const reject = event.target.closest('.reject-reservation');
    if (reject && !reject.disabled) {
      const reason = prompt('Lý do từ chối yêu cầu:');
      if (reason === null) return;
      reject.disabled = true;
      try {
        await api(`/api/manager/reservations/${reject.dataset.id}/reject`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({rejectionReason:reason})});
        message('Đã từ chối yêu cầu.');
        await load();
      } catch (e) { message(e.message, true); reject.disabled = false; }
      return;
    }
    const button = event.target.closest('.confirm-reservation');
    if (!button || button.disabled || !confirm('Xác nhận yêu cầu và tạo hợp đồng cùng hai khoản phải thu?')) return;
    button.disabled = true;
    try {
      const contract = await api(`/api/manager/reservations/${button.dataset.id}/confirm`, {method:'POST'});
      message(`Đã tạo hợp đồng ${contract.contractNumber}.`);
      await load();
    } catch (e) { message(e.message, true); button.disabled = false; }
  });
  SafeBoxAuth.requireRole('FACILITY_MANAGER').then(authorized => {
    if (!authorized) return;
    const user = SafeBoxAuth.getCurrentUser();
    if (user?.role !== 'FACILITY_MANAGER') return;
    if (headerUser) headerUser.textContent = user.fullName;
    load();
  });
})();
