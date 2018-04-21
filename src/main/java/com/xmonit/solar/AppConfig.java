package com.xmonit.solar;

import com.xmonit.solar.arduino.ArduinoConfig;
import com.xmonit.solar.epever.EpeverConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class AppConfig implements ArduinoConfig, EpeverConfig {

    @Value("${arduino.usb.command}")
    public String cmd;

    @Value("${http.remoteHostRegEx}")
    public String remoteHostRegEx;

    @Value("${arduino.usb.commPortRegEx}")
    public String commPortRegEx;

    @Value("${arduino.usb.baudRate}")
    public Integer baudRate;

    @Value("${arduino.monitoring.updateIntervalMs}")
    public String arduinoUpdateIntervalMs;


    @Value("${epever.monitoring.updateIntervalMs}")
    public String epeverUpdateIntervalMs;

    @Value("${epever.usb.commPortRegEx}")
    public String epeverSerialNameRegEx;


}
