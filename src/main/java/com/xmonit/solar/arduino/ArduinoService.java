package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.dao.sensor.SensorDao;
import com.xmonit.solar.arduino.data.sensor.Sensor;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import com.xmonit.solar.arduino.serial.ArduinoSerialBusGroup;
import com.xmonit.solar.arduino.serial.PJCArduinoSerialPort;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@EnableScheduling
public class ArduinoService {

	private static final Logger logger = LoggerFactory.getLogger(ArduinoService.class);

	public AppConfig conf;

	MeterRegistry meterRegistry;

	protected List<ArduinoMetrics> metricsList = new ArrayList<>();

	protected ArduinoSerialBusGroup serialBusGroup = new ArduinoSerialBusGroup();

	public ArduinoService(AppConfig conf, MeterRegistry meterRegistry) throws ArduinoException {

		this.conf = conf;
		this.meterRegistry = meterRegistry;
		init();
	}

	public void close() {
		serialBusGroup.close();
	}

	/**
	 * Put daily summary in log
	 */
	@Timed
	@Scheduled(cron = "0 0 0 1/1 * ?")
	private void dailySummary() {
		for (ArduinoMetrics arduinoMetrics : metricsList) {
			arduinoMetrics.updateStatsTracker.logReliabiltyInfo();
			arduinoMetrics.updateStatsTracker.reset();
		}
	}

	public ArduinoSerialBus getBusByName(String arduinoName) {
		return serialBusGroup.getByName(arduinoName);
	}

	protected synchronized void init() throws ArduinoException {

		serialBusGroup.init(PJCArduinoSerialPort.class, conf.commPortRegEx, conf);
		//serialBusGroup.init(JSCArduinoSerialPort.class, conf.commPortRegEx, conf);

		for (ArduinoSerialBus bus : serialBusGroup.values()) {
			try {
				ArduinoMetrics metrics = new ArduinoMetrics(meterRegistry);
				metrics.arduinoName = bus.name;
				metrics.arduinoId = bus.id;
				logger.info(metrics.arduinoName + " arduino intialized using " + bus.getPortName());
				metricsList.add(metrics);
			} catch (Exception e) {
				logger.error("Failed initializing arduino '" + bus.name + "'");
			}
		}
	}

	public void reload() throws ArduinoException {
		serialBusGroup.reload();
	}

	/**
	 * Updates Spring Actuator framework with latest Arduino data (for monitoring
	 * systems)
	 */
	@Timed
	@Scheduled(fixedDelayString = "${arduino.monitoring.updateIntervalMs}")
	private void updateMonitoringMetrics() {
		final int maxRetryCnt = 4;

		for (ArduinoMetrics arduinoMetrics : metricsList) {
			arduinoMetrics.updateStatsTracker.incrementCnt();
			String arduinoName = arduinoMetrics.arduinoName;
			ArduinoSerialBus serialBus = serialBusGroup.getByName(arduinoName);
			for (int i = 1; i <= maxRetryCnt; i++) {
				try {
					arduinoMetrics.updateStatsTracker.incrementAttemptCnt();
					SensorDao sensorDao = new SensorDao(serialBus);
					boolean bVerbose = arduinoMetrics.getSensors().isEmpty();
					Sensor[] sensors = sensorDao.list(bVerbose);
					arduinoMetrics.update(sensors);
					arduinoMetrics.updateStatsTracker.succeeded(i);
					break;
				} catch (Exception ex) {
					if (i == maxRetryCnt) {
						String msg = ex.getMessage();
						logger.error("Scheduled GET failed (attempted " + i + " times). ");
						if (msg != null) {
							logger.error(msg);
						}
						logger.error("Failed updating monitoring statistics.", ex);
						arduinoMetrics.invalidate(ex);
					}
				}
			}
		}
	}

	public Collection<Sensor> getCachedMetrics(Integer arduinoId) {
		return metricsList.stream().filter( metrics -> metrics.arduinoId == arduinoId ).findAny().get().getSensors();
	}
}
