(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value || 0);
  const {formatDateVN, formatDateTimeVN} = SafeBoxUc12Time;
  const refundStatus = {REQUESTED:'Đã gửi yêu cầu', UNDER_REVIEW:'Đang xét duyệt', APPROVED:'Đã duyệt, chờ hoàn tiền', REJECTED:'Đã từ chối', COMPLETED:'Đã hoàn tiền', FAILED:'Hoàn tiền thất bại'};
  const routes = {
    'tong-quan':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    'co-so-cua-toi':'#manager-facilities',
    'loai-kho':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'kho-vat-ly':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'yeu-cau-dat-kho':'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html'
  };
  document.querySelectorAll('aside a[data-path]').forEach(link => {
    if (routes[link.dataset.path]) link.href = routes[link.dataset.path];
    if (link.dataset.path === 'dang-nhap') link.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
  });
  document.querySelectorAll('header button').forEach(button => button.hidden = true);
  document.querySelectorAll('header img[alt="Profile"]').forEach(img => img.hidden = true);
  const headerFacility = document.querySelector('header span.font-headline-sm');
  const headerInfo = document.querySelector('header span.font-body-sm');
  const headerUser = document.querySelector('header .border-l span.font-label-button');
  const badge = document.querySelector('aside [data-path="yeu-cau-dat-kho"] > span');
  function message(value, error = false) {
    $('manager-message').textContent = value;
    $('manager-message').className = `rounded-lg p-3 ${error ? 'bg-error-container text-error' : 'bg-tertiary-fixed text-on-tertiary-fixed'}`;
    $('manager-message').hidden = !value;
  }
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Không tải được dữ liệu quản lý.');
    return data;
  }
  async function load() {
    $('manager-loading').hidden = false;
    $('manager-facility-list').replaceChildren();
    $('manager-pending-list').replaceChildren();
    $('manager-awaiting-list').replaceChildren();
    $('manager-refund-list').replaceChildren();
    $('manager-staff-list').replaceChildren();
    $('manager-pending-count').textContent = '0';
    if (badge) badge.textContent = '0 MỚI';
    try {
      const facilities = await api('/api/manager/facilities');
      const allowed = new Set(facilities.map(f => f.facilityId));
      const filter = $('manager-facility-filter');
      const selected = allowed.has(filter.value) ? filter.value : '';
      filter.innerHTML = '<option value="">Tất cả cơ sở</option>' + facilities.map(f => `<option value="${escape(f.facilityId)}">${escape(f.name)}</option>`).join('');
      filter.value = selected;
      const shownFacilities = selected ? facilities.filter(f => f.facilityId === selected) : facilities;
      const [pending, awaiting, staffGroups, refunds] = await Promise.all([
        api('/api/manager/reservations/pending'),
        api('/api/manager/appointments/awaiting-assignment' + (selected ? '?facilityId=' + encodeURIComponent(selected) : '')),
        Promise.all(shownFacilities.map(async f => ({facility:f, staff:await api('/api/manager/staff?facilityId=' + encodeURIComponent(f.facilityId))}))),
        api('/api/manager/deposit-refunds')
      ]);
      const rows = pending.filter(r => allowed.has(r.facilityId) && (!selected || r.facilityId === selected));
      const names = shownFacilities.map(f => f.name).join(', ');
      headerFacility.textContent = names || 'Chưa được phân công cơ sở';
      headerInfo.textContent = shownFacilities.length === 1 ? shownFacilities[0].address : `${shownFacilities.length} cơ sở được phân công`;
      $('manager-heading').textContent = names || 'Chưa được phân công cơ sở. Vui lòng liên hệ quản trị viên.';
      $('manager-facility-list').innerHTML = shownFacilities.map(f => `<article class="bg-white rounded-xl p-5 shadow-sm"><h3 class="font-headline-sm text-headline-sm text-primary">${escape(f.name)}</h3><p class="text-on-surface-variant mt-2">${escape(f.address)}</p></article>`).join('');
      $('manager-facility-empty').hidden = facilities.length > 0;
      $('manager-pending-count').textContent = String(rows.length);
      if (badge) badge.textContent = `${rows.length} MỚI`;
      $('manager-pending-list').innerHTML = rows.map(r => `<article class="bg-white rounded-xl p-5 shadow-sm space-y-3"><div class="flex flex-wrap justify-between gap-3"><div><h3 class="font-headline-sm text-headline-sm text-primary">${escape(r.customerName)}</h3><p>${escape(r.facilityName)} · ${escape(r.typeName)}</p></div><span class="text-secondary">Chờ xác nhận</span></div><p>Thuê: ${formatDateVN(r.startDate)} → ${formatDateVN(r.endDate)} · Tạo: ${formatDateTimeVN(r.createdAt)}</p><p>Giá tháng: ${money(r.monthlyPrice)} · Tiền cọc: ${money(r.depositAmount)}</p><div class="flex flex-wrap gap-2"><button class="confirm-reservation px-5 py-2.5 rounded-lg bg-secondary text-white disabled:opacity-50" data-id="${escape(r.reservationId)}">Xác nhận và tạo hợp đồng</button><button class="reject-reservation px-5 py-2.5 rounded-lg bg-error-container text-error disabled:opacity-50" data-id="${escape(r.reservationId)}">Từ chối</button></div></article>`).join('');
      $('manager-pending-empty').hidden = rows.length > 0;
      const future = awaiting.filter(a => allowed.has(a.facilityId) && (!selected || a.facilityId === selected));
      $('manager-awaiting-list').innerHTML = future.map(a => `<article class="bg-white rounded-xl p-5 shadow-sm space-y-2"><strong>${escape(a.contractCode)}</strong><p>${escape(a.customerName)} · ${escape(a.facilityName)} · Kho ${escape(a.storageUnitCode)}</p><p>${formatDateTimeVN(a.appointmentTime)} · Cọc đã thu ${money(a.depositPaidAmount)}, còn ${money(a.depositRemainingAmount)} · Tiền thuê còn ${money(a.remainingRentAmount)} · Chờ nhận · Chưa phân công</p><a class="inline-block px-4 py-2 rounded-lg bg-secondary text-white" href="/staff-tasks.html?facilityId=${encodeURIComponent(a.facilityId)}&date=${encodeURIComponent(a.appointmentTime?.slice(0,10) || '')}#awaiting-assignment-section">Phân công nhân viên</a></article>`).join('');
      $('manager-awaiting-empty').hidden = future.length > 0;
      const refundRows = refunds.filter(r => allowed.has(r.facilityId) && (!selected || r.facilityId === selected));
      $('manager-refund-list').innerHTML = refundRows.map(r => `<article class="bg-white rounded-xl p-5 shadow-sm space-y-2"><strong>Hợp đồng ${escape(r.contractId)}</strong><p>Gửi ${formatDateTimeVN(r.createdAt)}${r.reviewerId ? ` · Xét duyệt ${formatDateTimeVN(r.updatedAt)}` : ''}${r.completedAt ? ` · Hoàn ${formatDateTimeVN(r.completedAt)}` : ''}</p><p>Đề nghị hoàn ${money(r.requestedAmount)} · ${escape(r.reason)}</p><p>Trạng thái: ${refundStatus[r.status] || 'Chưa rõ'}${r.approvedAmount ? ` · Đã duyệt ${money(r.approvedAmount)}` : ''}</p><div class="flex flex-wrap gap-2">${['REQUESTED','UNDER_REVIEW'].includes(r.status) ? `<button class="refund-review px-3 py-2 rounded-lg bg-secondary text-white" data-id="${escape(r.id)}" data-amount="${escape(r.requestedAmount)}">Xét duyệt</button><button class="refund-reject px-3 py-2 rounded-lg bg-error-container text-error" data-id="${escape(r.id)}">Từ chối</button>` : r.status === 'APPROVED' ? `<button class="refund-complete px-3 py-2 rounded-lg bg-secondary text-white" data-id="${escape(r.id)}">Ghi nhận đã hoàn tiền</button><button class="refund-failed px-3 py-2 rounded-lg bg-error-container text-error" data-id="${escape(r.id)}">Hoàn tiền thất bại</button>` : ''}</div></article>`).join('');
      $('manager-refund-empty').hidden = refundRows.length > 0;
      const staffRows = staffGroups.flatMap(group => group.staff.map(s => ({...s, facilityName:group.facility.name})));
      $('manager-staff-list').innerHTML = staffRows.map(s => `<article class="bg-white rounded-xl p-4 shadow-sm"><strong>${escape(s.name)}</strong><p>${escape(s.facilityName)}</p></article>`).join('');
      $('manager-staff-empty').hidden = staffRows.length > 0;
    } catch (error) {
      headerFacility.textContent = 'Không tải được cơ sở';
      headerInfo.textContent = 'Vui lòng thử lại sau.';
      $('manager-heading').textContent = 'Không tải được dữ liệu quản lý.';
      $('manager-facility-empty').hidden = true;
      $('manager-pending-empty').hidden = true;
      $('manager-awaiting-empty').hidden = true;
      $('manager-refund-empty').hidden = true;
      $('manager-staff-empty').hidden = true;
      message(error.message, true);
    } finally { $('manager-loading').hidden = true; }
  }
  $('manager-facility-filter').addEventListener('change', () => load());
  $('manager-refund-list').addEventListener('click', async event => {
    const button = event.target.closest('.refund-review,.refund-reject,.refund-complete,.refund-failed');
    if (!button || button.disabled) return;
    let path = `/api/manager/deposit-refunds/${button.dataset.id}/review`, body;
    if (button.classList.contains('refund-review')) {
      const amount = prompt('Số tiền cọc duyệt hoàn (VND):', button.dataset.amount);
      if (amount === null) return;
      body = {decision:'APPROVED',approvedAmount:amount,note:'Xem xét thủ công'};
    } else if (button.classList.contains('refund-reject')) {
      const note = prompt('Lý do từ chối hoàn cọc:');
      if (note === null || !note.trim()) return;
      body = {decision:'REJECTED',note:note.trim()};
    } else if (button.classList.contains('refund-failed')) {
      const note = prompt('Lý do giao dịch hoàn tiền thất bại:');
      if (note === null || !note.trim()) return;
      body = {decision:'FAILED',note:note.trim()};
    } else {
      const reference = prompt('Mã giao dịch hoàn tiền đã thực hiện:');
      if (reference === null || !reference.trim()) return;
      path = `/api/manager/deposit-refunds/${button.dataset.id}/complete`;
      body = {transactionReference:reference.trim()};
    }
    button.disabled = true;
    try { await api(path, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}); await load(); message('Đã cập nhật yêu cầu hoàn cọc.'); }
    catch (error) { message(error.message,true); button.disabled = false; }
  });
  $('manager-pending-list').addEventListener('click', async event => {
    const reject = event.target.closest('.reject-reservation');
    if (reject && !reject.disabled) {
      const reason = prompt('Lý do từ chối yêu cầu:');
      if (reason === null) return;
      reject.disabled = true;
      try {
        await api(`/api/manager/reservations/${reject.dataset.id}/reject`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({rejectionReason:reason})});
        await load();
        message('Đã từ chối yêu cầu.');
      } catch (error) { message(error.message, true); reject.disabled = false; }
      return;
    }
    const button = event.target.closest('.confirm-reservation');
    if (!button || button.disabled || !confirm('Xác nhận yêu cầu và tạo hợp đồng cùng hai khoản phải thu?')) return;
    button.disabled = true;
    try {
      const contract = await api(`/api/manager/reservations/${button.dataset.id}/confirm`, {method:'POST'});
      await load();
      message(`Đã tạo hợp đồng ${contract.contractNumber}.`);
    } catch (error) { message(error.message, true); button.disabled = false; }
  });
  SafeBoxAuth.requireRole('FACILITY_MANAGER').then(authorized => {
    if (!authorized) return;
    const user = SafeBoxAuth.getCurrentUser();
    if (user?.role !== 'FACILITY_MANAGER') return;
    if (headerUser) headerUser.textContent = user.fullName;
    load();
  });
})();
