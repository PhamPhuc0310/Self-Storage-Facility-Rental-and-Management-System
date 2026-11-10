(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const statusText = {REQUESTED:'Đã gửi, chờ trả kho và kiểm tra', SETTLEMENT_APPROVED:'Đã duyệt quyết toán', REFUNDED:'Đã hoàn tất quyết toán', REJECTED:'Đã từ chối'};
  const money = value => `${Number(value || 0).toLocaleString('vi-VN')} đ`;
  let role, selectedId, contracts = new Map();
  function friendlyError(value) {
    const text = String(value || '');
    if (/already in progress/i.test(text)) return 'Hợp đồng đã có yêu cầu trả trước hạn đang xử lý.';
    if (/return appointment already exists/i.test(text)) return 'Hợp đồng đã có lịch trả kho. Vui lòng kiểm tra lịch hiện tại.';
    if (/active contract/i.test(text)) return 'Chỉ hợp đồng đang thuê mới được yêu cầu trả trước hạn.';
    if (/future and before the contract end date/i.test(text)) return 'Thời gian trả phải ở tương lai và trước ngày kết thúc hợp đồng.';
    if (/deposit refund request/i.test(text)) return 'Cần xử lý yêu cầu hoàn cọc đang mở trước khi trả trước hạn.';
    return text;
  }
  async function api(path, options = {}) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw Error(friendlyError(data?.message || `Không thể xử lý yêu cầu (${response.status}).`));
    return data;
  }
  function message(value) { $('message').textContent = value; }
  function label(row) {
    const contract = contracts.get(row.contractId);
    return contract?.contractNumber || `Hợp đồng ${String(row.contractId).slice(0, 8)}`;
  }
  function card(row) {
    const appointment = row.status === 'REQUESTED' && role === 'CUSTOMER'
      ? `<a class="sb-button sb-secondary" href="/stitch/dat_lich_tra_kho_safebox_storage.html?contractId=${encodeURIComponent(row.contractId)}">Đặt lịch trả kho</a>` : '';
    const review = ['REQUESTED','SETTLEMENT_APPROVED','REFUNDED'].includes(row.status)
      ? `<button class="sb-button review" data-id="${esc(row.id)}" type="button">Kiểm tra quyết toán</button>` : '';
    return `<article class="sb-card" style="margin:.75rem 0"><h3>${esc(label(row))}</h3><p><span class="sb-badge">${esc(statusText[row.status] || row.status)}</span></p><p>Thời gian đề xuất: ${esc(SafeBoxUc12Time.formatDateTimeVN(row.scheduledReturnAt))}</p><p>Lý do: ${esc(row.reason || '—')}</p><div class="sb-actions" style="justify-content:flex-start">${appointment}${review}</div></article>`;
  }
  async function load() {
    const rows = await api(role === 'CUSTOMER' ? '/api/customer/early-returns' : '/api/manager/early-returns');
    $('requests').innerHTML = rows.length ? rows.map(card).join('') : '<p class="sb-muted">Chưa có yêu cầu trả kho trước hạn trong phạm vi của bạn.</p>';
  }
  async function review(id) {
    selectedId = id;
    $('approval-form').hidden = true;
    $('refund-button').hidden = true;
    $('refund-confirmation').hidden = true;
    $('settlement-state').textContent = 'Đang kiểm tra điều kiện...';
    if (!$('settlement-dialog').open) $('settlement-dialog').showModal();
    try {
      const state = await api(`/api/${role === 'CUSTOMER' ? 'customer' : 'manager'}/early-returns/${encodeURIComponent(id)}/settlement`);
      const text = {
        PENDING_POLICY:'Chưa có chính sách tài chính trả trước hạn được duyệt và có hiệu lực. Chưa thể quyết toán.',
        POLICY_CONFLICT:'Có chính sách được duyệt chồng lấn. Cần xử lý trước khi quyết toán.',
        PENDING_INSPECTION:'Cần hoàn tất nhận lại kho và biên bản kiểm tra trước khi quyết toán.',
        READY_FOR_APPROVAL:'Đã đủ điều kiện kiểm tra khấu trừ và duyệt quyết toán.'
      };
      $('settlement-state').textContent = text[state.status] || (state.settlement ? `Quyết toán: ${money(state.settlement.depositRefundAmount)} hoàn cọc; ${money(state.settlement.inspectionDeduction)} khấu trừ.` : 'Chưa thể quyết toán.');
      if (state.settlement) {
        const s = state.settlement;
        $('settlement-state').textContent = `Tiền cọc đã thu: ${money(s.depositCollected)}. Hoàn cọc được duyệt: ${money(s.depositRefundAmount)}. Tiền thuê đã trả: ${money(s.rentalCollected)}; hoàn tiền thuê: ${money(s.unusedRentRefund)}. Công nợ: ${money(s.outstandingReceivables)}. Khấu trừ sau kiểm tra: ${money(s.inspectionDeduction)}. ${Number(s.depositRefundAmount) === 0 ? 'Không phát sinh khoản hoàn cọc; có thể hoàn tất thủ tục đóng hợp đồng khi đủ điều kiện.' : 'Chỉ ghi nhận hoàn cọc sau khi thực hiện khoản hoàn được duyệt.'}`;
      }
      if (state.calculation) {
        const c=state.calculation;
        const decision=state.settlement?.refundConfirmedAt ? 'Đã xác nhận thực hiện quyết toán' : state.settlement ? 'Đã duyệt, chưa xác nhận thực hiện' : c.estimated ? 'Ước tính theo lịch hẹn, chưa được duyệt' : 'Theo bàn giao thực tế, chờ duyệt';
        $('settlement-state').textContent = `${decision}. Ngày trả: ${SafeBoxUc12Time.formatDateVN(c.returnDate)}. Tiền thuê xác nhận đủ điều kiện: ${money(c.eligiblePrepaidRent)}. Tiền thuê chưa sử dụng: ${money(c.unusedEligibleRent)}. Hoàn 80%: ${money(c.rentalRefund)}. Giữ lại 20%: ${money(c.retainedRent)} (không thu thêm khoản này). Số dư cọc: ${money(c.depositBalance)}. Chi phí kiểm tra có chứng từ: ${money(c.documentedDeductions)}. Công nợ, gồm chi phí đã lập hóa đơn: ${money(c.outstandingDebt)}. Bù trừ công nợ một lần: ${money(c.liabilityOffset)}. Tổng hoàn thực nhận: ${money(c.netRefund)}. Nợ còn lại: ${money(c.remainingDebt)}.`;
      }
      $('approval-form').hidden = role !== 'FACILITY_MANAGER' || state.status !== 'READY_FOR_APPROVAL';
      const canConfirm=role==='FACILITY_MANAGER'&&state.settlement?.status==='APPROVED'
        &&(state.calculation||Number(state.settlement.depositRefundAmount)>0);
      $('refund-button').hidden=!canConfirm;
      $('refund-confirmation').hidden=!canConfirm;
      if(canConfirm)$('refund-amount').value=state.calculation?.netRefund ?? state.settlement.depositRefundAmount;
    } catch (error) { $('settlement-state').textContent = error.message; }
  }
  async function initialize() {
    if (!await SafeBoxAuth.requireRole('CUSTOMER','FACILITY_MANAGER')) return;
    role = SafeBoxAuth.getCurrentUser().role;
    $('customer-section').hidden = role !== 'CUSTOMER';
    $('heading').textContent = role === 'CUSTOMER' ? 'Yêu cầu trả kho trước hạn' : 'Quyết toán trả kho trước hạn';
    $('list-title').textContent = role === 'CUSTOMER' ? 'Yêu cầu của tôi' : 'Yêu cầu tại cơ sở của tôi';
    try {
      if (role === 'CUSTOMER') {
        const rows = await api('/api/contracts/my');
        const today = SafeBoxUc12Time.todayISODateVN();
        const active = rows.filter(row => row.status === 'ACTIVE' && row.endDate > today);
        contracts = new Map(active.map(row => [row.contractId, row]));
        $('contract').innerHTML = '<option value="">Chọn hợp đồng</option>' + active.map(row => `<option value="${esc(row.contractId)}">${esc(row.contractNumber || row.unitNumber || row.contractId)} · đến ${esc(SafeBoxUc12Time.formatDateVN(row.endDate))}</option>`).join('');
        if (!active.length) message('Chưa có hợp đồng đang thuê đủ điều kiện gửi yêu cầu trả trước hạn.');
      }
      await load();
    } catch (error) { message(error.message); }
  }
  $('request-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      await api(`/api/customer/contracts/${encodeURIComponent($('contract').value)}/early-return`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({proposedReturnAt:$('return-at').value, reason:$('reason').value.trim(), notes:null})});
      message('Đã gửi yêu cầu. Tiếp theo, đặt lịch trả kho cho hợp đồng này.');
      $('request-form').reset();
      await load();
    } catch (error) { message(error.message); }
    finally { button.disabled = false; }
  });
  $('requests').addEventListener('click', event => { const button = event.target.closest('.review'); if (button) review(button.dataset.id); });
  $('approval-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = event.submitter;
    button.disabled = true;
    try {
      await api(`/api/manager/early-returns/${encodeURIComponent(selectedId)}/settlement/approve`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({inspectionDeduction:Number($('deduction').value), deductionReason:$('deduction-reason').value.trim()})});
      await review(selectedId);
      await load();
    } catch (error) { $('settlement-state').textContent = error.message; }
    finally { button.disabled = false; }
  });
  $('refund-confirmation').addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('refund-button');
    button.disabled = true;
    try {
      await api(`/api/manager/early-returns/${encodeURIComponent(selectedId)}/refund`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reference:$('refund-reference').value.trim(),paymentMethod:$('refund-method').value,amount:Number($('refund-amount').value)})});
      await review(selectedId);
      await load();
    } catch (error) { $('settlement-state').textContent = error.message; }
    finally { button.disabled = false; }
  });
  $('close-dialog').addEventListener('click', () => $('settlement-dialog').close());
  initialize();
})();
