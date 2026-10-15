(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c]));
  const money = value => value == null ? 'Chưa có giá' : new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value);
  const routes = {
    'tong-quan':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    'co-so-cua-toi':'/stitch/t_ng_quan_qu_n_l_safebox_manager.html#manager-facilities',
    'loai-kho':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'kho-vat-ly':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'yeu-cau-dat-kho':'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html'
  };
  document.querySelectorAll('aside a[data-path]').forEach(link => {
    if (routes[link.dataset.path]) link.href = routes[link.dataset.path];
    if (link.dataset.path === 'dang-nhap') link.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
  });
  document.querySelectorAll('header button, header img[alt="Profile"]').forEach(element => element.hidden = true);
  const headerFacility = document.querySelector('header span.font-headline-sm');
  const headerInfo = document.querySelector('header span.font-body-sm');
  const headerUser = document.querySelector('header .border-l span.font-label-button');
  const badge = document.querySelector('aside [data-path="yeu-cau-dat-kho"] > span');
  let facilities = [];
  async function api(path) {
    const response = await SafeBoxAuth.authFetch(path);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Không tải được danh mục kho.');
    return data;
  }
  function message(value) {
    $('catalog-message').textContent = value;
    $('catalog-message').className = 'rounded-lg p-3 bg-error-container text-error';
    $('catalog-message').hidden = !value;
  }
  async function loadFacility() {
    const id = $('catalog-facility').value;
    const facility = facilities.find(f => f.facilityId === id);
    if (!facility) return;
    $('catalog-loading').hidden = false;
    $('catalog-types').replaceChildren();
    $('catalog-units').replaceChildren();
    headerFacility.textContent = facility.name;
    headerInfo.textContent = facility.address;
    try {
      const [types, units] = await Promise.all([
        api(`/api/manager/facilities/${id}/unit-types`),
        api(`/api/manager/facilities/${id}/units`)
      ]);
      const names = new Map(types.map(t => [t.typeId, t.typeName]));
      $('catalog-types').innerHTML = types.length ? types.map(t => `<article class="p-4 rounded-lg bg-surface-container-low"><strong>${escape(t.typeName)}</strong> · ${escape(t.storageMode)} · ${escape(t.status)}<p>Giá tháng: ${money(t.monthlyPrice)}</p></article>`).join('') : '<p>Chưa có loại kho tại cơ sở này.</p>';
      $('catalog-units').innerHTML = units.length ? units.map(u => `<article class="p-4 rounded-lg bg-surface-container-low"><strong>${escape(u.unitNumber)}</strong> · ${escape(names.get(u.typeId) || u.typeId)} · ${escape(u.status)}<p>Tầng: ${escape(u.floor || '—')} · Khu: ${escape(u.zone || '—')}</p></article>`).join('') : '<p>Chưa có kho vật lý tại cơ sở này.</p>';
      message('');
    } catch (error) { message(error.message); }
    finally { $('catalog-loading').hidden = true; }
  }
  $('catalog-facility').addEventListener('change', loadFacility);
  SafeBoxAuth.requireRole('FACILITY_MANAGER').then(async authorized => {
    if (!authorized) return;
    const user = SafeBoxAuth.getCurrentUser();
    if (user?.role !== 'FACILITY_MANAGER') return;
    if (headerUser) headerUser.textContent = user.fullName;
    try {
      const [assigned, pending] = await Promise.all([
        api('/api/manager/facilities'), api('/api/manager/reservations/pending')
      ]);
      facilities = assigned;
      if (badge) badge.textContent = `${pending.length} MỚI`;
      $('catalog-facility').innerHTML = assigned.map(f => `<option value="${escape(f.facilityId)}">${escape(f.name)}</option>`).join('');
      $('catalog-facility').disabled = !assigned.length;
      $('catalog-empty').hidden = assigned.length > 0;
      if (assigned.length) await loadFacility();
      else { headerFacility.textContent = 'Chưa được phân công cơ sở'; headerInfo.textContent = 'Vui lòng liên hệ quản trị viên.'; $('catalog-loading').hidden = true; }
    } catch (error) { $('catalog-loading').hidden = true; message(error.message); }
  });
})();
