package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.dao.sensor.SensorDao;
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

    ArduinoMetrics arduinoMetrics;


    public ArduinoService(AppConfig conf, ArduinoMetrics arduinoMetrics) {

        super(conf);
        this.arduinoMetrics = arduinoMetrics;
    }


    /**
     * Updates Spring Actuator framework with latest Arduino data (for monitoring systems)
     */
    @Timed
    @Scheduled(fixedDelayString = "${arduino.monitoring.updateIntervalMs}")
    private void updateStats() {
        logger.debug("START updating stats");
        final int maxRetryCnt = 4;
        arduinoMetrics.updateStatsTracker.incrementCnt();
        for ( int i = 1; i <= maxRetryCnt; i++ ) {
            try {
                arduinoMetrics.updateStatsTracker.incrementAttemptCnt();
                SensorDao sensorDao = new SensorDao(this);
                boolean bVerbose = false; // just want name and value
                arduinoMetrics.update(getPortName(),sensorDao.list(bVerbose));
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

                    arduinoMetrics.invalidate(ex);
                }
            }
        }
        logger.debug("END: updating stats");
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
