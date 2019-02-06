package com.xmonit.solar.arduino;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmonit.solar.arduino.data.sensor.Sensor;
import com.xmonit.solar.metrics.UpdateStatsTracker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;


/**
 * Exposes Arduino data read from USB as monitoring metrics (default is Prometheus)
 */
@Component
public class ArduinoMetrics {

    private static final Logger logger = LoggerFactory.getLogger(ArduinoMetrics.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    public UpdateStatsTracker updateStatsTracker = new UpdateStatsTracker("arduino");
    private Sensor[] sensors;
    private MeterRegistry registry;
    private AtomicInteger serialReadErrorCnt = new AtomicInteger();
    private AtomicInteger serialReadOk = new AtomicInteger();


    public ArduinoMetrics(MeterRegistry registry) {

        this.registry = registry;
    }


    public void update(String portName, Sensor[] sensors) {

        try {
            if (this.sensors == null) {
                initRegistry(portName, sensors);
            } else {
                this.sensors = sensors;
            }
            serialReadErrorCnt.set(0);
            serialReadOk.set(1);
        } catch (Exception ex) {
            invalidate(ex);
            logger.error("Failed converting arduino JSON to monitoring metrics");
            logger.error(ex.getMessage());
        }
    }

    public void invalidate(Exception ex) {
        sensors = null;
    }


    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

        Gauge.Builder<T> b = Gauge.builder("arduino.solar." + name, obj, f);

        if (tags != null) {
            b.tags(tags);
        }

        return b.register(registry);
    }

    static class SensorValueSupplier implements Supplier<Number> {

        ArduinoMetrics metrics;
        int sensorIndex;
        SensorValueSupplier(ArduinoMetrics metrics, int sensorIndex) {
            this.metrics = metrics;
            this.sensorIndex = sensorIndex;
        }

        @Override
        public Number get() {
            Sensor[] sensors = metrics.sensors;
            if ( sensors == null ) {
                return Double.NaN;
            } else {
                return sensors[sensorIndex].getValue();
            }
        }
    }

    private void initRegistry(String portName, Sensor[] sensors) {

        this.sensors = sensors;

        updateStatsTracker.name = portName + " arduino";

        for (int i = 0; i < sensors.length; i++ ) {
            final int sensorIndex = i;
            //List<Tag> tags = Collections.singletonList(new ImmutableTag("name", sensor.name));
            String camelCaseName = StringUtils.uncapitalize(sensors[sensorIndex].name.replaceAll("[ -]", ""));
            Gauge.Builder b = Gauge.builder("arduino.solar." + camelCaseName, new SensorValueSupplier(this,i));
            b.register(registry);
        }

        List<Tag> tags = Collections.singletonList(new ImmutableTag("commPort",portName));
        registry.gauge("arduino.solar.serialReadErrorCnt", tags, serialReadErrorCnt);
        registry.gauge("arduino.solar.serialReadOk", tags, serialReadOk);

        registry.gauge("arduino.solar.serial.scheduled.requestCnt", tags, updateStatsTracker.cnt);
        registry.gauge("arduino.solar.serial.scheduled.attemptCnt", tags, updateStatsTracker.attemptCnt);
        registry.gauge("arduino.solar.serial.scheduled.successCnt", tags, updateStatsTracker.successCnt);
        registry.gauge("arduino.solar.serial.scheduled.successAttemptCnt", tags, updateStatsTracker.successAttemptCnt);

    }


}