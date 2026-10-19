(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const date = value => value ? SafeBoxUc12Time.formatDateVN(value) : '—';
  const badge = document.querySelector('aside [data-path="yeu-cau-dat-kho"] > span');
  const headerName = document.querySelector('header span.font-headline-sm');
  const headerInfo = document.querySelector('header span.font-body-sm');
  const headerUser = document.querySelector('header .border-l span.font-label-button');
  document.querySelectorAll('header button').forEach(button => button.hidden = true);
  const routes = {
    'co-so-cua-toi':'#manager-facilities',
    'loai-kho':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'kho-vat-ly':'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',
    'yeu-cau-dat-kho':'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html'
  };
  document.querySelectorAll('aside a[data-path]').forEach(link => {
    if (routes[link.dataset.path]) link.href = routes[link.dataset.path];
    if (link.dataset.path === 'dang-nhap') link.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
  });
  async function api(path) {
    const response = await SafeBoxAuth.authFetch(path);
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || 'Không tải được dữ liệu quản lý.');
    return body;
  }
  let facilities = [];
  let pending = [];
  function showError(error) {
    $('dashboard-message').textContent = error.message;
    $('dashboard-message').hidden = false;
  }
  function selectedFacilities() {
    const id = $('dashboard-facility').value;
    return id ? facilities.filter(f => f.facilityId === id) : facilities;
  }
  async function render() {
    $('dashboard-message').hidden = true;
    for (const metric of ['dashboard-total', 'dashboard-available', 'dashboard-maintenance']) $(metric).textContent = '—';
    $('dashboard-unit-map').innerHTML = '<p class="col-span-6 text-on-surface-variant">Đang tải kho vật lý...</p>';
    $('dashboard-legend').replaceChildren();
    const shown = selectedFacilities();
    const id = $('dashboard-facility').value;
    const name = shown.length === 1 ? shown[0].name : shown.length ? `${shown.length} cơ sở được phân công` : 'Chưa được phân công cơ sở';
    headerName.textContent = name;
    headerInfo.textContent = shown.length === 1 ? shown[0].address : name;
    $('dashboard-facility-name').textContent = name;
    $('dashboard-facility-status').textContent = shown.length === 1 ? shown[0].status : '';
    $('dashboard-address').textContent = shown.length === 1 ? shown[0].address : shown.map(f => f.address).join(' · ') || 'Vui lòng liên hệ quản trị viên.';
    $('dashboard-phone').textContent = shown.length === 1 ? (shown[0].phone || 'Chưa có số liên hệ') : 'Chọn một cơ sở để xem liên hệ';
    $('dashboard-hours').textContent = shown.length === 1 && shown[0].openingTime && shown[0].closingTime ? `${shown[0].openingTime} – ${shown[0].closingTime}` : '';
    const allowed = new Set(facilities.map(f => f.facilityId));
    const rows = pending.filter(r => allowed.has(r.facilityId) && (!id || r.facilityId === id));
    badge.textContent = `${pending.filter(r => allowed.has(r.facilityId)).length} MỚI`;
    $('dashboard-pending').textContent = rows.length;
    $('dashboard-pending-label').textContent = `${rows.length} YÊU CẦU`;
    $('dashboard-pending-rows').innerHTML = rows.slice(0, 5).map(r => `<tr><td class="p-4 text-secondary">${escape(r.reservationId)}</td><td class="p-4">${escape(r.customerName)}</td><td class="p-4">${escape(r.facilityName)} · ${escape(r.typeName)}</td><td class="p-4">${date(r.startDate)} – ${date(r.endDate)}</td><td class="p-4">${date(r.createdAt)}</td></tr>`).join('');
    $('dashboard-pending-empty').hidden = rows.length > 0;
    // Every request uses a facility ID supplied by the server's assignment list.
    const unitGroups = await Promise.all(shown.map(f => api(`/api/manager/facilities/${encodeURIComponent(f.facilityId)}/units`)));
    const units = unitGroups.flat();
    const count = status => units.filter(u => u.status === status).length;
    $('dashboard-total').textContent = units.length;
    $('dashboard-available').textContent = count('AVAILABLE');
    $('dashboard-maintenance').textContent = count('MAINTENANCE');
    $('dashboard-map-context').textContent = shown.length ? `${units.length} kho vật lý tại ${name}` : 'Chưa có cơ sở được phân công.';
    const colors = {AVAILABLE:'bg-tertiary-fixed text-primary', RESERVED:'bg-secondary-container text-white', OCCUPIED:'bg-secondary-container text-white', IN_USE:'bg-secondary-container text-white', MAINTENANCE:'bg-error/70 text-white', INACTIVE:'bg-outline text-white'};
    const labels = {AVAILABLE:'Còn trống', RESERVED:'Đã đặt', OCCUPIED:'Đang sử dụng', IN_USE:'Đang sử dụng', MAINTENANCE:'Bảo trì', INACTIVE:'Ngừng hoạt động'};
    $('dashboard-unit-map').innerHTML = units.length ? units.map(u => `<div class="h-7 rounded flex items-center justify-center text-[10px] font-mono truncate ${colors[u.status] || 'bg-outline text-white'}" title="${escape([u.unitNumber, u.floor, u.zone, labels[u.status] || u.status].filter(Boolean).join(' · '))}">${escape(u.unitNumber)}</div>`).join('') : '<p class="col-span-6 text-on-surface-variant">Chưa có kho vật lý tại cơ sở đã chọn.</p>';
    $('dashboard-legend').innerHTML = [...new Set(units.map(u => u.status))].map(status => `<span>${escape(labels[status] || status)} (${count(status)})</span>`).join('');
  }
  async function load() {
    try {
      [facilities, pending] = await Promise.all([api('/api/manager/facilities'), api('/api/manager/reservations/pending')]);
      const filter = $('dashboard-facility');
      const previous = filter.value;
      filter.innerHTML = '<option value="">Tất cả cơ sở</option>' + facilities.map(f => `<option value="${escape(f.facilityId)}">${escape(f.name)}</option>`).join('');
      filter.value = facilities.some(f => f.facilityId === previous) ? previous : '';
      filter.disabled = facilities.length < 2;
      await render();
    } catch (error) { showError(error); }
  }
  $('dashboard-facility').addEventListener('change', () => render().catch(showError));
  SafeBoxAuth.requireRole('FACILITY_MANAGER').then(ok => {
    if (!ok) return;
    const user = SafeBoxAuth.getCurrentUser();
    $('dashboard-user').textContent = user?.fullName || 'Quản lý cơ sở';
    $('dashboard-role').textContent = user?.role || 'FACILITY_MANAGER';
    headerUser.textContent = user?.fullName || '';
    load();
  });
})();
