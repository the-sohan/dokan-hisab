package bd.dokanhisab.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class BackupJobService extends JobService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Future<?> work;
    @Override public boolean onStartJob(JobParameters params){
        if(!BackupManager.automaticConfigured(this))return false;
        work=executor.submit(()->{try{String error=BackupManager.run(this,false);jobFinished(params,!error.isEmpty());}catch(Exception ex){jobFinished(params,true);}});return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(work!=null)work.cancel(true);return true;}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
