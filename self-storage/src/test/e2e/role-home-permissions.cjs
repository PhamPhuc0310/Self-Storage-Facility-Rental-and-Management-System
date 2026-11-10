const { chromium } = require('playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '../../main/resources/static');
const expected = {
  CUSTOMER: ['/storage.html', '/stitch/y_u_c_u_c_a_t_i_safebox_storage.html',
    '/stitch/kho_dang_thue_safebox_storage.html', '/stitch/uc11-customer-payments-appointments.html',
    '/stitch/gia_han_hop_dong_safebox_storage.html', '/stitch/early-return.html', '/stitch/ho_tro_safebox.html'],
  FACILITY_STAFF: ['/staff-tasks.html', '/staff-measurements.html',
    '/stitch/ho_tro_safebox.html'],
  FACILITY_MANAGER: ['/stitch/t_ng_quan_qu_n_l_safebox_manager.html',
    '/stitch/uc11-manager-catalog.html', '/stitch/uc11-manager-approvals.html',
    '/stitch/uc12-manager-operations.html', '/stitch/early-return.html', '/management-pricing.html',
    '/management-policies.html', '/reports.html'],
  BUSINESS_OPERATIONS_MANAGER: ['/management-business.html', '/management-pricing.html',
    '/management-policies.html', '/stitch/business-reports.html'],
  SYSTEM_ADMIN: ['/management-admin.html', '/stitch/management-audit.html']
};
const types = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.svg':'image/svg+xml'};
const server = http.createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
  if (pathname === '/api/auth/me') {
    res.writeHead(200, {'Content-Type':'application/json'});
    return res.end(JSON.stringify({role:req.headers.authorization?.slice(7), fullName:'Tài khoản thử nghiệm'}));
  }
  const file = path.resolve(root, '.' + pathname);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404); return res.end('Not found');
  }
  res.writeHead(200, {'Content-Type':types[path.extname(file)] || 'application/octet-stream'});
  fs.createReadStream(file).pipe(res);
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({headless:true});
  const results = [];
  try {
    for (const [role, links] of Object.entries(expected)) {
      const context = await browser.newContext();
      await context.addInitScript(name => {
        sessionStorage.setItem('safebox.token', name);
        sessionStorage.setItem('safebox.user', JSON.stringify({role:name, fullName:'Tài khoản thử nghiệm'}));
      }, role);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(base + '/role-home.html', {waitUntil:'networkidle'});
      const actual = await page.locator('main.sb-container .sb-cards a').evaluateAll(nodes => nodes.map(a => a.getAttribute('href')));
      assert.deepEqual(actual, links, `${role} landing links`);
      assert.equal(errors.length, 0, `${role} JavaScript errors: ${errors.join(', ')}`);
      results.push({role, links:actual.length, errors:0});
      await context.close();
    }
    console.log(JSON.stringify({result:'PASS', roles:results}, null, 2));
  } finally {
    await browser.close();
    server.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
