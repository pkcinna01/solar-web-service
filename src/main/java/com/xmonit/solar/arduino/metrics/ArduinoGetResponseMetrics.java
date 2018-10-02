package com.xmonit.solar.arduino.metrics;

import com.xmonit.solar.arduino.ArduinoException;
import com.xmonit.solar.arduino.ArduinoResponseProcessor;
import com.xmonit.solar.arduino.ArduinoSerialBus;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
import com.xmonit.solar.metrics.UpdateStatsTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Exposes Arduino data read from USB as monitoring metrics (default is Prometheus)
 */
@Component
public class ArduinoGetResponseMetrics implements ArduinoResponseProcessor {

    public UpdateStatsTracker updateStatsTracker = new UpdateStatsTracker("arduino");

    protected ArduinoGetResponse data;

    private static final Logger logger = LoggerFactory.getLogger(ArduinoGetResponseMetrics.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private MeterRegistry registry;
    private AtomicInteger serialReadErrorCnt = new AtomicInteger();
    private AtomicInteger serialReadOk = new AtomicInteger();


    public ArduinoGetResponseMetrics(MeterRegistry registry) {

        this.registry = registry;
    }


    public ArduinoGetResponse getLatestResponse() throws IOException, ClassNotFoundException {

        return data.deepCopy();
    }


    @Override
    public void invalidate(ArduinoSerialBus serialBus, Exception ex) {

        if (data != null) {
            data.invalidate(ex);
        }
        serialReadErrorCnt.set(1);
        serialReadOk.set(0);
    }


    @Override
    public void process(ArduinoSerialBus serialBus, JsonNode arduinoResp) {

        // Arduino returns array of response objects but assuming only single 'GET,SENSORS' was in request
        try {
            ArduinoGetResponse[] getRespArr = mapper.readValue(arduinoResp, ArduinoGetResponse[].class);

            if ( getRespArr.length == 1 ) {
                ArduinoGetResponse getResp = getRespArr[0];
                if (getResp.respCode != 0) {
                    throw new ArduinoException(getResp.respMsg, getResp.respCode);
                }

                if (data == null) {
                    initRegistry(serialBus, getResp);
                } else {
                    data.copy(getResp);
                }

                serialReadErrorCnt.set(0);
                serialReadOk.set(1);
            } else {
                throw new ArduinoException("Expected JSON response array to have one response object but found " + getRespArr.length, -1);
            }
        } catch (Exception ex) {
            invalidate(serialBus, ex);
            logger.error("Failed converting arduino JSON to monitoring metrics");
            logger.error(ex.getMessage());
        }
    }


    private void initRegistry(final ArduinoSerialBus serialBus, ArduinoGetResponse resp) {

        data = resp;

        updateStatsTracker.name = serialBus.getPortName() + " arduino";

        ArduinoGaugeBuilder builder = new ArduinoGaugeBuilder(serialBus, registry);

        builder.init(resp);

        // General stats for backward compatibility with Grafana dashboard
        // serialReadErrorCnt is an error count per execute command but only supporting 1 TTY now
        List<Tag> tags = builder.getCommonTags();
        registry.gauge("arduino.solar.serialReadErrorCnt", tags, serialReadErrorCnt);
        registry.gauge("arduino.solar.serialReadOk", tags, serialReadOk);

        registry.gauge("arduino.solar.serial.scheduled.requestCnt", tags, updateStatsTracker.cnt);
        registry.gauge("arduino.solar.serial.scheduled.attemptCnt", tags, updateStatsTracker.attemptCnt);
        registry.gauge("arduino.solar.serial.scheduled.successCnt", tags, updateStatsTracker.successCnt);
        registry.gauge("arduino.solar.serial.scheduled.successAttemptCnt", tags, updateStatsTracker.successAttemptCnt);

    }


}