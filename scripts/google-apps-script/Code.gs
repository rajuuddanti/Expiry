/**
 * MahaMart Expiry Manager: Supabase expiry_entry_uploads -> Google Sheets.
 * Store all secrets in Apps Script Project Settings > Script properties.
 */
const TZ='Asia/Kolkata';
const TABLE='expiry_entry_uploads';
const PAGE=1000;
const DEFAULT_SCHEDULE='https://raw.githubusercontent.com/rajuuddanti/Expiry/feature/new-expiry-upload-table/app/src/main/assets/inspection_schedule.json';

function setupDailyTrigger(){
  ScriptApp.getProjectTriggers().filter(t=>t.getHandlerFunction()==='exportPreviousBusinessDay')
    .forEach(t=>ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('exportPreviousBusinessDay').timeBased().everyDays(1)
    .atHour(15).nearMinute(15).inTimezone(TZ).create();
  Logger.log('Trigger created (Apps Script runs near 3:15 PM IST, not guaranteed to the exact minute).');
}
function exportPreviousBusinessDay(){exportDate_(targetDate_());}
function exportPreviousBusinessDayNow(){exportDate_(targetDate_());}
function exportBusinessDateForTesting(yyyyMMdd){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(String(yyyyMMdd)))throw Error('Use YYYY-MM-DD');
  exportDate_(String(yyyyMMdd));
}
function targetDate_(){
  const today=Utilities.formatDate(new Date(),TZ,'yyyy-MM-dd').split('-').map(Number);
  const d=new Date(Date.UTC(today[0],today[1]-1,today[2]-1));
  return Utilities.formatDate(d,'UTC','yyyy-MM-dd');
}
function config_(){
  const p=PropertiesService.getScriptProperties(), c={
    url:(p.getProperty('SUPABASE_URL')||'').replace(/\/$/,''),
    key:p.getProperty('SUPABASE_SERVICE_ROLE_KEY')||'',
    root:p.getProperty('ROOT_FOLDER_ID')||'',
    schedule:p.getProperty('SCHEDULE_JSON_URL')||DEFAULT_SCHEDULE
  };
  if(!c.url||!c.key||!c.root)throw Error('Set SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and ROOT_FOLDER_ID in Script properties.');
  return c;
}
function exportDate_(date){
  const c=config_(), next=addDays_(date,1), start=boundary_(date), end=boundary_(next);
  const rows=fetchRows_(c,start,end), scheduled=getScheduled_(c.schedule,date);
  const folderLabel=Utilities.formatDate(localDate_(date),TZ,'d MMM')+' '+
    clean_(scheduled.length?scheduled.map(short_).join('-'):'Schedule-Not-Found');
  const root=DriveApp.getFolderById(c.root);
  const year=getFolder_(root,date.slice(0,4));
  const month=getFolder_(year,Utilities.formatDate(localDate_(date),TZ,'MMMM'));
  const day=getFolder_(month,folderLabel);
  // Only create sheets for categories that actually occur in the Supabase rows.
  const groups={};
  rows.forEach(r=>{
    const category=String(r.category||'').trim()||'Uncategorised';
    const name=clean_(category);
    if(!groups[name])groups[name]=[];
    groups[name].push(r);
  });
  const categories=Object.keys(groups).sort();

  // Remove obsolete generated category sheets from this date's folder. This
  // includes scheduled categories with no Supabase rows and categories that
  // appeared in an earlier export but are absent from the current result.
  const existingFiles=day.getFiles();
  while(existingFiles.hasNext()){
    const file=existingFiles.next();
    if(file.getMimeType()===MimeType.GOOGLE_SHEETS && !groups[file.getName()]){
      file.setTrashed(true);
    }
  }
  categories.forEach(k=>writeSheet_(day,k,groups[k]));
  logExport_(root,date,start,end,rows.length,categories,day.getId());
  Logger.log(JSON.stringify({businessDate:date,rows:rows.length,scheduledCategories:scheduled,exportedCategories:categories,folder:folderLabel}));
}
function fetchRows_(c,start,end){
  const out=[];let offset=0;
  while(true){
    const filters='&and=(session_start.gte.'+encodeURIComponent(start)+',session_start.lt.'+encodeURIComponent(end)+')';
    const url=c.url+'/rest/v1/'+TABLE+'?select=*&order=session_start.asc,location.asc,id.asc&limit='+PAGE+'&offset='+offset+filters;
    const res=UrlFetchApp.fetch(url,{headers:{apikey:c.key,Authorization:'Bearer '+c.key,Accept:'application/json'},muteHttpExceptions:true});
    const status=res.getResponseCode();if(status<200||status>=300)throw Error('Supabase HTTP '+status+': '+res.getContentText().slice(0,1000));
    const page=JSON.parse(res.getContentText()||'[]');if(!Array.isArray(page))throw Error('Unexpected Supabase response.');
    out.push.apply(out,page);if(page.length<PAGE)break;offset+=page.length;
  }return out;
}
function getScheduled_(url,date){
  const res=UrlFetchApp.fetch(url,{muteHttpExceptions:true});if(res.getResponseCode()<200||res.getResponseCode()>=300)throw Error('Schedule JSON HTTP '+res.getResponseCode());
  const data=JSON.parse(res.getContentText()),seen={};
  return data.filter(x=>x.date===date).map(x=>String(x.category||'').trim()).filter(x=>x&&!seen[x]&&(seen[x]=true));
}
function writeSheet_(folder,category,rows){
  const name=clean_(category), found=folder.getFilesByName(name);let book;
  if(found.hasNext()){const f=found.next();book=SpreadsheetApp.openById(f.getId());while(found.hasNext())found.next().setTrashed(true);}
  else{book=SpreadsheetApp.create(name);DriveApp.getFileById(book.getId()).moveTo(folder);}
  const sh=book.getSheets()[0];sh.setName('Expiry Entries');sh.clearContents();
  const headers=['id','location','skucode','skuname','ean_sku_scanned','qty','expiry_date','category','date','time','is_deleted','created_at','deleted_at','session_start','uploaded_at'];
  sh.getRange(1,1,1,headers.length).setValues([headers]);
  for(let i=0;i<rows.length;i+=1500){
    const values=rows.slice(i,i+1500).map(r=>headers.map(h=>r[h]===null||r[h]===undefined?'':r[h]));
    if(values.length)sh.getRange(i+2,1,values.length,headers.length).setValues(values);
  }
  sh.setFrozenRows(1);sh.getRange(1,1,1,headers.length).setFontWeight('bold');
  const existingFilter=sh.getFilter();
  if(existingFilter)existingFilter.remove();
  if(sh.getLastRow()>1)sh.getRange(1,1,sh.getLastRow(),headers.length).createFilter();
  sh.autoResizeColumns(1,headers.length);book.setSpreadsheetTimeZone(TZ);
}
function logExport_(root,date,start,end,count,categories,folderId){
  const it=root.getFilesByName('_export_log');let book;
  if(it.hasNext())book=SpreadsheetApp.openById(it.next().getId());
  else{book=SpreadsheetApp.create('_export_log');DriveApp.getFileById(book.getId()).moveTo(root);}
  const sh=book.getSheets()[0];sh.setName('Export Log');
  if(!sh.getLastRow()){sh.appendRow(['business_date','range_start_inclusive','range_end_exclusive','row_count','categories_exported','folder_id','exported_at_ist']);sh.setFrozenRows(1);}
  let row=0;if(sh.getLastRow()>1){const vals=sh.getRange(2,1,sh.getLastRow()-1,1).getDisplayValues();for(let i=0;i<vals.length;i++)if(vals[i][0]===date){row=i+2;break;}}
  const record=[[date,start,end,count,categories.join(' | '),folderId,Utilities.formatDate(new Date(),TZ,'yyyy-MM-dd HH:mm:ss')]];
  sh.getRange(row||sh.getLastRow()+1,1,1,record[0].length).setValues(record);
}
function getFolder_(parent,name){const it=parent.getFoldersByName(name);return it.hasNext()?it.next():parent.createFolder(name);}
function boundary_(date){const p=date.split('-').map(Number);return new Date(Date.UTC(p[0],p[1]-1,p[2],15,0,0)-19800000).toISOString();}
function addDays_(date,n){const p=date.split('-').map(Number),d=new Date(Date.UTC(p[0],p[1]-1,p[2]+n));return Utilities.formatDate(d,'UTC','yyyy-MM-dd');}
function localDate_(date){const p=date.split('-').map(Number);return new Date(p[0],p[1]-1,p[2],12);}
function short_(s){return String(s).replace(/\s*\([^)]*\)/g,'').replace(/\s+/g,' ').trim();}
function clean_(s){return String(s||'Uncategorised').replace(/[\\/:*?"<>|#%{}~&]/g,'-').replace(/\s+/g,' ').replace(/-+/g,'-').replace(/^[- .]+|[- .]+$/g,'').slice(0,150)||'Uncategorised';}
