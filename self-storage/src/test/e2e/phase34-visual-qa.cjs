const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.SAFEBOX_E2E_BASE_URL;
const password = process.env.SAFEBOX_E2E_PASSWORD;
if (base !== 'http://127.0.0.1:18081' || !password) throw new Error('Isolated test URL and password required');
const output=path.resolve(__dirname,'../../../../docs/testing/evidence/phase34-visual');
fs.mkdirSync(output,{recursive:true});
const roles={
  customer:['customer3@safebox.vn',['/storage.html','/stitch/y_u_c_u_c_a_t_i_safebox_storage.html','/stitch/uc11-customer-payments-appointments.html','/stitch/kho_dang_thue_safebox_storage.html']],
  manager:['manager.q7@safebox.vn',['/stitch/t_ng_quan_qu_n_l_safebox_manager.html','/stitch/qu_n_l_lo_i_kho_kho_v_t_l_safebox_manager.html','/stitch/x_l_y_u_c_u_t_kho_safebox_manager.html','/staff-tasks.html','/management-pricing.html','/management-policies.html']],
  staff:['staff.q7@safebox.vn',['/staff-tasks.html','/role-home.html']],
  business:['business@safebox.vn',['/management-business.html','/management-pricing.html','/management-policies.html','/reports.html','/role-home.html']],
  admin:['admin@safebox.vn',['/management-admin.html','/role-home.html']]
};
const widths=[390,768,1366,1920],result={pages:[],localRoutes:{},errors:[]};
async function login(browser,email){
  const context=await browser.newContext({viewport:{width:1366,height:900}});
  const page=await context.newPage();page.setDefaultTimeout(12000);
  await page.goto(base+'/stitch/ng_nh_p_ng_k_safebox_storage.html');
  await page.locator('#login-email').fill(email);await page.locator('#login-password').fill(password);
  await page.locator('#btn-login-submit').click();await page.waitForURL(u=>!u.pathname.endsWith('/ng_nh_p_ng_k_safebox_storage.html'),{timeout:15000});
  return {context,page};
}
async function main(){
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  try{
    for(const [role,[email,pages]] of Object.entries(roles)){
      const {context,page}=await login(browser,email);
      page.on('pageerror',e=>result.errors.push({role,path:new URL(page.url()).pathname,error:e.message}));
      for(const pathname of pages){
        try{
          const response=await page.goto(base+pathname,{waitUntil:'domcontentloaded',timeout:15000});
          await page.waitForTimeout(900);
          const visible=await page.evaluate(()=>getComputedStyle(document.body).visibility!=='hidden');
          const text=await page.locator('body').innerText();
          const artificial=(text.match(/Seed Data|Snapshot|BR\d{1,2}\.\d|\bUC\d{2}\b|admin@safebox\.vn|customer1@safebox\.vn/gi)||[]).slice(0,8);
          const localLinks=await page.locator('a[href]').evaluateAll(links=>links.map(a=>a.getAttribute('href')).filter(h=>h&&h.startsWith('/')&&h.includes('.html')));
          for(const href of localLinks)result.localRoutes[href.split('?')[0]]=null;
          for(const width of widths){
            await page.setViewportSize({width,height:900});
            await page.waitForTimeout(120);
            const metrics=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth+2,scrollWidth:document.documentElement.scrollWidth,viewport:innerWidth}));
            const name=path.basename(pathname).replace('.html','');
            await page.screenshot({path:path.join(output,`${role}-${name}-${width}.png`),fullPage:false});
            result.pages.push({role,path:pathname,width,status:response.status(),visible,artificial,...metrics});
          }
        }catch(e){result.errors.push({role,path:pathname,error:e.message});}
      }
      await context.close();
    }
    const request=await browser.newContext();
    for(const href of Object.keys(result.localRoutes)){
      try{result.localRoutes[href]=(await request.request.get(base+href,{timeout:5000})).status();}
      catch(e){result.localRoutes[href]=e.message;}
    }
    await request.close();
  }finally{fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(result,null,2));await browser.close();}
  console.log(JSON.stringify({pages:result.pages.length,overflow:result.pages.filter(p=>p.overflow),hidden:result.pages.filter(p=>!p.visible),artificial:result.pages.filter(p=>p.artificial.length),brokenRoutes:Object.entries(result.localRoutes).filter(([,status])=>status!==200),errors:result.errors}));
}
main().catch(e=>{console.error(e.stack||String(e));process.exitCode=1;});
