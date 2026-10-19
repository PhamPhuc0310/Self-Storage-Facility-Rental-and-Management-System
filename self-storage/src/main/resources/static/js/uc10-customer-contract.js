/**
 * SafeBox Storage - Logic Xử lý Hợp đồng & Thanh toán (UC10)
 * Quản lý xem chi tiết hợp đồng, ký xác nhận điện tử và thanh toán tiền cọc + tiền thuê ban đầu
 */
let currentContract = null;
let currentInvoice = null;
let allContracts = [];
let selectedPaymentMethod = 'BANK_TRANSFER';

document.addEventListener('DOMContentLoaded', async () => {
    // 1. Kiểm tra xác thực & hiển thị thông tin người dùng
    const user = SafeBoxAuth.getCurrentUser();
    if (user && user.fullName) {
        const sigInput = document.getElementById('input-signature');
        if (sigInput && !sigInput.value) sigInput.value = user.fullName;
    }

    // 2. Đọc query params (?contractId=... hoặc ?reservationId=...)
    const params = new URLSearchParams(window.location.search);
    const contractId = params.get('contractId');
    const reservationId = params.get('reservationId');

    // 3. Tải danh sách hợp đồng đếm số lượng
    await loadContractsList();

    // 4. Tải hợp đồng chi tiết
    if (contractId) {
        await loadContractById(contractId);
    } else if (reservationId) {
        await loadContractByReservation(reservationId);
    } else if (allContracts.length > 0) {
        await loadContractById(allContracts[0].contractId);
    } else {
        showEmptyState();
    }
});

// =========================================================================
// Chuyển đổi giữa 2 tab (Chi tiết hợp đồng vs Danh sách tất cả hợp đồng)
// =========================================================================
function switchContractTab(tab) {
    const btnDetail = document.getElementById('btn-tab-detail');
    const btnList = document.getElementById('btn-tab-list');
    const viewDetail = document.getElementById('view-contract-detail');
    const viewList = document.getElementById('view-contract-list');

    if (tab === 'DETAIL') {
        btnDetail.className = 'px-4 py-2 rounded-xl text-sm font-semibold transition-all bg-white text-primary shadow-sm';
        btnList.className = 'px-4 py-2 rounded-xl text-sm font-semibold transition-all bg-white/10 hover:bg-white/20 text-white';
        viewDetail.classList.remove('hidden');
        viewList.classList.add('hidden');
    } else {
        btnList.className = 'px-4 py-2 rounded-xl text-sm font-semibold transition-all bg-white text-primary shadow-sm';
        btnDetail.className = 'px-4 py-2 rounded-xl text-sm font-semibold transition-all bg-white/10 hover:bg-white/20 text-white';
        viewList.classList.remove('hidden');
        viewDetail.classList.add('hidden');
        renderContractsTable();
    }
}

// =========================================================================
// API Tra cứu Hợp đồng
// =========================================================================
async function loadContractsList() {
    try {
        const res = await SafeBoxAuth.authFetch('/api/contracts/my');
        if (res.ok) {
            allContracts = await res.json();
            const badge = document.getElementById('contract-count-badge');
            if (badge) badge.textContent = allContracts.length;
            const countText = document.getElementById('list-count-text');
            if (countText) countText.textContent = allContracts.length + ' hợp đồng';
        }
    } catch (e) {
        console.error('Không thể tải danh sách hợp đồng:', e);
    }
}

async function loadContractById(contractId) {
    showLoading();
    try {
        const res = await SafeBoxAuth.authFetch('/api/contracts/my/' + contractId);
        if (!res.ok) {
            showEmptyState();
            return;
        }
        const contract = await res.json();
        renderContractDetail(contract);
    } catch (e) {
        showEmptyState();
    }
}

async function loadContractByReservation(reservationId) {
    showLoading();
    try {
        const res = await SafeBoxAuth.authFetch('/api/contracts/my/by-reservation/' + reservationId);
        if (!res.ok) {
            showEmptyState();
            return;
        }
        const contract = await res.json();
        renderContractDetail(contract);
    } catch (e) {
        showEmptyState();
    }
}

