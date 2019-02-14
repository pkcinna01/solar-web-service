package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.constraint.ConstraintDao;
import com.xmonit.solar.arduino.data.constraint.Constraint;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController()
@RequestMapping("arduino/constraint")
@CrossOrigin()
public class ConstraintController extends DomainDaoController<Constraint, ConstraintDao> {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintController.class);

    @Override
    public ConstraintDao createDao(ArduinoSerialBus sb) {
        return new ConstraintDao(sb);
    }
}