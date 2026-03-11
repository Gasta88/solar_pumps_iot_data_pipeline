package com.solarpumps.flink.aggregation;

import com.solarpumps.flink.model.AggregatedMetric;
import com.solarpumps.flink.model.TelemetryMessage;

import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;

/**
 * Computes avg/max/min statistics for flow rate, solar power, and vibration
 * over a tumbling time window keyed by {@code pump_id}.
 *
 * <p>Used for both the 1-minute and 5-minute tumbling windows; the caller
 * sets the {@code windowSize} label accordingly.
 */
public class TelemetryAggregationFunction
        implements WindowFunction<TelemetryMessage, AggregatedMetric, String, TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final String windowSizeLabel;

    /**
     * @param windowSizeLabel human-readable window label (e.g. "1m" or "5m")
     */
    public TelemetryAggregationFunction(String windowSizeLabel) {
        this.windowSizeLabel = windowSizeLabel;
    }

    @Override
    public void apply(String pumpId,
                       TimeWindow window,
                       Iterable<TelemetryMessage> input,
                       Collector<AggregatedMetric> out) {

        double sumFlow = 0, maxFlow = Double.MIN_VALUE, minFlow = Double.MAX_VALUE;
        double sumSolar = 0, maxSolar = Double.MIN_VALUE, minSolar = Double.MAX_VALUE;
        double sumVib = 0, maxVib = Double.MIN_VALUE, minVib = Double.MAX_VALUE;
        long count = 0;

        for (TelemetryMessage msg : input) {
            double flow = msg.getPump().getFlowRateLpm();
            double solar = msg.getSolarPanel().getPowerW();
            double vib = msg.getSystem().getVibrationMmS();

            sumFlow += flow;
            maxFlow = Math.max(maxFlow, flow);
            minFlow = Math.min(minFlow, flow);

            sumSolar += solar;
            maxSolar = Math.max(maxSolar, solar);
            minSolar = Math.min(minSolar, solar);

            sumVib += vib;
            maxVib = Math.max(maxVib, vib);
            minVib = Math.min(minVib, vib);

            count++;
        }

        if (count == 0) {
            return; // nothing to emit
        }

        AggregatedMetric metric = new AggregatedMetric();
        metric.setPumpId(pumpId);
        metric.setWindowStart(new Timestamp(window.getStart()));
        metric.setWindowEnd(new Timestamp(window.getEnd()));
        metric.setWindowSize(windowSizeLabel);

        metric.setAvgFlowRate(round(sumFlow / count));
        metric.setMaxFlowRate(round(maxFlow));
        metric.setMinFlowRate(round(minFlow));

        metric.setAvgSolarPower(round(sumSolar / count));
        metric.setMaxSolarPower(round(maxSolar));
        metric.setMinSolarPower(round(minSolar));

        metric.setAvgVibration(round(sumVib / count));
        metric.setMaxVibration(round(maxVib));
        metric.setMinVibration(round(minVib));

        metric.setRecordCount(count);

        out.collect(metric);
    }

    /** Round to 3 decimal places. */
    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
