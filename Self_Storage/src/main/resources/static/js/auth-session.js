(function () {
  const paths = {
    home: '/safebox_storage_home/code.html',
    login: '/safebox_storage_login/code.html'
  };

  const roleDestinations = Object.freeze({
    CUSTOMER: '/safebox_storage_my_storage_dashboard/code.html',
    FACILITY_STAFF: '/safebox_storage_reservation_requests_management/code.html',
    FACILITY_MANAGER: '/safebox_storage_facility_storage_unit_management/code.html',
    BUSINESS_OPERATIONS_MANAGER: '/safebox_storage_facility_storage_unit_management/code.html',
    SYSTEM_ADMIN: '/safebox_storage_facility_storage_unit_management/code.html'
  });

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
    setText('[data-auth-user="role"]', user.role.replaceAll('_', ' '));

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
      throw new Error('Unable to validate authentication');
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
      throw new Error('Authentication is required');
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

    if (pageMode === 'login') {
      const authentication = getAuthentication();
      if (!authentication) return;
      const user = await verifySession({ redirectOnFailure: false });
      const destination = user ? dashboardForRole(user.role) : null;
      if (destination) window.location.replace(destination);
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
    saveAuthentication,
    verifySession
  });

  bindLogout();
  void initializePage();

  window.addEventListener('pageshow', function (event) {
    if (event.persisted) void initializePage();
  });
})();
