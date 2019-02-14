package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.capability.CapabilityDao;
import com.xmonit.solar.arduino.data.capability.Capability;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController()
@RequestMapping("arduino/capability")
@CrossOrigin()
public class CapabilityController extends DomainDaoController<Capability, CapabilityDao> {

    private static final Logger logger = LoggerFactory.getLogger(CapabilityController.class);

    @Override
    public CapabilityDao createDao(ArduinoSerialBus sb) {
        return new CapabilityDao(sb);
    }
}