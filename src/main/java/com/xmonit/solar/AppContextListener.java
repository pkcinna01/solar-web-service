package com.xmonit.solar;

import com.xmonit.solar.arduino.ArduinoService;
import com.xmonit.solar.arduino.SerialCmd;
import com.xmonit.solar.arduino.dao.Dao;
import com.xmonit.solar.arduino.dao.annotation.*;
import com.xmonit.solar.epever.EpeverService;
import com.xmonit.solar.epever.metrics.MetricsSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Slf4j
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
                            || name.matches("jvm[.](memory[.]used).*")
                            || name.matches( "system[.]temperature.*")) {
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
                log.warn(ex.getMessage());
            }

            String scanPackage = "com.xmonit.solar.arduino.dao";
            ClassPathScanningCandidateComponentProvider provider =
                    new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(ArduinoDao.class));

            for (BeanDefinition beanDef : provider.findCandidateComponents(scanPackage)) {
                try {
                    Class<?> daoClass = Class.forName(beanDef.getBeanClassName());
                    ArduinoDao daoAnnotation = daoClass.getAnnotation(ArduinoDao.class);
                    List<Dao.FieldMetaData> fields = new ArrayList<>();
                    for (Method method : daoClass.getMethods()) {
                        Class rtnClass = method.getReturnType();
                        if (SerialCmd.FieldAccessor.class.isAssignableFrom(rtnClass)) {
                            Class[] accessorTypes = {IntegerAccessor.class, DoubleAccessor.class,StringAccessor.class,BooleanAccessor.class,ChoiceAccessor.class,ConstraintAccessor.class};
                            for( Class accessorType : accessorTypes) {
                                Object fieldAccessor = method.getAnnotation(accessorType);
                                if ( fieldAccessor != null ) {
                                    Class fac = fieldAccessor.getClass();
                                    Method m = fac.getMethod("validationRegEx");
                                    String validationRegEx = (String) m.invoke(fieldAccessor);
                                    m = fac.getMethod("value");
                                    String value = (String) m.invoke(fieldAccessor);
                                    m = fac.getMethod("readOnly");
                                    boolean readOnly = (boolean) m.invoke(fieldAccessor);
                                    String type = accessorType.getSimpleName().replaceAll("Accessor$","");
                                    if (StringUtils.isEmpty(value)) {
                                        value = method.getName();
                                    }
                                    fields.add(new Dao.FieldMetaData(value, type, readOnly, validationRegEx));
                                    break;
                                }
                            }
                        }
                    }
                    Dao.metaDataDictionary.put(daoClass.getSimpleName().replaceAll("Dao$",""),fields);
                } catch (Exception ex) {
                    log.error("Failed reading Dao class meta data and field accessors",ex);
                }
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
