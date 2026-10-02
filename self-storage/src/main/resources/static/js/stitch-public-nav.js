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
      login.parentElement.insertBefore(badge, login);
      login.href = '#';
      login.textContent = 'Đăng xuất';
      login.addEventListener('click', event => { event.preventDefault(); SafeBoxAuth.logout(); });
      if (register) register.hidden = true;
    })
    .catch(() => SafeBoxAuth.clearAuth());
})();