// =========================================================================
// Đổ dữ liệu Hợp đồng vào giao diện chi tiết
// =========================================================================
function renderContractDetail(contract) {
    currentContract = contract;
    hideLoading();

    const card = document.getElementById('contract-card');
    if (card) card.classList.remove('hidden');

    // Header & Trạng thái
    setText('contract-number-display', contract.contractNumber || 'HD-Chưa cấp');
    setText('reservation-code-display', contract.requestCode || (contract.reservationId ? contract.reservationId.substring(0, 8) : ''));
    setText('created-at-display', formatDateTime(contract.createdAt));

    const statusBadge = document.getElementById('badge-contract-status');
    if (statusBadge) {
        statusBadge.textContent = contract.statusLabel || contract.status;
        if (contract.status === 'PENDING_CONFIRMATION') {
            statusBadge.className = 'px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800';
        } else if (contract.status === 'PENDING_HANDOVER') {
            statusBadge.className = 'px-3 py-1 rounded-full text-xs font-bold bg-blue-100 text-blue-800';
        } else if (contract.status === 'ACTIVE') {
            statusBadge.className = 'px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800';
        } else {
            statusBadge.className = 'px-3 py-1 rounded-full text-xs font-bold bg-gray-100 text-gray-800';
        }
    }

    // Bên thuê (Khách hàng)
    setText('customer-name-display', contract.customerName || 'Khách hàng SafeBox');
    setText('customer-phone-display', contract.customerPhone || 'Chưa cập nhật');
    setText('customer-email-display', contract.customerEmail || 'Chưa cập nhật');
    setText('customer-id-number-display', contract.customerIdentityNumber || 'Chưa cập nhật');
    setText('customer-address-display', contract.customerAddress || 'Chưa cập nhật');

    // Bên cho thuê (Cơ sở)
    setText('facility-name-display', contract.facilityName || 'SafeBox Storage');
    setText('facility-address-display', contract.facilityAddress || 'Chưa cập nhật');
    setText('facility-phone-display', contract.facilityPhone || 'Chưa cập nhật');
    setText('manager-name-display', contract.approvedByName || 'Quản lý cơ sở');
    setText('company-signature-display', contract.companySignature || 'SafeBox Storage Vietnam');

    // Ô kho vật lý
    setText('unit-number-display', contract.unitNumber || 'Kho');
    setText('unit-location-display', [contract.floor, contract.zone].filter(Boolean).join(', ') || 'Chưa cập nhật');
    setText('unit-type-display', contract.typeName || 'Kho tự quản');
    setText('unit-size-display', (contract.sizeName || '') + (contract.area ? ' (' + contract.area + ' m²)' : ''));
    setText('unit-mode-display', contract.storageMode || 'STANDARD');

    // Thời gian thuê & Tài chính
    setText('duration-display', contract.months != null ? contract.months + ' tháng' : '—');
    setText('date-range-display', formatDate(contract.startDate) + ' đến ' + formatDate(contract.endDate));
    setText('monthly-fee-display', formatCurrency(contract.monthlyRentalFee));
    setText('deposit-display', formatCurrency(contract.depositAmount));
    setText('total-payable-display', formatCurrency(contract.totalPayable));
    setText('terms-version-display', 'Phiên bản: ' + (contract.termsVersion || '—'));

    // Điều khoản
    const termsContainer = document.getElementById('terms-list-container');
    if (termsContainer) termsContainer.innerHTML = contract.termsSummary?.length
        ? contract.termsSummary.map(t => `<p class="leading-relaxed">${escapeHtml(t)}</p>`).join('')
        : '<p>Điều khoản chưa có thông tin.</p>';

    // Trạng thái Bước 1: Ký xác nhận hợp đồng
    const confirmForm = document.getElementById('confirm-form');
    const confirmedBadge = document.getElementById('confirmed-badge');
    const isConfirmed = contract.customerSignedAt != null;

    if (isConfirmed) {
        if (confirmForm) confirmForm.classList.add('hidden');
        if (confirmedBadge) {
            confirmedBadge.classList.remove('hidden');
            setText('signed-name-display', contract.customerSignature || contract.customerName || 'Đã ký trực tuyến');
            setText('signed-at-display', formatDateTime(contract.customerSignedAt));
        }
    } else {
        if (confirmForm) confirmForm.classList.remove('hidden');
        if (confirmedBadge) confirmedBadge.classList.add('hidden');
    }

    // The payment page owns settlement; read its balance for this same contract.
    loadCanonicalPaymentSummary(contract.contractId);
}

