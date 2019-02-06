package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.dao.sensor.SensorDao;
import com.xmonit.solar.arduino.data.sensor.Sensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.Writer;

//import com.xmonit.solar.arduino.data.Fan;
//import com.xmonit.solar.arduino.data.Voltmeter;


@RestController()
@RequestMapping("arduino/sensor")
@CrossOrigin()
public class SensorController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    ArduinoService arduinoService;

    private static final Logger logger = LoggerFactory.getLogger(SensorController.class);

    CachedResponse<Sensor[]> cachedSensors = new CachedResponse<>("list");

    @GetMapping(value = "list", produces = "application/json")
    public ResponseEntity<Sensor[]> listSensors() throws Exception {
        HttpStatus status = HttpStatus.OK;
        Sensor[] sensors = {};
        try {
            SensorDao sensorDao = new SensorDao(arduinoService);
            sensors = sensorDao.list();
        } catch (ArduinoException ex) {
            int errorCode = ex.reasonCode.orElse(HttpStatus.INTERNAL_SERVER_ERROR.value());
            if ( errorCode < 0 ) {
                errorCode = Math.abs(errorCode);
            }
            if ( errorCode < 200 || errorCode >= 600 ) {
                logger.warn("Unmapped arduino exception error code: " + errorCode + ".");
                errorCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
            status = HttpStatus.valueOf(errorCode);
        }
        return new ResponseEntity<Sensor[]>(sensors,status);
    }


    public void index(Writer respWriter, HttpServletResponse resp) throws Exception {
        listSensors();
    }

}