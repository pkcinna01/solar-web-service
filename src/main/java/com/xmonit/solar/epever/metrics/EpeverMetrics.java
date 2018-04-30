package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.epever.EpeverException;
import com.xmonit.solar.epever.SolarCharger;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.metrics.UpdateStatsTracker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;


/**
 * Exposes EPEver solar charge controlled data read from USB as monitoring metrics (default is Prometheus)
 */
@Component
public class EpeverMetrics {


    private static final Logger logger = LoggerFactory.getLogger(EpeverMetrics.class);

    public UpdateStatsTracker updateStatsTracker;

    public AtomicInteger serialReadErrorCnt = new AtomicInteger();
    public AtomicInteger serialReadOk = new AtomicInteger();

    protected List<Tag> commonTags;

    private MeterRegistry registry;

    String strMetricPrefix = "solar.charger.";


    public EpeverMetrics(MeterRegistry registry) {

        this.registry = registry;
    }


    public <T> Gauge gauge(SolarCharger charger, String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

        Gauge.Builder<T> b = Gauge.builder(strMetricPrefix + name, obj, f);

        if (tags != null) {
            b.tags(tags);
        }

        return b.register(registry);
    }


    public void init(SolarCharger charger, EpeverFieldList fields) throws EpeverException {
        updateStatsTracker = new UpdateStatsTracker(charger.getSerialName() + " (" + charger.getDeviceInfo().model + ")");
        commonTags = getCommonTags(charger);
        for (EpeverField field : fields) {
            gauge(charger, field.getCamelCaseName(), field, EpeverField::doubleValue, commonTags);
        }
        registry.gauge(strMetricPrefix+"serialReadErrorCnt", commonTags, serialReadErrorCnt);
        registry.gauge(strMetricPrefix+"serialReadOk", commonTags, serialReadOk);

        registry.gauge(strMetricPrefix+"serial.scheduled.requestCnt", commonTags, updateStatsTracker.cnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.attemptCnt", commonTags, updateStatsTracker.attemptCnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.successCnt", commonTags, updateStatsTracker.successCnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.successAttemptCnt", commonTags, updateStatsTracker.successAttemptCnt);
    }


    protected List<Tag> getCommonTags(SolarCharger charger) throws EpeverException {
        return Arrays.asList(
                new ImmutableTag("port", charger.getSerialName()),
                new ImmutableTag("model", charger.getDeviceInfo().model));
    }

}