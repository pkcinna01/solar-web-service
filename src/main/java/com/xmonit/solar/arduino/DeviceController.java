package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.device.DeviceDao;
import com.xmonit.solar.arduino.data.constraint.Constraint;
import com.xmonit.solar.arduino.data.device.Device;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("arduino/device")
@CrossOrigin()
public class DeviceController extends DomainDaoController<Device, DeviceDao> {

	private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

	@Override
	public DeviceDao createDao(ArduinoSerialBus sb) {
		return new DeviceDao(sb);
	}

	@PutMapping(value = "constraintMode/{arduinoId}/{id}/{mode}", produces = "application/json")
	public Device setOn(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable String mode) throws ArduinoException {
		createDao(getBusById(arduinoId)).mode(id).set(Constraint.Mode.valueOf(mode));
		return getById(arduinoId, id, false);
	}

	@PutMapping(value = "enabled/{arduinoId}/{id}/{enabled}", produces = "application/json")
	public Device setOn(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable boolean enabled,
						@RequestParam(required = false, defaultValue = "false") Boolean persist) throws ArduinoException {
		createDao(getBusById(arduinoId)).enabled(id).set(enabled).saveIf(persist,enabled);
		return getById(arduinoId, id, false);
	}
}