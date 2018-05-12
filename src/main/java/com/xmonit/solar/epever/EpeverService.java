package com.xmonit.solar.epever;

import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.MetricsSource;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Service
@EnableScheduling
public class EpeverService {


    private static final Logger logger = LoggerFactory.getLogger(EpeverService.class);

    public static String getModel(SolarCharger charger) {
        try {
            return charger.getDeviceInfo().model;
        } catch (EpeverException ex) {
            logger.error("Failed getting solar charger device model",ex);
            return null;
        }
    }

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


    public Map<SolarCharger, List<EpeverField>> findFieldsByNameGroupByCharger(String titlePattern) throws Exception {
        return findFieldsGroupByCharger( f -> f.name.matches(titlePattern) );
    }

    public synchronized Map<SolarCharger, List<EpeverField>> findFieldsGroupByCharger(Predicate<EpeverField> filterOp) throws Exception {
       Map<SolarCharger, List<EpeverField>> fieldsByCharger = new LinkedHashMap();

        for (MetricsSource ms : metricSourceList) {

            List<EpeverField> fields = EpeverFieldList.masterFieldList.stream().filter(filterOp)
                .map( f-> EpeverField.createByAddr(ms.charger,f.addr) ).collect(Collectors.toList());
            fieldsByCharger.put(ms.charger, fields);
        }

        return fieldsByCharger;
    }


    public List<JsonNode> asJson(Map<SolarCharger,List<EpeverField>> fieldsByCharger ){
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> json = fieldsByCharger.entrySet().stream().map(e-> {
            ObjectNode root = factory.objectNode();
            root.put("commPort", e.getKey().getSerialName());
            root.put("model", getModel(e.getKey()));
            List<EpeverField> fields = e.getValue();
            ArrayNode fieldsNode = factory.arrayNode();
            e.getValue().stream().forEach(f->{ fieldsNode.add(f.asJson()); });
            root.put("fields",fieldsNode);
            return root;
        }).collect(Collectors.toList());
        return json;
    }


    public List<JsonNode> valuesAsJson(Map<SolarCharger,List<EpeverField>> fieldsByCharger ){
        JsonNodeFactory factory = JsonNodeFactory.instance;
        List<JsonNode> json = fieldsByCharger.entrySet().stream().map(e-> {
            ObjectNode root = factory.objectNode();
            SolarCharger charger = e.getKey();
            root.put("commPort", charger.getSerialName());
            root.put("model", EpeverService.getModel(charger));

            ArrayNode fieldsNode = factory.arrayNode();
            e.getValue().stream().forEach(f->{
                ObjectNode n = f.valueAsJson();
                fieldsNode.add(n);
            });
            root.put("fields",fieldsNode);
            return root;
        }).collect(Collectors.toList());
        return json;
    }

    public void readValues(Map<SolarCharger,List<EpeverField>> fieldsByCharger) throws Exception {

        for( Map.Entry<SolarCharger,List<EpeverField>> entry: fieldsByCharger.entrySet() ) {
            readValues(entry.getKey(),entry.getValue());
        }
    }


    public synchronized void readValues(SolarCharger charger, List<EpeverField> fields) throws Exception {

        charger.withConnection( () -> EpeverFieldList.readValues(charger, fields) );
    }

    public Map<SolarCharger,List<EpeverField>> getCachedMetrics(String commPort, String model) throws Exception {
        return getCachedMetrics(commPort, model, f -> true); // all metrics
    }

    public synchronized  Map<SolarCharger,List<EpeverField>> getCachedMetrics(String commPort, String model, Predicate<EpeverField> filterOp) throws Exception {
        Map<SolarCharger, List<EpeverField>> fieldsByCharger = new LinkedHashMap();
        for (MetricsSource ms : metricSourceList) {
            boolean bCommPortOk = StringUtils.isEmpty(commPort) || commPort.equalsIgnoreCase(ms.charger.getSerialName());
            boolean bModelOk =  StringUtils.isEmpty(model) || model.equalsIgnoreCase(ms.charger.getDeviceInfo().model);
            if ( bCommPortOk && bModelOk ) {
                fieldsByCharger.put(ms.charger, ms.fields.stream().filter(f -> !f.isRating() && filterOp.test(f)).collect(Collectors.toList()));
            }
        }
        return fieldsByCharger;
    }

    /**
     * Updates Spring Actuator framework with latest data from EPEver charge controllers
     */
    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private synchronized void refreshMetrics() {

        for (MetricsSource ms : metricSourceList) {
            final int maxRetryCnt = 3;
            for (int attempt = 1; attempt <= maxRetryCnt; attempt++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    ms.charger.withConnection( () -> ms.fields.readValues() );
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
                    } else {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            logger.error(e.getLocalizedMessage() + " (" + Thread.currentThread().getStackTrace()[1].toString() + ")" );
                        }
                        logger.warn("Scheduled EPEver charge controller read failed (attempted #" + attempt + "). ");
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


    protected synchronized void init() {

        List<String> serialNames = EpeverSolarCharger.findSerialPortNames(conf.getEpeverSerialNameRegEx());

        for (String serialName : serialNames) {

            MetricsSource ms = new MetricsSource(meterRegistry);
            try {
                ms.charger.init(serialName);
                logger.info(serialName + " charge controller intialized");
                ms.charger.withConnection(()->logger.info(ms.charger.getDeviceInfo().toString()));
                ms.init();
                metricSourceList.add(ms);
            } catch (Exception e) {
                logger.error("Failed initializing solar charger '" + serialName + "'");
            }
        }
    }

    /**
     * If some request read data from charger controller, update the metrics used for monitoring so
     * it reflects the latest values too
     * @param fieldsByCharger
     */
    public void updateCachedMetrics(Map<SolarCharger,List<EpeverField>> fieldsByCharger) {
        for (MetricsSource ms : metricSourceList) {
            fieldsByCharger.entrySet().stream().filter( entry -> ms.charger == entry.getKey() ).findFirst().ifPresent( e -> ms.fields.updateValues(e.getValue()) );
        }
    }

    public List<EpeverField> findFieldsMatching(String modelPattern, String commPortPattern, String namePattern) throws EpeverException {
        List<EpeverField> fields = new LinkedList<>();
        for( MetricsSource ms : metricSourceList) {
            if ( ms.charger.getDeviceInfo().model.matches(modelPattern)
                    && ms.charger.getSerialName().matches(commPortPattern)){
                    EpeverFieldList.masterFieldList.stream().filter(f->f.name.matches(namePattern))
                            .map( f-> EpeverField.createByAddr(ms.charger,f.addr) ).forEach(fields::add);
            }
        }
        return fields;
    }
}
