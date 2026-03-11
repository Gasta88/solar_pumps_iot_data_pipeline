package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Ambient environmental conditions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("ambient_temperature_c")
    private double ambientTemperatureC;

    @JsonProperty("humidity_percent")
    private double humidityPercent;

    public EnvironmentTelemetry() {}

    public EnvironmentTelemetry(double ambientTemperatureC, double humidityPercent) {
        this.ambientTemperatureC = ambientTemperatureC;
        this.humidityPercent = humidityPercent;
    }

    public double getAmbientTemperatureC() { return ambientTemperatureC; }
    public void setAmbientTemperatureC(double ambientTemperatureC) { this.ambientTemperatureC = ambientTemperatureC; }

    public double getHumidityPercent() { return humidityPercent; }
    public void setHumidityPercent(double humidityPercent) { this.humidityPercent = humidityPercent; }

    @Override
    public String toString() {
        return "EnvironmentTelemetry{ambientTemp=" + ambientTemperatureC
                + ", humidity=" + humidityPercent + "}";
    }
}
