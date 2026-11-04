const {chromium} = require('playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../../main/resources/static');
const output = path.resolve(__dirname, '../../../../docs/testing/evidence/phase46-ui-static');
const widths = process.env.SAFEBOX_UI_WIDTH ? [Number(process.env.SAFEBOX_UI_WIDTH)] : [390, 768, 1366, 1920];
const pages = {
  CUSTOMER: ['/stitch/trang_ch_safebox_storage.html', '/storage.html', '/stitch/uc11-customer-payments-appointments.html', '/stitch/gia_han_hop_dong_safebox_storage.html', '/stitch/qua_han_cong_no_khach_hang.html', '/stitch/dat_lich_tra_kho_safebox_storage.html', '/stitch/ho_tro_safebox.html'],
  FACILITY_STAFF: ['/staff-tasks.html', '/staff-measurements.html', '/stitch/ho_tro_khach_hang_safebox_storage.html'],
  FACILITY_MANAGER: ['/stitch/t_ng_quan_qu_n_l_safebox_manager.html', '/stitch/uc11-manager-catalog.html', '/staff-tasks.html', '/stitch/quan_ly_gia_han_safebox_manager.html', '/stitch/quan_ly_qua_han_safebox_manager.html', '/stitch/bien_ban_tra_kho_safebox_manager.html', '/management-pricing.html', '/management-policies.html'],
  BUSINESS_OPERATIONS_MANAGER: ['/role-home.html', '/management-business.html', '/management-pricing.html', '/reports.html'],
  SYSTEM_ADMIN: ['/role-home.html', '/management-admin.html']
};
const representative = {
  CUSTOMER: '/stitch/uc11-customer-payments-appointments.html',
  FACILITY_STAFF: '/staff-tasks.html',
  FACILITY_MANAGER: '/staff-tasks.html',
  BUSINESS_OPERATIONS_MANAGER: '/management-pricing.html',
  SYSTEM_ADMIN: '/management-admin.html'
};
const types = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.svg':'image/svg+xml', '.jpg':'image/jpeg', '.png':'image/png'};
const server = http.createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
  if (pathname === '/api/auth/me') {
    const role = req.headers.authorization?.slice(7) || 'CUSTOMER';
    res.writeHead(200, {'Content-Type':'application/json'});
    return res.end(JSON.stringify({userId:'00000000-0000-0000-0000-000000000001', role, fullName:'Tài khoản thử nghiệm'}));
  }
  if (pathname.startsWith('/api/')) {
    res.writeHead(200, {'Content-Type':'application/json'});
    return res.end('[]');
  }
  const file = path.resolve(root, '.' + pathname);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404); return res.end('Not found');
  }
  res.writeHead(200, {'Content-Type':types[path.extname(file)] || 'application/octet-stream'});
  fs.createReadStream(file).pipe(res);
});

(async () => {
  fs.mkdirSync(output, {recursive:true});
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless:true});
  const result = {kind:'static UI smoke with mocked empty API lists', pages:[], errors:[], brokenLocalRoutes:[]};
  try {
    for (const [role, paths] of Object.entries(pages)) {
      if (process.env.SAFEBOX_UI_ROLE && role !== process.env.SAFEBOX_UI_ROLE) continue;
      const context = await browser.newContext({viewport:{width:1366,height:900}});
      await context.addInitScript(roleName => {
        localStorage.setItem('safebox.token', roleName);
        localStorage.setItem('safebox.user', JSON.stringify({role:roleName, fullName:'Tài khoản thử nghiệm'}));
      }, role);
      const page = await context.newPage();
      page.on('pageerror', error => result.errors.push({role, path:new URL(page.url()).pathname, message:error.message}));
      for (const pathname of paths) {
        if (process.env.SAFEBOX_UI_PATH && pathname !== process.env.SAFEBOX_UI_PATH) continue;
        for (const width of widths) {
          await page.setViewportSize({width, height:900});
          const response = await page.goto(base + pathname, {waitUntil:'domcontentloaded', timeout:15000});
          await page.waitForTimeout(450);
          const metrics = await page.evaluate(() => ({
            visible:getComputedStyle(document.body).visibility !== 'hidden',
            overflow:document.documentElement.scrollWidth > innerWidth + 2,
            scrollWidth:document.documentElement.scrollWidth,
            offenders:[...document.querySelectorAll('body *')].filter(node => {
              const box = node.getBoundingClientRect();
              return box.width && box.right > innerWidth + 2 && getComputedStyle(node).position !== 'fixed';
            }).slice(0, 30).map(node => `${node.tagName.toLowerCase()}${node.id ? '#' + node.id : ''}.${String(node.className).split(' ').slice(0, 2).join('.')} (${Math.round(node.getBoundingClientRect().right)}px; parent=${node.parentElement?.tagName.toLowerCase()}.${String(node.parentElement?.className).split(' ').slice(0, 2).join('.')})`),
            title:document.title,
            hotline:[...document.querySelectorAll('[data-support-phone]')].every(node => node.textContent === '028 7300 5585'),
            queueHeading:document.querySelector('#pending-payment-section h2')?.textContent.trim() || null,
            pickupSlots:document.querySelectorAll('#appointment-slot option').length,
            demoQrInitiallyHidden:document.querySelector('#payment-bank-qr')?.hidden ?? null,
            links:[...document.querySelectorAll('a[href]')].map(node => node.getAttribute('href')).filter(href => href?.startsWith('/') && href.includes('.html'))
          }));
          for (const href of metrics.links) {
            const file = path.resolve(root, '.' + href.split(/[?#]/)[0]);
            if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) result.brokenLocalRoutes.push({role, pathname, href});
          }
          delete metrics.links;
          result.pages.push({role, pathname, width, status:response.status(), ...metrics});
          if (pathname === representative[role]) {
            await page.screenshot({path:path.join(output, `${role.toLowerCase()}-${width}.png`), fullPage:false});
          }
        }
      }
      await context.close();
    }
    const qr = await browser.newPage();
    result.qrAssetStatus = (await qr.goto(base + '/images/techcombank-demo-vietqr.jpg')).status();
    await qr.close();
  } finally {
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(result, null, 2));
    await browser.close();
    server.close();
  }
  console.log(JSON.stringify({pages:result.pages.length, overflow:result.pages.filter(p => p.overflow), hidden:result.pages.filter(p => !p.visible), hotlineFailures:result.pages.filter(p => !p.hotline), brokenRoutes:result.brokenLocalRoutes, qrAssetStatus:result.qrAssetStatus, errors:result.errors}, null, 2));
})().catch(error => { console.error(error.stack || error); server.close(); process.exitCode = 1; });
