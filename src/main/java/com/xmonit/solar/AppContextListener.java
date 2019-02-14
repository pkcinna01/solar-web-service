package com.xmonit.solar;

import com.xmonit.solar.arduino.ArduinoService;
import com.xmonit.solar.epever.EpeverService;
import com.xmonit.solar.epever.metrics.MetricsSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Scanner;


@Component
public class AppContextListener {

    class TemperatureReader {

        public double getValue() {
            try {
                String strMilliCelcius = new Scanner(new File("/sys/class/thermal/thermal_zone0/temp")).useDelimiter("\\Z").next();
                return Double.parseDouble(strMilliCelcius) / 1000.0;
            } catch (Exception ex) {
                return Double.NaN;
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(AppContextListener.class);

    @Autowired
    ArduinoService arduinoService;

    @Autowired
    EpeverService epeverService;

    @Autowired
    MeterRegistry meterRegistry;

    TemperatureReader temperatureReader = new TemperatureReader();


    @Bean
    MeterRegistryCustomizer<MeterRegistry> filteredMetricsCustomizer() {
        return registry -> registry.config().meterFilter(
                MeterFilter.deny(iff -> {
                    String name = iff.getName();
                    if (name.matches("(solar[.]charger|arduino[.]).*")
                            || (name.startsWith("logback.events") && iff.getTag("level").matches("(error|warn)"))
                            || name.matches("jvm[.](memory[.]used).*")) {
                        return false;
                    } else {
                        //System.out.println("denying: " + name);
                    }
                    return true;
                }));
    }


    @EventListener
    public void handleEvent(Object event) {

        if (event instanceof ApplicationStartedEvent) {

            try {

                Gauge.Builder<TemperatureReader> b = Gauge.builder("system.temperature.celcius",
                        temperatureReader, TemperatureReader::getValue);

                b.register(meterRegistry);
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }

        } else if (event instanceof ContextClosedEvent) {

            arduinoService.close();

            for (MetricsSource ms : epeverService.metricSourceList) {
                try {
                    ms.charger.disconnect();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

        } else if (event instanceof ContextRefreshedEvent) {

        }
    }
}
