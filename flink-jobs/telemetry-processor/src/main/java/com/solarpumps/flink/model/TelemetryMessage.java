package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Top-level telemetry message deserialized from RabbitMQ JSON.
 *
 * <p>Mirrors the Python simulator's {@code TelemetryMessage} schema exactly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("pump_id")
    private String pumpId;

    private String timestamp;

    private LocationTelemetry location;

    @JsonProperty("solar_panel")
    private SolarPanelTelemetry solarPanel;

    private PumpTelemetry pump;

    private SystemTelemetry system;

    private EnvironmentTelemetry environment;

    public TelemetryMessage() {}

    // --- Getters & Setters ---

    public String getPumpId() { return pumpId; }
    public void setPumpId(String pumpId) { this.pumpId = pumpId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public LocationTelemetry getLocation() { return location; }
    public void setLocation(LocationTelemetry location) { this.location = location; }

    public SolarPanelTelemetry getSolarPanel() { return solarPanel; }
    public void setSolarPanel(SolarPanelTelemetry solarPanel) { this.solarPanel = solarPanel; }

    public PumpTelemetry getPump() { return pump; }
    public void setPump(PumpTelemetry pump) { this.pump = pump; }

    public SystemTelemetry getSystem() { return system; }
    public void setSystem(SystemTelemetry system) { this.system = system; }

    public EnvironmentTelemetry getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentTelemetry environment) { this.environment = environment; }

    @Override
    public String toString() {
        return "TelemetryMessage{pumpId='" + pumpId + "'"
                + ", timestamp='" + timestamp + "'"
                + ", solarPanel=" + solarPanel
                + ", pump=" + pump
                + ", system=" + system
                + ", environment=" + environment + "}";
    }
}
