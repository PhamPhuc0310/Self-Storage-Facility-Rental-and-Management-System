const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.SAFEBOX_E2E_BASE_URL||'http://localhost:18080';
const password=process.env.SAFEBOX_E2E_PASSWORD;
if(!password)throw new Error('SAFEBOX_E2E_PASSWORD is required');
const facility='414e5607-b0b4-f111-a965-932c2c6addb7';
const evidence=path.resolve(__dirname,'../../../../docs/testing/evidence');
const result={facility,events:[]};
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    const context=await browser.newContext({viewport:{width:1440,height:900}});
    const page=await context.newPage();
    page.on('response',r=>{if(r.url().startsWith(base+'/api/'))result.events.push({path:new URL(r.url()).pathname,method:r.request().method(),status:r.status()});});
    await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html',{waitUntil:'domcontentloaded'});
    await page.locator('#login-email').fill('manager.q7@safebox.vn');await page.locator('#login-password').fill(password);await page.locator('#btn-login-submit').click();
    await page.waitForURL(url=>!url.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'));
    await page.goto(base+'/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html',{waitUntil:'domcontentloaded'});
    await page.locator(`#catalog-facility option[value="${facility}"]`).waitFor({state:'attached'});
    await page.locator('#catalog-facility').selectOption(facility);
    await page.locator('#catalog-edit-facility').click();
    result.originalName=await page.locator('#catalog-facility-name').inputValue();
    result.originalAddress=await page.locator('#catalog-facility-address').inputValue();
    result.testAddress=result.originalAddress+' [E2E verification]';
    await page.locator('#catalog-facility-address').fill(result.testAddress);
    const change=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}`&&r.request().method()==='PUT');
    await page.locator('#catalog-facility-save').click();
    const response=await change;result.changeStatus=response.status();assert.equal(result.changeStatus,200);
    assert.equal((await response.json()).address,result.testAddress);
    await page.reload({waitUntil:'domcontentloaded'});
    await page.locator(`#catalog-facility option[value="${facility}"]`).waitFor({state:'attached'});
    await page.locator('#catalog-facility').selectOption(facility);
    await page.locator('#catalog-edit-facility').click();
    assert.equal(await page.locator('#catalog-facility-address').inputValue(),result.testAddress);
    await page.screenshot({path:path.join(evidence,'facility-manager-updated.png'),fullPage:true});
    await page.locator('#catalog-facility-address').fill(result.originalAddress);
    const restore=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/manager/facilities/${facility}`&&r.request().method()==='PUT');
    await page.locator('#catalog-facility-save').click();
    result.restoreStatus=(await restore).status();assert.equal(result.restoreStatus,200);
    await page.screenshot({path:path.join(evidence,'facility-manager-restored.png'),fullPage:true});
    await context.close();
  }finally{fs.writeFileSync(path.join(evidence,'facility-management-results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify({facility,changeStatus:result.changeStatus,restoreStatus:result.restoreStatus}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
