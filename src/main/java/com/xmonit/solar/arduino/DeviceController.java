package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.device.DeviceDao;
import com.xmonit.solar.arduino.data.device.Device;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("arduino/device")
@CrossOrigin()
public class DeviceController extends DomainDaoController<Device, DeviceDao> {

	private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);

	@Override
	public DeviceDao createDao(ArduinoSerialBus sb) {
		return new DeviceDao(sb);
	}
}