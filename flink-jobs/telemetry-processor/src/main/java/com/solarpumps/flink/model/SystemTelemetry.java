package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Operational metadata for the pump system.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("operating_hours")
    private double operatingHours;

    @JsonProperty("vibration_mm_s")
    private double vibrationMmS;

    @JsonProperty("error_code")
    private int errorCode;

    private String status;

    public SystemTelemetry() {}

    public SystemTelemetry(double operatingHours, double vibrationMmS,
                            int errorCode, String status) {
        this.operatingHours = operatingHours;
        this.vibrationMmS = vibrationMmS;
        this.errorCode = errorCode;
        this.status = status;
    }

    public double getOperatingHours() { return operatingHours; }
    public void setOperatingHours(double operatingHours) { this.operatingHours = operatingHours; }

    public double getVibrationMmS() { return vibrationMmS; }
    public void setVibrationMmS(double vibrationMmS) { this.vibrationMmS = vibrationMmS; }

    public int getErrorCode() { return errorCode; }
    public void setErrorCode(int errorCode) { this.errorCode = errorCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "SystemTelemetry{hours=" + operatingHours
                + ", vibration=" + vibrationMmS
                + ", errorCode=" + errorCode
                + ", status='" + status + "'}";
    }
}