async function loadCanonicalPaymentSummary(contractId) {
    const box = document.getElementById('box-initial-payment');
    if (!box) return;
    box.innerHTML = '<p class="text-sm text-on-surface-variant">Đang tải tình trạng thanh toán...</p>';
    try {
        const res = await SafeBoxAuth.authFetch(`/api/contracts/${contractId}/payments/summary`);
        if (!res.ok) throw new Error('Không thể tải tình trạng thanh toán.');
        const summary = await res.json();
        if (currentContract?.contractId !== contractId || summary.contractId !== contractId) return;
        const line = (label, component) => `<div class="flex justify-between gap-4 border-b border-surface-container py-2"><span>${label}</span><strong>${formatCurrency(component.paidAmount)} / ${formatCurrency(component.amountDue)} · ${component.remainingAmount <= 0 ? 'Đã thanh toán' : 'Còn thiếu ' + formatCurrency(component.remainingAmount)}</strong></div>`;
        box.innerHTML = `<h3 class="text-lg font-bold">Thanh toán hợp đồng</h3><p class="text-sm text-on-surface-variant">Tình trạng thanh toán của hợp đồng ${escapeHtml(currentContract.contractNumber)}.</p>${line('Tiền cọc', summary.deposit)}${line(summary.termsVersion === 'v2' ? 'Phần tiền thuê còn lại' : 'Tiền thuê ban đầu', summary.initialRent)}<a class="inline-flex px-5 py-3 rounded-xl bg-secondary text-white font-semibold" href="/stitch/uc11-customer-payments-appointments.html?contractId=${encodeURIComponent(contractId)}">Thanh toán và đặt lịch nhận kho</a>`;
    } catch (error) {
        box.textContent = error.message;
    }
}

// =========================================================================
// API Tra cứu Hóa đơn ban đầu & Cập nhật giao diện thanh toán
// =========================================================================
async function loadInitialInvoice(contractId) {
    try {
        const res = await SafeBoxAuth.authFetch(`/api/contracts/my/${contractId}/initial-invoice`);
        if (!res.ok) return;

        const invoice = await res.json();
        currentInvoice = invoice;

        setText('inv-number-display', invoice.invoiceNumber);
        setText('inv-deposit-display', formatCurrency(invoice.depositAmount));
        setText('inv-rental-display', formatCurrency(invoice.rentalAmount));
        setText('inv-total-display', formatCurrency(invoice.totalAmount));

        const pendingSection = document.getElementById('payment-pending-section');
        const successSection = document.getElementById('payment-success-section');
        const invBadge = document.getElementById('inv-status-badge');

        if (invBadge) {
            invBadge.textContent = invoice.statusLabel || invoice.status;
            if (invoice.status === 'PAID') {
                invBadge.className = 'px-3 py-0.5 rounded-full text-xs font-bold bg-green-100 text-green-800';
            } else {
                invBadge.className = 'px-3 py-0.5 rounded-full text-xs font-bold bg-amber-100 text-amber-800';
            }
        }

        if (invoice.status === 'PAID') {
            // Đã thanh toán -> Hiển thị biên lai thành công
            if (pendingSection) pendingSection.classList.add('hidden');
            if (successSection) {
                successSection.classList.remove('hidden');
                setText('paid-inv-number', invoice.invoiceNumber);
                setText('paid-amount-display', formatCurrency(invoice.paidAmount));

                const firstTx = (invoice.transactions && invoice.transactions.length > 0) ? invoice.transactions[0] : null;
                setText('paid-method-display', firstTx ? firstTx.paymentMethodLabel : 'Chuyển khoản');
                setText('paid-ref-display', firstTx ? firstTx.referenceCode : 'N/A');
            }
        } else {
            // Chưa thanh toán -> Hiển thị số tiền và mã tham chiếu từ hóa đơn
            if (pendingSection) pendingSection.classList.remove('hidden');
            if (successSection) successSection.classList.add('hidden');

            updatePaymentReference(invoice);
        }
    } catch (e) {
        console.error('Lỗi khi tải hóa đơn:', e);
    }
}

function updatePaymentReference(invoice) {
    const rawNumber = currentContract?.contractNumber?.replace(/[^a-zA-Z0-9]/g, '');
    const memo = rawNumber ? `SAFEBOX ${rawNumber}` : '—';
    const amount = invoice.totalAmount;

    setText('bank-amount-display', amount == null ? '—' : formatCurrency(amount));
    setText('bank-memo-display', memo);
}

