package com.xmonit.solar.epever;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.EpeverMetrics;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

import static com.xmonit.solar.epever.EpeverFieldDefinitions.REAL_TIME_CLOCK;


@Service
@EnableScheduling
public class EpeverService {

    private static final Logger logger = LoggerFactory.getLogger(EpeverService.class);

    class MetricsSource {
        EpeverSolarCharger charger;
        EpeverFieldList fields;
        EpeverMetrics metrics;

        MetricsSource(){
            charger = new EpeverSolarCharger();
            fields = new EpeverFieldList(charger, fd -> fd.isStatistic() || fd == REAL_TIME_CLOCK);
            metrics = new EpeverMetrics(meterRegistry);
        }
    }

    MeterRegistry meterRegistry;

    List<MetricsSource> metricSourceList = new LinkedList();

    AppConfig conf;


    public EpeverService(AppConfig conf, MeterRegistry meterRegistry) {

        this.conf = conf;
        this.meterRegistry = meterRegistry;
        initMetricsSources();
    }


    public void initMetricsSources() {

        List<String> serialNames = EpeverSolarCharger.findSerialNames(conf.getEpeverSerialNameRegEx());
        releaseMetricsSources();
        for( String serialName: serialNames ) {
            MetricsSource ms = new MetricsSource();
            try {
                ms.charger = new EpeverSolarCharger();
                ms.charger.init(serialName);
                ms.charger.connect();
                metricSourceList.add(ms);
            } catch (Exception e) {
                logger.error("Failed initializing solar charger '" + serialName + "'");
            }
        }
    }


    public void releaseMetricsSources() {
        for(MetricsSource ms : metricSourceList) {
            try {
                ms.charger.disconnect();
            } catch (ModbusIOException e) {
                logger.error("Failed disconnecting solar charger '" + ms.charger.getSerialName() + "'");
            }
        }
        metricSourceList.clear();
    }


    /**
     * Updates Spring Actuator framework with latest data from EPEver charge controllers
     */
    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private void updateStats() {
        for( MetricsSource ms : metricSourceList ) {
            final int maxRetryCnt = 2;
            for (int i = 1; i <= maxRetryCnt; i++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    ms.fields.readValues();
                    ms.metrics.updateStatsTracker.succeeded(i);
                    break;
                } catch (Exception ex) {
                    if (i == maxRetryCnt) {
                        String msg = ex.getMessage();
                        logger.error("Scheduled EPEver charge controller read failed (attempted " + i + " times). ");
                        if (msg != null) {
                            logger.error(msg);
                        }
                        logger.error("Failed updating monitoring statistics.", ex);

                        //for (ArduinoResponseProcessor p : responseProcessors) {
                        //    p.invalidate(this, ex);
                        //}
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
        for( MetricsSource ms : metricSourceList ) {
            ms.metrics.updateStatsTracker.logReliabiltyInfo();
            ms.metrics.updateStatsTracker.reset();
        }
    }

}
