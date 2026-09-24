(function () {
  const paths = {
    home: '/safebox_storage_home/code.html',
    login: '/safebox_storage_login/code.html'
  };

  const roleDestinations = Object.freeze({
    CUSTOMER: '/safebox_storage_my_storage_dashboard/code.html',
    FACILITY_STAFF: '/safebox_storage_reservation_requests_management/code.html',
    FACILITY_MANAGER: '/safebox_storage_facility_storage_unit_management/code.html'
  });

  const roleLabels = Object.freeze({
    CUSTOMER: 'Khách hàng',
    FACILITY_STAFF: 'Nhân viên cơ sở',
    FACILITY_MANAGER: 'Quản lý cơ sở',
    BUSINESS_OPERATIONS_MANAGER: 'Quản lý vận hành',
    SYSTEM_ADMIN: 'Quản trị viên hệ thống',
    SYSTEM_ADMINISTRATOR: 'Quản trị viên hệ thống'
  });

  const authErrorLabels = Object.freeze({
    'Email is required': 'Vui lòng nhập email.',
    'Email must be valid': 'Email không đúng định dạng.',
    'Enter a valid email address': 'Email không đúng định dạng.',
    'Email must not exceed 255 characters': 'Email không được vượt quá 255 ký tự.',
    'Password is required': 'Vui lòng nhập mật khẩu.',
    'Password must be between 8 and 72 characters': 'Mật khẩu phải có từ 8 đến 72 ký tự.',
    'Please confirm your password': 'Vui lòng xác nhận mật khẩu.',
    'Password and confirmation do not match.': 'Mật khẩu xác nhận không khớp.',
    'Full name is required': 'Vui lòng nhập họ và tên.',
    'Full name must be at least 2 characters': 'Họ và tên phải có từ 2 ký tự trở lên.',
    'Full name must be between 2 and 100 characters': 'Họ và tên phải có từ 2 đến 100 ký tự.',
    'Phone number is required': 'Vui lòng nhập số điện thoại.',
    'Phone number must be between 7 and 20 characters': 'Số điện thoại phải có từ 7 đến 20 ký tự.',
    'Enter a valid phone number': 'Số điện thoại không hợp lệ.',
    'An account with this email already exists.': 'Email này đã được sử dụng.',
    'Registration is temporarily unavailable. Please try again later.': 'Tạm thời chưa thể đăng ký. Vui lòng thử lại sau.',
    'Invalid email or password': 'Email hoặc mật khẩu không đúng.',
    'User account is not active': 'Tài khoản chưa hoạt động. Vui lòng liên hệ hỗ trợ.',
    'Authentication required': 'Vui lòng đăng nhập để tiếp tục.',
    'Access denied': 'Bạn không có quyền truy cập.',
    'Session expired': 'Phiên đăng nhập đã hết hạn.',
    'Please sign in again': 'Vui lòng đăng nhập lại.'
  });

  function localizedError(message, fallback) {
    return Object.hasOwn(authErrorLabels, message) ? authErrorLabels[message] : fallback;
  }

  function clearStorage(storage) {
    storage.removeItem('accessToken');
    storage.removeItem('currentUser');
  }

  function clearAuthentication() {
    clearStorage(localStorage);
    clearStorage(sessionStorage);
  }

  function readStorage(storage) {
    const accessToken = storage.getItem('accessToken');
    const serializedUser = storage.getItem('currentUser');

    if (!accessToken && !serializedUser) return null;
    if (!accessToken || !serializedUser) {
      clearStorage(storage);
      return null;
    }

    try {
      const currentUser = JSON.parse(serializedUser);
      if (!currentUser || !currentUser.userId || !currentUser.email || !currentUser.fullName || !currentUser.role) {
        clearStorage(storage);
        return null;
      }
      return { accessToken, currentUser, storage };
    } catch (error) {
      clearStorage(storage);
      return null;
    }
  }

  function getAuthentication() {
    return readStorage(localStorage) || readStorage(sessionStorage);
  }

  function saveAuthentication(accessToken, currentUser, remember) {
    clearAuthentication();
    const storage = remember ? localStorage : sessionStorage;
    storage.setItem('accessToken', accessToken);
    storage.setItem('currentUser', JSON.stringify(currentUser));
  }

  function dashboardForRole(role) {
    return roleDestinations[role] || null;
  }

  function redirectToLogin() {
    window.location.replace(paths.login);
  }

  function setText(selector, value) {
    document.querySelectorAll(selector).forEach((element) => {
      element.textContent = value;
    });
  }

  function renderAuthenticatedUser(user) {
    setText('[data-auth-user="fullName"]', user.fullName);
    setText('[data-auth-user="email"]', user.email);
    setText('[data-auth-user="role"]', Object.hasOwn(roleLabels, user.role) ? roleLabels[user.role] : user.role.replaceAll('_', ' '));

    const dashboard = dashboardForRole(user.role);
    document.querySelectorAll('[data-auth-dashboard]').forEach((element) => {
      if (dashboard) {
        element.href = dashboard;
        element.classList.remove('hidden');
      } else {
        element.removeAttribute('href');
        element.classList.add('hidden');
      }
    });
    document.querySelectorAll('[data-auth-no-dashboard]').forEach((element) => {
      element.classList.toggle('hidden', Boolean(dashboard));
    });
  }

  function renderPublicState(user) {
    document.querySelectorAll('[data-auth-logged-out]').forEach((element) => {
      element.classList.toggle('hidden', Boolean(user));
    });
    document.querySelectorAll('[data-auth-logged-in]').forEach((element) => {
      element.classList.toggle('hidden', !user);
    });
    if (user) renderAuthenticatedUser(user);
  }

  async function requestSession(authentication) {
    const response = await fetch('/api/auth/session', {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer ' + authentication.accessToken
      }
    });

    if (response.status === 401) {
      clearAuthentication();
      return null;
    }
    if (!response.ok) {
      throw new Error('Không thể xác minh phiên đăng nhập.');
    }

    const user = await response.json();
    if (!user || !user.userId || !user.email || !user.fullName || !user.role) {
      clearAuthentication();
      return null;
    }

    authentication.storage.setItem('currentUser', JSON.stringify(user));
    return user;
  }

  async function verifySession(options = {}) {
    const redirectOnFailure = options.redirectOnFailure !== false;
    const authentication = getAuthentication();

    if (!authentication) {
      if (redirectOnFailure) redirectToLogin();
      return null;
    }

    try {
      const user = await requestSession(authentication);
      if (!user && redirectOnFailure) redirectToLogin();
      if (user) renderAuthenticatedUser(user);
      return user;
    } catch (error) {
      clearAuthentication();
      if (redirectOnFailure) redirectToLogin();
      return null;
    }
  }

  window.authFetch = async function (url, options = {}) {
    const authentication = getAuthentication();
    if (!authentication) {
      clearAuthentication();
      redirectToLogin();
      throw new Error('Vui lòng đăng nhập để tiếp tục.');
    }

    const headers = new Headers(options.headers || {});
    headers.set('Authorization', 'Bearer ' + authentication.accessToken);
    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
      clearAuthentication();
      redirectToLogin();
    }
    return response;
  };

  function bindLogout() {
    const logoutControls = Array.from(document.querySelectorAll('[data-auth-action="logout"]'));
    if (!logoutControls.length) {
      const fallback = Array.from(document.querySelectorAll('a, button')).find((element) => {
        const icon = element.querySelector('.material-symbols-outlined');
        return icon && icon.textContent.trim() === 'logout';
      });
      if (fallback) logoutControls.push(fallback);
    }

    logoutControls.forEach((control) => {
      control.addEventListener('click', async function (event) {
        event.preventDefault();
        try {
          const authentication = getAuthentication();
          if (authentication) {
            await fetch('/api/auth/logout', {
              method: 'POST',
              headers: { Authorization: 'Bearer ' + authentication.accessToken }
            });
          }
        } finally {
          clearAuthentication();
          window.location.replace(paths.home);
        }
      });
    });
  }

  async function initializePage() {
    const pageMode = document.body.dataset.authPage || 'protected';

    if (pageMode === 'public') {
      const authentication = getAuthentication();
      if (!authentication) {
        renderPublicState(null);
        return;
      }
      renderPublicState(await verifySession({ redirectOnFailure: false }));
      return;
    }

    if (pageMode === 'login' || pageMode === 'register') {
      const authentication = getAuthentication();
      if (!authentication) return;
      const user = await verifySession({ redirectOnFailure: false });
      const destination = user ? dashboardForRole(user.role) : null;
      if (user) window.location.replace(destination || paths.home);
      return;
    }

    const user = await verifySession({ redirectOnFailure: true });
    if (!user) return;

    const requiredRoles = (document.body.dataset.requiredRoles || '')
      .split(',')
      .map((role) => role.trim())
      .filter(Boolean);
    if (requiredRoles.length && !requiredRoles.includes(user.role)) {
      const destination = dashboardForRole(user.role);
      window.location.replace(destination || paths.home);
    }
  }

  window.SafeBoxAuth = Object.freeze({
    clearAuthentication,
    dashboardForRole,
    getAuthentication,
    localizedError,
    saveAuthentication,
    verifySession
  });

  bindLogout();
  void initializePage();

  window.addEventListener('pageshow', function (event) {
    if (event.persisted) void initializePage();
  });
})();
