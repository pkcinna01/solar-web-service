package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
import com.xmonit.solar.arduino.metrics.ArduinoGetResponseMetrics;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;


@Service
@EnableScheduling
public class ArduinoService extends ArduinoSerialBus {

    private static final Logger logger = LoggerFactory.getLogger(ArduinoService.class);

    ArduinoGetResponseMetrics arduinoMetrics;

    public String strLastStatsResp;


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
        final int maxRetryCnt = 4;
        arduinoMetrics.updateStatsTracker.incrementCnt();
        for ( int i = 1; i <= maxRetryCnt; i++ ) {
            try {
                arduinoMetrics.updateStatsTracker.incrementAttemptCnt();
                String strResp = execute("GET,SENSORS", null, null, true);
                processResponse(strResp);
                cachedGetResp.update(this.getPortName(),strResp);
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


    @Override
    public synchronized String execute(String cmd, String ttyRegEx, Integer explicitRequestId, boolean validate) throws Exception {
        String strResp = super.execute(cmd, ttyRegEx, explicitRequestId,validate);
        if (cmd.startsWith("SET,")||cmd.startsWith("SETUP,")) {
            //TODO - parse strResp as json and check for success before invalidating
            cachedGetResp.invalidate();
        }
        return strResp;
    }


    private CachedCmdResp cachedGetResp = new CachedCmdResp("get");

    public String execute(String cmd, String ttyRegEx, boolean useCached, boolean validate) throws Exception {
        if ( useCached ) {
            if ( "get,sensors".equalsIgnoreCase(cmd) ) {
                String cachedGet = cachedGetResp.getLatest(ttyRegEx);
                if ( cachedGet != null ) {
                    return cachedGet;
                }
            }
        }
        return super.execute(cmd,ttyRegEx,null,validate);
    }
}
