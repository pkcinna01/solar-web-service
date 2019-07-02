package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.constraint.ConstraintDao;
import com.xmonit.solar.arduino.data.constraint.Constraint;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;


@RestController()
@RequestMapping("arduino/constraint")
@CrossOrigin()
public class ConstraintController extends DomainDaoController<Constraint, ConstraintDao> {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintController.class);

    @Override
    public ConstraintDao createDao(ArduinoSerialBus sb) {
        return new ConstraintDao(sb);
    }

    @PutMapping(value = "mode/{arduinoId}/{id}/{mode}", produces = "application/json")
    public Constraint setMode(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable String mode,
                              @RequestParam(required = false, defaultValue = "false") Boolean persist) throws ArduinoException {
        Constraint.Mode modeVal = Constraint.Mode.valueOf(mode);
        createDao(getBusById(arduinoId)).mode(id).set(modeVal).saveIf(persist,modeVal);
        return getById(arduinoId, id, false);
    }

    @PutMapping(value = "passed/{arduinoId}/{id}/{passed}", produces = "application/json")
    public Constraint setPassed(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable Boolean bPassed) throws ArduinoException {
        createDao(getBusById(arduinoId)).passed(id).set(bPassed);
        return getById(arduinoId, id, false);
    }
}