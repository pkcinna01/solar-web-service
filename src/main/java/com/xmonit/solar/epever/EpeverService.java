package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.MetricsSource;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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


    public JsonNode asJson(EpeverFieldList fieldList, Function<EpeverField,ObjectNode> fieldToJsonFn){
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        EpeverSolarCharger cc = fieldList.getSolarCharger();
        ObjectNode root = factory.objectNode();
        root.put("commPort", cc.getSerialName());
        root.put("model", cc.getDeviceInfo().getModel());
        root.put("id", cc.getId());
        ArrayNode fieldsNode = factory.arrayNode();
        fieldList.stream().forEach(f->{ fieldsNode.add(f.asJson()); });
        root.put("fields",fieldsNode);
        return root;
    }


    /*
    public List<JsonNode> asJson(List<EpeverFieldList> fieldLists ){
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> json = fieldLists.stream().map(fieldList-> asJson(fieldList,EpeverField::asJson)).collect(Collectors.toList());
        return json;
    }


    public List<JsonNode> valuesAsJson(List<EpeverFieldList> fieldLists ){
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> json = fieldLists.stream().map(fieldList-> asJson(fieldList,EpeverField::valueAsJson)).collect(Collectors.toList());
        return json;
    }*/


    public EpeverFieldList getCachedMetrics(String chargerId) throws Exception {
        return getCachedMetrics(chargerId, f -> true); // all metrics
    }


    public synchronized EpeverFieldList getCachedMetrics(String chargerId, Predicate<EpeverField> filterOp) throws Exception {
        MetricsSource metricsSource = metricSourceList.stream().filter(ms->ms.charger.getId().equals(chargerId)).findFirst().get();
        if ( metricsSource == null ) {
            return null;
        }
        List<EpeverField> fields = metricsSource.fields.stream().filter(f -> !f.isRating() && filterOp.test(f)).collect(Collectors.toList());
        return new EpeverFieldList(metricsSource.charger,fields);
    }


    /**
     * Updates Spring Actuator framework with latest data from EPEver charge controllers
     */
    @Timed
    @Scheduled(fixedDelayString = "${epever.monitoring.updateIntervalMs}")
    private synchronized void refreshMetrics() {

        for (MetricsSource ms : metricSourceList) {
            final int maxRetryCnt = 6;
            for (int attempt = 1; attempt <= maxRetryCnt; attempt++) {
                ms.metrics.updateStatsTracker.incrementCnt();
                try {
                    ms.metrics.updateStatsTracker.incrementAttemptCnt();
                    ms.charger.withConnection( () -> ms.fields.readValues() );
                    ms.requestSucceeded(attempt);
                    break;
                } catch (Exception ex) {
                    String msg = ex.getMessage();
                    if (attempt == maxRetryCnt) {
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
                        logger.warn("Scheduled EPEver charge controller read failed (attempted #" + attempt + ");");
                        logger.warn("\t" + ex.getClass().getSimpleName() + ": " + msg);
                        if ( ex.getCause() != null ) {
                            logger.warn("\t\t" + ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage());
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


    public void updateCachedMetrics(EpeverFieldList fieldList) {
        for (MetricsSource ms : metricSourceList) {
            if ( ms.charger == fieldList.getSolarCharger() ) {
                ms.fields.copyValuesFrom(fieldList);
                break;
            }
        }
    }

}
