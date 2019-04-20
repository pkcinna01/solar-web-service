package com.xmonit.solar.arduino;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmonit.solar.AppConfig;
import com.xmonit.solar.DevAppConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;


public class ArduinoServiceTest {

    private static ObjectMapper mapper = new ObjectMapper();

    MeterRegistry registry;
    ArduinoService arduinoService;

    AppConfig appConfig;

    ArduinoMetrics metrics;


    @Before
    public void initAll() throws ArduinoException {
        appConfig = new DevAppConfig();
        registry = new SimpleMeterRegistry();
        metrics = new ArduinoMetrics(appConfig,registry);

        arduinoService = new ArduinoService(appConfig,registry);
        //arduinoService.init();
    }

    @Test
    public void readUsbLiveData() throws Exception {

        long startMs = System.currentTimeMillis();
        String resp = arduinoService.serialBusGroup.values().iterator().next().execute("GET,SENSORS");
        System.out.println(resp);
        System.out.println("Elapsed seconds: " + (System.currentTimeMillis()-startMs)/1000.0);
    }

    @Test
    public void checkRegistryValues() throws Exception {

/*
        JsonNode respNode = mapper.readTree(MockData.JSON_GET_RESP_STR);

        assertEquals(2,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);
        assertEquals(61.16,registry.get("arduino.solar.tempSensor.temp").tag("device","Bench").tag("tempSensor","DHT").gauge().value(),0.0001);
        assertEquals(85.0,registry.get("arduino.solar.fan.offTemp").tag("device","Bench").tag("fan","Exhaust").gauge().value(),0.0001);

        try {
            registry.get("arduino.solar.fan.offTemp").tag("device", "Beach").tag("fan", "Exhaust").gauge().value();
            fail("Should not find a device named 'Beach'");
        } catch (Exception ex) {
            assertEquals(io.micrometer.core.instrument.search.MeterNotFoundException.class,ex.getClass());
        }

        ObjectNode mutableNode = (ObjectNode) respNode;
        mutableNode.put("fanMode",1);
        //arduinoService.processResponse(respNode);
        assertEquals(1,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);

        //arduinoService.processResponse(MockData.JSON_ERROR_RESP_STR);
        assertEquals(Double.NaN,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);

        //arduinoService.processResponse(respNode);
        assertEquals(1,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);
        */
    }


}