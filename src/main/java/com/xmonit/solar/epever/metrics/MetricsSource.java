package com.xmonit.solar.epever.metrics;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.EpeverException;
import com.xmonit.solar.epever.EpeverSolarCharger;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import io.micrometer.core.instrument.MeterRegistry;

public class MetricsSource {

    public EpeverSolarCharger charger;
    public EpeverFieldList fields;
    public EpeverMetrics metrics;


    public MetricsSource(MeterRegistry meterRegistry) {

        charger = new EpeverSolarCharger();
        fields = new EpeverFieldList(charger).addFromDefs(fd -> EpeverField.isMetric(fd.registerAddress));// || fd == REAL_TIME_CLOCK);
        metrics = new EpeverMetrics(meterRegistry);
    }


    public void init(AppConfig conf) throws EpeverException {

        metrics.init(conf,charger,fields);
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
        metrics.updateTimeMs = System.currentTimeMillis();
    }
}
