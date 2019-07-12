package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.device.PowerSwitchDao;
import com.xmonit.solar.arduino.data.device.PowerSwitch;
import com.xmonit.solar.arduino.serial.ArduinoSerialBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("arduino/powerSwitch")
@CrossOrigin()
public class PowerSwitchController extends DeviceController {
    //DomainDaoController<PowerSwitch, PowerSwitchDao>

	private static final Logger logger = LoggerFactory.getLogger(PowerSwitchController.class);

	public PowerSwitchDao createPowerSwitchDao(ArduinoSerialBus sb) {
		return new PowerSwitchDao(sb);
	}

    @PutMapping(value = "on/{arduinoId}/{id}/{on}", produces = "application/json")
    public PowerSwitch setOn(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable boolean on,
                             @RequestParam(required = false, defaultValue = "false") Boolean persist) throws ArduinoException {
        arduinoService.refreshMetrics(() -> { new PowerSwitchDao(getBusById(arduinoId)).on(id).set(on).saveIf(persist,on);} );
        return (PowerSwitch) getById(arduinoId, id, false);
    }

    @PutMapping(value = "relayPin/{arduinoId}/{id}/{pinNumber}", produces = "application/json")
    public PowerSwitch setRelayPin(@PathVariable Integer arduinoId, @PathVariable Integer id, @PathVariable Integer relayPin,
                                   @RequestParam(required = false, defaultValue = "false") Boolean persist) throws ArduinoException {
        new PowerSwitchDao(getBusById(arduinoId)).relayPin(id).set(relayPin).saveIf(persist,relayPin);
        return (PowerSwitch) getById(arduinoId, id, false);
    }

    @PutMapping(value = "relayOnSignal/{arduinoId}/{id}/{relayOnSignal}", produces = "application/json")
    public PowerSwitch setRelayOnSignal(@PathVariable Integer arduinoId, @PathVariable Integer id,
                                        @PathVariable com.xmonit.solar.arduino.data.LogicLevel relayOnSignal,
                                        @RequestParam(required = false, defaultValue = "false") Boolean persist) throws ArduinoException {
        new PowerSwitchDao(getBusById(arduinoId)).relayOnSignal(id).set(relayOnSignal).saveIf(persist,relayOnSignal);
        return (PowerSwitch) getById(arduinoId, id, false);
    }
}