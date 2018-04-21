package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.arduino.ArduinoException;
import com.xmonit.solar.arduino.ArduinoResponseProcessor;
import com.xmonit.solar.arduino.ArduinoSerialBus;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
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
public class EpeverMetrics implements ArduinoResponseProcessor {

    public class UpdateStatsTracker {
        AtomicInteger cnt = new AtomicInteger();
        AtomicInteger attemptCnt = new AtomicInteger();
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger successAttemptCnt = new AtomicInteger();

        public void incrementCnt() {
            cnt.incrementAndGet();
        }

        public void incrementAttemptCnt() {
            attemptCnt.incrementAndGet();
        }

        public void reset() {
            cnt.set(0);
            attemptCnt.set(0);
            successCnt.set(0);
            successAttemptCnt.set(0);
        }

        public void succeeded(int tries) {
            successCnt.incrementAndGet();
            successAttemptCnt.addAndGet(tries);
        }

        public void logReliabiltyInfo() {
            logger.info("EPever communications summary:");
            logger.info("                 total: " + cnt);
            logger.info("      attempts/request: " + attemptCnt.get()*1.0/cnt.get());
            logger.info("          unsuccessful: " + (cnt.get() - successCnt.get()));
            logger.info("      attempts/success: " + (successAttemptCnt.get()*1.0/successCnt.get()));
        }
    }

    public UpdateStatsTracker updateStatsTracker = new UpdateStatsTracker();

    private static final Logger logger = LoggerFactory.getLogger(EpeverMetrics.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    protected ArduinoGetResponse data;
    private MeterRegistry registry;
    private AtomicInteger serialReadErrorCnt = new AtomicInteger();
    private AtomicInteger serialReadOk = new AtomicInteger();


    public EpeverMetrics(MeterRegistry registry) {

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
        //data = null;
        //System.gc();
    }


    @Override
    public void process(ArduinoSerialBus serialBus, JsonNode arduinoResp) {

        try {

            ArduinoGetResponse getResp = mapper.readValue(arduinoResp, ArduinoGetResponse.class);

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

        } catch (Exception ex) {
            invalidate(serialBus, ex);
            logger.error("Failed converting arduino JSON to monitoring metrics");
            logger.error(ex.getMessage());
        }
    }


    private void initRegistry(final ArduinoSerialBus serialBus, ArduinoGetResponse resp) {

        data = resp;

        EpeverGaugeBuilder builder = new EpeverGaugeBuilder(serialBus, registry);

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