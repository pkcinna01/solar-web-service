package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.metrics.ArduinoGetResponseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

import static com.xmonit.solar.arduino.ArduinoSerialBus.NO_VALIDATION_REQ_ID;
import static org.junit.Assert.*;


public class ArduinoServiceTest {

    private static ObjectMapper mapper = new ObjectMapper();

    MeterRegistry registry;
    ArduinoService serialBus;

    AppConfig appConfig;

    ArduinoGetResponseMetrics metricsRespHandler;


    @Before
    public void initAll() {
        appConfig = new AppConfig();
        appConfig.cmd = "GET";
        appConfig.baudRate=38400;
        appConfig.commPortRegEx="ttyACM.*";
        registry = new SimpleMeterRegistry();
        metricsRespHandler = new ArduinoGetResponseMetrics(registry);

        serialBus = new ArduinoService(appConfig,metricsRespHandler);
        //serialBus.setCommPortName( "ttyACM0" );
    }

    @Test public void readUsbLiveData() throws Exception {

        String resp = serialBus.execute("GET", null, NO_VALIDATION_REQ_ID);
        System.out.println(resp);
    }

    @Test
    public void checkRegistryValues() throws Exception {

        JsonNode respNode = mapper.readTree(MockData.JSON_GET_RESP_STR);

        //serialBus.processResponse(respNode);

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
        //serialBus.processResponse(respNode);
        assertEquals(1,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);

        //serialBus.processResponse(MockData.JSON_ERROR_RESP_STR);
        assertEquals(Double.NaN,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);

        //serialBus.processResponse(respNode);
        assertEquals(1,registry.get("arduino.solar.fanMode").gauge().value(),0.0001);
    }


}