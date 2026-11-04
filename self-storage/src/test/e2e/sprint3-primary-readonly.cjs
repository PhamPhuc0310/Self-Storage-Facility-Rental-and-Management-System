const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const base = 'http://127.0.0.1:18082';
const pid = Number(process.env.SAFEBOX_PRIMARY_APP_PID);
const password = process.env.SAFEBOX_E2E_PASSWORD;
assert(pid && password, 'Primary preflight PID and local password are required');
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/sprint3-primary-20261102');
fs.mkdirSync(evidence, { recursive: true });
function identity() {
  const query = `SET NOCOUNT ON; IF DB_NAME()<>'SelfStorageFacilityDB' OR DB_ID()<>5 THROW 52010,'Wrong database',1;
    IF NOT EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id=DB_ID()) THROW 52011,'Missing primary app session',1;
    IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE host_process_id=${pid} AND database_id<>DB_ID()) THROW 52012,'App connected elsewhere',1; SELECT DB_NAME();`;
  assert(execFileSync('sqlcmd', ['-S','tcp:127.0.0.1,1433','-E','-C','-d','SelfStorageFacilityDB','-b','-W','-h','-1','-Q',query], { encoding: 'utf8' }).includes('SelfStorageFacilityDB'));
}
const routes = [
  ['customer','customer3@safebox.vn',[
    '/stitch/kho_dang_thue_safebox_storage.html',
    '/stitch/gia_han_hop_dong_safebox_storage.html?contractId=00000000-0000-4000-8000-00000000D401',
    '/stitch/qua_han_cong_no_khach_hang.html','/stitch/early-return.html','/stitch/ho_tro_safebox.html','/storage.html']],
  ['manager','manager.q7@safebox.vn',[
    '/stitch/quan_ly_gia_han_safebox_manager.html','/stitch/quan_ly_qua_han_safebox_manager.html',
    '/stitch/bien_ban_tra_kho_safebox_manager.html','/stitch/early-return.html','/stitch/ho_tro_safebox.html','/management-policies.html','/reports.html']],
  ['business','business@safebox.vn',['/management-policies.html','/reports.html']],
  ['staff','staff.q7@safebox.vn',['/staff-tasks.html','/stitch/bien_ban_tra_kho_safebox_manager.html','/stitch/ho_tro_safebox.html']],
  ['admin','admin@safebox.vn',['/stitch/quan_ly_pham_vi_nhan_vien.html','/management-admin.html']]
];
(async () => {
  identity();
  const browser = await chromium.launch({ headless: true });
  const result = { database: 'SelfStorageFacilityDB', appPid: pid, generatedAt: new Date().toISOString(), roles: {}, blockedWrites: [] };
  const save = () => fs.writeFileSync(path.join(evidence, 'browser-preflight.json'), JSON.stringify(result, null, 2));
  try {
    for (const [role,email,pages] of routes) {
      identity();
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      let loginAllowed = true;
      await context.route('**/*', async route => {
        const request = route.request();
        const url = new URL(request.url());
        if (['GET','HEAD','OPTIONS'].includes(request.method())) return route.continue();
        // AuthService.login only reads the user and signs an in-memory JWT.
        if (loginAllowed && request.method() === 'POST' && url.origin === base && url.pathname === '/api/auth/login') {
          loginAllowed = false;
          return route.continue();
        }
        result.blockedWrites.push({ role, method: request.method(), path: url.pathname });
        return route.abort('blockedbyclient');
      });
      const page = await context.newPage();
      page.setDefaultTimeout(12000);
      const errors = [], api = [];
      page.on('pageerror', error => errors.push(error.message));
      page.on('response', response => {
        const url = new URL(response.url());
        if (url.origin === base && url.pathname.startsWith('/api/')) api.push({ method: response.request().method(), path: url.pathname, status: response.status() });
      });
      const record = result.roles[role] = { pages: [], api, errors };
      try {
        await page.goto(base + '/stitch/ng_nh_p_ng_k_safebox_storage.html', { waitUntil: 'domcontentloaded' });
        await page.getByRole('textbox', { name: 'Email' }).fill(email);
        await page.getByRole('textbox', { name: 'Mật khẩu' }).fill(password);
        const loginResponse = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
        await page.getByRole('button', { name: /Đăng nhập/ }).click();
        record.login = (await loginResponse).status();
        assert.equal(record.login, 200, `${role} login`);
        await page.waitForURL(url => !url.pathname.includes('ng_nh_p_ng_k'));
        for (let index=0; index<pages.length; index++) {
          const response = await page.goto(base + pages[index], { waitUntil: 'domcontentloaded' });
          await page.waitForTimeout(1100);
          assert.equal(result.blockedWrites.length, 0, 'Opening a page attempted a write; stop preflight');
          const text = await page.locator('body').innerText();
          record.pages.push({ path: pages[index], status: response.status(), actualPath: new URL(page.url()).pathname,
            headings: await page.locator('h1,h2').allTextContents(),
            navigation: await page.locator('header nav a:visible,aside nav a:visible').allTextContents(),
            volumeLabels: text.split('\n').filter(line => /m³|chưa xác minh/i.test(line)).slice(0,15),
            demoContracts: ['DEMO-RENEW-01','DEMO-OVERDUE-01','DEMO-RETURN-01'].filter(code => text.includes(code)) });
          await page.screenshot({ path: path.join(evidence, `${role}-${index+1}.png`), fullPage: true });
          save();
        }
        if (role === 'customer') {
          record.preview = await page.evaluate(async () => {
            const response = await SafeBoxAuth.authFetch('/api/customer/contracts/00000000-0000-4000-8000-00000000D401/renewals/preview?months=1');
            const data = await response.json();
            return { status: response.status, canRenew: data.canRenew, warning: data.warningMessage, total: data.totalAmount };
          });
        }
        record.unexpectedResponses = api.filter(item => item.status >= 400);
        console.log(JSON.stringify({ role, login: record.login, pages: record.pages.length, errors, unexpectedResponses: record.unexpectedResponses, preview: record.preview }));
      } finally { save(); await context.close(); }
    }
    identity();
    assert.equal(result.blockedWrites.length, 0);
  } finally { save(); await browser.close(); }
})().catch(error => { console.error(error.stack); process.exitCode=1; });
