(function () {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value || 0);
  const {formatDateVN, formatDateTimeVN, toISODate, todayISODateVN} = SafeBoxUc12Time;
  const uniqueStaff = rows => [...new Map(rows.map(row => [row.id, row])).values()];
  const labels = {CHECK_IN:'Nhận kho', RETURN:'Trả kho', SCHEDULED:'Chờ nhận', COMPLETED:'Hoàn thành', CANCELLED:'Đã hủy'};
  let currentContract = null, summary = null, busy = false, staffOptions = [], currentHandoverContract = null;
  function taskActions(r) {
    const role = SafeBoxAuth.getCurrentUser()?.role;
    const handover = role === 'FACILITY_STAFF' && r.status === 'SCHEDULED' && r.assignedStaffId
      ? `<button type="button" class="open-handover text-secondary underline" data-id="${escape(r.contractId)}" data-code="${escape(r.contractCode)}">Thực hiện bàn giao</button>` : '';
    const assignment = role === 'FACILITY_STAFF' ? `<button class="open-payment text-secondary underline" data-id="${escape(r.contractId)}">Xem khoản thu</button>` : r.status !== 'SCHEDULED' ? '—' : r.assignedStaffId ? `<span>Đã phân công · ${escape(r.assignedStaffName || 'Nhân viên')}</span><br><button type="button" class="show-staff-options text-secondary underline" data-id="${escape(r.contractId)}" data-facility="${escape(r.facilityId)}">Đổi nhân viên</button><div class="staff-assignment-control" hidden></div>` : '<span>Chưa phân công</span> · <a class="text-secondary underline" href="#assignment-section">Phân công nhân viên</a>';
    return `${handover}${handover && assignment ? '<br>' : ''}${assignment}`;
  }
  function showSelectedDate() {
    const iso = toISODate($('task-date').value);
    $('selected-date-label').textContent = formatDateVN(iso);
    $('task-date-hint').textContent = `Ngày lọc: ${formatDateVN(iso)}`;
  }
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Không thể tải dữ liệu.');
    return data;
  }
  function message(value, error) {
    $('task-message').hidden = !value; $('task-message').textContent = value;
    $('task-message').className = `p-3 rounded-lg ${error ? 'bg-error-container text-error' : 'bg-tertiary-fixed text-on-tertiary-fixed'}`;
  }
  async function load() {
    $('task-loading').hidden = false;
    $('task-empty').hidden = true;
    try {
    const params = new URLSearchParams();
    params.set('date', toISODate($('task-date').value));
    for (const [id,key] of [['task-type','taskType'],['task-status','status'],['task-staff','staffId'],['task-facility','facilityId']]) if ($(id).value.trim()) params.set(key,$(id).value.trim());
    const rows = await api('/api/staff/tasks?' + params);
    showSelectedDate();
    $('task-total').textContent = rows.length;
    $('task-pending').textContent = rows.filter(r => r.status === 'SCHEDULED').length;
    $('task-completed').textContent = rows.filter(r => r.status === 'COMPLETED').length;
    $('task-checkins').textContent = rows.filter(r => r.taskType === 'CHECK_IN').length;
    $('task-empty').hidden = rows.length > 0;
    $('task-rows').innerHTML = rows.map(r => `<tr class="border-b border-outline-variant/30"><td class="p-4">${formatDateTimeVN(r.appointmentTime)}</td><td class="p-4">${labels[r.taskType]}</td><td class="p-4">${escape(r.contractCode)}</td><td class="p-4">${escape(r.customerName)}</td><td class="p-4">${escape(r.storageUnitCode || 'Chưa phân kho')}</td><td class="p-4">${escape(r.facilityName)}</td><td class="p-4">${escape(r.assignedStaffName || 'Chưa phân công')}</td><td class="p-4">${r.readyForHandover ? 'Đã thanh toán đủ' : 'Chưa đủ điều kiện bàn giao'}</td><td class="p-4">${labels[r.status]}</td><td class="p-4">${taskActions(r)}</td></tr>`).join('');
    if (SafeBoxAuth.getCurrentUser()?.role === 'FACILITY_MANAGER') await loadAwaiting();
    } catch (e) {
      $('task-rows').replaceChildren();
      for (const id of ['task-total','task-pending','task-completed','task-checkins']) $(id).textContent = '—';
      $('task-empty').hidden = true;
      message(e.message, true);
    } finally { $('task-loading').hidden = true; }
    if (SafeBoxAuth.getCurrentUser()?.role === 'FACILITY_STAFF') await loadUpcoming();
  }
  async function loadUpcoming() {
    $('staff-upcoming-loading').hidden = false;
    $('staff-upcoming-empty').hidden = true;
    try {
      const rows = await api('/api/staff/tasks/upcoming');
      $('staff-upcoming-list').innerHTML = rows.map(r => `<article class="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-surface-container-low p-3"><span>${formatDateTimeVN(r.appointmentTime)} · ${escape(r.facilityName)} · ${escape(r.contractCode)}</span><button type="button" class="jump-task-date text-secondary underline" data-date="${escape(r.appointmentTime?.slice(0,10))}">Xem ngày</button></article>`).join('');
      $('staff-upcoming-empty').hidden = rows.length > 0;
    } catch (e) {
      $('staff-upcoming-list').replaceChildren();
      message(e.message, true);
    } finally { $('staff-upcoming-loading').hidden = true; }
  }
  async function loadAwaiting() {
    $('awaiting-loading').hidden = false;
    $('awaiting-empty').hidden = true;
    try {
      const selected = $('task-facility').value;
      const rows = await api('/api/manager/appointments/awaiting-assignment' + (selected ? '?facilityId=' + encodeURIComponent(selected) : ''));
      $('awaiting-list').innerHTML = rows.map(r => {
        return `<article class="rounded-lg border border-outline-variant p-4 space-y-2"><div class="flex flex-wrap justify-between gap-2"><strong>${escape(r.contractCode)}</strong><span>${escape(r.facilityName)}</span></div><p>${escape(r.customerName)} · Kho ${escape(r.storageUnitCode)} · ${formatDateTimeVN(r.appointmentTime)}</p><p>Cọc đã thu ${money(r.depositPaidAmount)}, còn ${money(r.depositRemainingAmount)} · Tiền thuê còn ${money(r.remainingRentAmount)} · Chờ nhận · Chưa phân công</p><button type="button" class="show-staff-options px-4 py-2 rounded-lg bg-secondary text-white" data-id="${escape(r.contractId)}" data-facility="${escape(r.facilityId)}">Phân công nhân viên</button><div class="staff-assignment-control" hidden></div></article>`;
      }).join('');
      $('awaiting-empty').hidden = rows.length > 0;
    } catch (e) {
      $('awaiting-list').replaceChildren();
      message(e.message, true);
    } finally { $('awaiting-loading').hidden = true; }
  }
  async function paymentDetail() {
    const base = `/api/contracts/${currentContract}/payments`;
    const [s, history] = await Promise.all([api(base+'/summary'), api(base)]);
    summary = s;
    $('task-payment-summary').textContent = `Tiền cọc còn ${money(s.deposit.remainingAmount)} · ${s.termsVersion === 'v2' ? 'Phần còn lại' : 'Tiền thuê ban đầu'} còn ${money(s.initialRent.remainingAmount)}${s.termsVersion === 'v2' ? ` · Đến hạn: ${s.remainingDueDate ? formatDateVN(s.remainingDueDate) : 'chưa đặt lịch'}` : ''}`;
    const payable = [];
    if (s.initialRent.remainingAmount > 0 && s.remainingPaymentAvailable)
      payable.push(`<option value="${s.rentPaymentType}">${s.termsVersion === 'v2' ? 'Phần tiền thuê còn lại' : 'Tiền thuê ban đầu'}</option>`);
    $('task-payment-type').innerHTML = payable.join('') || '<option value="">Chưa có khoản đến hạn</option>';
    $('task-payment-type').disabled = !payable.length;
    $('task-payment-amount').disabled = !payable.length;
    $('task-payment-method').disabled = !payable.length;
    $('task-payment-submit').disabled = !payable.length;
    $('pending-transfers').innerHTML = history.filter(p => p.status === 'PENDING' && p.paymentMethod === 'BANK_TRANSFER' && p.paymentType === s.rentPaymentType).map(p => `<div class="flex justify-between gap-3 p-2 bg-surface-container-low rounded-lg my-2"><span>${escape(p.transactionCode)} · ${money(p.amount)}</span><button class="confirm-transfer text-secondary underline" data-id="${p.id}">Xác nhận</button></div>`).join('') || '<p>Không có chuyển khoản chờ xác nhận.</p>';
    $('task-payment-dialog').showModal();
  }
  $('task-filters').addEventListener('submit', event => { event.preventDefault(); load().catch(e => message(e.message,true)); });
  $('task-date').addEventListener('change', () => { load(); });
  $('task-facility').addEventListener('change', async () => {
    $('task-staff').innerHTML = '<option value="">Tất cả nhân viên</option>';
    staffOptions = [];
    try {
      if ($('task-facility').value) {
        staffOptions = uniqueStaff(await api('/api/manager/staff?facilityId=' + encodeURIComponent($('task-facility').value)));
        $('task-staff').innerHTML += staffOptions.map(s => `<option value="${s.id}">${escape(s.name)}</option>`).join('');
      }
      await load();
    } catch (e) { message(e.message,true); }
  });
  document.addEventListener('change', async event => {
    const select = event.target.closest('.assign-staff');
    if (!select || !select.value) return;
    try { await api(`/api/manager/contracts/${select.dataset.id}/pickup-appointment/assignment`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({staffId:select.value})}); message('Đã phân công nhân viên.'); await load(); }
    catch (e) { message(e.message,true); }
  });
  document.addEventListener('click', async event => {
    const openHandover = event.target.closest('.open-handover');
    if (openHandover) {
      currentHandoverContract = openHandover.dataset.id;
      $('handover-contract-label').textContent = `Hợp đồng: ${openHandover.dataset.code}`;
      $('handover-dialog').showModal();
      return;
    }
    const jump = event.target.closest('.jump-task-date');
    if (jump) { $('task-date').value = formatDateVN(jump.dataset.date); $('task-status').value = ''; $('task-type').value = ''; await load(); return; }
    const showStaff = event.target.closest('.show-staff-options');
    if (showStaff && !showStaff.disabled) {
      showStaff.disabled = true;
      try {
        const staff = uniqueStaff(await api('/api/manager/staff?facilityId=' + encodeURIComponent(showStaff.dataset.facility)));
        const control = showStaff.nextElementSibling;
        control.innerHTML = staff.length
          ? `<label>Nhân viên <select class="choose-staff p-2 bg-surface-container-low rounded-lg"><option value="">Chọn nhân viên</option>${staff.map(s => `<option value="${escape(s.id)}">${escape(s.name)}</option>`).join('')}</select></label><button type="button" class="confirm-staff-assignment ml-2 px-4 py-2 rounded-lg bg-secondary text-white" data-id="${escape(showStaff.dataset.id)}">Xác nhận</button>`
          : '<p>Chưa có nhân viên ACTIVE tại cơ sở này.</p>';
        control.hidden = false;
      } catch (e) { message(e.message,true); showStaff.disabled = false; }
      return;
    }
    const confirmStaff = event.target.closest('.confirm-staff-assignment');
    if (confirmStaff && !confirmStaff.disabled) {
      const staffId = confirmStaff.parentElement.querySelector('.choose-staff')?.value;
      if (!staffId) { message('Vui lòng chọn nhân viên.',true); return; }
      confirmStaff.disabled = true;
      try {
        await api(`/api/manager/contracts/${confirmStaff.dataset.id}/pickup-appointment/assignment`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({staffId})});
        message('Đã phân công nhân viên. Lịch vẫn ở trạng thái SCHEDULED.');
        await load();
      } catch (e) { message(e.message,true); confirmStaff.disabled = false; }
      return;
    }
    const open = event.target.closest('.open-payment');
    if (open) { currentContract = open.dataset.id; try { await paymentDetail(); } catch (e) { message(e.message,true); } }
    const confirm = event.target.closest('.confirm-transfer');
    if (confirm && !busy && window.confirm('Bạn đã đối chiếu và xác minh khoản chuyển này đã vào tài khoản?')) { busy = true; confirm.disabled = true; try { await api(`/api/staff/contracts/${currentContract}/payments/${confirm.dataset.id}/confirm-transfer`,{method:'POST'}); message('Đã xác nhận chuyển khoản.'); await paymentDetail(); await load(); } catch (e) { message(e.message,true); } finally { busy = false; confirm.disabled = false; } }
  });
  $('task-payment-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; busy = true; $('task-payment-submit').disabled = true;
    const type = $('task-payment-type').value;
    if (!type) { message('Chưa có khoản đến hạn để ghi nhận.',true); busy = false; $('task-payment-submit').disabled = true; return; }
    const remaining = summary.initialRent.remainingAmount;
    if (type !== summary.rentPaymentType || !summary.remainingPaymentAvailable) { message('Phần tiền còn lại chưa đến hạn.',true); busy = false; $('task-payment-submit').disabled = false; return; }
    const amount = Number($('task-payment-amount').value);
    if (amount <= 0 || amount > remaining) { message('Số tiền vượt quá khoản còn thiếu.',true); busy = false; $('task-payment-submit').disabled = false; return; }
    if ($('task-payment-method').value === 'CASH' && !window.confirm(`Xác nhận đã nhận ${money(amount)} tiền mặt từ khách hàng?`)) {
      busy = false; $('task-payment-submit').disabled = false; return;
    }
    try { const payment = await api(`/api/contracts/${currentContract}/payments`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({paymentType:type, amount, paymentMethod:$('task-payment-method').value,idempotencyKey:crypto.randomUUID()})}); message(payment.status === 'SUCCESS' ? 'Đã ghi nhận tiền mặt đã nhận.' : 'Chuyển khoản đang chờ xác minh.'); $('task-payment-dialog').close(); await load(); }
    catch (e) { message(e.message,true); } finally { busy = false; $('task-payment-submit').disabled = false; }
  });
  $('handover-form').addEventListener('submit', async event => {
    event.preventDefault(); if (busy || !currentHandoverContract) return; busy = true; $('handover-submit').disabled = true;
    const payload = {conditionStatus:$('handover-condition').value, accessMethod:$('handover-access-method').value.trim(), accessIdentifier:$('handover-access-identifier').value.trim(), customerSignature:$('handover-customer-signature').value.trim(), staffSignature:$('handover-staff-signature').value.trim() || null, note:$('handover-note').value.trim() || null, temperature:null, humidity:null, estimatedRepairCost:Number($('handover-repair-cost').value || 0)};
    try { await api(`/api/staff/contracts/${currentHandoverContract}/handover`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload)}); message('Đã lưu biên bản và hoàn tất bàn giao kho.'); $('handover-dialog').close(); $('handover-form').reset(); await load(); }
    catch (e) { message(e.message, true); } finally { busy = false; $('handover-submit').disabled = false; }
  });
  $('logout').addEventListener('click', () => SafeBoxAuth.logout());
  $('task-date').value = formatDateVN(todayISODateVN());
  showSelectedDate();
  SafeBoxAuth.requireRole('FACILITY_STAFF','FACILITY_MANAGER').then(async authorized => {
    if (!authorized) return;
    const role = SafeBoxAuth.getCurrentUser()?.role;
    if (!['FACILITY_STAFF', 'FACILITY_MANAGER'].includes(role)) return;
    const manager = role === 'FACILITY_MANAGER';
    $('manager-home').hidden = !manager; $('staff-filter-wrap').hidden = !manager; $('facility-filter-wrap').hidden = !manager;
    $('assignment-section').hidden = !manager;
    document.querySelector('a[href="/staff-tasks.html#assignment-section"]').hidden = !manager;
    $('handover-section').hidden = manager;
    document.querySelector('a[href="/staff-tasks.html#handover-section"]').hidden = manager;
    $('awaiting-assignment-section').hidden = !manager;
    $('staff-upcoming-section').hidden = manager;
    try {
      const facilities = await api(manager ? '/api/manager/facilities' : '/api/staff/facilities');
      $('task-facilities').textContent = facilities.length
        ? `Cơ sở được phân công: ${facilities.map(f => `${f.name} · ${f.address}`).join('; ')}`
        : 'Bạn chưa được phân công cơ sở làm việc. Vui lòng liên hệ quản trị viên.';
      if (manager) {
        $('task-facility').innerHTML += facilities.map(f => `<option value="${f.facilityId}">${escape(f.name)}</option>`).join('');
        const query = new URLSearchParams(location.search);
        const requestedFacility = query.get('facilityId');
        const requestedDate = query.get('date');
        if (facilities.some(f => f.facilityId === requestedFacility)) {
          $('task-facility').value = requestedFacility;
          staffOptions = uniqueStaff(await api('/api/manager/staff?facilityId=' + encodeURIComponent(requestedFacility)));
          $('task-staff').innerHTML += staffOptions.map(s => `<option value="${escape(s.id)}">${escape(s.name)}</option>`).join('');
        }
        if (/^\d{4}-\d{2}-\d{2}$/.test(requestedDate || '')) $('task-date').value = formatDateVN(requestedDate);
      }
      await load();
    } catch (e) { $('task-facilities').textContent = 'Không tải được thông tin cơ sở được phân công.'; message(e.message,true); }
  });
})();
