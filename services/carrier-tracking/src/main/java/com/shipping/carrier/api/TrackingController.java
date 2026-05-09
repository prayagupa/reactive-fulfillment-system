package com.shipping.carrier.api;

import com.shipping.carrier.application.query.GetTrackingQuery;
import com.shipping.carrier.application.query.GetTrackingQueryHandler;
import com.shipping.carrier.application.query.TrackingEventView;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final GetTrackingQueryHandler getTrackingHandler;

    public TrackingController(GetTrackingQueryHandler getTrackingHandler) {
        this.getTrackingHandler = getTrackingHandler;
    }

    /**
     * Returns the full tracking timeline for a shipment.
     * Reads from ScyllaDB wide-row table via {@link GetTrackingQueryHandler}.
     */
    @GetMapping("/{trackingNumber}")
    public List<TrackingEventView> getTracking(@PathVariable String trackingNumber) {
        return getTrackingHandler.handle(new GetTrackingQuery(trackingNumber));
    }

    /** Inbound webhook from a carrier. Normalised to TrackingEvent by CarrierWebhookProcessor. */
    @PostMapping("/webhooks/{carrierId}")
    public Map<String, String> receiveWebhook(@PathVariable String carrierId,
                                               @RequestBody Map<String, Object> payload) {
        return Map.of("status", "accepted");
    }
}
