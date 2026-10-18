package bd.dokanhisab.tests;

import android.app.Instrumentation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import bd.dokanhisab.app.BackupCodec;
import bd.dokanhisab.app.BackupManager;
import bd.dokanhisab.app.LedgerStore;
import bd.dokanhisab.app.LicenseManager;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

public final class BackupInstrumentation extends Instrumentation {
    private Bundle arguments;private int checks;
    interface Action {void run()throws Exception;}
    private void check(boolean value,String title){checks++;if(!value)throw new AssertionError(title);}
    private void reject(Action work,String title)throws Exception{boolean rejected=false;try{work.run();}catch(IllegalArgumentException|IllegalStateException expected){rejected=true;}check(rejected,title);}
    private static byte[] encode(JSONObject obj){return obj.toString().getBytes(StandardCharsets.UTF_8);}
    private static JSONObject altered(BackupCodec.Snapshot snap,String operation)throws Exception{
        JSONObject root=new JSONObject(snap.envelope.toString()),payload=new JSONObject(root.getString("payload"));
        if(operation.equals("duplicate"))payload.getJSONArray("customers").put(payload.getJSONArray("customers").getJSONObject(0));
        if(operation.equals("orphan"))payload.getJSONArray("entries").getJSONObject(0).put("customer_id","00000000-0000-0000-0000-000000000000");
        if(operation.equals("decimal"))payload.getJSONArray("entries").getJSONObject(0).put("amount",1.5);
        if(operation.equals("negative")){for(int i=0;i<payload.getJSONArray("entries").length();i++){JSONObject e=payload.getJSONArray("entries").getJSONObject(i);if(e.getString("kind").equals("payment"))e.put("amount",999999999L);}}
        String text=payload.toString();StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));root.put("payload",text);root.put("sha256",hash.toString());return root;
    }
    private void restorePrefs(SharedPreferences p,Map<String,?> map){SharedPreferences.Editor edit=p.edit().clear();for(Map.Entry<String,?> e:map.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);}edit.commit();}
    @Override public void onCreate(Bundle args){arguments=args;super.onCreate(args);start();}
    @Override public void onStart(){
        Context c=getTargetContext();SharedPreferences license=c.getSharedPreferences("license",0),backup=BackupManager.prefs(c);
        if("true".equals(arguments.getString("reset_preview_prefs"))){license.edit().clear().commit();backup.edit().clear().commit();}
        Map<String,?> oldLicense=license.getAll(),oldBackup=backup.getAll();LedgerStore source=null,dest=null;Bundle result=new Bundle();int resultCode=-1;
        try{
            c.getDatabasePath("backup-source-test.db").getParentFile().mkdirs();c.deleteDatabase("backup-source-test.db");c.deleteDatabase("backup-target-test.db");source=new LedgerStore(c,"backup-source-test.db");dest=new LedgerStore(c,"backup-target-test.db");
            String id=source.saveCustomer(null,"রহিম উদ্দিন","01712345678");source.saveEntry(null,id,"credit",100050,"2026-10-17","চাল, ডাল");source.saveEntry(null,id,"payment",30025,"2026-10-18","নগদ");
            String deleted=source.saveCustomer(null,"মুছে দেওয়া","");source.saveEntry(null,deleted,"credit",100,"2026-10-18","");source.deleteCustomer(deleted);
            BackupCodec.Snapshot snap=source.backup();check(snap.customerCount==1&&snap.entryCount==2,"Backup counts exclude tombstones");check(snap.customers.length()==2&&snap.entries.length()==3,"Backup preserves tombstones for future sync");
            BackupCodec.Snapshot parsed=BackupCodec.parse(snap.bytes());check(parsed.hash.equals(snap.hash),"Checksum round trip");
            String before=dest.saveCustomer(null,"আগের হিসাব","");dest.saveEntry(null,before,"credit",2500,"2026-10-18","");
            final LedgerStore target=dest;
            dest.getWritableDatabase().execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT,'verification failure'); END");
            boolean rolledBack=false;try{dest.restore(parsed);}catch(android.database.sqlite.SQLiteException expected){rolledBack=true;}
            check(rolledBack&&dest.customer(before)!=null&&dest.customer(before).balance()==2500,"Import SQL failure rolls back every table");dest.getWritableDatabase().execSQL("DROP TRIGGER fail_import");
            dest.restore(parsed);check(dest.customer(before)==null,"Import replaces prior ledger");check(dest.customer(id).balance()==70025,"Restore keeps exact poisha");check(dest.customer(id).name.equals("রহিম উদ্দিন"),"Unicode names round trip");check(dest.entries(id).get(1).date.equals("2026-10-17"),"Entry dates round trip");check(dest.customer(deleted)==null,"Deleted customers stay deleted");check(dest.backup().hash.equals(snap.hash),"Stable IDs and all metadata round trip");
            JSONObject damaged=new JSONObject(snap.envelope.toString());damaged.put("sha256","bad");reject(()->target.restore(BackupCodec.parse(encode(damaged))),"Corrupt checksum rejected");check(dest.customer(id).balance()==70025,"Rejected backup preserves current data");
            JSONObject newer=new JSONObject(snap.envelope.toString());newer.put("version",99);reject(()->BackupCodec.parse(encode(newer)),"Future schema rejected");
            for(String operation:new String[]{"duplicate","orphan","decimal","negative"})reject(()->BackupCodec.parse(encode(altered(snap,operation))),"Reject invalid payload: "+operation);
            reject(()->BackupCodec.parse("not a backup".getBytes(StandardCharsets.UTF_8)),"Non-backup file rejected");reject(()->BackupCodec.parse(new byte[BackupCodec.MAX_BYTES+1]),"Oversize file bounded");
            license.edit().clear().commit();backup.edit().clear().commit();check(!LicenseManager.isPro(c),"Free default");reject(()->LicenseManager.activate(c,"DH-INVALID"),"Invalid license rejected");check(!LicenseManager.isPro(c),"Invalid license cannot unlock");
            Uri manual=Uri.parse("content://bd.dokanhisab.tests.files/backup.json"),cloudFailure=Uri.parse("content://bd.dokanhisab.tests.files/failed");
            reject(()->BackupManager.run(c,true),"Free cannot create backup");reject(()->BackupManager.write(c,manual,snap.bytes()),"Free cannot export");reject(()->BackupManager.target(c,"local",manual),"Free cannot connect backup target");reject(()->BackupManager.restore(c,snap),"Free cannot import");
            String key=arguments.getString("license_key","");LicenseManager.activate(c,key.toLowerCase(java.util.Locale.ROOT));check(LicenseManager.isPro(c),"Known key unlocks Pro offline");check(!license.getAll().containsValue(key),"Raw license key is not stored");
            BackupManager.write(c,manual,snap.bytes());BackupCodec.Snapshot file=BackupCodec.read(c.getContentResolver().openInputStream(manual));check(file.hash.equals(snap.hash),"Manual phone export succeeds");reject(()->BackupManager.target(c,"local",manual),"Phone auto-backup target is rejected");
            JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);BackupManager.schedule(c);check(scheduler.getPendingJob(BackupManager.JOB_ID)==null&&!BackupManager.due(c),"No auto job before Drive is connected");
            backup.edit().putString("local_uri",manual.toString()).putLong("interval",24*BackupManager.HOUR).putBoolean("enabled",false).commit();BackupManager.schedule(c);check(!backup.contains("local_uri")&&!backup.contains("interval")&&!backup.contains("enabled"),"Legacy phone auto-backup settings are removed");
            BackupManager.target(c,"cloud",manual);BackupManager.schedule(c);JobInfo job=scheduler.getPendingJob(BackupManager.JOB_ID);check(job!=null&&job.getIntervalMillis()==BackupManager.HOUR&&job.isPersisted(),"Drive backup runs hourly and persists over reboot");
            String errors=BackupManager.run(c,true);check(errors.isEmpty(),"Drive document-provider export succeeds");long first=backup.getLong("cloud_at",0);String hash=backup.getString("cloud_hash","");String recoveryHash=BackupCodec.parse(BackupManager.latest(c)).hash;check(first>0&&!hash.isEmpty(),"Successful Drive write records status");BackupManager.run(c,false);check(backup.getLong("cloud_at",0)==first,"Unchanged ledger does not rewrite Drive file");
            BackupManager.disconnect(c,"cloud");BackupManager.schedule(c);check(scheduler.getPendingJob(BackupManager.JOB_ID)==null,"Disconnecting Drive cancels auto backup");BackupManager.target(c,"cloud",cloudFailure);errors=BackupManager.run(c,true);check(!errors.isEmpty(),"Cloud provider failure reported");check(backup.getLong("cloud_at",0)==0&&backup.getString("cloud_hash","").isEmpty(),"Failure is not reported as cloud success");check(BackupCodec.parse(BackupManager.latest(c)).hash.equals(recoveryHash),"Internal recovery copy survives provider failure");
            result.putString("stream","PASS: "+checks+" backup, import, licensing and scheduling checks\n");
        }catch(Throwable ex){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(ex));resultCode=0;}
        finally{if(source!=null)source.close();if(dest!=null)dest.close();c.deleteDatabase("backup-source-test.db");c.deleteDatabase("backup-target-test.db");restorePrefs(license,oldLicense);restorePrefs(backup,oldBackup);BackupManager.schedule(c);}
        finish(resultCode,result);
    }
}
