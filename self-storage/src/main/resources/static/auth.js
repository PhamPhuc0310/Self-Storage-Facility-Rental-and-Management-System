(function () {
  const loginPath = '/stitch/ng_nh_p_ng_k_safebox_storage.html';
  const destinations = {
    CUSTOMER: '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html',
    FACILITY_STAFF: '/role-home.html',
    FACILITY_MANAGER: '/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    BUSINESS_OPERATIONS_MANAGER: '/role-home.html',
    SYSTEM_ADMIN: '/role-home.html'
  };

  function storage() {
    return localStorage.getItem('safebox.token') ? localStorage : sessionStorage;
  }
  function getToken() { return storage().getItem('safebox.token'); }
  function getCurrentUser() {
    try { return JSON.parse(storage().getItem('safebox.user')); } catch (_) { return null; }
  }
  function clearAuth() {
    for (const store of [localStorage, sessionStorage]) {
      store.removeItem('safebox.token');
      store.removeItem('safebox.user');
    }
  }
  function saveAuth(token, user, remember) {
    clearAuth();
    const store = remember ? localStorage : sessionStorage;
    store.setItem('safebox.token', token);
    store.setItem('safebox.user', JSON.stringify(user));
  }
  function redirectForRole(role) {
    const destination = destinations[role];
    if (!destination) throw new Error('Vai trò tài khoản không được hỗ trợ.');
    location.replace(destination);
  }
  function deny() {
    document.body.style.visibility = 'visible';
    document.body.innerHTML = '<main style="font:16px sans-serif;padding:3rem"><h1>403 · Không có quyền truy cập</h1><p>Tài khoản của bạn không có quyền xem trang này.</p><a href="/">Về trang chủ</a></main>';
  }
  async function authFetch(url, options = {}) {
    const token = getToken();
    if (!token) { clearAuth(); location.replace(loginPath); throw new Error('Unauthorized'); }
    const headers = new Headers(options.headers || {});
    headers.set('Authorization', 'Bearer ' + token);
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401) { clearAuth(); location.replace(loginPath); throw new Error('Unauthorized'); }
    if (response.status === 403) { deny(); throw new Error('Forbidden'); }
    return response;
  }
  async function requireRole(...roles) {
    if (!getToken()) { clearAuth(); location.replace(loginPath); return; }
    try {
      const response = await authFetch('/api/auth/me');
      if (!response.ok) throw new Error('Không thể xác minh phiên đăng nhập.');
      const user = await response.json();
      storage().setItem('safebox.user', JSON.stringify(user));
      if (roles.length && !roles.includes(user.role)) { deny(); return; }
      document.body.style.visibility = 'visible';
    } catch (error) {
      if (error.message !== 'Forbidden' && error.message !== 'Unauthorized') {
        clearAuth();
        location.replace(loginPath);
      }
    }
  }
  async function logout() {
    const token = getToken();
    if (token) {
      try { await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + token } }); }
      catch (_) { /* Local sign-out still completes when offline. */ }
    }
    clearAuth();
    location.replace(loginPath);
  }
  window.SafeBoxAuth = { getToken, getCurrentUser, isAuthenticated: () => !!getToken(), authFetch,
    logout, requireAuth: () => requireRole(), requireRole, saveAuth, redirectForRole, clearAuth };
})();
