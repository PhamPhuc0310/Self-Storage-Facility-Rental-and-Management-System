(() => {
  if (!window.SafeBoxAuth) return;
  const header = document.querySelector('header');
  if (!header) return;
  const wasSticky = header.classList.contains('sticky');
  const css = document.createElement('style');
  css.textContent = `
    #safebox-site-header{z-index:50;width:100%;background:rgba(250,248,255,.97);backdrop-filter:blur(16px);box-shadow:0 1px 8px rgba(0,0,0,.08)}
    #safebox-site-header .sb-inner{max-width:1280px;height:80px;margin:auto;padding:0 24px;display:flex;align-items:center;gap:16px}
    #safebox-site-header .sb-brand{display:flex;align-items:center;gap:10px;min-width:0;flex:none;text-decoration:none;color:#001229}
    #safebox-site-header .sb-brand img{height:32px;width:auto;max-width:44px;object-fit:contain}
    #safebox-site-header .sb-brand-name{display:block;font-weight:700;font-size:16px;line-height:20px;white-space:nowrap}
    #safebox-site-header .sb-brand-sub{display:block;color:#44474d;font-size:10px;line-height:14px;white-space:nowrap}
    #safebox-site-header .sb-nav{display:none;align-items:center;justify-content:center;gap:2px;min-width:0;flex:1}
    #safebox-site-header .sb-link{display:block;padding:9px 7px;border-radius:8px;color:#44474d;font-size:12px;font-weight:600;line-height:18px;text-decoration:none;white-space:nowrap}
    #safebox-site-header .sb-link:hover,#safebox-site-header .sb-link[aria-current="page"]{background:#e2e7ff;color:#001229}
    #safebox-site-header .sb-account{margin-left:auto;display:flex;align-items:center;gap:9px;min-width:0;flex:none}
    #safebox-site-header .sb-avatar{width:32px;height:32px;border-radius:50%;display:grid;place-items:center;background:#dbe1ff;color:#0051d5;flex:none}
    #safebox-site-header .sb-identity{display:none;min-width:0;max-width:122px}
    #safebox-site-header .sb-name{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;font-weight:700;line-height:16px;color:#131b2e}
    #safebox-site-header .sb-role{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;line-height:15px;color:#44474d}
    #safebox-site-header .sb-account-action,#safebox-site-header .sb-menu-button{border:0;background:transparent;color:#001229;border-radius:8px;padding:7px;display:grid;place-items:center;cursor:pointer;flex:none}
    #safebox-site-header .sb-account-action:hover,#safebox-site-header .sb-menu-button:hover{background:#e2e7ff}
    #safebox-site-header [hidden]{display:none!important}
    #safebox-site-header .sb-mobile-panel{position:absolute;left:0;right:0;top:80px;max-height:calc(100vh - 80px);overflow-y:auto;padding:12px 24px 20px;background:#faf8ff;box-shadow:0 8px 16px rgba(0,18,41,.14)}
    #safebox-site-header .sb-mobile-panel[hidden]{display:none!important}
    #safebox-site-header .sb-mobile-panel .sb-link{padding:12px;font-size:14px}
    #safebox-site-header .sb-mobile-identity{padding:12px;color:#44474d;font-size:12px;border-top:1px solid #c4c6ce}
    @media(min-width:640px){#safebox-site-header .sb-identity{display:block}}
    @media(min-width:1280px){#safebox-site-header .sb-nav{display:flex}#safebox-site-header .sb-menu-button{display:none}#safebox-site-header .sb-mobile-panel{display:none!important}}
    @media(max-width:639px){#safebox-site-header .sb-inner{padding:0 16px;gap:8px}#safebox-site-header .sb-brand-sub{display:none}#safebox-site-header .sb-brand-name{font-size:14px}}
  `;
  document.head.appendChild(css);
  header.id = 'safebox-site-header';
  header.className = wasSticky ? 'sticky top-0' : 'fixed top-0 left-0';
  header.innerHTML = `<div class="sb-inner">
    <a class="sb-brand" href="/stitch/trang_ch_safebox_storage.html"><img src="/stitch/assets/safebox-storage-logo.svg" alt=""><span><span class="sb-brand-name">SafeBox Storage</span><span class="sb-brand-sub">Hệ thống lưu trữ thông minh</span></span></a>
    <nav class="sb-nav" aria-label="Điều hướng chính"></nav>
    <div class="sb-account"><span class="sb-avatar material-symbols-outlined" aria-hidden="true">account_circle</span><span class="sb-identity"><span class="sb-name" id="nav-user-name"></span><span class="sb-role"></span></span><button class="sb-account-action" id="btn-logout" type="button" aria-label="Đăng xuất" title="Đăng xuất" hidden><span class="material-symbols-outlined">logout</span></button><button class="sb-menu-button" type="button" aria-label="Mở menu" aria-expanded="false" aria-controls="safebox-mobile-menu"><span class="material-symbols-outlined">menu</span></button></div>
  </div><nav class="sb-mobile-panel" id="safebox-mobile-menu" aria-label="Điều hướng di động" hidden></nav>`;
  const desktop = header.querySelector('.sb-nav');
  const mobile = header.querySelector('.sb-mobile-panel');
  const menuButton = header.querySelector('.sb-menu-button');
  const logout = header.querySelector('#btn-logout');
  const publicLinks = [
    ['Trang chủ','/stitch/trang_ch_safebox_storage.html'],
    ['Tìm kho','/stitch/t_m_kho_l_u_tr_safebox_storage.html'],
    ['Cơ sở','/stitch/trang_ch_safebox_storage.html#home-facilities']
  ];
  const customerLinks = [
    ['Yêu cầu của tôi','/stitch/y_u_c_u_c_a_t_i_safebox_storage.html'],
    ['Hợp đồng của tôi','/uc10-customer-contract.html'],
    ['Kho đang thuê','/stitch/kho_dang_thue_safebox_storage.html'],
    ['Thanh toán và lịch nhận kho','/stitch/uc11-customer-payments-appointments.html'],
    ['Hỗ trợ kỹ thuật & Sự cố','/stitch/ho_tro_khach_hang_safebox_storage.html']
  ];
  const managerLinks = [
    ['Tổng quan quản lý','/stitch/t_ng_quan_qu_n_l_safebox_manager.html'],
    ['Duyệt yêu cầu','/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html'],
    ['Vận hành và phân công','/stitch/uc12-manager-operations.html'],
    ['Tiếp nhận trả kho','/stitch/bien_ban_tra_kho_safebox_manager.html']
  ];
  function link(label, href) {
    const a = document.createElement('a');
    a.className = 'sb-link';
    a.href = href;
    a.textContent = label;
    if (href === '/stitch/kho_dang_thue_safebox_storage.html' && !document.getElementById('nav-active-count-badge')) {
      const count = document.createElement('span');
      count.id = 'nav-active-count-badge';
      count.style.marginLeft = '4px';
      count.textContent = '—';
      a.appendChild(count);
    }
    if (location.pathname === new URL(href, location.origin).pathname) a.setAttribute('aria-current', 'page');
    return a;
  }
  function render(user) {
    desktop.replaceChildren();
    mobile.replaceChildren();
    const role = user?.role;
    let links = publicLinks;
    if (role === 'CUSTOMER') links = [...links, ...customerLinks];
    else if (role === 'FACILITY_MANAGER') links = [...links, ...managerLinks];
    else if (role === 'FACILITY_STAFF') links = [...links, ['Công việc của tôi','/staff-tasks.html'], ['Tiếp nhận trả kho','/stitch/bien_ban_tra_kho_safebox_manager.html']];
    else if (role === 'SYSTEM_ADMIN' || role === 'BUSINESS_OPERATIONS_MANAGER') links = [...links, ['Cổng điều hướng','/role-home.html']];
    for (const [label, href] of links) {
      desktop.appendChild(link(label, href));
      mobile.appendChild(link(label, href));
    }
    const roleName = {CUSTOMER:'Khách hàng',FACILITY_MANAGER:'Quản lý cơ sở',FACILITY_STAFF:'Nhân viên cơ sở',SYSTEM_ADMIN:'Quản trị viên',BUSINESS_OPERATIONS_MANAGER:'Quản lý kinh doanh'}[role] || '';
    header.querySelector('.sb-name').textContent = user?.fullName || 'Khách';
    header.querySelector('.sb-role').textContent = roleName;
    if (user) {
      logout.hidden = false;
      logout.addEventListener('click', () => SafeBoxAuth.logout(), {once:true});
      const identity = document.createElement('p');
      identity.className = 'sb-mobile-identity';
      identity.textContent = `${user.fullName || ''} · ${roleName}`;
      mobile.appendChild(identity);
      const mobileLogout = document.createElement('button');
      mobileLogout.type = 'button';
      mobileLogout.className = 'sb-link';
      mobileLogout.textContent = 'Đăng xuất';
      mobileLogout.addEventListener('click', () => SafeBoxAuth.logout());
      mobile.appendChild(mobileLogout);
    } else {
      logout.hidden = true;
      const signIn = link('Đăng nhập', '/stitch/ng_nh_p_ng_k_safebox_storage.html');
      desktop.appendChild(signIn);
      mobile.appendChild(link('Đăng nhập', '/stitch/ng_nh_p_ng_k_safebox_storage.html'));
    }
  }
  menuButton.addEventListener('click', () => {
    const open = mobile.hidden;
    mobile.hidden = !open;
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.setAttribute('aria-label', open ? 'Đóng menu' : 'Mở menu');
    menuButton.querySelector('.material-symbols-outlined').textContent = open ? 'close' : 'menu';
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') { mobile.hidden = true; menuButton.setAttribute('aria-expanded', 'false'); }
  });
  render(null);
  if (SafeBoxAuth.getToken()) {
    SafeBoxAuth.authFetch('/api/auth/me').then(response => response.ok ? response.json() : null)
      .then(user => { if (user) render(user); })
      .catch(() => {});
  }
})();