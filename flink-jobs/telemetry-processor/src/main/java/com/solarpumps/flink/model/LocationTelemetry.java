package com.solarpumps.flink.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Location coordinates embedded in telemetry.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationTelemetry implements Serializable {

    private static final long serialVersionUID = 1L;

    private double latitude;
    private double longitude;
    private String region;

    public LocationTelemetry() {}

    public LocationTelemetry(double latitude, double longitude, String region) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.region = region;
    }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    @Override
    public String toString() {
        return "LocationTelemetry{latitude=" + latitude
                + ", longitude=" + longitude
                + ", region='" + region + "'}";
    }
}
