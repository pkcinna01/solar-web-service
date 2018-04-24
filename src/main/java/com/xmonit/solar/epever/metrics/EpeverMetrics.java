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
import java.util.function.ToDoubleFunction;


/**
 * Exposes EPEver solar charge controlled data read from USB as monitoring metrics (default is Prometheus)
 */
@Component
public class EpeverMetrics {


    private static final Logger logger = LoggerFactory.getLogger(EpeverMetrics.class);

    public UpdateStatsTracker updateStatsTracker;

    protected List<Tag> commonTags;

    private MeterRegistry registry;


    public EpeverMetrics(MeterRegistry registry) {

        this.registry = registry;
    }


    public <T> Gauge gauge(SolarCharger charger, String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

        Gauge.Builder<T> b = Gauge.builder("epever.solar." + name, obj, f);

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
    }


    protected List<Tag> getCommonTags(SolarCharger charger) throws EpeverException {
        return Arrays.asList(
                new ImmutableTag("commPort", charger.getSerialName()),
                new ImmutableTag("deviceModel", charger.getDeviceInfo().model));
    }

}