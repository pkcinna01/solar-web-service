package com.xmonit.solar.epever;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.MetricsSource;
import com.xmonit.solar.metrics.MetricsWatchdog;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
@EnableScheduling
public class EpeverService {

    public List<MetricsSource> metricSourceList = new LinkedList();
    AppConfig conf;
    MeterRegistry meterRegistry;
    protected MetricsWatchdog metricsWatchdog = new MetricsWatchdog<EpeverSolarCharger>("EPEVER"){
        @Override
        public synchronized void attemptRecover(){
            log.info("Attempting to restart service from " + getName() + " watchdog");
            ProcessBuilder processBuilder = new ProcessBuilder();
            // Add via visudo: xmonit ALL=NOPASSWD:/bin/systemctl restart solar-web
            processBuilder.redirectErrorStream(true).command("bash", "-c", "sudo /bin/systemctl restart solar-web");
            try {
                Process process = processBuilder.start();
                log.info("Launched: " + String.join(" ",processBuilder.command()) );
                try (InputStream in = process.getInputStream();) {
                    byte[] bytes = new byte[2048];
                    int len;
                    while ((len = in.read(bytes)) != -1) {
                        System.out.write(bytes, 0, len);
                    }
                }
                if ( !process.isAlive() ) {
                    // should not get here since process shutdown usually takes a minute while OS decides to kill unresponsive service
                    log.error("Exit code: " + process.exitValue() );
                }
            } catch (Exception e) {
                log.error("Failed restarting service from " + getName() + " watchdog",e);
            }
        }
    };


    public EpeverService(AppConfig conf, MeterRegistry meterRegistry) throws SerialPortException {

        this.conf = conf;
        this.meterRegistry = meterRegistry;
        init();
    }

    public EpeverSolarCharger findCharger(Predicate<SolarCharger> filterOp) {
        Stream<EpeverSolarCharger> chargerStream = metricSourceList.stream().map(ms -> ms.charger);
        return chargerStream.filter(filterOp).findFirst().get();
    }

    public EpeverSolarCharger findChargerById(String id) {
        Predicate<SolarCharger> filterOp = (charger) -> {
            return id.equalsIgnoreCase(charger.getId());
        };
        return findCharger(filterOp);
    }

