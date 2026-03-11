package com.solarpumps.flink.metrics;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.MeterView;
import org.apache.flink.metrics.MetricGroup;

import java.io.Serializable;

/**
 * Encapsulates all custom Prometheus metrics exposed by the pipeline.
 *
 * <p>Metrics registered:
 * <ul>
 *   <li>{@code records_processed} &mdash; Meter (rate + total count)</li>
 *   <li>{@code validation_errors_schema} &mdash; Counter</li>
 *   <li>{@code validation_errors_timestamp} &mdash; Counter</li>
 *   <li>{@code validation_errors_range} &mdash; Counter</li>
 *   <li>{@code records_written_timescale} &mdash; Counter</li>
 * </ul>
 *
 * <p>Call {@link #register(MetricGroup)} once during operator open, then
 * use the increment helpers throughout the processing logic.
 */
public class PipelineMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    private transient Meter recordsProcessed;
    private transient Counter validationErrorsSchema;
    private transient Counter validationErrorsTimestamp;
    private transient Counter validationErrorsRange;
    private transient Counter recordsWrittenTimescale;

    /**
     * Register all metrics against the supplied Flink {@link MetricGroup}.
     */
    public void register(MetricGroup metricGroup) {
        Counter processedCounter = metricGroup.counter("records_processed_total");
        this.recordsProcessed = metricGroup.meter(
                "records_processed", new MeterView(processedCounter));

        this.validationErrorsSchema =
                metricGroup.counter("validation_errors_schema_total");
        this.validationErrorsTimestamp =
                metricGroup.counter("validation_errors_timestamp_total");
        this.validationErrorsRange =
                metricGroup.counter("validation_errors_range_total");
        this.recordsWrittenTimescale =
                metricGroup.counter("records_written_timescale_total");
    }

    /** Mark one record as processed. */
    public void incRecordsProcessed() {
        if (recordsProcessed != null) {
            recordsProcessed.markEvent();
        }
    }

    /** Increment the validation-error counter for the given error type. */
    public void incValidationError(String errorType) {
        if (errorType == null) return;
        switch (errorType) {
            case "SCHEMA":
                if (validationErrorsSchema != null) validationErrorsSchema.inc();
                break;
            case "TIMESTAMP":
                if (validationErrorsTimestamp != null) validationErrorsTimestamp.inc();
                break;
            case "RANGE":
                if (validationErrorsRange != null) validationErrorsRange.inc();
                break;
            default:
                // unknown type — count as schema
                if (validationErrorsSchema != null) validationErrorsSchema.inc();
                break;
        }
    }

    /** Increment the counter after a successful TimescaleDB write. */
    public void incRecordsWrittenTimescale() {
        if (recordsWrittenTimescale != null) {
            recordsWrittenTimescale.inc();
        }
    }
}
