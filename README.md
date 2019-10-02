# solar-web-service

## Synopsis

Application for Off Grid Solar System managing misc hardware via USB cables.  
1. Arduino ATMEGA2560 for managing fans, sensors, and power switches.
2. EPEver charge controllers using modbus protocol for monitoring and configuring.

From a high level, it is the backend service layer for the 
[solar-web-client](https://github.com/pkcinna01/solar-web-client) web interface.  It uses the 
[arduino-solar-serialbus](https://github.com/pkcinna01/arduino-solar-serialbus)
and
[epever-solar-modbus](https://github.com/pkcinna01/epever-solar-modbus)
java jar files to communicate with the devices connected with USB cables.

This is a Spring Boot web application that runs as a service on Linux on port 9202 by default.
It uses the Spring Actuator API to expose arduino data in Prometheus and Grafana.

## Motivation

Provides a bridge between solar equiment, Prometheus/Graphana, OpenHAB, and misc services that manage available power going to home appliances (primarily cheap low power HVAC equiment).
[Grafana example](https://snapshot.raintank.io/dashboard/snapshot/EAR5W7iO009fUdixSgTiXrLFSIlaiozB?orgId=2&kiosk)

## Installation

Maven and Java JDK 1.8+ are required.  Example build:

Build jar from project folder:
```
mvn clean package -Dmaven.test.skip
```
**Run as service:**

Use spring boot guide lines for installing spring standalone apps as a service.

From a high level:
```
1) sudo cp target/solar-web-service-1.0.SNAPSHOT.jar /etc/init.d/solar-web-service
2) chmod ugo+x /etc/init.d/solar-web-service
3) sudo update-rc.d solar-web-service defaults
4) sudo service solar-web-service start
5) tail -100f /var/log/solar-web-service.log
```

## Configuration

Refer to Spring Boot external property file management to override the default 
properties in src/main/resources/application.properties 
