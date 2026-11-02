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
function exportPreviousBusinessDay(){runExportSafely_(targetDate_());}
function exportPreviousBusinessDayNow(){runExportSafely_(targetDate_());}
function exportBusinessDateForTesting(yyyyMMdd){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(String(yyyyMMdd)))throw Error('Use YYYY-MM-DD');
  runExportSafely_(String(yyyyMMdd));
}
function runExportSafely_(date){
  const lock=LockService.getScriptLock();
  try{lock.waitLock(30000);}catch(err){notifyExportFailure_(date,err);throw err;}
  try{exportDate_(date);}catch(err){notifyExportFailure_(date,err);throw err;}finally{lock.releaseLock();}
}
function notifyExportFailure_(date,err){
  const email=PropertiesService.getScriptProperties().getProperty('EXPORT_ALERT_EMAIL');
  const message='MahaMart Expiry Google Sheets export failed.\nBusiness date: '+date+'\nTime (IST): '+Utilities.formatDate(new Date(),TZ,'yyyy-MM-dd HH:mm:ss')+'\nError: '+String(err&&err.stack?err.stack:err).slice(0,4000)+'\n\nCheck the Apps Script Executions page for the full run details.';
  if(!email){Logger.log('EXPORT_ALERT_EMAIL is not configured. '+message);return;}
  try{MailApp.sendEmail(email,'MahaMart Expiry export FAILED — '+date,message);}catch(mailErr){Logger.log('Could not send export failure email: '+mailErr);}
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
  // NO EXPIRY confirmations go to a separate CSV; category selection is saved separately.
  const groups={};
  const noExpiryRows=[];
  let deletedNoExpiryCount=0;
  rows.forEach(r=>{
    const isNoExpiry=String(r.expiry||'').trim().toUpperCase()==='NO EXPIRY';
    if(isNoExpiry){
      if(String(r.is_deleted).toLowerCase()!=='true') noExpiryRows.push([String(r.location||''),date,String(r.no_expiry_category||'')]);
      else deletedNoExpiryCount++;
      return;
    }
    const rowCategory=String(r.category||'').trim()||'Uncategorised';
    const name=clean_(rowCategory);
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
  let sheetRowsWritten=0;
  categories.forEach(k=>{sheetRowsWritten+=writeSheet_(day,k,groups[k]);});
  const noExpiryRowsWritten=writeNoExpiryCsv_(day,noExpiryRows);
  const exportedRows=sheetRowsWritten+noExpiryRowsWritten;
  const expectedRows=rows.length-deletedNoExpiryCount;
  if(exportedRows!==expectedRows)throw Error('Export row-count mismatch: Supabase rows='+rows.length+', excluded deleted NO EXPIRY='+deletedNoExpiryCount+', expected exported='+expectedRows+', verified exported='+exportedRows+'.');
  logExport_(root,date,start,end,rows.length,categories,day.getId(),{exportedRows:exportedRows,deletedNoExpiry:deletedNoExpiryCount,noExpiryRows:noExpiryRowsWritten,status:'SUCCESS',error:''});
  Logger.log(JSON.stringify({businessDate:date,supabaseRows:rows.length,exportedRows:exportedRows,deletedNoExpiryExcluded:deletedNoExpiryCount,scheduledCategories:scheduled,exportedCategories:categories,folder:folderLabel,status:'SUCCESS'}));
}

function writeNoExpiryCsv_(folder,rows){
  const esc=v=>'"'+String(v===null||v===undefined?'':v).replace(/"/g,'""')+'"';
  const lines=[['Store name','Date','No expiry'].map(esc).join(',')];
  rows.forEach(row=>lines.push(row.map(esc).join(',')));
  const old=folder.getFilesByName('no expiry.csv');
  while(old.hasNext())old.next().setTrashed(true);
  const file=folder.createFile('no expiry.csv',lines.join('\r\n'),MimeType.CSV);
  if(!file.getSize())throw Error('NO EXPIRY CSV was created but is empty.');
  return rows.length;
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
  const headers=['id','location','skucode','skuname','ean_sku_scanned','qty','expiry_date','expiry','category','date','time','is_deleted','created_at','deleted_at','session_start','uploaded_at'];
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
  const verifiedRows=Math.max(0,sh.getLastRow()-1);
  if(verifiedRows!==rows.length)throw Error('Sheet row-count mismatch for '+name+': expected '+rows.length+', found '+verifiedRows+'.');
  return verifiedRows;
}
function logExport_(root,date,start,end,count,categories,folderId,metrics){
  const it=root.getFilesByName('_export_log');let book;
  if(it.hasNext())book=SpreadsheetApp.openById(it.next().getId());
  else{book=SpreadsheetApp.create('_export_log');DriveApp.getFileById(book.getId()).moveTo(root);}
  const sh=book.getSheets()[0];sh.setName('Export Log');
  const headers=['business_date','range_start_inclusive','range_end_exclusive','row_count','categories_exported','folder_id','exported_at_ist','status','exported_row_count','excluded_deleted_no_expiry','no_expiry_csv_rows','error'];
  if(!sh.getLastRow()){sh.appendRow(headers);sh.setFrozenRows(1);}
  else if(sh.getLastColumn()<headers.length)sh.getRange(1,1,1,headers.length).setValues([headers]);
  let row=0;if(sh.getLastRow()>1){const vals=sh.getRange(2,1,sh.getLastRow()-1,1).getDisplayValues();for(let i=0;i<vals.length;i++)if(vals[i][0]===date){row=i+2;break;}}
  const record=[[date,start,end,count,categories.join(' | '),folderId,Utilities.formatDate(new Date(),TZ,'yyyy-MM-dd HH:mm:ss'),metrics.status,metrics.exportedRows,metrics.deletedNoExpiry,metrics.noExpiryRows,metrics.error]];
  sh.getRange(row||sh.getLastRow()+1,1,1,record[0].length).setValues(record);
}
function getFolder_(parent,name){const it=parent.getFoldersByName(name);return it.hasNext()?it.next():parent.createFolder(name);}
function boundary_(date){const p=date.split('-').map(Number);return new Date(Date.UTC(p[0],p[1]-1,p[2],15,0,0)-19800000).toISOString();}
function addDays_(date,n){const p=date.split('-').map(Number),d=new Date(Date.UTC(p[0],p[1]-1,p[2]+n));return Utilities.formatDate(d,'UTC','yyyy-MM-dd');}
function localDate_(date){const p=date.split('-').map(Number);return new Date(p[0],p[1]-1,p[2],12);}
function short_(s){return String(s).replace(/\s*\([^)]*\)/g,'').replace(/\s+/g,' ').trim();}
function clean_(s){return String(s||'Uncategorised').replace(/[\\/:*?"<>|#%{}~&]/g,'-').replace(/\s+/g,' ').replace(/-+/g,'-').replace(/^[- .]+|[- .]+$/g,'').slice(0,150)||'Uncategorised';}
