// Run against a local instance. Every business mutation below is intercepted in memory.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL || 'http://127.0.0.1:18084';
const env = Object.fromEntries(fs.readFileSync(path.resolve(__dirname, '../../../../.env.codex.local'), 'utf8')
  .split(/\r?\n/).filter(x => /^[A-Za-z_][A-Za-z0-9_]*=/.test(x)).map(x => {
    const i = x.indexOf('='); return [x.slice(0, i), x.slice(i + 1).replace(/^['"]|['"]$/g, '')];
  }));
const password = process.env.SAFEBOX_E2E_PASSWORD || env.SAFEBOX_DEMO_PASSWORD;
assert(password, 'Existing account password required');
const result = { readOnly: [], simulated: [], blockedWrites: [] };

(async () => {
  const browser = await chromium.launch({ headless: true });
  const errors = [];
  async function actor(email) {
    const context = await browser.newContext({ timezoneId: 'America/Los_Angeles', locale: 'en-US' });
    let loginAllowed = true;
    await context.route('**/*', route => {
      const r = route.request(), url = new URL(r.url());
      if (['GET', 'HEAD', 'OPTIONS'].includes(r.method())) return route.continue();
      if (loginAllowed && url.origin === base && url.pathname === '/api/auth/login' && r.method() === 'POST') {
        loginAllowed = false; return route.continue(); // AuthService.login only reads and signs a JWT.
      }
      result.blockedWrites.push(`${r.method()} ${url.pathname}`);
      return route.abort();
    });
    const page = await context.newPage();
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html');
    const status = await page.evaluate(async ({ email, password }) => {
      const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
      const data = await r.json();
      if (r.ok) SafeBoxAuth.saveAuth(data.token, data, false);
      return r.status;
    }, { email, password });
    assert.equal(status, 200, email);
    return { context, page };
  }
  try {
    const customer = await actor('customer3@safebox.vn');
    await customer.page.goto(base + '/storage.html');
    await customer.page.locator('.reserve').first().waitFor();
    const inventory = await customer.page.evaluate(async () => (await fetch('/api/discovery/units')).json());
    assert(inventory.some(u => u.bookable && u.volumeM3 === null));
    assert(inventory.filter(u => ['OCCUPIED', 'RESERVED', 'MAINTENANCE', 'INACTIVE'].includes(u.status)).every(u => !u.bookable));
    result.readOnly.push('Existing unmeasured inventory selectable; occupied/reserved/maintenance inventory blocked');
    let submitted;
    await customer.page.route('**/api/reservations', async route => {
      assert.equal(route.request().method(), 'POST');
      submitted = route.request().postDataJSON();
      await route.fulfill({ json: { reservationId: '00000000-0000-4000-8000-000000000001' } });
    });
    await customer.page.locator('.reserve').first().click();
    assert.match(await customer.page.locator('#reservation-summary').innerText(), /\d{2}\/\d{2}\/\d{4}/);
    await customer.page.locator('#reservation-form button[type=submit]').click();
    await customer.page.getByText('Đã gửi yêu cầu đặt kho.', { exact: false }).waitFor();
    assert.match(submitted.startDate, /^\d{4}-\d{2}-\d{2}$/);
    assert.equal(submitted.months, 1);
    result.simulated.push('Customer submission uses ISO date and selected facility/type; POST mocked');
    await customer.context.close();

    const manager = await actor('manager.q7@safebox.vn');
    await manager.page.goto(base + '/reports.html');
    await manager.page.locator('#results:visible').waitFor();
    assert.match(await manager.page.locator('#report-state').innerText(), /\d{2}\/\d{2}\/\d{4}/);
    await manager.page.locator('#start-date').fill('01/10/2026');
    await manager.page.locator('#end-date').fill('31/10/2026');
    const financial = manager.page.waitForResponse(r => new URL(r.url()).pathname === '/api/reports/financial-summary');
    await manager.page.getByRole('button', { name: 'Xem báo cáo' }).click();
    const response = await financial;
    assert.equal(response.status(), 200);
    assert.equal(new URL(response.url()).searchParams.get('startDate'), '2026-10-01');
    await manager.page.locator('#results:visible').waitFor();
    result.readOnly.push('Scoped manager reports render existing data; dd/MM/yyyy filters send ISO');
    await manager.context.close();

    const business = await actor('business@safebox.vn');
    await business.page.goto(base + '/reports.html');
    await business.page.getByText(/Quản trị viên cần mở Quản trị truy cập/).waitFor();
    result.readOnly.push('Business account denied with actionable missing-scope message');
    // Simulate a grant, without modifying assignments or querying outside the real account scope.
    const fid = '414e5607-b0b4-f111-a965-932c2c6addb7';
    await business.page.route('**/api/reports/**', async route => {
      const pathname = new URL(route.request().url()).pathname;
      const data = pathname.endsWith('/facilities') ? [fid] : pathname.endsWith('/revenue-trend') ? { data: [{ periodStart: '2026-10-01', rentalCollections: 500 }] }
        : pathname.endsWith('/facility-performance') ? [{ facilityId: fid, totalUnits: 2, occupiedUnits: 1, availableUnits: 1, maintenanceUnits: 0, occupancyRate: 50, confirmedCollections: 500, outstandingReceivables: 300 }]
        : pathname.endsWith('/financial-summary') ? { startDate: '2026-10-01', endDate: '2026-10-31', rentalCollections: 500, outstandingReceivables: 300 }
        : { activeContracts: 1, expiringWithin30Days: 0, overdueActiveContracts: 0 };
      await route.fulfill({ json: data });
    });
    await business.page.reload();
    await business.page.locator('#results:visible').waitFor();
    assert.match(await business.page.locator('#revenue-chart').innerText(), /01\/10\/2026/);
    result.simulated.push('Authorized Business Manager report rendering with mocked scope and report responses');
    await business.context.close();

    const admin = await actor('admin@safebox.vn');
    await admin.page.goto(base + '/management-admin.html');
    const row = admin.page.locator('tr').filter({ hasText: 'business@safebox.vn' });
    await row.locator('[data-facility]').fill(fid);
    const requests = [];
    await admin.page.route('**/api/admin/report-scopes/**', async route => {
      const r = route.request(); requests.push({ method: r.method(), path: new URL(r.url()).pathname, body: r.postDataJSON() });
      await route.fulfill({ json: [] });
    });
    await row.getByRole('button', { name: 'Cấp cơ sở', exact: true }).click();
    await admin.page.getByText('Đã cập nhật quyền truy cập.', { exact: true }).waitFor();
    await row.locator('[data-facility]').fill(fid);
    const revoked = admin.page.waitForResponse(r => r.request().method() === 'DELETE' && new URL(r.url()).pathname.startsWith('/api/admin/report-scopes/'));
    await row.locator('[data-action=revoke-facility]').click();
    await revoked;
    assert.equal(requests.length, 2);
    assert.equal(requests[0].method, 'POST'); assert.deepEqual(requests[0].body, { facilityId: fid });
    assert.equal(requests[1].method, 'DELETE'); assert(requests[1].path.endsWith('/' + fid));
    result.simulated.push('Admin Business Manager grant and revoke route to report-scopes API; writes mocked');
    await admin.context.close();
    assert.deepEqual(errors, []); assert.deepEqual(result.blockedWrites, []);
    console.log(JSON.stringify(result, null, 2));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
