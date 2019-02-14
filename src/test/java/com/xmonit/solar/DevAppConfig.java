package com.xmonit.solar;

import com.xmonit.solar.arduino.serial.ArduinoSerialPort;

public class DevAppConfig extends AppConfig {

    public DevAppConfig() {
        commPortRegEx = "ttyUSB0";
    }

    @Override
    public PortConfig getPortConfig(String devicedId) {

        PortConfig config = new PortConfig();
        config.baudRate = 115200;
        config.dataBits = ArduinoSerialPort.DATABITS_8;
        config.parity = ArduinoSerialPort.PARITY_NONE;
        config.stopBits = ArduinoSerialPort.STOPBITS_1;
        return config;
    }

}