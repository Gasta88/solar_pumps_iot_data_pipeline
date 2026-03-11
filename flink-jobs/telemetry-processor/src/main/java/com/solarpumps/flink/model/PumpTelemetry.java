package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Water pump motor sensor readings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PumpTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("motor_voltage_v")
    private double motorVoltageV;

    @JsonProperty("motor_current_a")
    private double motorCurrentA;

    private int rpm;

    @JsonProperty("flow_rate_lpm")
    private double flowRateLpm;

    @JsonProperty("discharge_pressure_bar")
    private double dischargePressureBar;

    @JsonProperty("suction_pressure_bar")
    private double suctionPressureBar;

    @JsonProperty("water_temperature_c")
    private double waterTemperatureC;

    public PumpTelemetry() {}

    public PumpTelemetry(double motorVoltageV, double motorCurrentA, int rpm,
                          double flowRateLpm, double dischargePressureBar,
                          double suctionPressureBar, double waterTemperatureC) {
        this.motorVoltageV = motorVoltageV;
        this.motorCurrentA = motorCurrentA;
        this.rpm = rpm;
        this.flowRateLpm = flowRateLpm;
        this.dischargePressureBar = dischargePressureBar;
        this.suctionPressureBar = suctionPressureBar;
        this.waterTemperatureC = waterTemperatureC;
    }

    public double getMotorVoltageV() { return motorVoltageV; }
    public void setMotorVoltageV(double motorVoltageV) { this.motorVoltageV = motorVoltageV; }

    public double getMotorCurrentA() { return motorCurrentA; }
    public void setMotorCurrentA(double motorCurrentA) { this.motorCurrentA = motorCurrentA; }

    public int getRpm() { return rpm; }
    public void setRpm(int rpm) { this.rpm = rpm; }

    public double getFlowRateLpm() { return flowRateLpm; }
    public void setFlowRateLpm(double flowRateLpm) { this.flowRateLpm = flowRateLpm; }

    public double getDischargePressureBar() { return dischargePressureBar; }
    public void setDischargePressureBar(double dischargePressureBar) { this.dischargePressureBar = dischargePressureBar; }

    public double getSuctionPressureBar() { return suctionPressureBar; }
    public void setSuctionPressureBar(double suctionPressureBar) { this.suctionPressureBar = suctionPressureBar; }

    public double getWaterTemperatureC() { return waterTemperatureC; }
    public void setWaterTemperatureC(double waterTemperatureC) { this.waterTemperatureC = waterTemperatureC; }

    @Override
    public String toString() {
        return "PumpTelemetry{voltage=" + motorVoltageV
                + ", current=" + motorCurrentA
                + ", rpm=" + rpm
                + ", flow=" + flowRateLpm
                + ", discharge=" + dischargePressureBar
                + ", suction=" + suctionPressureBar
                + ", waterTemp=" + waterTemperatureC + "}";
    }
}
