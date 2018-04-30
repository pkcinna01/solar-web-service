package com.xmonit.solar.epever;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.MetricsSource;
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


@Service
@EnableScheduling
public class EpeverService {


    private static final Logger logger = LoggerFactory.getLogger(EpeverService.class);

    AppConfig conf;
    MeterRegistry meterRegistry;
    public List<MetricsSource> metricSourceList = new LinkedList();


    public EpeverService(AppConfig conf, MeterRegistry meterRegistry) {

        this.conf = conf;
        this.meterRegistry = meterRegistry;
        init();
    }


    public synchronized Map<String, List<EpeverField>> findFieldsByNameGroupBySerialPort(String titlePattern) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = findFieldsByNameGroupByCharger(titlePattern);

        return fieldsByCharger.entrySet().stream().collect( Collectors.toMap(e->e.getKey().getSerialName(),e->e.getValue()) );
    }


    public synchronized Map<SolarCharger, List<EpeverField>> findFieldsByNameGroupByCharger(String titlePattern) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = new LinkedHashMap();

        for (MetricsSource ms : metricSourceList) {

            List<EpeverField> fields = EpeverFieldList.masterFieldList.stream().filter(f -> f.name.matches(titlePattern))
                .map( f-> EpeverField.createByAddr(ms.charger,f.addr) ).collect(Collectors.toList());
            fieldsByCharger.put(ms.charger, fields);
        }

        return fieldsByCharger;
    }


    public void readValues(Map<SolarCharger,List<EpeverField>> fieldsByCharger) throws ModbusIOException, EpeverException {

        for( Map.Entry<SolarCharger,List<EpeverField>> entry: fieldsByCharger.entrySet() ) {
            readValues(entry.getKey(),entry.getValue());
        }
    }


    public void readValues(SolarCharger charger, List<EpeverField> fields) throws ModbusIOException, EpeverException {

        charger.connect();

        try {
            EpeverFieldList.readValues(charger, fields);
        } finally {
            charger.disconnect();
        }
    }


    /**
     * Updates Spring Actuator framework with latest data from EPEver charge controllers
     */
    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private synchronized void updateStats() {

        for (MetricsSource ms : metricSourceList) {
            final int maxRetryCnt = 2;
            for (int attempt = 1; attempt <= maxRetryCnt; attempt++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    ms.charger.connect();
                    try {
                        ms.fields.readValues();
                    } finally {
                        ms.charger.disconnect();
                    }
                    ms.requestSucceeded(attempt);
                    break;
                } catch (Exception ex) {
                    if (attempt == maxRetryCnt) {
                        String msg = ex.getMessage();
                        logger.error("Scheduled EPEver charge controller read failed (attempted " + attempt + " times). ");
                        if (msg != null) {
                            logger.error(msg);
                        }
                        logger.error("Failed updating monitoring statistics.", ex);
                        ms.invalidate(ex);
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


    protected void init() {

        List<String> serialNames = EpeverSolarCharger.findSerialPortNames(conf.getEpeverSerialNameRegEx());

        for (String serialName : serialNames) {

            MetricsSource ms = new MetricsSource(meterRegistry);
            try {
                ms.charger.init(serialName);
                logger.info(serialName + " charge controller intialized");
                ms.charger.connect();
                try {
                    logger.info(ms.charger.getDeviceInfo().toString());
                } finally {
                    ms.charger.disconnect();
                }
                ms.init();
                metricSourceList.add(ms);
            } catch (Exception e) {
                logger.error("Failed initializing solar charger '" + serialName + "'");
            }
        }
    }

}
