# solar-web-service

## Synopsis

Application for Off Grid Solar System using EPEver Charge Controllers.  
1) Queries and updates an Arduino via USB.  The Arduino manages fans and temperatures.
It Also monitors power usage and voltages.
2) Queries and updates EPEver Charge Controllers via USB using modbus protocol.

From a high level, it is the backend service layer for the 
[solar-web-client](https://github.com/pkcinna01/solar-web-client) web interface.  It uses the 
[arduino-solar-serialbus](https://github.com/pkcinna01/arduino-solar-serialbus)
and
[epever-solar-modbus](https://github.com/pkcinna01/epever-solar-modbus)
java jar files to communicate with the devices connected with USB cables.

This is a Spring Boot web application that runs as a service on Linux on port 9202 by default.
It uses the Spring Actuator API to expose arduino data in Prometheus and Grafana.


**TO DO:**

1. Add a Docker compose project to run Prometheus,
Grafana, Java, Maven, Nginx, etc...

## Examples

**Get arduino metrics as JSON using cURL**

[(see arduino-solar-serialbus for url syntax)](https://github.com/pkcinna01/arduino-solar-serialbus) 
```
# View arduino status and configuration
curl -X POST http://localhost:9202/arduino/execute
```

**View supported commands:**
```
pkcinna@jax1:/opt/prometheus/bin$ curl -X POST http://localhost:9202/arduino/help
```

## Motivation

This project manages fans for cooling epever solar charge controllers 
and the structure they are housed in.  It provides hooks for external applications
to manage fans and temperatures or let the Arduino app do it.
  

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