    public JsonNode asJson(EpeverFieldList fieldList, Function<EpeverField, ObjectNode> fieldToJsonFn) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        EpeverSolarCharger cc = fieldList.getSolarCharger();
        ObjectNode root = factory.objectNode();
        root.put("commPort", cc.getSerialName());
        root.put("model", cc.getDeviceInfo().getModel());
        root.put("id", cc.getId());
        ArrayNode fieldsNode = factory.arrayNode();
        fieldList.stream().forEach(f -> {
            fieldsNode.add(f.asJson());
        });
        root.set("fields", fieldsNode);
        return root;
    }

    public EpeverFieldList getCachedMetrics(String chargerId) throws Exception {
        return getCachedMetrics(chargerId, f -> true); // all metrics
    }

    public synchronized EpeverFieldList getCachedMetrics(String chargerId, Predicate<EpeverField> filterOp) throws Exception {
        MetricsSource metricsSource = metricSourceList.stream().filter(ms -> ms.charger.getId().equals(chargerId)).findFirst().get();
        if (metricsSource == null) {
            return null;
        }
        List<EpeverField> fields = metricsSource.fields.stream().filter(f -> !f.isRating() && filterOp.test(f)).collect(Collectors.toList());
        return new EpeverFieldList(metricsSource.charger, fields);
    }


    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private void refreshMetrics() {

        for (MetricsSource ms : metricSourceList) {
            metricsWatchdog.setStep(ms.charger, "Begin metrics refresh for " + ms.charger.getId());
            final int maxRetryCnt = 4;
            for (int attempt = 1; attempt <= maxRetryCnt; attempt++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    if (attempt > 1) {
                        log.info("Begining attempt #" + attempt);
                    }
                    metricsWatchdog.setStep(ms.charger, "Begin withConnection call: " + ms.charger.getId());
                    final int attemptCnt = attempt;
                    ms.charger.withConnection(() -> {
                        if (attemptCnt > 1) {
                            log.info("Reading field values...");
                        }
                        metricsWatchdog.setStep(ms.charger, "Begin field values update: " + ms.charger.getId());
                        ms.fields.readValues();
                    });
                    ms.requestSucceeded(attempt);
                    if (attempt > 1) {
                        log.info("Success on attempt #" + attempt);
                    }
                    metricsWatchdog.setStep(ms.charger, "Field values updated: " + ms.charger.getId());
                    metricsWatchdog.updateComplete();
                    break;
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    metricsWatchdog.setStep(ms.charger, "" + ex + " occured for " + ms.charger.getId());
                    if (attempt == maxRetryCnt) {
                        log.error("Scheduled EPEver charge controller read failed (attempted " + attempt + " times). ");
                        if (msg != null) {
                            log.error(msg);
                        }
                        log.error("Failed updating monitoring statistics.", ex);
                        ms.invalidate(ex);
                        try {
                            log.info("Reconnecting solar charger " + ms.charger.getId());
                            ms.charger.reconnect();
                        } catch ( Exception e ) {
                            log.warn(e.getMessage());
                        }
                    } else {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            log.error(e.getLocalizedMessage() + " (" + Thread.currentThread().getStackTrace()[1].toString() + ")");
                        }
                        log.warn("Scheduled EPEver charge controller read failed (attempted #" + attempt + ");");
                        log.warn("\t" + ex.getClass().getSimpleName() + ": " + msg);
                        if (ex.getCause() != null) {
                            log.warn("\t\t" + ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage());
                        }
                    }

                }
            }
        }
    }


    /**
     * Put daily summary in log
     */
    @Timed
    @Scheduled(cron = "5 0 0 1/1 * ?")
    private void dailySummary() {
        for (MetricsSource ms : metricSourceList) {
            ms.metrics.updateStatsTracker.logReliabiltyInfo();
            ms.metrics.updateStatsTracker.reset();
        }
    }


    protected synchronized void init() throws SerialPortException {

        log.info("Initializing EPEVER controllers");
        switch (conf.getEpeverSerialImpl().toLowerCase()) {
            case "jserialcomm":
            case "jsc":
                SerialUtils.setSerialPortFactory( new SerialPortFactoryJSerialComm());
                break;
            case "scream3r":
            case "jssc":
                SerialUtils.setSerialPortFactory( new SerialPortFactoryJSSC());
                break;
            //case "rxtx":
            //    SerialUtils.setSerialPortFactory( new SerialPortFactoryRXTX());
            //    break;
            case "purejavacomm":
            case "pjc":
            default:
                SerialUtils.setSerialPortFactory( new SerialPortFactoryPJC());
                break;
        }
        log.info("EPEVER ModBus Implementation: " + SerialUtils.getSerialPortFactory().getClass().getSimpleName());

        List<String> serialNames = EpeverSolarCharger.findSerialPortNames(conf.getEpeverSerialNameRegEx());

        for (String serialName : serialNames) {

            MetricsSource ms = new MetricsSource(meterRegistry);
            try {
                ms.charger.init(serialName);
                ms.charger.maxConnectionAgeMs = conf.epeverMaxConnectionAgeMs;
                log.info(serialName + " charge controller intialized");
                ms.charger.connect();
                log.info(ms.charger.getDeviceInfo().toString());
                ms.init(conf);
                metricSourceList.add(ms);
            } catch (Exception e) {
                log.error("Failed initializing solar charger '" + serialName + "'", e);
            }
        }
        metricsWatchdog.setMaxAgeMs(conf.epeverExpiredMetricMs);
        metricsWatchdog.start();
    }

    public void close() {

        log.info("Stopping EPEVER controllers");

        metricsWatchdog.stop();

        for (MetricsSource ms : metricSourceList) {
            try {
                ms.charger.disconnect();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void updateCachedMetrics(EpeverFieldList fieldList) {
        for (MetricsSource ms : metricSourceList) {
            if (ms.charger == fieldList.getSolarCharger()) {
                ms.fields.copyValuesFrom(fieldList);
                break;
            }
        }
    }

}
