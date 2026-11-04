const {chromium} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (base !== 'http://127.0.0.1:18081' || !password) throw new Error('Explicit isolated test app URL and demo password required');
const output = path.resolve(__dirname, '../../../../docs/testing/evidence/management-phase15-results.json');
const result = {base, facilityCode:'PH15_ACCEPTANCE_20261028', regionCode:'PH15_TEST', events:[]};
const edge='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

async function login(browser,email) {
  const context=await browser.newContext({viewport:{width:1360,height:900}});
  const page=await context.newPage();
  page.setDefaultTimeout(10000);
  page.on('response',r=>{if(r.url().startsWith(base+'/api/')) result.events.push({actor:email,path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'),{timeout:10000});
  return {context,page};
}
async function responseFor(page,method,pathName,action) {
  const pending=page.waitForResponse(r=>new URL(r.url()).pathname===pathName && r.request().method()===method,{timeout:10000});
  await action();
  return pending;
}
async function main() {
  const browser=await chromium.launch({executablePath:edge,headless:true});
  let admin,business,manager;
  try {
    business=await login(browser,'business@safebox.vn');
    await business.page.goto(base+'/management-business.html',{waitUntil:'domcontentloaded'});
    await business.page.locator('#list').waitFor();
    const existing=await business.page.evaluate(async code=>{
      const r=await SafeBoxAuth.authFetch('/api/business/facilities');
      return (await r.json()).find(f=>f.facilityCode===code)||null;
    },result.facilityCode);
    assert.equal(existing,null,'Acceptance facility already exists; reuse needs a separate verification plan');
    await business.page.locator('#code').fill(result.facilityCode);
    await business.page.locator('#region').fill(result.regionCode);
    await business.page.locator('#name').fill('SafeBox Phase 1.5 Test');
    await business.page.locator('#address').fill('123 Duong Kiem Thu, Quan 7, TP HCM');
    await business.page.locator('#status').selectOption('ACTIVE');
    const created=await responseFor(business.page,'POST','/api/business/facilities',()=>business.page.locator('#form button').first().click());
    assert.equal(created.status(),200);
    result.facilityId=(await created.json()).facilityId;
    assert.ok(result.facilityId);
    await business.page.locator(`#list button[data-id="${result.facilityId}"]`).click();
    await business.page.locator('#address').fill('125 Duong Kiem Thu, Quan 7, TP HCM');
    const updated=await responseFor(business.page,'PUT',`/api/business/facilities/${result.facilityId}`,()=>business.page.locator('#form button').first().click());
    assert.equal(updated.status(),200);
    result.businessUpdateStatus=updated.status();

    manager=await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await manager.page.locator('#catalog-loading').waitFor({state:'hidden'});
    assert.equal(await manager.page.locator(`#catalog-facility option[value="${result.facilityId}"]`).count(),0);

    admin=await login(browser,'admin@safebox.vn');
    await admin.page.goto(base+'/management-admin.html',{waitUntil:'domcontentloaded'});
    const managerRow=admin.page.locator('#users tr').filter({hasText:'manager.q7@safebox.vn'});
    await managerRow.waitFor();
    result.managerId=await managerRow.locator('button[data-action="grant-facility"]').getAttribute('data-id');
    await managerRow.locator(`input[data-facility="${result.managerId}"]`).fill(result.facilityId);
    const granted=await responseFor(admin.page,'POST',`/api/admin/access/managers/${result.managerId}/facilities/${result.facilityId}`,()=>managerRow.locator('button[data-action="grant-facility"]').click());
    assert.equal(granted.status(),200);
    result.managerGrantStatus=granted.status();

    await manager.page.reload({waitUntil:'domcontentloaded'});
    await manager.page.locator(`#catalog-facility option[value="${result.facilityId}"]`).waitFor({state:'attached'});
    await manager.page.locator('#catalog-facility').selectOption(result.facilityId);
    await manager.page.locator('#catalog-loading').waitFor({state:'hidden'});
    await manager.page.locator('#catalog-create-unit').click();
    const type=manager.page.locator('#catalog-unit-type option').first();
    await type.waitFor({state:'attached'});
    result.typeId=Number(await type.getAttribute('value'));
    assert.ok(result.typeId>0);
    result.unitNumber='PH15-UNIT-01';
    await manager.page.locator('#catalog-unit-number').fill(result.unitNumber);
    await manager.page.locator('#catalog-unit-floor').fill('1');
    await manager.page.locator('#catalog-unit-zone').fill('PH15');
    await manager.page.locator('#catalog-unit-length').fill('2');
    await manager.page.locator('#catalog-unit-width').fill('3');
    await manager.page.locator('#catalog-unit-height').fill('4');
    await manager.page.locator('#catalog-unit-dimensions-verified').check();
    assert.match(await manager.page.locator('#catalog-unit-volume').textContent(),/24\.000 m³/);
    assert.equal(await manager.page.locator('#catalog-unit-status').inputValue(),'INACTIVE');
    const unitCreated=await responseFor(manager.page,'POST',`/api/manager/facilities/${result.facilityId}/units`,()=>manager.page.locator('#catalog-unit-save').click());
    assert.equal(unitCreated.status(),200);
    const unit=await unitCreated.json();
    result.unitId=unit.unitId;
    assert.equal(Number(unit.volumeM3),24);
    assert.equal(unit.status,'INACTIVE');
    assert.equal(unit.dimensionsVerified,true);

    await manager.page.locator(`.catalog-edit-unit[data-id="${result.unitId}"]`).click();
    await manager.page.locator('#catalog-unit-status').selectOption('AVAILABLE');
    await manager.page.locator('#catalog-unit-ready').check();
    const unavailable=await responseFor(manager.page,'PUT',`/api/manager/facilities/${result.facilityId}/units/${result.unitId}`,()=>manager.page.locator('#catalog-unit-save').click());
    assert.equal(unavailable.status(),400);
    result.unpricedAvailableStatus=unavailable.status();
    await manager.page.locator('#catalog-unit-status').selectOption('MAINTENANCE');
    const maintenance=await responseFor(manager.page,'PUT',`/api/manager/facilities/${result.facilityId}/units/${result.unitId}`,()=>manager.page.locator('#catalog-unit-save').click());
    assert.equal(maintenance.status(),200);
    result.maintenanceStatus=maintenance.status();

    result.pricingStatus=await manager.page.evaluate(async facilityId=>{
      const token=SafeBoxAuth.getToken();
      const r=await fetch(`/api/manager/facilities/${facilityId}/unit-types`,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({typeName:'Denied',storageMode:'STANDARD',sizeName:'Test',width:1,length:1,height:1,monthlyPrice:100000,status:'ACTIVE'})});
      return r.status;
    },result.facilityId);
    assert.equal(result.pricingStatus,403);

    const staffRow=admin.page.locator('#users tr').filter({hasText:'staff.q8@safebox.vn'});
    await staffRow.waitFor();
    result.staffId=await staffRow.locator('button[data-action="grant-region"]').getAttribute('data-id');
    await staffRow.locator(`input[data-region="${result.staffId}"]`).fill(result.regionCode);
    const regionGranted=await responseFor(admin.page,'POST',`/api/admin/access/staff/${result.staffId}/regions`,()=>staffRow.locator('button[data-action="grant-region"]').click());
    assert.equal(regionGranted.status(),200);
    result.regionGrantStatus=regionGranted.status();
    const refreshedStaff=admin.page.locator('#users tr').filter({hasText:'staff.q8@safebox.vn'});
    await refreshedStaff.locator(`input[data-region="${result.staffId}"]`).fill(result.regionCode);
    const regionRevoked=await responseFor(admin.page,'DELETE',`/api/admin/access/staff/${result.staffId}/regions/${result.regionCode}`,()=>refreshedStaff.locator('button[data-action="revoke-region"]').click());
    assert.equal(regionRevoked.status(),200);
    result.regionRevokeStatus=regionRevoked.status();

    const refreshedManager=admin.page.locator('#users tr').filter({hasText:'manager.q7@safebox.vn'});
    await refreshedManager.locator(`input[data-facility="${result.managerId}"]`).fill(result.facilityId);
    const revoked=await responseFor(admin.page,'DELETE',`/api/admin/access/managers/${result.managerId}/facilities/${result.facilityId}`,()=>refreshedManager.locator('button[data-action="revoke-facility"]').click());
    assert.equal(revoked.status(),200);
    result.managerRevokeStatus=revoked.status();
    result.afterRevokeStatus=await manager.page.evaluate(async facilityId=>{
      const token=SafeBoxAuth.getToken();
      const r=await fetch(`/api/manager/facilities/${facilityId}/units`,{headers:{Authorization:'Bearer '+token}});
      return r.status;
    },result.facilityId);
    assert.equal(result.afterRevokeStatus,403);
    result.passed=true;
  } finally {
    fs.mkdirSync(path.dirname(output),{recursive:true});
    fs.writeFileSync(output,JSON.stringify(result,null,2));
    for (const item of [manager,admin,business]) if(item) await item.context.close().catch(()=>{});
    await browser.close();
  }
  console.log(JSON.stringify({passed:result.passed,facilityId:result.facilityId,unitId:result.unitId,events:result.events.length}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