function onPaymentMethodChange(method) {
    selectedPaymentMethod = method;
    document.querySelectorAll('.payment-method-card').forEach(card => {
        card.classList.remove('border-2', 'border-secondary', 'bg-secondary/5');
        card.classList.add('border', 'border-surface-container', 'bg-white');
    });

    const activeCard = document.getElementById(`card-method-${method}`);
    if (activeCard) {
        activeCard.classList.remove('border', 'border-surface-container', 'bg-white');
        activeCard.classList.add('border-2', 'border-secondary', 'bg-secondary/5');
    }

    const qrBox = document.getElementById('method-bank-transfer-box');
    if (qrBox) {
        if (method === 'BANK_TRANSFER') {
            qrBox.classList.remove('hidden');
        } else {
            qrBox.classList.add('hidden');
        }
    }
}

// =========================================================================
// Thao tác Ký & Xác nhận Hợp đồng (POST /api/contracts/my/{id}/confirm)
// =========================================================================
async function handleConfirmContract() {
    if (!currentContract) return;

    const chk = document.getElementById('chk-agree-terms');
    if (!chk || !chk.checked) {
        showAlert('Vui lòng tích chọn đồng ý với các điều khoản hợp đồng trước khi xác nhận.', 'error');
        chk.focus();
        return;
    }

    const sigInput = document.getElementById('input-signature');
    const signature = sigInput ? sigInput.value.trim() : '';

    const btn = document.getElementById('btn-submit-confirm');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin-fast"></span> Đang xử lý ký...`;
    }

    try {
        const res = await SafeBoxAuth.authFetch(`/api/contracts/my/${currentContract.contractId}/confirm`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                agreedToTerms: true,
                signature: signature
            })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            showAlert(err.message || 'Xác nhận hợp đồng thất bại. Vui lòng thử lại.', 'error');
            return;
        }

        const updated = await res.json();
        showAlert('Bạn đã ký xác nhận hợp đồng điện tử. Xem các khoản cần thanh toán bên dưới.', 'success');
        renderContractDetail(updated);

        // Cuộn xuống phần thanh toán Bước 2
        const paymentBox = document.getElementById('box-initial-payment');
        if (paymentBox) {
            paymentBox.scrollIntoView({ behavior: 'smooth' });
        }
    } catch (e) {
        showAlert('Không thể kết nối đến máy chủ. Vui lòng kiểm tra lại đường truyền.', 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<span class="material-symbols-outlined text-base">draw</span> <span>Ký & Xác nhận hợp đồng</span>`;
        }
    }
}

// =========================================================================
// Thao tác Thanh toán tiền cọc & tiền thuê ban đầu (POST /api/contracts/my/{id}/pay-initial)
// =========================================================================
async function handlePayInvoice() {
    if (!currentContract) return;

    const refInput = document.getElementById('input-payment-ref');
    const refCode = refInput ? refInput.value.trim() : '';

    const btn = document.getElementById('btn-submit-payment');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = `<span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin-fast"></span> Đang xác nhận thanh toán...`;
    }

    try {
        const res = await SafeBoxAuth.authFetch(`/api/contracts/my/${currentContract.contractId}/pay-initial`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                paymentMethod: selectedPaymentMethod,
                referenceCode: refCode,
                notes: 'Thanh toán trực tuyến qua SafeBox Web'
            })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            showAlert(err.message || 'Xác nhận thanh toán thất bại. Vui lòng thử lại.', 'error');
            return;
        }

        const result = await res.json();
        showAlert(result.message || 'Thanh toán thành công!', 'success');

        // Tải lại chi tiết hợp đồng và hóa đơn
        await loadContractById(currentContract.contractId);
    } catch (e) {
        showAlert('Có lỗi khi xử lý thanh toán. Vui lòng thử lại.', 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `<span class="material-symbols-outlined text-base">check_circle</span> <span>Xác nhận đã thanh toán</span>`;
        }
    }
}

