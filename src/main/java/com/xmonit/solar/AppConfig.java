package com.xmonit.solar;

import com.xmonit.solar.arduino.ArduinoConfig;
import com.xmonit.solar.epever.EpeverConfig;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class AppConfig implements ArduinoConfig, EpeverConfig {

    @Value("${arduino.usb.default.baudRate}")
    public Integer arduinoDefaultBaudRate;

    @Value("${arduino.usb.default.dataBits}")
    public Integer arduinoDefaultDataBits;

    @Value("${arduino.usb.default.parity}")
    public Integer arduinoDefaultParity;

    @Value("${arduino.usb.default.stopBits}")
    public Integer arduinoDefaultStopBits;

    @Value("${arduino.monitoring.updateIntervalMs}")
    public Integer arduinoUpdateIntervalMs;

    @Value("${arduino.monitoring.expiredMetricMs}")
    public Integer arduinoExpiredMetricMs;

    @Value("${arduino.usb.commPortRegEx}")
    public String commPortRegEx;

    @Value("${epever.usb.commPortRegEx}")
    public String epeverSerialNameRegEx;

    @Value("${epever.monitoring.updateIntervalMs}")
    public Integer epeverUpdateIntervalMs;

    @Value("${epever.monitoring.expiredMetricMs}")
    public Integer epeverExpiredMetricMs;

    @Value("${http.remoteHostRegEx}")
    public String remoteHostRegEx;

    public PortConfig getPortConfig(String hardwareDeviceId) {
        // just return same settings for all arduino devices for now...
        PortConfig portConfig = new PortConfig();
        portConfig.dataBits = arduinoDefaultDataBits;
        portConfig.baudRate = arduinoDefaultBaudRate;
        portConfig.parity = arduinoDefaultParity;
        portConfig.stopBits = arduinoDefaultStopBits;
        return portConfig;
    }
}
