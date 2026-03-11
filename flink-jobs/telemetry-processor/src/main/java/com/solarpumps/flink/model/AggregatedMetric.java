package com.solarpumps.flink.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * POJO holding the result of a windowed aggregation over telemetry data.
 *
 * <p>Used for both 1-minute and 5-minute tumbling windows. Each row is
 * keyed by {@code pumpId} and the window interval, and stores computed
 * statistics for flow rate, solar power, and vibration.
 */
public class AggregatedMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pumpId;
    private Timestamp windowStart;
    private Timestamp windowEnd;
    private String windowSize;

    // Flow rate (litres per minute)
    private double avgFlowRate;
    private double maxFlowRate;
    private double minFlowRate;

    // Solar power (watts)
    private double avgSolarPower;
    private double maxSolarPower;
    private double minSolarPower;

    // Vibration (mm/s)
    private double avgVibration;
    private double maxVibration;
    private double minVibration;

    private long recordCount;

    public AggregatedMetric() {}

    // --- Getters & Setters ---

    public String getPumpId() { return pumpId; }
    public void setPumpId(String pumpId) { this.pumpId = pumpId; }

    public Timestamp getWindowStart() { return windowStart; }
    public void setWindowStart(Timestamp windowStart) { this.windowStart = windowStart; }

    public Timestamp getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Timestamp windowEnd) { this.windowEnd = windowEnd; }

    public String getWindowSize() { return windowSize; }
    public void setWindowSize(String windowSize) { this.windowSize = windowSize; }

    public double getAvgFlowRate() { return avgFlowRate; }
    public void setAvgFlowRate(double avgFlowRate) { this.avgFlowRate = avgFlowRate; }

    public double getMaxFlowRate() { return maxFlowRate; }
    public void setMaxFlowRate(double maxFlowRate) { this.maxFlowRate = maxFlowRate; }

    public double getMinFlowRate() { return minFlowRate; }
    public void setMinFlowRate(double minFlowRate) { this.minFlowRate = minFlowRate; }

    public double getAvgSolarPower() { return avgSolarPower; }
    public void setAvgSolarPower(double avgSolarPower) { this.avgSolarPower = avgSolarPower; }

    public double getMaxSolarPower() { return maxSolarPower; }
    public void setMaxSolarPower(double maxSolarPower) { this.maxSolarPower = maxSolarPower; }

    public double getMinSolarPower() { return minSolarPower; }
    public void setMinSolarPower(double minSolarPower) { this.minSolarPower = minSolarPower; }

    public double getAvgVibration() { return avgVibration; }
    public void setAvgVibration(double avgVibration) { this.avgVibration = avgVibration; }

    public double getMaxVibration() { return maxVibration; }
    public void setMaxVibration(double maxVibration) { this.maxVibration = maxVibration; }

    public double getMinVibration() { return minVibration; }
    public void setMinVibration(double minVibration) { this.minVibration = minVibration; }

    public long getRecordCount() { return recordCount; }
    public void setRecordCount(long recordCount) { this.recordCount = recordCount; }

    @Override
    public String toString() {
        return "AggregatedMetric{pumpId='" + pumpId + "'"
                + ", window=" + windowStart + " -> " + windowEnd
                + ", windowSize='" + windowSize + "'"
                + ", records=" + recordCount
                + ", avgFlow=" + avgFlowRate
                + ", avgSolar=" + avgSolarPower
                + ", avgVibration=" + avgVibration + "}";
    }
}
