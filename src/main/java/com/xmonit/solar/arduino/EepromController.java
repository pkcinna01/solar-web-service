package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.EepromDao;
import com.xmonit.solar.arduino.data.Eeprom;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("arduino/eeprom")
@CrossOrigin()
public class EepromController extends SingletonDaoController<Eeprom, EepromDao> {

	@Override
	public EepromDao createDao(ArduinoSerialBus sb) {
		return new EepromDao(sb);
	}
}