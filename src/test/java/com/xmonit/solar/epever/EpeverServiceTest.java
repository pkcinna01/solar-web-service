package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverFieldList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class EpeverServiceTest {

    private static ObjectMapper mapper = new ObjectMapper();

    MeterRegistry registry;
    EpeverService epeverService;
    AppConfig appConfig;


    @Before
    public void initAll() {
        appConfig = new AppConfig();
        registry = new SimpleMeterRegistry();
        //serialBus = new ArduinoService(appConfig,metricsRespHandler);
        //serialBus.setCommPortName( "ttyACM999" );
    }


    @Test
    public void listStatusDataWithNewMasterModbus() throws Exception {

        Thread t = new Thread() {
            public void run() {
                System.out.println("running...");
                String[] commPortNames = new String[]{ "ttyXRUSB0", "ttyXRUSB1", "ttyXRUSB0", "ttyXRUSB1" };
                try {
                    for (String commPortName: commPortNames) {
                        EpeverSolarCharger solarCharger = new EpeverSolarCharger();
                        solarCharger.init(commPortName);
                        solarCharger.connect();
                        System.out.println("************ " + commPortName + " ********************");
                        System.out.println(solarCharger.getDeviceInfo());
                        EpeverFieldList fields = EpeverFieldList.createInputRegisterBackedFields(solarCharger);
                        //fields.get(0).readValue();
                        //System.out.println(fields.get(0).name + ": " + fields.get(0));
                        fields.readValues();
                        fields.forEach(f -> System.out.println(f.name + ": " + f.toString()));
                        solarCharger.disconnect();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                System.out.println("done.");
            }
        };
        t.start();
        t.join();

    }

    @Test
    public void listStatusDataReuseMasterModbus() throws Exception {

        Thread t = new Thread() {
            public void run() {
                System.out.println("running...");
                String[] commPortNames = new String[]{ "ttyXRUSB0", "ttyXRUSB1" };
                try {
                    EpeverSolarCharger solarCharger = new EpeverSolarCharger();
                    for (String commPortName: commPortNames) {
                        solarCharger.init(commPortName);
                        solarCharger.connect();
                        System.out.println("========================= " + commPortName + " =============================");
                        //solarCharger.getDeviceInfo().forEach(line -> System.out.println(line));
                        for ( int i = 0; i < 2; i++) {
                            System.out.println("************ " + commPortName + " " + i + " ********************");
                            EpeverFieldList fields = EpeverFieldList.createInputRegisterBackedFields(solarCharger);
                            fields.readValues();
                            fields.forEach(f -> System.out.println(f.name + ": " + f.toString()));
                        }
                        solarCharger.disconnect();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                System.out.println("done.");
            }
        };
        t.start();
        t.join();

    }


}