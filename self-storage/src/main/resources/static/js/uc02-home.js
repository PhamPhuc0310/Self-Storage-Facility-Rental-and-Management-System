(() => {
  const listPath = '/stitch/t_m_kho_l_u_tr_safebox_storage.html';
  const form = document.getElementById('home-search-form');
  const searchInput = document.getElementById('home-search-input');
  const typeFilter = document.getElementById('home-type-filter');
  const typeGrid = document.getElementById('home-types');
  const grid = document.getElementById('home-facilities');
  const status = document.getElementById('home-status');
  const money = value => Number(value) > 0 ? `${new Intl.NumberFormat('vi-VN').format(Number(value))} VNĐ/tháng` : 'Giá đang cập nhật';
  const modeLabel = mode => ({ STANDARD:'Kho thường', DRY: 'Kho thường', GENERAL: 'Kho thường', COOL:'Kho mát', COLD: 'Kho đông lạnh', CLIMATE_CONTROLLED: 'Kho mát' }[String(mode || '').toUpperCase()] || mode || '');
  let facilities = [];

  function goToList() {
    const url = new URL(listPath, location.origin);
    const search = searchInput.value.trim();
    if (search) url.searchParams.set('search', search);
    if (typeFilter.value) url.searchParams.set('storageMode', typeFilter.value);
    location.href = url.pathname + url.search;
  }
  form.addEventListener('submit', event => { event.preventDefault(); goToList(); });

  function render() {
    grid.replaceChildren();
    const mode = typeFilter.value;
    const shown = facilities.filter(facility => !mode || (facility.unitTypes || []).some(type => type.storageMode === mode));
    for (const facility of shown.slice(0, 4)) {
      const card = document.createElement('article');
      card.className = 'rounded-xl bg-surface-container-lowest overflow-hidden shadow-sm hover:shadow-md transition-all flex flex-col border border-surface-container';
      card.innerHTML = '<div class="h-44 bg-surface-container-high"><img class="cover h-full w-full object-cover" alt="Ảnh minh họa kho lưu trữ" loading="lazy"></div><div class="p-5 flex-1 flex flex-col justify-between gap-4"><div><h3 class="font-headline-sm text-headline-sm text-on-surface"></h3><p class="address font-body-sm text-body-sm text-on-surface-variant mt-2"></p><p class="available font-body-sm text-body-sm text-on-surface-variant mt-2"></p><p class="types font-body-sm text-body-sm text-on-surface-variant mt-2"></p></div><div class="pt-4 border-t border-surface-container"><p class="price font-body-sm text-body-sm text-on-surface-variant"></p><span class="text-xs text-on-surface-variant">Ảnh minh họa</span><br><a class="inline-block mt-3 px-3.5 py-1.5 rounded-lg bg-surface-container-high hover:bg-secondary hover:text-on-secondary">Chọn loại kho</a></div></div>';
      const cover = card.querySelector('.cover'); cover.src = facility.imagePath || '/images/demo/standard-storage.png';
      cover.onerror = () => { cover.onerror = null; cover.src = '/images/demo/standard-storage.png'; };
      card.querySelector('h3').textContent = facility.name || 'Cơ sở chưa có tên';
      card.querySelector('.address').textContent = facility.address || 'Chưa có địa chỉ';
      card.querySelector('.available').textContent = `Kho sẵn sàng hiện tại: ${facility.availableUnitsCount || 0}`;
      card.querySelector('.types').textContent = [...new Set((facility.unitTypes || []).map(type => modeLabel(type.storageMode)))].join(', ') || 'Chưa có loại kho để chọn';
      card.querySelector('.price').textContent = `Từ ${money(facility.startingPrice)}`;
      const url = new URL(listPath, location.origin);
      if (facility.name) url.searchParams.set('search', facility.name);
      card.querySelector('a').href = url.pathname + url.search;
      grid.append(card);
    }
    status.textContent = shown.length ? 'Tình trạng trống theo thời gian thuê sẽ được xác nhận khi đặt kho.' : 'Không có cơ sở phù hợp.';
  }

  async function load() {
    try {
      const response = await fetch('/api/facilities?search=&page=0&size=4');
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (!Array.isArray(data.content)) throw new Error('Invalid response');
      facilities = data.content;
      const modes = new Map();
      for (const facility of facilities) for (const type of facility.unitTypes || []) if (type.storageMode) modes.set(type.storageMode, modeLabel(type.storageMode));
      for (const [value, label] of modes) typeFilter.add(new Option(label, value));
      typeGrid.replaceChildren();
      if (!modes.size) typeGrid.textContent = 'Chưa có loại kho để hiển thị.';
      for (const [value, label] of modes) {
        const item = document.createElement('a');
        item.className = 'rounded-xl bg-surface-container-lowest p-8 shadow-sm hover:shadow-md border border-surface-container text-primary font-headline-md';
        item.textContent = label;
        const url = new URL(listPath, location.origin);
        url.searchParams.set('storageMode', value);
        item.href = url.pathname + url.search;
        typeGrid.append(item);
      }
      typeFilter.addEventListener('change', render);
      render();
    } catch (error) {
      typeGrid.textContent = 'Không tải được loại kho.';
      grid.replaceChildren();
      status.textContent = 'Không tải được cơ sở. ';
      const retry = document.createElement('button'); retry.type = 'button'; retry.textContent = 'Thử lại';
      retry.className = 'text-secondary underline'; retry.addEventListener('click', load); status.append(retry);
      console.error(error);
    }
  }
  load();
})();
