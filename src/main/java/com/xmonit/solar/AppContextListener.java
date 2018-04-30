package com.xmonit.solar;

import com.xmonit.solar.arduino.ArduinoService;
import com.xmonit.solar.epever.EpeverService;
import com.xmonit.solar.epever.metrics.MetricsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppContextListener {

    @Autowired
    ArduinoService arduinoService;

    @Autowired
    EpeverService epeverService;


    @EventListener
    public void handleEvent(Object event) {

        //System.out.println("Event: " + event);

        if ( event instanceof ContextClosedEvent) {

            if ( arduinoService.isOpen() ) {
                arduinoService.close();
            }

            for( MetricsSource ms : epeverService.metricSourceList) {
                try {
                    ms.charger.disconnect();
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                }
            }

        } else if ( event instanceof ContextRefreshedEvent) {

        }
    }
}
