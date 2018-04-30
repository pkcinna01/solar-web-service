package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.epever.EpeverException;
import com.xmonit.solar.epever.EpeverSolarCharger;
import com.xmonit.solar.epever.field.EpeverFieldList;
import io.micrometer.core.instrument.MeterRegistry;

import static com.xmonit.solar.epever.EpeverFieldDefinitions.REAL_TIME_CLOCK;

public class MetricsSource {

    public EpeverSolarCharger charger;
    public EpeverFieldList fields;
    public EpeverMetrics metrics;


    public MetricsSource(MeterRegistry meterRegistry) {

        charger = new EpeverSolarCharger();
        fields = new EpeverFieldList(charger, fd -> fd.isStatistic());// || fd == REAL_TIME_CLOCK);
        metrics = new EpeverMetrics(meterRegistry);
    }


    public void init() throws EpeverException {

        metrics.init(charger,fields);
    }


    public void invalidate(Exception ex) {

        fields.reset();
        metrics.serialReadErrorCnt.set(1);
        metrics.serialReadOk.set(0);
    }


    public void requestSucceeded(int attempt) {
        metrics.updateStatsTracker.succeeded(attempt);
        metrics.serialReadErrorCnt.set(0);
        metrics.serialReadOk.set(1);
    }
}
