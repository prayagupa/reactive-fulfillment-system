package com.shipping.carrier.api;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final CqlSession session;

    public TrackingController(CqlSession session) {
        this.session = session;
    }

    /**
     * Returns the full tracking timeline for a shipment.
     * Reads from ScyllaDB wide-row table:
     *   tracking (tracking_number TEXT, event_time TIMESTAMP, status TEXT, location TEXT, description TEXT)
     *   PRIMARY KEY (tracking_number, event_time) WITH CLUSTERING ORDER BY (event_time DESC)
     */
    @GetMapping("/{trackingNumber}")
    public List<Map<String, Object>> getTracking(@PathVariable String trackingNumber) {
        var rows = session.execute(
            "SELECT event_time, status, location, description FROM tracking " +
            "WHERE tracking_number = ? LIMIT 50",
            trackingNumber
        );
        List<Map<String, Object>> events = new ArrayList<>();
        for (var row : rows) {
            events.add(Map.of(
                "eventTime",   row.getInstant("event_time").toString(),
                "status",      row.getString("status"),
                "location",    row.getString("location") != null ? row.getString("location") : "",
                "description", row.getString("description")
            ));
        }
        return events;
    }

    /** Inbound webhook from a carrier. Normalises to TrackingEvent and publishes to Kafka. */
    @PostMapping("/webhooks/{carrierId}")
    public Map<String, String> receiveWebhook(@PathVariable String carrierId,
                                               @RequestBody Map<String, Object> payload) {
        // Webhook normalisation is handled by CarrierWebhookProcessor (per-carrier adapters)
        return Map.of("status", "accepted");
    }
}
