package com.codex.smsrelay;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class RelayJobService extends JobService {
    private volatile Thread worker;

    @Override
    public boolean onStartJob(final JobParameters params) {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean retry = RelayWorker.processQueue(RelayJobService.this);
                jobFinished(params, retry);
            }
        }, "sms-relay");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread running = worker;
        if (running != null) {
            running.interrupt();
        }
        return true;
    }

}
