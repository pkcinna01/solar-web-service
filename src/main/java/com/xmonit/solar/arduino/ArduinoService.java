package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.metrics.ArduinoGetResponseMetrics;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
@EnableScheduling
public class ArduinoService extends ArduinoSerialBus {

    private static final Logger logger = LoggerFactory.getLogger(ArduinoService.class);

    ArduinoGetResponseMetrics arduinoMetrics;


    public ArduinoService(AppConfig conf, ArduinoGetResponseMetrics arduinoMetrics) {

        super(conf, arduinoMetrics);
        this.arduinoMetrics = arduinoMetrics;
    }


    /**
     * Updates Spring Actuator framework with latest Arduino data (for monitoring systems)
     */
    @Timed
    @Scheduled(fixedDelayString = "${arduino.monitoring.updateIntervalMs}")
    private void updateStats() {
        final int maxRetryCnt = 2;
        arduinoMetrics.updateStatsTracker.incrementCnt();
        for ( int i = 1; i <= maxRetryCnt; i++ ) {
            try {
                arduinoMetrics.updateStatsTracker.incrementAttemptCnt();
                String strResp = execute("GET", null, null);
                processResponse(strResp);
                arduinoMetrics.updateStatsTracker.succeeded(i);
                break;
            } catch (Exception ex) {
                if ( i == maxRetryCnt ) {
                    String msg = ex.getMessage();
                    logger.error("Scheduled GET failed (attempted " + i + " times). ");
                    if (msg != null) {
                        logger.error(msg);
                    }
                    logger.error("Failed updating monitoring statistics.",ex);

                    for (ArduinoResponseProcessor p : responseProcessors) {
                        p.invalidate(this, ex);
                    }
                }
            }
        }
    }


    /**
     * Put daily summary in log
     */
    @Timed
    @Scheduled(cron = "0 0 0 1/1 * ?")
    private void dailySummary() {
        arduinoMetrics.updateStatsTracker.logReliabiltyInfo();
        arduinoMetrics.updateStatsTracker.reset();
    }

}
