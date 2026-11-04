const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw new Error('SAFEBOX_E2E_PASSWORD is required');
const facility='414e5607-b0b4-f111-a965-932c2c6addb7';
const unitNumber=`NVQ-E2E-${Date.now()}`;
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const result={facility,unitNumber,events:[]};
async function login(browser,email){
  const context=await browser.newContext({viewport:{width:1440,height:900}});
  const page=await context.newPage();
  page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({actor:email,path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
  await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
  return {context,page};
}
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const other=await login(browser,'manager.thuduc@safebox.vn');
    await other.page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await other.page.locator('#catalog-loading').waitFor({state:'hidden'});
    assert.equal(await other.page.locator('#catalog-facility option').count(),0);
    await other.page.screenshot({path:path.join(evidence,'unit-unassigned-manager.png'),fullPage:true});
    await other.context.close();

    const manager=await login(browser,'manager.q7@safebox.vn');
    await manager.page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await manager.page.locator(`#catalog-facility option[value="${facility}"]`).waitFor({state:'attached'});
    await manager.page.locator('#catalog-facility').selectOption(facility);
    await manager.page.locator('#catalog-loading').waitFor({state:'hidden'});
    await manager.page.locator('#catalog-create-unit').click();
    await manager.page.locator('#catalog-unit-dialog').waitFor({state:'visible'});
    result.typeId=Number(await manager.page.locator('#catalog-unit-type option').first().getAttribute('value'));
    assert.ok(result.typeId>0);
    await manager.page.locator('#catalog-unit-number').fill(unitNumber);
    await manager.page.locator('#catalog-unit-floor').fill('1');
    await manager.page.locator('#catalog-unit-zone').fill('E2E');
    const create=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}/units`&&r.request().method()==='POST');
    await manager.page.locator('#catalog-unit-save').click();
    const created=await create;result.createStatus=created.status();assert.equal(result.createStatus,200);
    result.unitId=(await created.json()).unitId;
    await manager.page.locator('#catalog-units').getByText(unitNumber).waitFor();
    await manager.page.screenshot({path:path.join(evidence,'unit-manager-created.png'),fullPage:true});

    await manager.page.locator('#catalog-create-unit').click();
    await manager.page.locator('#catalog-unit-number').fill(unitNumber);
    await manager.page.locator('#catalog-unit-floor').fill('1');
    await manager.page.locator('#catalog-unit-zone').fill('E2E');
    const duplicate=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}/units`&&r.request().method()==='POST');
    await manager.page.locator('#catalog-unit-save').click();
    result.duplicateStatus=(await duplicate).status();assert.equal(result.duplicateStatus,409);
    await manager.page.locator('#catalog-unit-error').waitFor({state:'visible'});
    await manager.page.screenshot({path:path.join(evidence,'unit-duplicate-rejected.png'),fullPage:true});
    await manager.page.locator('#catalog-unit-cancel').click();

    const edit=manager.page.locator(`.catalog-edit-unit[data-id="${result.unitId}"]`);
    await edit.click();
    await manager.page.locator('#catalog-unit-status').selectOption('MAINTENANCE');
    const maintain=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}/units/${result.unitId}`&&r.request().method()==='PUT');
    await manager.page.locator('#catalog-unit-save').click();
    result.maintenanceStatus=(await maintain).status();assert.equal(result.maintenanceStatus,200);
    await manager.page.screenshot({path:path.join(evidence,'unit-manager-maintenance.png'),fullPage:true});
    await manager.page.locator(`.catalog-edit-unit[data-id="${result.unitId}"]`).click();
    await manager.page.locator('#catalog-unit-status').selectOption('AVAILABLE');
    const available=manager.page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}/units/${result.unitId}`&&r.request().method()==='PUT');
    await manager.page.locator('#catalog-unit-save').click();
    result.availableStatus=(await available).status();assert.equal(result.availableStatus,200);
    await manager.page.screenshot({path:path.join(evidence,'unit-manager-available.png'),fullPage:true});
    await manager.context.close();
  }finally{fs.writeFileSync(path.join(evidence,'unit-management-results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify(result));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
