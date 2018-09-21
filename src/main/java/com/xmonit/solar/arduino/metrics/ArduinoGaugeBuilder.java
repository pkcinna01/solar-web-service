package com.xmonit.solar.arduino.metrics;

import com.xmonit.solar.arduino.ArduinoSerialBus;
import com.xmonit.solar.arduino.data.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Binds Arduino response data with Spring Actuator monitoring registry.  The
 * data can be viewed from /actuator/prometheus endpoint.
 */
public class ArduinoGaugeBuilder {

    MeterRegistry registry;
    ArduinoSerialBus serialBus;


    public ArduinoGaugeBuilder(ArduinoSerialBus serialBus, MeterRegistry registry) {
        this.serialBus = serialBus;
        this.registry = registry;
    }

    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

        Gauge.Builder<T> b = Gauge.builder("arduino.solar." + name, obj, f)
                .tags(getCommonTags());

        if (tags != null) {
            b.tags(tags);
        }

        return b.register(registry);
    }

    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f) {

        return gauge(name, obj, f, null);
    }

    protected List<Tag> getCommonTags() {
        return Collections.singletonList(new ImmutableTag("commPort",
                serialBus == null ? "" : serialBus.getPortName() == null ? "" : serialBus.getPortName()));
    }

    void init(ArduinoGetResponse resp) {
        for (Sensor sensor : resp.sensors) {
            List<Tag> tags = Collections.singletonList(new ImmutableTag("name", sensor.name));
            String camelCaseName = StringUtils.uncapitalize(sensor.name.replaceAll("[ -]", ""));
            gauge( camelCaseName, sensor, Sensor::getValue, tags);
        }
    }
}
