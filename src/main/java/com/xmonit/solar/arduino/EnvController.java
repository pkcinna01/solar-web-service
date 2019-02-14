package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.EnvDao;
import com.xmonit.solar.arduino.data.Environment;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("arduino/env")
@CrossOrigin()
public class EnvController extends SingletonDaoController<Environment, EnvDao> {

    @Override
    public EnvDao createDao(ArduinoSerialBus sb) {
        return new EnvDao(sb);
    }
}