package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.AppConfig;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


/**
 * Exposes EPEver solar charge controlled data read from USB as monitoring metrics (default is Prometheus)
 */
@Component
public class EpeverMetrics {


    private static final Logger logger = LoggerFactory.getLogger(EpeverMetrics.class);

    class ValueSupplier implements Supplier<Number> {

        EpeverField field;

        public ValueSupplier(EpeverField field) {
            this.field = field;
        }

        @Override
        public Number get() {
            if (field == null || isExpired() ) {
                return Double.NaN;
            } else {
                return field.doubleValue();
            }
        }

        protected boolean isExpired() {
            return (System.currentTimeMillis() - updateTimeMs) > expiredMetricMs;
        }
    }

    public UpdateStatsTracker updateStatsTracker;

    public AtomicInteger serialReadErrorCnt = new AtomicInteger();
    public AtomicInteger serialReadOk = new AtomicInteger();

    private MeterRegistry registry;

    String strMetricPrefix = "solar.charger.";

    protected long updateTimeMs, expiredMetricMs = 30000;

    public EpeverMetrics(MeterRegistry registry) {

        this.registry = registry;
    }


    //public <T> Gauge gauge(SolarCharger charger, String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {
    public <T> Gauge gauge(SolarCharger charger, String name, EpeverField field, List<Tag> tags) {
        ValueSupplier valueSupplier = new ValueSupplier(field);
        Gauge.Builder<Supplier<Number>> b = Gauge.builder(strMetricPrefix + name, valueSupplier);

        if (tags != null) {
            b.tags(tags);
        }

        return b.register(registry);
    }


    public void init(AppConfig conf, SolarCharger charger, EpeverFieldList fields) throws EpeverException {
        expiredMetricMs = conf.epeverExpiredMetricMs;
        updateStatsTracker = new UpdateStatsTracker(charger.getSerialName() + " (" + charger.getDeviceInfo().model + ")");

        List<Tag> commonFieldTags = Collections.singletonList(new ImmutableTag("model", charger.getDeviceInfo().model));
        for (EpeverField field : fields) {
            gauge(charger, field.getCamelCaseName(), field, commonFieldTags);
        }

        List<Tag> commonTags = Arrays.asList(
                new ImmutableTag("port", charger.getSerialName()),
                new ImmutableTag("model", charger.getDeviceInfo().model));
        registry.gauge(strMetricPrefix+"serialReadErrorCnt", commonTags, serialReadErrorCnt);
        registry.gauge(strMetricPrefix+"serialReadOk", commonTags, serialReadOk);

        registry.gauge(strMetricPrefix+"serial.scheduled.requestCnt", commonTags, updateStatsTracker.cnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.attemptCnt", commonTags, updateStatsTracker.attemptCnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.successCnt", commonTags, updateStatsTracker.successCnt);
        registry.gauge(strMetricPrefix+"serial.scheduled.successAttemptCnt", commonTags, updateStatsTracker.successAttemptCnt);
    }

}