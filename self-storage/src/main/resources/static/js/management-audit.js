(async()=>{
  if(!await SafeBoxAuth.requireRole('SYSTEM_ADMIN'))return;
  const {esc,mount,drawer,api}=SafeBoxGrid,$=id=>document.getElementById(id),time=SafeBoxUc12Time.formatDateTimeVN;
  const actions={CHANGE_ROLE:'Đổi vai trò',CHANGE_USER_STATUS:'Đổi trạng thái tài khoản',GRANT_MANAGER_FACILITY:'Cấp cơ sở cho quản lý',REVOKE_MANAGER_FACILITY:'Thu hồi cơ sở quản lý',GRANT_STAFF_REGION:'Cấp vùng nhân viên',REVOKE_STAFF_REGION:'Thu hồi vùng nhân viên'};
  let names=new Map();
  const actor=id=>names.get(id)||'Tài khoản không có trong danh sách',target=x=>names.get(x.entityId)||x.entityType||'Không ghi nhận';
  const grid=mount($('audit'),[
    {label:'Thời gian',value:x=>time(x.createdAt),sortValue:x=>SafeBoxUc12Time.appointmentInstant(x.createdAt)||0},
    {label:'Người thực hiện',value:x=>actor(x.userId)},
    {label:'Hành động',value:x=>actions[x.actionType]||x.actionType},
    {label:'Đối tượng',value:target}
  ],{search:$('search'),filter:x=>{const date=SafeBoxUc12Time.appointmentLocalFields(x.createdAt)?.date;return (!$('action-filter').value||x.actionType===$('action-filter').value)&&(!$('audit-from').value||date>=$('audit-from').value)&&(!$('audit-to').value||date<=$('audit-to').value)}});
  const open=drawer($('detail'));
  $('audit').addEventListener('grid-open',e=>{const x=e.detail;open(`<dl><dt>Thời gian</dt><dd>${esc(time(x.createdAt))}</dd><dt>Người thực hiện</dt><dd>${esc(actor(x.userId))}</dd><dt>Hành động</dt><dd>${esc(actions[x.actionType]||x.actionType)}</dd><dt>Đối tượng</dt><dd>${esc(target(x))}</dd><dt>Mã người thực hiện</dt><dd>${esc(x.userId||'Không ghi nhận')}</dd><dt>Mã đối tượng</dt><dd>${esc(x.entityId||'Không ghi nhận')}</dd><dt>Chi tiết</dt><dd><pre>${esc(x.description||'Không có mô tả.')}</pre></dd></dl>`)});
  for(const id of ['action-filter','audit-from','audit-to'])$(id).onchange=()=>{if($('audit-from').value&&$('audit-to').value&&$('audit-from').value>$('audit-to').value){$('message').textContent='Ngày kết thúc phải từ ngày bắt đầu trở đi.';return}$('message').textContent='Đang xem nhật ký gần nhất.';grid.refresh()};
  async function load(){
    $('message').textContent='Đang tải nhật ký...';$('reload').disabled=true;$('audit').setAttribute('aria-busy','true');
    try{const [logs,users]=await Promise.all([api('/api/admin/access/audit'),api('/api/admin/access/users')]);names=new Map(users.map(u=>[u.userId,u.fullName||u.email]));$('action-filter').innerHTML='<option value="">Tất cả hành động</option>';[...new Set(logs.map(x=>x.actionType))].forEach(a=>$('action-filter').add(new Option(actions[a]||a,a)));grid.set(logs);$('message').textContent=`Đã tải ${logs.length} bản ghi gần nhất.`}catch(e){grid.set([]);$('message').textContent=e.message}finally{$('reload').disabled=false;$('audit').setAttribute('aria-busy','false')}
  }
  $('reload').onclick=load;await load();
})();
