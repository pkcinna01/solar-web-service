package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.EpeverMetrics;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xmonit.solar.epever.EpeverFieldDefinitions.REAL_TIME_CLOCK;


@Service
@EnableScheduling
public class EpeverService {

    private static final Logger logger = LoggerFactory.getLogger(EpeverService.class);
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
        for (String serialName : serialNames) {
            MetricsSource ms = new MetricsSource();
            try {
                ms.charger.init(serialName);
                logger.info(serialName + " charge controller intialized");
                metricSourceList.add(ms);
            } catch (Exception e) {
                logger.error("Failed initializing solar charger '" + serialName + "'");
            }
        }
    }

    public void releaseMetricsSources() {
        for (MetricsSource ms : metricSourceList) {
            /*try {
                ms.charger.disconnect();
            } catch (ModbusIOException e) {
                logger.error("Failed disconnecting solar charger '" + ms.charger.getSerialName() + "'");
            }*/
        }
        metricSourceList.clear();
    }

    public synchronized Map<String, List<EpeverField>> findFieldsByNameGroupBySerialPort(String titlePattern) throws Exception {
        Map<String, List<EpeverField>> fieldsBySerialPortId = new LinkedHashMap();
        for (MetricsSource ms : metricSourceList) {
            List<EpeverField> fields = EpeverFieldList.masterFieldList.stream().filter(f -> f.name.matches(titlePattern)).collect(Collectors.toList());
            fieldsBySerialPortId.put(ms.charger.serialName, fields);
            ms.charger.connect();
            try {
                EpeverFieldList.readValues(ms.charger, fields);
            } finally {
                ms.charger.disconnect();
            }
        }
        return fieldsBySerialPortId;
    }

    /**
     * Updates Spring Actuator framework with latest data from EPEver charge controllers
     */
    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private synchronized void updateStats() {
        for (MetricsSource ms : metricSourceList) {
            final int maxRetryCnt = 2;
            for (int i = 1; i <= maxRetryCnt; i++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    ms.charger.connect();
                    try {
                        ms.fields.readValues();
                    } finally {
                        ms.charger.disconnect();
                    }
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
                        releaseMetricsSources();
                        initMetricsSources();
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
        for (MetricsSource ms : metricSourceList) {
            ms.metrics.updateStatsTracker.logReliabiltyInfo();
            ms.metrics.updateStatsTracker.reset();
        }
    }

    class MetricsSource {
        EpeverSolarCharger charger;
        EpeverFieldList fields;
        EpeverMetrics metrics;

        MetricsSource() {
            charger = new EpeverSolarCharger();
            fields = new EpeverFieldList(charger, fd -> fd.isStatistic() || fd == REAL_TIME_CLOCK);
            metrics = new EpeverMetrics(meterRegistry);
        }
    }

}
