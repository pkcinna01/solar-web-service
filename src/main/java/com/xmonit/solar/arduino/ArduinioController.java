package com.xmonit.solar.arduino;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("arduino")
@CrossOrigin()
public class ArduinioController extends AsyncTaskRunner {

    @Autowired
    ArduinoService arduinoService;

    private static final Logger logger = LoggerFactory.getLogger(ArduinioController.class);


    @PutMapping(value = "reload", produces = "application/json")
    public void reload() throws ArduinoException {
       arduinoService.reload();
    }

}