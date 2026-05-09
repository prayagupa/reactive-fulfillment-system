package com.shipping.carrier.application.query;

import com.datastax.oss.driver.api.core.CqlSession;
import com.shipping.cqrs.QueryHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CQRS read side: handles {@link GetTrackingQuery}.
 * <p>
 * Reads the wide-row {@code tracking} table in ScyllaDB and projects
 * each row to an immutable {@link TrackingEventView} read model.
 *
 * <pre>
 *   CREATE TABLE tracking (
 *     tracking_number TEXT,
 *     event_time      TIMESTAMP,
 *     status          TEXT,
 *     location        TEXT,
 *     description     TEXT,
 *     PRIMARY KEY (tracking_number, event_time)
 *   ) WITH CLUSTERING ORDER BY (event_time DESC);
 * </pre>
 */
@Service
public class GetTrackingQueryHandler
        implements QueryHandler<GetTrackingQuery, List<TrackingEventView>> {

    private final CqlSession session;

    public GetTrackingQueryHandler(CqlSession session) {
        this.session = session;
    }

    @Override
    public List<TrackingEventView> handle(GetTrackingQuery query) {
        var rows = session.execute(
            "SELECT event_time, status, location, description FROM tracking " +
            "WHERE tracking_number = ? LIMIT 50",
            query.trackingNumber());

        List<TrackingEventView> events = new ArrayList<>();
        for (var row : rows) {
            events.add(new TrackingEventView(
                row.getInstant("event_time").toString(),
                row.getString("status"),
                row.getString("location") != null ? row.getString("location") : "",
                row.getString("description")));
        }
        return events;
    }
}
