(() => {
  const listPath = '/storage.html';
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
    if (typeFilter.value) url.searchParams.set('typeId', typeFilter.value);
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
      card.innerHTML = '<div class="h-44 bg-surface-container-high"><img class="cover h-full w-full object-cover" alt="Hình ảnh cơ sở lưu trữ" loading="lazy"></div><div class="p-5 flex-1 flex flex-col justify-between gap-4"><div><h3 class="font-headline-sm text-headline-sm text-on-surface"></h3><p class="address font-body-sm text-body-sm text-on-surface-variant mt-2"></p><p class="available font-body-sm text-body-sm text-on-surface-variant mt-2"></p><p class="volume font-body-sm text-body-sm text-on-surface-variant mt-2"></p><p class="types font-body-sm text-body-sm text-on-surface-variant mt-2"></p></div><div class="pt-4 border-t border-surface-container"><p class="price font-body-sm text-body-sm text-on-surface-variant"></p><a class="inline-block mt-3 px-3.5 py-1.5 rounded-lg bg-surface-container-high hover:bg-secondary hover:text-on-secondary">Xem kho thực tế</a></div></div>';
      const cover = card.querySelector('.cover');
      if (facility.imagePath) cover.src = facility.imagePath;
      else { cover.remove(); card.querySelector('.h-44').textContent = 'Chưa có ảnh được duyệt'; }
      card.querySelector('h3').textContent = facility.name || 'Cơ sở chưa có tên';
      card.querySelector('.address').textContent = facility.address || 'Chưa có địa chỉ';
      card.querySelector('.available').textContent = `Ô có thể đặt theo ngày thuê hiện tại: ${facility.availableUnitsCount || 0}`;
      const volumes = facility.verifiedVolumes;
      card.querySelector('.volume').textContent = volumes.length
        ? `Thể tích đã xác minh: ${Math.min(...volumes).toLocaleString('vi-VN')}–${Math.max(...volumes).toLocaleString('vi-VN')} m³`
        : 'Chưa có số đo thực địa đã xác minh';
      card.querySelector('.types').textContent = [...new Set((facility.unitTypes || []).map(type => type.typeName))].join(', ') || 'Chưa có loại kho để chọn';
      card.querySelector('.price').textContent = facility.startingPrice == null ? 'Chưa có giá thuê có hiệu lực' : `Từ ${money(facility.startingPrice)}`;
      const url = new URL(listPath, location.origin);
      if (facility.name) url.searchParams.set('search', facility.name);
      card.querySelector('a').href = url.pathname + url.search;
      grid.append(card);
    }
    status.textContent = shown.length ? 'Tình trạng trống theo thời gian thuê sẽ được xác nhận khi đặt kho.' : 'Không có cơ sở phù hợp.';
  }

  async function load() {
    try {
      const response = await fetch('/api/discovery/units');
      if (!response.ok) throw new Error('Không thể tải danh sách cơ sở. Vui lòng thử lại.');
      const data = await response.json();
      if (!Array.isArray(data)) throw new Error('Dữ liệu cơ sở chưa sẵn sàng. Vui lòng thử lại.');
      const grouped = new Map();
      for (const unit of data) {
        if (!grouped.has(unit.facilityId)) grouped.set(unit.facilityId, {
          facilityId: unit.facilityId, name: unit.facilityName, address: unit.address,
          imagePath: unit.facilityImage, availableUnitsCount: 0, startingPrice: null, unitTypes: [], verifiedVolumes: []
        });
        const facility = grouped.get(unit.facilityId);
        if (unit.volumeM3 != null && unit.measurementSource !== 'SYNTHETIC_DEMO')
          facility.verifiedVolumes.push(Number(unit.volumeM3));
        if (unit.bookable) {
          facility.availableUnitsCount++;
          facility.startingPrice = facility.startingPrice == null ? unit.monthlyPrice : Math.min(facility.startingPrice, unit.monthlyPrice);
        }
        if (!facility.unitTypes.some(type => type.storageMode === String(unit.typeId)))
          facility.unitTypes.push({ storageMode: String(unit.typeId), typeName: unit.typeName });
      }
      facilities = [...grouped.values()];
      const modes = new Map();
      for (const facility of facilities) for (const type of facility.unitTypes || []) if (type.storageMode) modes.set(type.storageMode, type.typeName);
      for (const [value, label] of modes) typeFilter.add(new Option(label, value));
      typeGrid.replaceChildren();
      if (!modes.size) typeGrid.textContent = 'Chưa có loại kho để hiển thị.';
      for (const [value, label] of modes) {
        const item = document.createElement('a');
        item.className = 'rounded-xl bg-surface-container-lowest p-8 shadow-sm hover:shadow-md border border-surface-container text-primary font-headline-md';
        item.textContent = label;
        const url = new URL(listPath, location.origin);
        url.searchParams.set('typeId', value);
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
