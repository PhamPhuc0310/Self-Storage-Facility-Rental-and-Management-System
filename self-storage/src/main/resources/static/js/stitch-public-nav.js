(() => {
  if (!window.SafeBoxAuth) return;
  const header = document.querySelector('header');
  if (!header) return;
  const login = header.querySelector('a[data-path="dang-nhap"], a[href="/stitch/ng_nh_p_ng_k_safebox_storage.html"]');
  const register = header.querySelector('a[data-path="dang-ky"], a[href^="/stitch/ng_nh_p_ng_k_safebox_storage.html?mode=register"]');
  const token = SafeBoxAuth.getToken();
  if (!login || !token) return;
  fetch('/api/auth/me', {headers: {Authorization: `Bearer ${token}`}})
    .then(response => {
      if (!response.ok) throw new Error('Phiên đăng nhập đã hết hạn.');
      return response.json();
    })
    .then(user => {
      const role = {
        CUSTOMER: 'Khách hàng', FACILITY_STAFF: 'Nhân viên',
        FACILITY_MANAGER: 'Quản lý cơ sở', BUSINESS_OPERATIONS_MANAGER: 'Quản lý kinh doanh',
        SYSTEM_ADMIN: 'Quản trị viên'
      }[user.role] || user.role;
      const badge = document.createElement('span');
      badge.className = 'hidden sm:inline text-xs text-on-surface-variant max-w-52 truncate';
      badge.textContent = `${user.fullName} · ${role} · Đang hoạt động`;
      if (user.role === 'FACILITY_MANAGER' || user.role === 'SYSTEM_ADMIN') {
        const portalBtn = document.createElement('a');
        portalBtn.className = 'px-3 py-1.5 rounded-lg bg-primary text-white font-label-button text-xs hover:bg-primary-container transition-colors';
        portalBtn.href = '/stitch/t_ng_quan_qu_n_l_safebox_manager.html';
        portalBtn.textContent = 'Cổng quản lý cơ sở';
        login.parentElement.insertBefore(portalBtn, login);
      } else if (user.role === 'CUSTOMER') {
        const reqBtn = document.createElement('a');
        reqBtn.className = 'px-3 py-1.5 rounded-lg bg-surface-container text-primary font-label-button text-xs hover:bg-surface-container-high transition-colors';
        reqBtn.href = '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html';
        reqBtn.textContent = 'Yêu cầu của tôi';
        login.parentElement.insertBefore(reqBtn, login);
      }
      login.parentElement.insertBefore(badge, login);
      login.href = '#';
      login.textContent = 'Đăng xuất';
      login.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
      if (register) register.hidden = true;

      // Add navigation links based on user role
      const nav = header.querySelector('nav');
      if (nav) {
        if (user.role === 'CUSTOMER' || user.role === 'SYSTEM_ADMIN') {
          if (!nav.querySelector('a[href="/stitch/kho_dang_thue_safebox_storage.html"]')) {
            const rentalLink = document.createElement('a');
            rentalLink.className = 'font-body-md text-body-md text-on-surface-variant hover:text-on-surface transition-colors px-2 py-1';
            rentalLink.href = '/stitch/kho_dang_thue_safebox_storage.html';
            rentalLink.textContent = 'Kho đang thuê';
            nav.appendChild(rentalLink);
          }
        }
        if (user.role === 'FACILITY_MANAGER' || user.role === 'SYSTEM_ADMIN' || user.role === 'FACILITY_STAFF') {
          if (!nav.querySelector('a[href="/stitch/t_ng_quan_qu_n_l_safebox_manager.html"]')) {
            const managerLink = document.createElement('a');
            managerLink.className = 'font-body-md text-body-md text-secondary font-bold hover:underline transition-colors px-2 py-1';
            managerLink.href = '/stitch/t_ng_quan_qu_n_l_safebox_manager.html';
            managerLink.textContent = 'Cổng quản lý cơ sở';
            nav.appendChild(managerLink);
          }
        }
        if (user.role === 'SYSTEM_ADMIN') {
          if (!nav.querySelector('a[href="/role-home.html"]')) {
            const hubLink = document.createElement('a');
            hubLink.className = 'font-body-md text-body-md text-indigo-600 font-bold hover:underline transition-colors px-2 py-1';
            hubLink.href = '/role-home.html';
            hubLink.textContent = 'Cổng điều hướng';
            nav.appendChild(hubLink);
          }
        }
      }
    })
    .catch(() => SafeBoxAuth.clearAuth());
})();
