package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.Dao;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Collection;

abstract public class DaoController<DataT, DaoT extends Dao> extends AsyncTaskRunner {

	private static final Logger logger = LoggerFactory.getLogger(DaoController.class);

	@Autowired
	ArduinoService arduinoService;

	abstract public DaoT createDao(ArduinoSerialBus sb);

	public ArduinoSerialBus getBus(String name) throws ArduinoException {
		ArduinoSerialBus bus = arduinoService.serialBusGroup.get(name);
		if (bus == null) {
			throw new RestException("No serial bus assigned to an arduino named: " + name, HttpStatus.NOT_FOUND);
		}
		return bus;
	}

	public Collection<ArduinoSerialBus> getBuses() {
		return arduinoService.serialBusGroup.values();
	}



}