const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function makeStore() {
  const data = new Map();
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value),
    removeItem: key => data.delete(key) };
}
function setup(fetch) {
  const localStorage = makeStore();
  const sessionStorage = makeStore();
  const location = { path: '', replace(value) { this.path = value; } };
  const body = { style: {}, innerHTML: '' };
  const context = { localStorage, sessionStorage, location, fetch,
    document: { body }, Headers, window: {} };
  vm.runInNewContext(fs.readFileSync(path.resolve('src/main/resources/static/auth.js'), 'utf8'), context);
  return { ...context, auth: context.window.SafeBoxAuth };
}

test('remember me chooses persistent storage; sign out clears both stores', async () => {
  const state = setup(async () => ({ status: 200 }));
  state.auth.saveAuth('token-one', { role: 'CUSTOMER' }, false);
  assert.equal(state.sessionStorage.getItem('safebox.token'), 'token-one');
  assert.equal(state.localStorage.getItem('safebox.token'), null);
  state.auth.saveAuth('token-two', { role: 'CUSTOMER' }, true);
  assert.equal(state.sessionStorage.getItem('safebox.token'), null);
  assert.equal(state.localStorage.getItem('safebox.token'), 'token-two');
  await state.auth.logout();
  assert.equal(state.localStorage.getItem('safebox.token'), null);
  assert.equal(state.localStorage.getItem('safebox.user'), null);
  assert.match(state.location.path, /ng_nh_p/);
});

test('protected fetch sends bearer and 401 clears auth', async () => {
  let header;
  const state = setup(async (_, options) => { header = options.headers.get('Authorization'); return { status: 401 }; });
  state.auth.saveAuth('signed', { role: 'CUSTOMER' }, true);
  await assert.rejects(state.auth.authFetch('/api/auth/me'), /Unauthorized/);
  assert.equal(header, 'Bearer signed');
  assert.equal(state.auth.getToken(), null);
  assert.match(state.location.path, /ng_nh_p/);
});

test('403 denies access while retaining token; reload checks server role', async () => {
  const state = setup(async () => ({ status: 200, ok: true,
    json: async () => ({ role: 'CUSTOMER', fullName: 'Customer' }) }));
  state.auth.saveAuth('signed', { role: 'FACILITY_MANAGER' }, false);
  await state.auth.requireRole('FACILITY_MANAGER');
  assert.match(state.document.body.innerHTML, /403/);
  assert.equal(state.auth.getToken(), 'signed');

  const denied = setup(async () => ({ status: 403 }));
  denied.auth.saveAuth('signed', { role: 'CUSTOMER' }, false);
  await assert.rejects(denied.auth.authFetch('/api/role/manager'), /Forbidden/);
  assert.equal(denied.auth.getToken(), 'signed');
});
