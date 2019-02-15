package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.data.sensor.Sensor;
import com.xmonit.solar.metrics.UpdateStatsTracker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Exposes Arduino data read from USB as monitoring metrics (default is
 * Prometheus)
 */
@Slf4j
@Component
public class ArduinoMetrics {

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
			if (sensors == null) {
				return Double.NaN;
			} else {
				return sensors[sensorIndex].getValue();
			}
		}
	}

	public String arduinoName;

	private MeterRegistry registry;

	private Sensor[] sensors;

	private AtomicInteger serialReadErrorCnt = new AtomicInteger();

	private AtomicInteger serialReadOk = new AtomicInteger();

	public UpdateStatsTracker updateStatsTracker = new UpdateStatsTracker("arduino");

	public ArduinoMetrics(MeterRegistry registry) {

		this.registry = registry;
	}

	public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

		Gauge.Builder<T> b = Gauge.builder("arduino.solar." + name, obj, f);

		if (tags != null) {
			b.tags(tags);
		}

		return b.register(registry);
	}

	private void initRegistry(Sensor[] sensors) {

		log.debug("Initializing monitoring metrics with " + sensors.length + " sensors.");
		this.sensors = sensors;
		updateStatsTracker.name = arduinoName + " arduino";

		for (int i = 0; i < sensors.length; i++) {
			final int sensorIndex = i;
			// List<Tag> tags = Collections.singletonList(new ImmutableTag("name",
			// sensor.name));
			String camelCaseName = StringUtils.uncapitalize(sensors[sensorIndex].name.replaceAll("[ -]", ""));
			Gauge.Builder<Supplier<Number>> b = Gauge.builder("arduino.solar." + camelCaseName,
					new SensorValueSupplier(this, i));
			b.register(registry);
		}

		List<Tag> tags = new LinkedList<>();
		tags.add(new ImmutableTag("arduinoName", arduinoName));

		registry.gauge("arduino.solar.serialReadErrorCnt", tags, serialReadErrorCnt);
		registry.gauge("arduino.solar.serialReadOk", tags, serialReadOk);

		registry.gauge("arduino.solar.serial.scheduled.requestCnt", tags, updateStatsTracker.cnt);
		registry.gauge("arduino.solar.serial.scheduled.attemptCnt", tags, updateStatsTracker.attemptCnt);
		registry.gauge("arduino.solar.serial.scheduled.successCnt", tags, updateStatsTracker.successCnt);
		registry.gauge("arduino.solar.serial.scheduled.successAttemptCnt", tags, updateStatsTracker.successAttemptCnt);

	}

	public void invalidate(Exception ex) {
		sensors = null;
	}

	public void update(Sensor[] sensors) throws Exception {
		try {
			if (this.sensors == null) {
				initRegistry(sensors);
			} else {
				this.sensors = sensors;
			}
			serialReadErrorCnt.set(0);
			serialReadOk.set(1);
		} catch (Exception ex) {
			invalidate(ex);
			log.error("Failed converting arduino JSON to monitoring metrics", ex);
		}
	}

}