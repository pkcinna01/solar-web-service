package com.xmonit.solar.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for tracking health of USB reads.  Keeps track of total reads and number that were successful
 */
public class UpdateStatsTracker {

    private static final Logger logger = LoggerFactory.getLogger(UpdateStatsTracker.class);

    public AtomicInteger cnt = new AtomicInteger();
    public AtomicInteger attemptCnt = new AtomicInteger();
    public AtomicInteger successCnt = new AtomicInteger();
    public AtomicInteger successAttemptCnt = new AtomicInteger();

    public String name;

    public UpdateStatsTracker(String name) {
        this.name = name;
    }

    public void incrementCnt() {
        cnt.incrementAndGet();
    }

    public void incrementAttemptCnt() {
        attemptCnt.incrementAndGet();
    }

    public void reset() {
        cnt.set(0);
        attemptCnt.set(0);
        successCnt.set(0);
        successAttemptCnt.set(0);
    }

    public void succeeded(int tries) {
        successCnt.incrementAndGet();
        successAttemptCnt.addAndGet(tries);
    }

    public void logReliabiltyInfo() {
        logger.info(name + " communications summary:");
        logger.info("                 total: " + cnt);
        logger.info("      attempts/request: " + attemptCnt.get() * 1.0 / cnt.get());
        logger.info("          unsuccessful: " + (cnt.get() - successCnt.get()));
        logger.info("      attempts/success: " + (successAttemptCnt.get() * 1.0 / successCnt.get()));
    }
}