package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Solar panel sensor readings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolarPanelTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("irradiance_w_m2")
    private double irradianceWM2;

    @JsonProperty("voltage_v")
    private double voltageV;

    @JsonProperty("current_a")
    private double currentA;

    @JsonProperty("power_w")
    private double powerW;

    @JsonProperty("temperature_c")
    private double temperatureC;

    public SolarPanelTelemetry() {}

    public SolarPanelTelemetry(double irradianceWM2, double voltageV,
                                double currentA, double powerW,
                                double temperatureC) {
        this.irradianceWM2 = irradianceWM2;
        this.voltageV = voltageV;
        this.currentA = currentA;
        this.powerW = powerW;
        this.temperatureC = temperatureC;
    }

    public double getIrradianceWM2() { return irradianceWM2; }
    public void setIrradianceWM2(double irradianceWM2) { this.irradianceWM2 = irradianceWM2; }

    public double getVoltageV() { return voltageV; }
    public void setVoltageV(double voltageV) { this.voltageV = voltageV; }

    public double getCurrentA() { return currentA; }
    public void setCurrentA(double currentA) { this.currentA = currentA; }

    public double getPowerW() { return powerW; }
    public void setPowerW(double powerW) { this.powerW = powerW; }

    public double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(double temperatureC) { this.temperatureC = temperatureC; }

    @Override
    public String toString() {
        return "SolarPanelTelemetry{irradiance=" + irradianceWM2
                + ", voltage=" + voltageV
                + ", current=" + currentA
                + ", power=" + powerW
                + ", temp=" + temperatureC + "}";
    }
}
