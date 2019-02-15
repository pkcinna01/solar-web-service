package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping(value = "fieldAccessors", produces = "application/json")
    public Map<String,List<Dao.FieldMetaData>> fieldAccessors() {
        return Dao.metaDataDictionary;
    }

    @GetMapping(value = "fieldAccessors/{type}", produces = "application/json")
    public List<Dao.FieldMetaData> fieldAccessors(@PathVariable String type) throws ArduinoException {
        return Dao.metaDataDictionary.get(type);
    }

}