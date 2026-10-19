(function () {
  const $ = id => document.getElementById(id);
  const navigation = {
    'trang-chu':'/stitch/trang_ch_safebox_storage.html',
    'tim-kho':'/stitch/t_m_kho_l_u_tr_safebox_storage.html',
    'co-so':'/stitch/t_m_kho_l_u_tr_safebox_storage.html',
    'yeu-cau-cua-toi':'/stitch/y_u_c_u_c_a_t_i_safebox_storage.html'
  };
  document.querySelectorAll('header a[data-path]').forEach(link => {
    if (navigation[link.dataset.path]) link.href = navigation[link.dataset.path];
    if (link.dataset.path === 'tai-khoan') link.hidden = true;
  });
  document.querySelectorAll('footer span.cursor-pointer').forEach(item => item.classList.remove('cursor-pointer'));
  const money = value => new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value || 0);
  const {formatDateVN, formatDateTimeVN, toISODate, todayISODateVN, formatAppointmentTime, appointmentActions} = SafeBoxUc12Time;
  const labels = {DEPOSIT:'Tiền cọc', INITIAL_RENT:'Tiền thuê ban đầu', REMAINING_RENT:'Phần tiền thuê còn lại', SUCCESS:'Đã thanh toán', FAILED:'Thanh toán thất bại', PENDING:'Chờ xác nhận', SCHEDULED:'Đã đặt lịch', COMPLETED:'Hoàn thành', CANCELLED:'Đã hủy', REQUESTED:'Đã gửi yêu cầu', UNDER_REVIEW:'Đang xét duyệt', APPROVED:'Đã duyệt, chờ hoàn tiền', REJECTED:'Từ chối', CASH:'Tiền mặt', BANK_TRANSFER:'Chuyển khoản ngân hàng', DEMO_ONLINE_PAYMENT:'Thanh toán trực tuyến'};
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let reservations = [], selected = null, summary = null, appointment = null, busy = false;
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const result = await response.json().catch(() => null);
    if (!response.ok) throw new Error(result?.message || `Yêu cầu thất bại (HTTP ${response.status}).`);
    return result;
  }
  function message(value, error = false) {
    $('sprint2-message').textContent = value;
    $('sprint2-message').className = `rounded-lg p-3 ${error ? 'bg-error-container text-error' : 'bg-tertiary-fixed text-on-tertiary-fixed'}`;
    $('sprint2-message').hidden = !value;
  }
  function card(r) {
    return `<article class="reservation-card bg-surface-container-lowest rounded-xl p-6 shadow-sm space-y-3" data-status="${escape(r.status)}" data-search="${escape((r.requestCode+' '+r.facilityName+' '+r.typeName).toLowerCase())}">
      <div class="flex flex-wrap justify-between gap-3"><div><h2 class="font-headline-sm text-headline-sm text-primary">${escape(r.requestCode)}</h2><p class="text-on-surface-variant">${escape(r.facilityName)} · ${escape(r.typeName)}</p></div><span class="px-3 py-1 rounded-full bg-surface-container text-secondary font-label-badge">${escape(r.statusLabel)}</span></div>
      <div class="flex flex-wrap gap-4 text-on-surface-variant"><span>Thuê: ${formatDateVN(r.startDate)} → ${formatDateVN(r.endDate)} (${escape(r.rentalMonths)} tháng)</span><span>Tiền thuê dự kiến: ${money(r.estimatedRentalAmount)}</span><span>Tiền cọc dự kiến: ${money(r.estimatedDepositAmount)}</span><span>Ngày tạo: ${formatDateTimeVN(r.createdAt)}</span></div>
      <div class="flex flex-wrap gap-3">${r.status === 'CONFIRMED' && r.contract ? `<button class="open-contract px-5 py-2.5 rounded-lg bg-secondary text-white font-label-button" data-id="${escape(r.reservationId)}">Thanh toán và lịch nhận kho</button>` : r.status === 'CONFIRMED' ? '<span class="text-on-surface-variant">Hợp đồng đang chờ lập.</span>' : ''}
      ${r.status === 'PENDING' ? `<button class="cancel-reservation px-5 py-2.5 rounded-lg bg-surface-container text-error font-label-button" data-id="${escape(r.reservationId)}">Hủy yêu cầu</button>` : ''}</div>
    </article>`;
  }
  function filter() {
    const status = $('contract-status').value, q = $('contract-search').value.trim().toLowerCase();
    let shown = 0;
    document.querySelectorAll('.reservation-card').forEach(card => {
      const visible = (status === 'ALL' || card.dataset.status === status) && card.dataset.search.includes(q);
      card.hidden = !visible; if (visible) shown++;
    });
    $('contract-empty').hidden = shown > 0;
  }
  async function load() {
    $('contract-loading').hidden = false;
    $('contract-empty').hidden = true;
    try {
    const requests = await api('/api/reservations/my');
    let contracts = [];
    try { contracts = await api('/api/contracts/my'); }
    catch (error) { message(`Đã tải yêu cầu, nhưng chưa tải được hợp đồng: ${error.message}`, true); }
    const byReservation = new Map(contracts.map(c => [c.reservationId, c]));
    reservations = requests.map(r => ({...r, contract:byReservation.get(r.reservationId)}));
    const created = new URLSearchParams(location.search).get('created');
    if (created && reservations.some(r => r.reservationId === created)) {
      message('Đã gửi yêu cầu đặt kho. Vui lòng chờ quản lý cơ sở xác nhận.');
      const url = new URL(location.href);
      url.searchParams.delete('created');
      history.replaceState(null, '', url.pathname + url.search);
    }
    const badge = document.querySelector('header [data-path="yeu-cau-cua-toi"] span:last-child');
    if (badge) badge.textContent = reservations.length;
    $('contract-list').innerHTML = reservations.map(card).join('');
    filter();
    const requestedContract = new URLSearchParams(location.search).get('contractId');
    if (requestedContract && !selected) {
      const matching = reservations.find(r => r.status === 'CONFIRMED' && r.contract?.contractId === requestedContract);
      if (matching) selected = matching;
    }
    if (selected) {
      const updated = reservations.find(r => r.reservationId === selected.reservationId);
      if (updated?.status === 'CONFIRMED') { selected = updated; await detail(); }
      else { selected = null; $('contract-detail').hidden = true; }
    }
    } finally { $('contract-loading').hidden = true; }
  }
  function component(name, value, type, available = true) {
    return `<div class="bg-surface-container-low rounded-xl p-5 space-y-2"><h3 class="font-headline-sm text-headline-sm">${name}</h3><p>Phải trả: <strong>${money(value.amountDue)}</strong></p><p>Đã thanh toán: <strong>${money(value.paidAmount)}</strong></p><p>Còn lại: <strong>${money(value.remainingAmount)}</strong></p><span class="px-3 py-1 rounded-full bg-surface-container-high text-secondary">${value.remainingAmount <= 0 ? 'Đã thanh toán' : value.paidAmount > 0 ? 'Thanh toán một phần' : 'Chưa thanh toán'}</span>
      <div>${type === 'DEPOSIT' ? `<button class="pay-button px-4 py-2 rounded-lg bg-secondary text-white disabled:opacity-50" data-type="${type}" ${value.remainingAmount <= 0 ? 'disabled' : ''}>${value.remainingAmount <= 0 ? 'Đã thanh toán' : 'Thanh toán tiền cọc'}</button>` : `<span class="text-on-surface-variant">${available ? 'Nhân viên được phân công sẽ xử lý khoản này vào ngày nhận kho.' : 'Chưa đến hạn thanh toán.'}</span>`}</div></div>`;
  }
  async function detail() {
    $('detail-loading').hidden = false;
    try {
    const id = selected.contract.contractId, base = `/api/contracts/${id}`;
    const [s, history, a, refunds] = await Promise.all([api(base+'/payments/summary'), api(base+'/payments'), api(base+'/pickup-appointment', {cache:'no-store'}), api(base+'/deposit-refunds', {cache:'no-store'})]);
    summary = s; appointment = a;
    $('contract-detail').hidden = false;
    $('detail-title').textContent = `Hợp đồng ${selected.contract.contractNumber} · ${selected.contract.facilityName}`;
    $('payment-components').innerHTML = component('Tiền cọc', s.deposit, 'DEPOSIT') + component(s.termsVersion === 'v2' ? 'Phần tiền thuê còn lại' : 'Tiền thuê ban đầu', s.initialRent, s.rentPaymentType, s.remainingPaymentAvailable);
    $('payment-breakdown').textContent = s.termsVersion === 'v2' ? `Tiền thuê trước VAT: ${money(s.subtotal)} · VAT 10%: ${money(s.vatAmount)} · Tổng hợp đồng: ${money(s.contractTotal)} · Ngày đến hạn phần còn lại: ${s.remainingDueDate ? formatDateVN(s.remainingDueDate) : 'Chưa có lịch nhận kho'}` : `Tổng các khoản theo hóa đơn: ${money(s.contractTotal)}.`;
    $('payment-ready').textContent = s.readyForHandover ? 'Đã thanh toán đủ các khoản. Bàn giao thực tế thuộc quy trình tiếp theo.' : s.deposit.remainingAmount > 0 ? a?.status === 'SCHEDULED' ? 'Cần thanh toán đủ tiền cọc để đổi lịch. Lịch đã đặt vẫn được giữ nguyên.' : 'Cần thanh toán đủ tiền cọc để đặt lịch nhận kho.' : 'Đã đủ cọc; phần còn lại đến hạn vào ngày nhận kho.';
    $('refund-history').innerHTML = refunds.length ? refunds.map(r => `<p class="bg-surface-container-low p-3 rounded-lg">${labels[r.status] || escape(r.status)} · Gửi ${formatDateTimeVN(r.createdAt)}${r.reviewerId ? ` · Xét duyệt ${formatDateTimeVN(r.updatedAt)}` : ''}${r.completedAt ? ` · Hoàn ${formatDateTimeVN(r.completedAt)}` : ''} · Đề nghị ${money(r.requestedAmount)}${r.approvedAmount ? ` · Duyệt ${money(r.approvedAmount)}` : ''} · ${escape(r.reason)}</p>`).join('') : '<p>Chưa có yêu cầu hoàn cọc.</p>';
    $('refund-form').hidden = s.deposit.paidAmount <= 0 || refunds.some(r => ['REQUESTED','UNDER_REVIEW','APPROVED'].includes(r.status));
    $('refund-amount').max = Math.max(0, s.deposit.paidAmount);
    $('payment-history').innerHTML = history.length ? history.map(p => `<tr class="border-b border-outline-variant/30"><td class="p-3">${escape(p.transactionCode)}</td><td class="p-3">${labels[p.paymentType] || escape(p.paymentType)}</td><td class="p-3">${money(p.amount)}</td><td class="p-3">${labels[p.paymentMethod] || 'Chưa rõ phương thức (dữ liệu cũ)'}</td><td class="p-3">${labels[p.status] || escape(p.status)}${p.failureReason ? `<br><small>${escape(p.failureReason)}</small>` : ''}${p.status === 'FAILED' ? `<br><button class="retry text-secondary underline" data-type="${p.paymentType}" data-amount="${p.amount}">Thử lại bằng chuyển khoản</button>` : ''}</td><td class="p-3">${formatDateTimeVN(p.createdAt)}</td></tr>`).join('') : '<tr><td class="p-3" colspan="6">Chưa có giao dịch.</td></tr>';
    $('appointment-info').innerHTML = a ? `<p>Ngày giờ: <strong>${formatDateTimeVN(a.appointmentTime)}</strong></p><p>Trạng thái: ${labels[a.status]}</p><p>Cơ sở: ${escape(selected.contract.facilityName)}</p><p>Mã kho: ${escape(selected.contract.unitNumber)}</p>${a.assignedStaffName ? `<p>Nhân viên phụ trách: ${escape(a.assignedStaffName)}</p>` : ''}` : '<p>Chưa có lịch nhận kho.</p>';
    const actions = appointmentActions(a, s.termsVersion, s.deposit.remainingAmount);
    $('appointment-create').hidden = !!a && a.status === 'SCHEDULED';
    $('appointment-create').disabled = s.termsVersion === 'v2' && s.deposit.remainingAmount > 0;
    $('appointment-change').hidden = !actions.canCancel; $('appointment-cancel').hidden = !actions.canCancel;
    $('appointment-change').disabled = !actions.canChange;
    } finally { $('detail-loading').hidden = true; }
  }
  function openPayment(type, amount) {
    if (type !== 'DEPOSIT') { message('Nhân viên được phân công sẽ xử lý khoản tiền thuê còn lại vào ngày nhận kho.', true); return; }
    const remaining = summary[type === 'DEPOSIT' ? 'deposit' : 'initialRent'].remainingAmount;
    if (remaining <= 0) { message('Khoản này đã được thanh toán đủ.', true); return; }
    $('payment-type').value = type; $('payment-type-label').textContent = labels[type];
    $('payment-amount').max = remaining; $('payment-amount').value = Math.min(Number(amount) || remaining, remaining);
    $('payment-limit').textContent = `Còn thiếu: ${money(remaining)}`;
    $('payment-dialog').showModal();
  }
  function openAppointment() {
    if (summary?.termsVersion === 'v2' && summary.deposit.remainingAmount > 0) {
      message('Vui lòng thanh toán đủ tiền cọc trước khi đặt hoặc đổi lịch. Lịch hiện có vẫn được giữ nguyên.', true);
      return;
    }
    $('appointment-dialog-title').textContent = appointment?.status === 'SCHEDULED' ? 'Đổi lịch nhận kho' : 'Đặt lịch nhận kho';
    $('appointment-context').textContent = `${selected.contract.facilityName} · ${selected.contract.unitNumber} · Thuê từ ${formatDateVN(selected.contract.startDate)} đến ${formatDateVN(selected.contract.endDate)}`;
    const today = todayISODateVN();
    $('appointment-date').dataset.min = selected.contract.startDate > today ? selected.contract.startDate : today;
    $('appointment-date').dataset.max = selected.contract.endDate;
    $('appointment-date').value = formatDateVN(appointment?.status === 'SCHEDULED' ? appointment.appointmentTime.slice(0,10) : $('appointment-date').dataset.min);
    $('appointment-dialog').showModal();
  }
  document.addEventListener('click', async event => {
    const open = event.target.closest('.open-contract');
    if (open) { selected = reservations.find(r => r.reservationId === open.dataset.id); try { await detail(); $('contract-detail').scrollIntoView({behavior:'smooth'}); } catch (e) { message(e.message,true); } }
    const pay = event.target.closest('.pay-button'); if (pay) openPayment(pay.dataset.type);
    const retry = event.target.closest('.retry'); if (retry) openPayment(retry.dataset.type, retry.dataset.amount);
    const cancel = event.target.closest('.cancel-reservation');
    if (cancel && confirm('Bạn có chắc muốn hủy yêu cầu thuê kho này?')) {
      try { await api(`/api/reservations/my/${cancel.dataset.id}/cancel`, {method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}); message('Đã hủy yêu cầu.'); await load(); }
      catch (e) { message(e.message,true); }
    }
  });
  $('contract-status').addEventListener('change', filter);
  $('contract-search').addEventListener('input', filter);
  $('payment-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; busy = true;
    const button = $('payment-submit'); button.disabled = true;
    try {
      const p = await api(`/api/contracts/${selected.contract.contractId}/payments`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paymentType:$('payment-type').value, amount:$('payment-amount').value, paymentMethod:$('payment-method').value, idempotencyKey:crypto.randomUUID()})});
      $('payment-dialog').close(); message(p.status === 'SUCCESS' ? 'Đã ghi nhận thanh toán tiền cọc.' : p.status === 'FAILED' ? `Thanh toán thất bại. ${p.failureReason || ''}` : 'Giao dịch đang chờ xác nhận.'); await detail();
    } catch (e) { message(e.message,true); } finally { busy = false; button.disabled = false; }
  });
  $('appointment-create').addEventListener('click', openAppointment);
  $('refund-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; busy = true; $('refund-submit').disabled = true;
    try {
      await api(`/api/contracts/${selected.contract.contractId}/deposit-refunds`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({amount:$('refund-amount').value,reason:$('refund-reason').value.trim()})});
      $('refund-form').reset(); await detail(); message('Đã gửi yêu cầu hoàn cọc để quản lý xem xét.');
    } catch (e) { message(e.message, true); } finally { busy = false; $('refund-submit').disabled = false; }
  });
  $('appointment-change').addEventListener('click', openAppointment);
  $('appointment-cancel').addEventListener('click', async () => {
    if (!confirm('Bạn có chắc muốn hủy lịch nhận kho?')) return;
    try { await api(`/api/contracts/${selected.contract.contractId}/pickup-appointment`, {method:'DELETE'}); message('Đã hủy lịch nhận kho.'); await detail(); } catch (e) { message(e.message,true); }
  });
  $('appointment-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; busy = true; $('appointment-submit').disabled = true;
    let accepted = false;
    try {
      const method = appointment?.status === 'SCHEDULED' ? 'PUT' : 'POST';
      const contractId = selected.contract.contractId;
      const path = `/api/contracts/${contractId}/pickup-appointment`;
      const selectedDate = toISODate($('appointment-date').value);
      if (selectedDate < $('appointment-date').dataset.min || selectedDate > $('appointment-date').dataset.max)
        throw new Error(`Ngày nhận kho phải trong khoảng ${formatDateVN($('appointment-date').dataset.min)} – ${formatDateVN($('appointment-date').dataset.max)}.`);
      const appointmentTime = formatAppointmentTime(selectedDate, $('appointment-slot').value);
      const saved = await api(path, {method, headers:{'Content-Type':'application/json'}, body:JSON.stringify({appointmentTime})});
      accepted = true;
      if (!saved?.id || saved.contractId !== contractId || saved.status !== 'SCHEDULED')
        throw new Error('Server chưa xác nhận lịch nhận kho hợp lệ. Vui lòng tải lại trang để kiểm tra.');
      const persisted = await api(path, {cache:'no-store'});
      if (!persisted || persisted.id !== saved.id || persisted.contractId !== contractId || persisted.status !== 'SCHEDULED' || persisted.appointmentTime !== saved.appointmentTime)
        throw new Error('Chưa xác minh được lịch đã lưu. Vui lòng tải lại trang trước khi thử lại.');
      await detail();
      $('appointment-dialog').close();
      message(method === 'POST' ? 'Đã đặt lịch nhận kho.' : 'Đã đổi lịch nhận kho.');
    } catch (e) {
      if (accepted) $('appointment-dialog').close();
      message(accepted ? `Server đã nhận yêu cầu nhưng chưa xác minh được lịch đã lưu: ${e.message}` : e.message, true);
    } finally { busy = false; $('appointment-submit').disabled = false; }
  });
  SafeBoxAuth.requireRole('CUSTOMER').then(authorized => { if (authorized) load().catch(e => message(e.message,true)); });
})();
