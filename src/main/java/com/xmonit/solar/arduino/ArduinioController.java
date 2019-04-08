package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.dao.Dao;
import com.xmonit.solar.arduino.data.sensor.Sensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

    static class ArduinoMetric {
        public Integer id;
        public String name;
        public Double value;
        public String type;
        public ArduinoMetric(Integer id, String name, Double value, String type) {
            this.id = id;
            this.name = name;
            this.value = value;
            this.type = type;
        }
    }

    static class SensorMetricsHolder {
        public String arduinoName;
        public Integer arduinoId;
        public Collection<Sensor> sensors;
    }

    //Get cached arduino name, id, and metrics for all connected arduinos (allows web dashboard to load faster)
    @GetMapping(value="sensorMetrics", produces="application/json")
    @ResponseBody
    public Collection<SensorMetricsHolder> sensorMetrics() throws Exception {
        List<SensorMetricsHolder> metricsHolderList = new LinkedList<>();
        for( ArduinoMetrics metrics : arduinoService.metricsList ) {
            SensorMetricsHolder metricsHolder = new SensorMetricsHolder();
            metricsHolder.arduinoId = metrics.arduinoId;
            metricsHolder.arduinoName = metrics.arduinoName;
            metricsHolder.sensors = metrics.getSensors();
            metricsHolderList.add(metricsHolder);
        }
        return metricsHolderList;
    }

    @GetMapping(value="metrics/{arduinoId}", produces="application/json")
    @ResponseBody
    public Collection<ArduinoMetric> metrics(@PathVariable(value = "arduinoId", required = true) Integer arduinoId ) throws Exception {

        Collection<Sensor> sensors = arduinoService.getCachedMetrics(arduinoId);
        if ( sensors == null ) {
            return Collections.emptyList();
        } else {
            return sensors.stream().map(
                    sensor -> new ArduinoMetric(sensor.id, sensor.name, sensor.getValue(), sensor.type)).collect(Collectors.toList()
            );
        }
    }

}