(() => {
  const input = document.getElementById('mainSearchInput');
  const results = document.getElementById('facility-results');
  const status = document.getElementById('list-status');
  const nav = document.getElementById('facility-pagination');
  const previous = document.getElementById('previous-page');
  const next = document.getElementById('next-page');
  const initial = new URLSearchParams(location.search);
  const initialMode = initial.get('storageMode');
  let search = initial.get('search') || '';
  let page = Math.max(0, parseInt(initial.get('page') || '0', 10) || 0);
  let requestId = 0;
  input.value = search;
  const price = value => Number(value) > 0 ? `${new Intl.NumberFormat('vi-VN').format(Number(value))} VNĐ/tháng` : 'Giá đang cập nhật';
  const modeLabel = mode => ({ STANDARD: 'Kho thường', DRY: 'Kho thường', GENERAL: 'Kho thường', COOL: 'Kho mát', COLD: 'Kho đông lạnh', CLIMATE_CONTROLLED: 'Kho mát' }[String(mode || '').toUpperCase()] || 'Loại kho');

  function makeCard(facility) {
    const card = document.createElement('article');
    card.className = 'bg-surface-container-lowest rounded-2xl overflow-hidden shadow-sm hover:shadow-md transition-all flex flex-col md:flex-row';
    card.innerHTML = '<div class="md:w-5/12 h-56 md:h-auto bg-surface-container-high"><img class="cover w-full h-full object-cover" alt="Ảnh minh họa kho lưu trữ" loading="lazy"></div><div class="p-6 md:w-7/12 flex flex-col justify-between gap-4"><div><h2 class="font-headline-sm text-headline-sm text-primary"></h2><p class="address font-body-sm text-body-sm text-on-surface-variant mt-1"></p><p class="available font-body-sm text-body-sm text-on-surface-variant mt-3"></p></div><div class="grid sm:grid-cols-2 gap-3"><label class="font-body-sm text-body-sm text-on-surface-variant">Loại kho<select class="type-choice w-full mt-1 p-2 rounded-xl bg-surface-container-low text-on-surface"></select></label><div><span class="font-body-sm text-body-sm text-on-surface-variant">Diện tích</span><p class="area font-label-button text-label-button text-secondary">—</p></div></div><div class="border-t border-surface-container pt-4 flex items-center justify-between gap-3"><div><span class="font-body-sm text-body-sm text-on-surface-variant">Giá thuê</span><p class="price font-label-numeric-price text-label-numeric-price text-primary">—</p><span class="text-xs text-on-surface-variant">Ảnh minh họa</span></div><a class="detail-link px-5 py-2.5 rounded-xl bg-secondary text-on-secondary font-label-button text-label-button text-center">Xem chi tiết</a></div></div>';
    const cover = card.querySelector('.cover');
    cover.src = facility.imagePath || '/images/demo/standard-storage.png';
    cover.onerror = () => { cover.onerror = null; cover.src = '/images/demo/standard-storage.png'; };
    card.querySelector('h2').textContent = facility.name || 'Cơ sở chưa có tên';
    card.querySelector('.address').textContent = facility.address || 'Chưa có địa chỉ';
    card.querySelector('.available').textContent = `Kho sẵn sàng hiện tại: ${facility.availableUnitsCount || 0}`;
    const select = card.querySelector('.type-choice');
    const types = Array.isArray(facility.unitTypes) ? facility.unitTypes : [];
    if (!types.length) {
      select.add(new Option('Chưa có loại kho để chọn', ''));
      select.disabled = true;
      card.querySelector('.detail-link').classList.add('opacity-50', 'pointer-events-none');
    } else {
      types.forEach(type => select.add(new Option(`${modeLabel(type.storageMode)}${type.area == null ? '' : ` · ${type.area} m²`}`, String(type.typeId))));
      const matchingType = types.find(type => type.storageMode === initialMode);
      if (matchingType) select.value = String(matchingType.typeId);
    }
    function updateType() {
      const type = types.find(item => String(item.typeId) === select.value);
      card.querySelector('.area').textContent = type?.area == null ? 'Chưa có thông tin' : `${type.area} m²`;
      card.querySelector('.price').textContent = price(type?.monthlyPrice);
      if (type) {
        const url = new URL('/stitch/chi_ti_t_kho_10m_safebox_th_c.html', location.origin);
        url.searchParams.set('facilityId', facility.facilityId);
        url.searchParams.set('typeId', type.typeId);
        card.querySelector('.detail-link').href = url.pathname + url.search;
      } else {
        card.querySelector('.detail-link').removeAttribute('href');
      }
    }
    select.addEventListener('change', updateType);
    updateType();
    return card;
  }

  async function load() {
    const currentRequest = ++requestId;
    results.replaceChildren();
    status.hidden = false;
    status.textContent = 'Đang tải cơ sở...';
    nav.hidden = true;
    const query = new URLSearchParams({ search, page: String(page), size: '8' });
    const visibleQuery = new URLSearchParams({ ...(search ? { search } : {}), ...(initialMode ? { storageMode: initialMode } : {}), page: String(page) });
    history.replaceState(null, '', `${location.pathname}?${visibleQuery}`);
    try {
      const response = await fetch(`/api/facilities?${query}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (currentRequest !== requestId) return;
      if (!Array.isArray(data.content)) throw new Error('Invalid response');
      document.getElementById('result-count').textContent = String(data.totalElements ?? data.content.length);
      if (!data.content.length) { status.textContent = 'Không tìm thấy cơ sở phù hợp.'; return; }
      data.content.forEach(facility => results.append(makeCard(facility)));
      status.hidden = true;
      const totalPages = Number(data.totalPages) || 1;
      nav.hidden = false;
      document.getElementById('page-indicator').textContent = `Trang ${page + 1} / ${totalPages}`;
      previous.disabled = page === 0;
      next.disabled = page + 1 >= totalPages;
    } catch (error) {
      if (currentRequest !== requestId) return;
      document.getElementById('result-count').textContent = '—';
      status.textContent = 'Không tải được danh sách cơ sở. ';
      const retry = document.createElement('button'); retry.type = 'button'; retry.textContent = 'Thử lại';
      retry.className = 'text-secondary underline'; retry.addEventListener('click', load); status.append(retry);
      console.error(error);
    }
  }
  function submitSearch() { search = input.value.trim(); page = 0; load(); }
  document.getElementById('search-button').addEventListener('click', submitSearch);
  input.addEventListener('keydown', event => { if (event.key === 'Enter') submitSearch(); });
  document.getElementById('clear-search').addEventListener('click', () => { input.value = ''; submitSearch(); });
  previous.addEventListener('click', () => { if (page > 0) { page--; load(); } });
  next.addEventListener('click', () => { page++; load(); });
  load();
})();
