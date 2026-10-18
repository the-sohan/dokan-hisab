package bd.dokanhisab.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;

/** Writes only user-chosen document URIs; no account credentials or broad Drive access. */
public final class BackupManager {
    public static final int JOB_ID=53471;
    public static final long HOUR=3600000L,DAY=24*HOUR;
    private static final Object LOCK=new Object();
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("backup",0);}
    public static void schedule(Context c) {
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(!LicenseManager.isPro(c)||!prefs(c).getBoolean("enabled",true)){jobs.cancel(JOB_ID);return;}
        long period=prefs(c).getLong("interval",HOUR);JobInfo current=jobs.getPendingJob(JOB_ID);
        if(current!=null&&current.getIntervalMillis()==period)return;
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,BackupJobService.class)).setPeriodic(period,Math.min(period/4,15*60000L)).setPersisted(true).setRequiresBatteryNotLow(true).build();
        if(jobs.schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("স্বয়ংক্রিয় ব্যাকআপ চালু করা যায়নি।");
    }
    public static boolean due(Context c){return LicenseManager.isPro(c)&&prefs(c).getBoolean("enabled",true)&&System.currentTimeMillis()-prefs(c).getLong("checked_at",0)>=prefs(c).getLong("interval",HOUR);}
    public static void target(Context c,String kind,Uri uri) {
        LicenseManager.requirePro(c);
        if(!kind.equals("local")&&!kind.equals("cloud"))throw new IllegalArgumentException();
        prefs(c).edit().putString(kind+"_uri",uri.toString()).remove(kind+"_hash").remove(kind+"_error").apply();
    }
    public static void disconnect(Context c,String kind){LicenseManager.requirePro(c);prefs(c).edit().remove(kind+"_uri").remove(kind+"_hash").remove(kind+"_at").remove(kind+"_error").apply();}
    public static String run(Context context,boolean force)throws Exception {
        Context c=context.getApplicationContext();LicenseManager.requirePro(c);
        synchronized(LOCK){
            try(LedgerStore store=new LedgerStore(c)){
                BackupCodec.Snapshot snap=store.backup();SharedPreferences p=prefs(c);long now=System.currentTimeMillis();
                if(force||!snap.hash.equals(p.getString("internal_hash",""))){
                    saveInternal(c,"latest.json",snap.bytes());
                    File daily=new File(directory(c),"day-"+LocalDate.now()+".json");
                    if(!daily.exists())saveInternal(c,daily.getName(),snap.bytes());
                    prune(c,"day-",7);
                    p.edit().putString("internal_hash",snap.hash).putLong("internal_at",now).apply();
                }
                StringBuilder errors=new StringBuilder();
                for(String kind:new String[]{"local","cloud"}){
                    String raw=p.getString(kind+"_uri","");if(raw.isEmpty())continue;
                    if(!force&&snap.hash.equals(p.getString(kind+"_hash","")))continue;
                    try {
                        write(c,Uri.parse(raw),snap.bytes());
                        p.edit().putString(kind+"_hash",snap.hash).putLong(kind+"_at",now).remove(kind+"_error").apply();
                    }catch(Exception ex){String msg=kind.equals("local")?"ফোনের ব্যাকআপ ফাইলে লেখা যায়নি। ফাইলটি আবার বেছে নিন।":"Drive/ক্লাউডের ফাইলে লেখা যায়নি। ইন্টারনেট ও ফাইলের অনুমতি পরীক্ষা করুন।";p.edit().putString(kind+"_error",msg).apply();if(errors.length()>0)errors.append("\n");errors.append(msg);}
                }
                p.edit().putLong("checked_at",now).apply();return errors.toString();
            }
        }
    }
    public static void write(Context c,Uri uri,byte[] bytes)throws Exception {
        LicenseManager.requirePro(c);
        try(OutputStream out=c.getContentResolver().openOutputStream(uri,"rwt")){if(out==null)throw new java.io.IOException("Cannot open file");out.write(bytes);out.flush();}
    }
    private static File directory(Context c)throws Exception {File f=new File(c.getFilesDir(),"backups");if(!f.exists()&&!f.mkdirs())throw new java.io.IOException("Backup directory unavailable");return f;}
    static void saveInternal(Context c,String name,byte[] bytes)throws Exception {
        AtomicFile file=new AtomicFile(new File(directory(c),name));FileOutputStream stream=null;
        try{stream=file.startWrite();stream.write(bytes);file.finishWrite(stream);}catch(Exception ex){if(stream!=null)file.failWrite(stream);throw ex;}
    }
    private static void prune(Context c,String prefix,int keep)throws Exception {File[] files=directory(c).listFiles((d,n)->n.startsWith(prefix)&&n.endsWith(".json"));if(files==null)return;Arrays.sort(files,Comparator.comparing(File::getName).reversed());for(int i=keep;i<files.length;i++)files[i].delete();}
    public static byte[] latest(Context c)throws Exception {LicenseManager.requirePro(c);File file=new File(directory(c),"latest.json");if(!file.exists())run(c,true);return Files.readAllBytes(file.toPath());}
    public static void restore(Context c,BackupCodec.Snapshot snapshot)throws Exception {
        LicenseManager.requirePro(c);synchronized(LOCK){
            try(LedgerStore store=new LedgerStore(c)){
                // Keep a recovery copy before replacing the ledger, including an empty ledger.
                saveInternal(c,"before-restore-"+System.currentTimeMillis()+".json",store.backup().bytes());prune(c,"before-restore-",3);
                store.restore(snapshot);
                prefs(c).edit().remove("internal_hash").remove("local_hash").remove("cloud_hash").remove("checked_at").apply();
            }
        }
    }
}
