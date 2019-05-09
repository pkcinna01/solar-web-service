package com.xmonit.solar.metrics;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricsWatchdog<MetricsSourceT> {
    private boolean bStop = false;
    private String step;
    private MetricsSourceT metricsSource;
    private long lastMetricsUpdate = 0;
    private long maxAgeMs = 60000;
    private String name;
    private Thread activeThread;

    //public static Object sharedSyncObj = new Object();

    public MetricsWatchdog(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public synchronized void attemptRecover() {
        log.warn("No recover override defined for " + this.name + " stuck metrics");
    }

    public MetricsSourceT getMetricsSource() {
        return metricsSource;
    }

    public void start() {
        new Thread(() -> {
            log.info(name + ": Begin watchdog thread");
            while (!bStop) {
                synchronized (MetricsWatchdog.this) {
                    if (lastMetricsUpdate > 0 && System.currentTimeMillis() - lastMetricsUpdate > maxAgeMs) {
                        log.error("****************************************************************");
                        log.error(name + ": Metrics stuck.");
                        log.error(name + " step: " + step);
                        lastMetricsUpdate = 0;
                        if ( activeThread != null ) {
                            log.error("Begin dump of stuck thread");
                            for (StackTraceElement ste : activeThread.getStackTrace()) {
                                log.error(ste.toString());
                            }
                            log.error("END dump of stuck thread");
                        }
                        log.error("****************************************************************");
                        log.info("Begin attempt recover...");
                        attemptRecover();
                        log.info("Attempt recover complete.");
                    }
                }
                try {
                    Thread.sleep(maxAgeMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.info(name + ": End watchdog thread");
        }).start();
    }

    public synchronized void stop() {
        this.bStop = true;
    }

    public synchronized void setStep(MetricsSourceT metricsSource, String step) {
        this.metricsSource = metricsSource;
        this.step = step;
        this.activeThread = Thread.currentThread();
        //log.debug(name + " step: " + step);
    }

    public synchronized void updateComplete() {
        lastMetricsUpdate = System.currentTimeMillis();
    }

    public synchronized void setMaxAgeMs(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }
}
