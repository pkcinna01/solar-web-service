package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.sensor.SensorDao;
import com.xmonit.solar.arduino.data.sensor.Sensor;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("arduino/sensor")
@CrossOrigin()
public class SensorController extends DomainDaoController<Sensor,SensorDao> {

    private static final Logger logger = LoggerFactory.getLogger(SensorController.class);

    @Override
    public SensorDao createDao(ArduinoSerialBus sb) {
        return new SensorDao(sb);
    }



}