// =========================================================================
// Render Danh sách tất cả Hợp đồng (View 2)
// =========================================================================
function renderContractsTable() {
    const container = document.getElementById('contracts-table-container');
    if (!container) return;

    if (!allContracts || allContracts.length === 0) {
        container.innerHTML = `
            <div class="p-8 text-center bg-white rounded-2xl border border-surface-container">
                <span class="material-symbols-outlined text-4xl text-outline mb-2">description</span>
                <p class="text-sm text-on-surface-variant font-medium">Bạn chưa có hợp đồng nào trong hệ thống.</p>
            </div>
        `;
        return;
    }

    container.innerHTML = allContracts.map(c => `
        <div class="bg-white rounded-2xl p-6 shadow-sm border border-surface-container hover:shadow-md transition-all flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div class="space-y-2">
                <div class="flex items-center gap-3">
                    <span class="text-xs font-semibold px-2.5 py-0.5 rounded-full ${getStatusBadgeClass(c.status)}">
                        ${escapeHtml(c.statusLabel || c.status)}
                    </span>
                    <span class="text-xs text-on-surface-variant font-mono">Đơn đặt: #${c.requestCode || ''}</span>
                </div>
                <h4 class="text-lg font-bold text-primary font-mono">${escapeHtml(c.contractNumber)}</h4>
                <div class="flex flex-wrap items-center gap-4 text-xs text-on-surface-variant">
                    <span>🏢 ${escapeHtml(c.facilityName || 'SafeBox')}</span>
                    <span>📦 Kho: <strong>${escapeHtml(c.unitNumber || 'N/A')}</strong> (${escapeHtml(c.typeName || '')})</span>
                    <span>📅 ${formatDate(c.startDate)} - ${formatDate(c.endDate)}</span>
                    <span>💰 Tổng thanh toán: <strong class="text-secondary">${formatCurrency(c.totalPayable)}</strong></span>
                </div>
            </div>
            <div class="flex items-center gap-3">
                <button onclick="selectAndShowContract('${c.contractId}')" class="px-5 py-2.5 rounded-xl bg-secondary text-white text-xs font-semibold hover:bg-secondary-container transition-all flex items-center gap-1.5 shadow-sm">
                    <span class="material-symbols-outlined text-base">visibility</span>
                    <span>Xem & Thanh toán</span>
                </button>
            </div>
        </div>
    `).join('');
}

function selectAndShowContract(contractId) {
    switchContractTab('DETAIL');
    loadContractById(contractId);
}

function getStatusBadgeClass(status) {
    if (status === 'PENDING_CONFIRMATION') return 'bg-amber-100 text-amber-800';
    if (status === 'PENDING_HANDOVER') return 'bg-blue-100 text-blue-800';
    if (status === 'ACTIVE') return 'bg-emerald-100 text-emerald-800';
    return 'bg-gray-100 text-gray-800';
}

// =========================================================================
// Tiện ích hỗ trợ (Helpers)
// =========================================================================
function showLoading() {
    const s = document.getElementById('loading-spinner');
    if (s) s.classList.remove('hidden');
    const c = document.getElementById('contract-card');
    if (c) c.classList.add('hidden');
    const e = document.getElementById('empty-state');
    if (e) e.classList.add('hidden');
}

function hideLoading() {
    const s = document.getElementById('loading-spinner');
    if (s) s.classList.add('hidden');
}

function showEmptyState() {
    hideLoading();
    const c = document.getElementById('contract-card');
    if (c) c.classList.add('hidden');
    const e = document.getElementById('empty-state');
    if (e) e.classList.remove('hidden');
}

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function showAlert(message, type = 'success') {
    const alert = document.getElementById('status-alert');
    if (!alert) return;

    alert.className = `mb-6 p-4 rounded-xl text-sm flex items-start gap-3 ${type === 'success' ? 'bg-green-50 text-green-900 border border-green-200' : 'bg-rose-50 text-rose-900 border border-rose-200'}`;
    const icon = type === 'success' ? 'check_circle' : 'error';
    alert.innerHTML = `
        <span class="material-symbols-outlined ${type === 'success' ? 'text-green-600' : 'text-rose-600'} text-xl mt-0.5">${icon}</span>
        <div class="flex-1 font-medium">${escapeHtml(message)}</div>
        <button onclick="document.getElementById('status-alert').classList.add('hidden')" class="text-on-surface-variant hover:text-on-surface">
            <span class="material-symbols-outlined text-base">close</span>
        </button>
    `;
    alert.classList.remove('hidden');
    alert.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function formatCurrency(amount) {
    if (amount == null) return '0 đ';
    return Number(amount).toLocaleString('vi-VN') + ' đ';
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
}

function formatDateTime(dtStr) {
    if (!dtStr) return 'N/A';
    try {
        const d = new Date(dtStr);
        return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    } catch (_) {
        return dtStr;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, function (m) {
        return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
    });
}

function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        showAlert(`Đã sao chép: "${text}" vào bộ nhớ tạm!`, 'success');
    }).catch(() => {
        showAlert('Không thể tự động sao chép. Vui lòng chọn và sao chép thủ công.', 'error');
    });
}
