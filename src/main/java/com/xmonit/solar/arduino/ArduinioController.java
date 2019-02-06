package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.data.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("arduino")
@CrossOrigin()
public class ArduinioController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    ArduinoService arduinoService;

    private static final Logger logger = LoggerFactory.getLogger(ArduinioController.class);


    static class ArduinoInfo {
        public String tty;
        public Environment env;
    }

    CachedResponse<ArduinoInfo> cachedInfo = new CachedResponse<>("info");

    @GetMapping(value = "info", produces = "application/json")
    public ResponseEntity<ArduinoInfo> info(@RequestParam(value = "useCache", required = false, defaultValue = "true") boolean useCache) throws Exception {

        SerialCmd cmd = new SerialCmd(arduinoService);
        ArduinoInfo info = cachedInfo.getLatest();
        if ( !useCache || info==null ) {
            Environment env = cmd.doGetEnvironment();
            info = new ArduinoInfo();
            info.env = env;
            info.tty = arduinoService.serialPort.getPortName();
            this.cachedInfo.update(info);
        }
        return new ResponseEntity<ArduinoInfo>(info, HttpStatus.OK);
    }

}