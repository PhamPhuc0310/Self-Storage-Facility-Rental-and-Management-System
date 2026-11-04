const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (base !== 'http://127.0.0.1:18081' || !password) throw new Error('Isolated test URL and password required');
const facilityId = '3f4e5607-b0b4-f111-a965-932c2c6addb7';
const typeId = '2';
const evidence = path.resolve(__dirname, '../../../../docs/testing/evidence/phase34-closure');
fs.mkdirSync(evidence, {recursive:true});
const result = {database:'SelfStorageFacilityDB_Test',events:[]};
function save() { fs.writeFileSync(path.join(evidence,'journey.json'),JSON.stringify(result,null,2)); }
async function login(browser,email) {
  const context = await browser.newContext({viewport:{width:1366,height:900}});
  const page = await context.newPage();page.setDefaultTimeout(12000);
  page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({actor:email,method:r.request().method(),path:new URL(r.url()).pathname,status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(u=>!u.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'),{timeout:15000});
  return {context,page};
}
async function main() {
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  let customer,manager;
  try {
    customer=await login(browser,'customer3@safebox.vn');
    await customer.page.goto(base+`/storage.html?facilityId=${facilityId}&typeId=${typeId}`);
    const card=customer.page.locator('#results article').filter({has:customer.page.locator('button.reserve')}).first();
    await card.waitFor();
    assert.match(await card.innerText(),/Còn trống/);
    result.discovery={facilityId,typeId,unit:await card.locator('h2').innerText(),price:await card.locator('.sb-price').innerText()};
    await customer.page.screenshot({path:path.join(evidence,'01-customer-discovery.png'),fullPage:false});save();
    await card.locator('button.reserve').click();
    const response=customer.page.waitForResponse(r=>new URL(r.url()).pathname==='/api/reservations'&&r.request().method()==='POST',{timeout:12000});
    await customer.page.locator('#reservation-form button[type="submit"]').click();
    const created=await response;result.reservationPost=created.status();
    assert.equal(created.status(),201);result.reservation=await created.json();result.reservationId=result.reservation.reservationId;save();
    await customer.page.screenshot({path:path.join(evidence,'02-customer-reserved.png'),fullPage:false});

    manager=await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base+'/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html');
    const approve=manager.page.locator(`.approval-confirm[data-id="${result.reservationId}"]`);
    await approve.waitFor({timeout:15000});
    manager.page.once('dialog',dialog=>dialog.accept());
    const approved=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/reservations/${result.reservationId}/confirm`&&r.request().method()==='POST',{timeout:12000});
    await approve.click();const confirmed=await approved;result.approvalPost=confirmed.status();
    assert.equal(confirmed.status(),200);result.contract=await confirmed.json();result.contractId=result.contract.contractId;save();
    await manager.page.screenshot({path:path.join(evidence,'03-manager-approved.png'),fullPage:false});

    await customer.page.goto(base+'/stitch/y_u_c_u_c_a_t_i_safebox_storage.html?created='+result.reservationId);
    await customer.page.locator('#reservations-list').getByText('Xem hợp đồng').first().waitFor({timeout:12000});
    await customer.page.screenshot({path:path.join(evidence,'04-customer-contract-visible.png'),fullPage:false});
    result.customerContractVisible=true;save();
  } finally {
    save();
    for(const item of [customer,manager]) if(item) await item.context.close().catch(()=>{});
    await browser.close();
  }
  console.log(JSON.stringify({reservationId:result.reservationId,contractId:result.contractId,post:result.reservationPost,approval:result.approvalPost}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
