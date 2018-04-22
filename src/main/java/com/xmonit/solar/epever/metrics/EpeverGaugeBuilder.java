package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.epever.EpeverSolarCharger;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;


/**
 * Binds Arduino response data with Spring Actuator monitoring registry.  The
 * data can be viewed from /actuator/prometheus endpoint.
 */
public class EpeverGaugeBuilder {

    MeterRegistry registry;
    EpeverSolarCharger charger;

    public EpeverGaugeBuilder(EpeverSolarCharger charger, MeterRegistry registry) {
        this.registry = registry;
        this.charger = charger;
    }

    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f, List<Tag> tags) {

        Gauge.Builder<T> b = Gauge.builder("epever.solar." + name, obj, f)
                .tags(getCommonTags());

        if (tags != null) {
            b.tags(tags);
        }

        return b.register(registry);
    }

    public <T> Gauge gauge(String name, T obj, ToDoubleFunction<T> f) {

        return gauge(name, obj, f, null);
    }

    protected List<Tag> getCommonTags() {
        return Collections.singletonList(new ImmutableTag("commPort", charger.getSerialName()));
    }

    /*void init(ArduinoGetResponse resp) {

        gauge("fanMode", resp, ArduinoGetResponse::getFanModeAsDouble);
        gauge("respCode", resp, ArduinoGetResponse::getRespCodeAsDouble);

        for (PowerMeter power : resp.powerMeters ) {
            List<Tag> tags = Collections.singletonList(new ImmutableTag("powerMeter", power.name));

            gauge("watts", power, PowerMeter::getWattsAsDouble, tags);

            gauge("shunt.amps", power.current, Shunt::getAmpsAsDouble, tags);
            gauge("shunt.ratedAmps", power.current, Shunt::getRatedAmpsAsDouble, tags);
            gauge("shunt.ratedMilliVolts", power.current, Shunt::getRatedMilliVoltsAsDouble, tags);

            gauge("voltage.volts", power.voltage, Voltmeter::getVoltsAsDouble, tags);
            gauge("voltage.analogPin", power.voltage, Voltmeter::getAnalogPinAsDouble, tags);
            gauge("voltage.assignedVcc", power.voltage, Voltmeter::getAssignedVccAsDouble, tags);
            gauge("voltage.assignedR1", power.voltage, Voltmeter::getAssignedR1AsDouble, tags);
            gauge("voltage.assignedR2", power.voltage, Voltmeter::getAssignedR2AsDouble, tags);
        }

        for (Device device : resp.devices) {
            for (Fan fan : device.fans) {
                List<Tag> tags = Arrays.asList(
                        new ImmutableTag("device", device.name),
                        new ImmutableTag("fan", fan.name));
                gauge("fan.onTemp", fan, Fan::getOnTempAsDouble, tags);
                gauge("fan.offTemp", fan, Fan::getOffTempAsDouble, tags);
                gauge("fan.on", fan, Fan::getRelayValueAsDouble, tags);
            }
            for (TempSensor tempSensor : device.tempSensors) {
                List<Tag> tags = Arrays.asList(
                        new ImmutableTag("device", device.name),
                        new ImmutableTag("tempSensor", tempSensor.name));
                gauge("tempSensor.temp", tempSensor, TempSensor::getTempAsDouble, tags);
                gauge("tempSensor.humidity", tempSensor, TempSensor::getHumidityAsDouble, tags);
                gauge("tempSensor.heatIndex", tempSensor, TempSensor::getHeatIndexAsDouble, tags);
            }
        }
    }*/

}
