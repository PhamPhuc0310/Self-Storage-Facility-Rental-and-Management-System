(() => {
  const $ = id => document.getElementById(id);
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c]));
  const money = value => value == null ? 'Chưa có giá' : new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(value);
  const statusLabel = status => ({AVAILABLE:'Sẵn sàng',RESERVED:'Đã giữ chỗ',OCCUPIED:'Đang cho thuê',MAINTENANCE:'Bảo trì',INACTIVE:'Ngừng sử dụng',ACTIVE:'Đang áp dụng'})[status] || status;
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
  let facilities = [], unitTypes = [], currentUnits = [];
  const facilityButton = document.createElement('button');
  facilityButton.id = 'catalog-edit-facility';
  facilityButton.className = 'ml-3 px-4 py-2 rounded-lg bg-secondary text-white';
  facilityButton.textContent = 'Thông tin cơ sở do quản lý kinh doanh cập nhật';
  facilityButton.hidden = true;
  $('catalog-facility').insertAdjacentElement('afterend', facilityButton);
  const facilityDialog = document.createElement('dialog');
  facilityDialog.id = 'catalog-facility-dialog';
  facilityDialog.className = 'rounded-xl p-6 shadow-xl w-[min(95vw,520px)] bg-white text-on-surface';
  facilityDialog.innerHTML = `<form id="catalog-facility-form" class="space-y-3"><h2 class="font-headline-md text-headline-md">Cập nhật cơ sở</h2>
    <p id="catalog-facility-error" class="text-error" role="alert" hidden></p>
    <label class="block">Tên cơ sở<input id="catalog-facility-name" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label>
    <label class="block">Địa chỉ<input id="catalog-facility-address" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label>
    <div class="flex justify-end gap-3"><button type="button" id="catalog-facility-cancel">Đóng</button><button id="catalog-facility-save" class="px-5 py-2 bg-secondary text-white rounded-lg">Lưu cơ sở</button></div></form>`;
  document.body.append(facilityDialog);
  $('catalog-facility-cancel').addEventListener('click', () => facilityDialog.close());
  facilityButton.addEventListener('click', () => {
    const facility = facilities.find(f => f.facilityId === $('catalog-facility').value);
    if (!facility) return;
    $('catalog-facility-error').hidden = true;
    $('catalog-facility-name').value = facility.name;
    $('catalog-facility-address').value = facility.address;
    facilityDialog.showModal();
  });
  $('catalog-facility-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('catalog-facility-save'); button.disabled = true;
    const facilityId = $('catalog-facility').value;
    const previous = facilities.find(f => f.facilityId === facilityId);
    try {
      const updated = await api(`/api/manager/facilities/${facilityId}`,
        {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({
          name:$('catalog-facility-name').value.trim(), address:$('catalog-facility-address').value.trim(), status:previous.status
        })});
      facilities = facilities.map(f => f.facilityId === facilityId ? updated : f);
      $('catalog-facility').selectedOptions[0].textContent = updated.name;
      facilityDialog.close(); await loadFacility();
    } catch (error) { $('catalog-facility-error').textContent = error.message; $('catalog-facility-error').hidden = false; }
    finally { button.disabled = false; }
  });
  async function api(path, options) {
    const response = await SafeBoxAuth.authFetch(path, options);
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || 'Không tải được danh mục kho.');
    return data;
  }
  function message(value) {
    $('catalog-message').textContent = value;
    $('catalog-message').className = 'rounded-lg p-3 bg-error-container text-error';
    $('catalog-message').hidden = !value;
  }
  const unitDialog = document.createElement('dialog');
  unitDialog.id = 'catalog-unit-dialog';
  unitDialog.className = 'rounded-xl p-6 shadow-xl w-[min(95vw,520px)] bg-white text-on-surface';
  unitDialog.innerHTML = `<form id="catalog-unit-form" class="space-y-3">
    <h2 id="catalog-unit-title" class="font-headline-md text-headline-md">Kho vật lý</h2>
    <p id="catalog-unit-error" class="text-error" role="alert" hidden></p>
    <label class="block">Mã kho<input id="catalog-unit-number" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label>
    <label class="block">Loại kho<select id="catalog-unit-type" required class="block w-full p-3 bg-surface-container-low rounded-lg"></select></label>
    <label class="block">Tầng<input id="catalog-unit-floor" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label>
    <label class="block">Khu<input id="catalog-unit-zone" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label>
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-2"><label>Dài (m)<input id="catalog-unit-length" type="number" min="0.001" step="0.001" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label><label>Rộng (m)<input id="catalog-unit-width" type="number" min="0.001" step="0.001" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label><label>Cao (m)<input id="catalog-unit-height" type="number" min="0.001" step="0.001" required class="block w-full p-3 bg-surface-container-low rounded-lg"></label></div>
    <p id="catalog-unit-volume" aria-live="polite">Thể tích: — m³</p>
    <label class="block"><input id="catalog-unit-dimensions-verified" type="checkbox" disabled> Kích thước đã xác minh qua phiếu đo hoặc kiểm tra trực tiếp</label>
    <label class="block"><input id="catalog-unit-ready" type="checkbox"> Đã kiểm tra và xác nhận kho sẵn sàng</label>
    <label class="block">Trạng thái<select id="catalog-unit-status" required class="block w-full p-3 bg-surface-container-low rounded-lg"><option value="AVAILABLE">Sẵn sàng</option><option value="MAINTENANCE">Bảo trì</option><option value="INACTIVE">Ngừng sử dụng</option></select></label>
    <div class="flex justify-end gap-3"><button type="button" id="catalog-unit-cancel">Đóng</button><button id="catalog-unit-save" class="px-5 py-2 bg-secondary text-white rounded-lg">Lưu kho</button></div>
  </form>`;
  document.body.append(unitDialog);
  const reviewPanel = document.createElement('section');
  reviewPanel.className = 'bg-surface-container-lowest rounded-xl p-6 shadow-sm space-y-3';
  reviewPanel.innerHTML = '<h2 class="font-headline-md text-headline-md">Duyệt số đo nhân viên</h2><div id="catalog-reviews" class="space-y-3"></div>';
  $('catalog-units').closest('section').insertAdjacentElement('afterend', reviewPanel);
  const decisionDialog = document.createElement('dialog');
  decisionDialog.className = 'rounded-xl p-6 shadow-xl w-[min(95vw,520px)] bg-white text-on-surface';
  decisionDialog.innerHTML = '<form id="catalog-decision-form" class="space-y-3"><h2 id="catalog-decision-title" class="font-headline-md"></h2><label class="block">Lý do quyết định<textarea id="catalog-decision-reason" required maxlength="1000" class="block w-full p-3 bg-surface-container-low rounded-lg"></textarea></label><p id="catalog-decision-error" role="alert" hidden></p><div class="flex justify-end gap-3"><button type="button" id="catalog-decision-cancel">Đóng</button><button class="px-5 py-2 bg-secondary text-white">Ghi nhận</button></div></form>';
  document.body.append(decisionDialog);
  $('catalog-decision-cancel').onclick = () => decisionDialog.close();
  let pendingDecision = null;
  $('catalog-decision-form').onsubmit = async event => {
    event.preventDefault();
    try {
      await api(`/api/manager/facilities/${$('catalog-facility').value}/measurements/${pendingDecision.id}/${pendingDecision.action}`,
        {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:$('catalog-decision-reason').value.trim()})});
      decisionDialog.close(); await loadFacility();
    } catch (error) { $('catalog-decision-error').textContent = error.message; $('catalog-decision-error').hidden = false; }
  };
  $('catalog-reviews').onclick = event => {
    const button = event.target.closest('[data-decision]'); if (!button) return;
    pendingDecision = {id:button.dataset.id,action:button.dataset.decision};
    $('catalog-decision-title').textContent = button.dataset.decision === 'approve' ? 'Duyệt số đo' : 'Từ chối số đo';
    $('catalog-decision-reason').value = ''; $('catalog-decision-error').hidden = true; decisionDialog.showModal();
  };
  const measureDialog = document.createElement('dialog');
  measureDialog.id = 'catalog-measure-dialog';
  measureDialog.className = 'rounded-xl p-6 shadow-xl w-[min(95vw,520px)] bg-white text-on-surface';
  measureDialog.innerHTML = `<form id="catalog-measure-form" class="space-y-3">
    <h2 class="font-headline-md text-headline-md">Bổ sung kích thước kho</h2>
    <p id="catalog-measure-unit"></p>
    <p>Chỉ nhập số đo trực tiếp của ô kho này. Kích thước mẫu của loại kho không phải số đo đã xác minh. Thao tác này không thay đổi tình trạng, giá thuê hoặc hợp đồng.</p>
    <p id="catalog-measure-error" role="alert" hidden></p>
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-2"><label>Dài (m)<input class="block w-full min-w-0 p-3 bg-surface-container-low rounded-lg" id="catalog-measure-length" type="number" min="0.001" step="0.001" required></label><label>Rộng (m)<input class="block w-full min-w-0 p-3 bg-surface-container-low rounded-lg" id="catalog-measure-width" type="number" min="0.001" step="0.001" required></label><label>Cao (m)<input class="block w-full min-w-0 p-3 bg-surface-container-low rounded-lg" id="catalog-measure-height" type="number" min="0.001" step="0.001" required></label></div>
    <p id="catalog-measure-volume" aria-live="polite">Thể tích: chưa xác minh</p>
    <label><input id="catalog-measure-confirm" type="checkbox" required> Tôi đã đo trực tiếp và xác nhận đủ ba kích thước</label>
    <div class="flex justify-end gap-3"><button type="button" id="catalog-measure-cancel">Đóng</button><button id="catalog-measure-save" class="px-5 py-2 bg-secondary text-white">Lưu số đo</button></div>
  </form>`;
  document.body.append(measureDialog);
  let measuringUnitId = null;
  $('catalog-measure-cancel').addEventListener('click', () => measureDialog.close());
  const measuredVolume = () => {
    const values = ['length','width','height'].map(k => Number($('catalog-measure-'+k).value));
    $('catalog-measure-volume').textContent = values.every(v => Number.isFinite(v) && v > 0)
      ? `Thể tích sau xác minh: ${(values[0]*values[1]*values[2]).toFixed(3)} m³` : 'Thể tích: chưa xác minh';
  };
  ['length','width','height'].forEach(k => $('catalog-measure-'+k).addEventListener('input', measuredVolume));
  $('catalog-measure-form').addEventListener('submit', async event => {
    event.preventDefault(); const button = $('catalog-measure-save'); button.disabled = true;
    const facilityId = $('catalog-facility').value;
    const payload = {lengthM:Number($('catalog-measure-length').value),widthM:Number($('catalog-measure-width').value),
      heightM:Number($('catalog-measure-height').value),physicallyMeasured:$('catalog-measure-confirm').checked};
    try {
      await api(`/api/manager/facilities/${facilityId}/units/${measuringUnitId}/dimensions`,
        {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
      measureDialog.close(); await loadFacility(); message('');
    } catch (error) { $('catalog-measure-error').textContent=error.message; $('catalog-measure-error').hidden=false; }
    finally { button.disabled=false; }
  });
  $('catalog-unit-cancel').addEventListener('click', () => unitDialog.close());
  function showVolume() { const v=['length','width','height'].map(x => Number($('catalog-unit-'+x).value)); $('catalog-unit-volume').textContent = `Thể tích đã xác minh: ${$('catalog-unit-dimensions-verified').checked && v.every(x => x > 0) ? (v[0]*v[1]*v[2]).toFixed(3) : '—'} m³`; }
  ['length','width','height'].forEach(x => $('catalog-unit-'+x).addEventListener('input', showVolume));
  $('catalog-unit-dimensions-verified').addEventListener('change', showVolume);
  let editingUnitId = null;
  function openUnitEditor(unit) {
    editingUnitId = unit?.unitId || null;
    $('catalog-unit-error').hidden = true;
    $('catalog-unit-title').textContent = unit ? 'Cập nhật kho vật lý' : 'Thêm kho vật lý';
    $('catalog-unit-type').innerHTML = unitTypes.filter(type => type.status === 'ACTIVE' || type.typeId === unit?.typeId)
      .map(type => `<option value="${type.typeId}">${escape(type.typeName)}</option>`).join('');
    $('catalog-unit-number').value = unit?.unitNumber || '';
    $('catalog-unit-type').value = unit?.typeId ?? unitTypes.find(type => type.status === 'ACTIVE')?.typeId ?? '';
    $('catalog-unit-floor').value = unit?.floor || '';
    $('catalog-unit-zone').value = unit?.zone || '';
    $('catalog-unit-length').value = unit?.lengthM ?? '';
    $('catalog-unit-width').value = unit?.widthM ?? '';
    $('catalog-unit-height').value = unit?.heightM ?? '';
    $('catalog-unit-dimensions-verified').checked = !!unit?.dimensionsVerified;
    $('catalog-unit-ready').checked = !!unit?.readinessVerified;
    $('catalog-unit-status').value = unit?.status || 'INACTIVE';
    showVolume();
    unitDialog.showModal();
  }
  $('catalog-units').addEventListener('click', event => {
    if (event.target.closest('#catalog-create-unit')) return openUnitEditor(null);
    const activate = event.target.closest('.catalog-activate-unit');
    if (activate) {
      const id = $('catalog-facility').value;
      api(`/api/manager/facilities/${id}/units/${activate.dataset.id}/activation-check`).then(check => {
        if (!check.eligible) { message(check.reasons.join(' ')); return; }
        return api(`/api/manager/facilities/${id}/units/${activate.dataset.id}/activate`, {method:'POST'}).then(() => loadFacility());
      }).catch(error => message(error.message)); return;
    }
    const measure = event.target.closest('.catalog-measure-unit');
    if (measure) {
      const unit = currentUnits.find(item => item.unitId === measure.dataset.id);
      if (!unit || unit.dimensionsVerified) return;
      measuringUnitId = unit.unitId; $('catalog-measure-unit').textContent = `Ô ${unit.unitNumber}`;
      for (const key of ['length','width','height']) $('catalog-measure-'+key).value = '';
      $('catalog-measure-confirm').checked = false; $('catalog-measure-error').hidden = true;
      measuredVolume(); measureDialog.showModal(); return;
    }
    const edit = event.target.closest('.catalog-edit-unit');
    if (edit) openUnitEditor(currentUnits.find(unit => unit.unitId === edit.dataset.id));
  });
  $('catalog-unit-form').addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('catalog-unit-save'); button.disabled = true;
    const facilityId = $('catalog-facility').value;
    const payload = {unitNumber:$('catalog-unit-number').value.trim(), typeId:Number($('catalog-unit-type').value),
      floor:$('catalog-unit-floor').value.trim(), zone:$('catalog-unit-zone').value.trim(), status:$('catalog-unit-status').value,
      lengthM:Number($('catalog-unit-length').value), widthM:Number($('catalog-unit-width').value), heightM:Number($('catalog-unit-height').value),
      dimensionsVerified:$('catalog-unit-dimensions-verified').checked, readinessVerified:$('catalog-unit-ready').checked};
    try {
      await api(`/api/manager/facilities/${facilityId}/units${editingUnitId ? '/' + editingUnitId : ''}`,
        {method:editingUnitId ? 'PUT' : 'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
      unitDialog.close(); await loadFacility(); message('');
    } catch (error) { $('catalog-unit-error').textContent = error.message; $('catalog-unit-error').hidden = false; }
    finally { button.disabled = false; }
  });
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
      const [types, units, reviews] = await Promise.all([
        api(`/api/manager/facilities/${id}/unit-types`),
        api(`/api/manager/facilities/${id}/units`),
        api(`/api/manager/facilities/${id}/measurements`)
      ]);
      unitTypes = types;
      currentUnits = units;
      const names = new Map(types.map(t => [t.typeId, t.typeName]));
      $('catalog-types').innerHTML = types.length ? types.map(t => `<article class="p-4 rounded-lg bg-surface-container-low"><strong>${escape(t.typeName)}</strong> · ${escape(statusLabel(t.status))}<p>Giá tháng: ${money(t.monthlyPrice)}</p></article>`).join('') : '<p>Chưa có loại kho tại cơ sở này.</p>';
      $('catalog-units').innerHTML = `<button id="catalog-create-unit" class="px-4 py-2 rounded-lg bg-secondary text-white">Thêm kho vật lý</button>`
        + (units.length ? units.map(u => `<article class="p-4 rounded-lg bg-surface-container-low"><strong>${escape(u.unitNumber)}</strong> · ${escape(names.get(u.typeId) || u.typeId)} · ${escape(statusLabel(u.status))}<p>Tầng: ${escape(u.floor || '—')} · Khu: ${escape(u.zone || '—')} · Thể tích: ${u.volumeM3 == null ? 'Chưa xác minh' : escape(u.volumeM3) + ' m³'}</p>${u.measurementSource === 'SYNTHETIC_DEMO' ? '<p>Số đo mô phỏng cho demo, không phải đo thực địa.</p>' : ''}${!u.dimensionsVerified ? `<button class="catalog-measure-unit text-secondary underline" data-id="${escape(u.unitId)}">Bổ sung số đo</button>` : ''} ${['AVAILABLE','MAINTENANCE','INACTIVE'].includes(u.status) ? `<button class="catalog-edit-unit text-secondary underline" data-id="${escape(u.unitId)}">Cập nhật</button>` : ''} ${u.status === 'INACTIVE' ? `<button class="catalog-activate-unit px-3 py-2 bg-secondary text-white rounded-lg" data-id="${escape(u.unitId)}">Mở cho thuê</button>` : ''}</article>`).join('') : '<p>Chưa có kho vật lý tại cơ sở này.</p>');
      $('catalog-reviews').innerHTML = reviews.length ? reviews.map(r => `<article class="p-4 rounded-lg bg-surface-container-low"><strong>${escape(units.find(u => u.unitId === r.unitId)?.unitNumber || r.unitId)}</strong> · ${escape(r.status === 'SUBMITTED' ? 'Chờ duyệt' : r.status === 'APPROVED' ? 'Đã duyệt' : 'Đã từ chối')}<p>${escape(r.lengthM)} × ${escape(r.widthM)} × ${escape(r.heightM)} m · ${escape(r.conditionNote)}</p><p>${r.source === 'SYNTHETIC_DEMO' ? 'Số đo mô phỏng demo; không phải đo thực địa' : 'Nhân viên ghi nhận đo thực địa'}</p>${r.decisionReason ? `<p>Lý do: ${escape(r.decisionReason)}</p>` : ''}${r.status === 'SUBMITTED' ? `<button class="text-secondary underline mr-3" data-decision="approve" data-id="${escape(r.submissionId)}">Duyệt</button><button class="text-error underline" data-decision="reject" data-id="${escape(r.submissionId)}">Từ chối</button>` : ''}</article>`).join('') : '<p>Chưa có phiếu số đo.</p>';
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
      facilityButton.disabled = !assigned.length;
      $('catalog-empty').hidden = assigned.length > 0;
      if (assigned.length) await loadFacility();
      else { headerFacility.textContent = 'Chưa được phân công cơ sở'; headerInfo.textContent = 'Vui lòng liên hệ quản trị viên.'; $('catalog-loading').hidden = true; }
    } catch (error) { $('catalog-loading').hidden = true; message(error.message); }
  });
})();
