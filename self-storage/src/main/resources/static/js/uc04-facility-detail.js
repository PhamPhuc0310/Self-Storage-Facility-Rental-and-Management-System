(() => {
  const params = new URLSearchParams(location.search);
  const facilityId = params.get('facilityId');
  const initialTypeId = Number(params.get('typeId'));
  const duration = document.getElementById('duration-select');
  const typeChoice = document.getElementById('detail-type-choice');
  const status = document.getElementById('detail-status');
  const paymentButton = document.getElementById('payment-button');
  const modal = document.getElementById('payment-modal');
  const validId = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const modeLabel = mode => ({STANDARD:'Kho thường', COOL:'Kho mát', COLD:'Kho đông lạnh'}[mode] || 'Loại kho');
  const money = value => {
    const n = Number(value);
    if (!Number.isFinite(n) || n <= 0) throw new Error('Giá không hợp lệ');
    return `${new Intl.NumberFormat('vi-VN').format(n)} VNĐ`;
  };
  const monthly = value => `${money(value)}/tháng`;
  const put = (id, value) => { document.getElementById(id).textContent = value || '—'; };
  let facility;
  let selectedType;
  let estimateRequest = 0;

  function showError(message, retry) {
    status.hidden = false;
    status.textContent = message;
    status.classList.add('text-error');
    if (retry) {
      const button = document.createElement('button');
      button.type = 'button'; button.textContent = 'Thử lại';
      button.className = 'ml-2 underline text-secondary';
      button.addEventListener('click', retry);
      status.append(button);
    }
    put('total-price-display', '—');
    paymentButton.disabled = true;
  }

  function renderType(type) {
    selectedType = type;
    const title = `${modeLabel(type.storageMode)} · ${type.area} m²`;
    put('detail-title', title);
    put('detail-breadcrumb-type', title);
    document.title = `${title} | SafeBox Storage`;
    put('detail-type', modeLabel(type.storageMode));
    put('detail-area', `${type.area} m²`);
    put('detail-dimensions', `${type.width} × ${type.length} × ${type.height} m`);
    put('detail-monthly-price', type.monthlyPrice > 0 ? monthly(type.monthlyPrice) : 'Giá đang cập nhật');
    put('detail-available-count', String(type.availableUnits || 0));
    put('detail-availability', `Kho sẵn sàng hiện tại: ${type.availableUnits || 0}`);
    put('detail-intro', type.demoIntro);
    put('detail-goods', type.demoGoods);
    put('detail-conditions', type.demoConditions);
    put('detail-features', type.features?.replaceAll('CCTV', 'camera'));
    put('detail-temperature', type.minTemperature != null && type.maxTemperature != null
      ? `Khoảng nhiệt độ: ${type.minTemperature} đến ${type.maxTemperature}°C.` : 'Không kiểm soát nhiệt độ riêng.');
    const image = document.getElementById('main-gallery-img');
    image.src = type.imagePath || facility.imagePath || '/images/demo/standard-storage.png';
    image.onerror = () => { image.onerror = null; image.src = '/images/demo/standard-storage.png'; };
    const url = new URL(location.href);
    url.searchParams.set('typeId', String(type.typeId));
    url.searchParams.set('months', duration.value);
    url.searchParams.delete('payment');
    history.replaceState(null, '', url.pathname + url.search);
    estimate();
  }

  async function estimate() {
    const request = ++estimateRequest;
    const months = Number(duration.value);
    put('display-duration', `${months} tháng`);
    put('total-price-display', 'Đang tính...');
    paymentButton.disabled = true;
    if (!selectedType || !Number.isInteger(months) || months < 1) {
      showError('Vui lòng chọn loại kho và thời gian thuê.'); return;
    }
    try {
      const query = new URLSearchParams({facilityId, typeId:String(selectedType.typeId), months:String(months)});
      const response = await fetch(`/api/pricing/estimate?${query}`);
      if (!response.ok) throw new Error('Không lấy được giá');
      const quote = await response.json();
      if (request !== estimateRequest) return;
      put('detail-price-per-month', monthly(quote.monthlyPrice));
      put('total-price-display', money(quote.estimatedTotal));
      paymentButton.disabled = false;
      status.hidden = true;
    } catch (_) {
      if (request !== estimateRequest) return;
      showError('Không lấy được giá dự kiến. Vui lòng thử lại.', estimate);
    }
  }

  function paymentReturn() {
    const url = new URL('/stitch/chi_ti_t_kho_10m_safebox_th_c.html', location.origin);
    url.searchParams.set('facilityId', facilityId);
    url.searchParams.set('typeId', String(selectedType.typeId));
    url.searchParams.set('months', duration.value);
    url.searchParams.set('payment', '1');
    return url.pathname + url.search;
  }

  function goLogin() {
    const url = new URL('/stitch/ng_nh_p_ng_k_safebox_storage.html', location.origin);
    url.searchParams.set('returnTo', paymentReturn());
    location.href = url.pathname + url.search;
  }

  async function handlePayment() {
    if (!selectedType || paymentButton.disabled) return;
    const token = window.SafeBoxAuth?.getToken();
    if (!token) { goLogin(); return; }
    try {
      const response = await fetch('/api/auth/me', {headers:{Authorization:`Bearer ${token}`}});
      if (response.status === 401) { SafeBoxAuth.clearAuth(); goLogin(); return; }
      if (!response.ok) throw new Error('Không xác minh được phiên');
      modal.classList.remove('hidden');
      document.getElementById('payment-modal-close').focus();
    } catch (_) { showError('Không xác minh được phiên đăng nhập. Vui lòng thử lại.', handlePayment); }
  }

  async function load() {
    if (!validId.test(facilityId || '')) {
      showError('Vui lòng chọn cơ sở từ trang tìm kho.'); return;
    }
    try {
      const response = await fetch(`/api/facilities/${encodeURIComponent(facilityId)}`);
      if (!response.ok) throw new Error('Không tải được cơ sở');
      facility = await response.json();
      const types = facility.unitTypes || [];
      if (!types.length) throw new Error('Không có loại kho');
      put('detail-address', `${facility.name} · ${facility.address}`);
      put('detail-facility-name', facility.name);
      put('detail-facility-intro', facility.demoIntro);
      put('detail-facility-address', facility.address);
      put('detail-safety', facility.demoSafety);
      put('detail-access', facility.demoAccess);
      put('detail-terms', facility.demoTerms);
      put('detail-phone', facility.phone ? `Điện thoại: ${facility.phone}` : '');
      put('detail-hours', facility.openingTime && facility.closingTime
        ? `Giờ ra vào: ${facility.openingTime}–${facility.closingTime}` : '');
      const facilityLink = document.getElementById('detail-facility-link');
      facilityLink.textContent = facility.name;
      facilityLink.href = `/stitch/t_m_kho_l_u_tr_safebox_storage.html?${new URLSearchParams({search:facility.name})}`;
      types.forEach(type => typeChoice.add(new Option(`${modeLabel(type.storageMode)} · ${type.area} m²`, String(type.typeId))));
      const initial = types.find(type => type.typeId === initialTypeId) || types[0];
      typeChoice.value = String(initial.typeId);
      if (['1','2','3','6','12'].includes(params.get('months'))) duration.value = params.get('months');
      renderType(initial);
      typeChoice.addEventListener('change', () => renderType(types.find(type => String(type.typeId) === typeChoice.value)));
      duration.addEventListener('change', () => { const url = new URL(location.href); url.searchParams.set('months', duration.value); history.replaceState(null,'',url.pathname+url.search); estimate(); });
      if (params.get('payment') === '1') {
        await new Promise(resolve => {
          const timer = setInterval(() => { if (!paymentButton.disabled || status.classList.contains('text-error')) { clearInterval(timer); resolve(); } }, 50);
          setTimeout(() => { clearInterval(timer); resolve(); }, 8000);
        });
        if (!paymentButton.disabled) handlePayment();
      }
    } catch (_) { showError('Không tải được chi tiết kho. Vui lòng thử lại.', load); }
  }

  paymentButton.addEventListener('click', handlePayment);
  document.getElementById('payment-modal-close').addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', event => { if (event.target === modal) modal.classList.add('hidden'); });
  document.addEventListener('keydown', event => { if (event.key === 'Escape') modal.classList.add('hidden'); });
  load();
})();
