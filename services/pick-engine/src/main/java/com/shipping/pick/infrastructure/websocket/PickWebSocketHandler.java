package com.shipping.pick.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipping.pick.application.PickTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for associate pick devices.
 *
 * Protocol (JSON messages):
 *   → CONNECT  {"action":"connect","associateId":"A-001","fcId":"FC-EAST-1"}
 *   ← TASK     {"pickListId":"...","itemSeq":1,"sku":"SKU-A","binLocation":"A-03-05","quantity":2}
 *   → SCAN     {"action":"scan","pickListId":"...","itemSeq":1,"barcode":"SKU-A","quantity":2}
 *   ← ACK      {"status":"OK","itemSeq":1}  or  {"status":"MISMATCH","expected":"SKU-A","scanned":"SKU-B"}
 *   ← COMPLETE {"status":"COMPLETE","pickListId":"..."}
 */
@Component
public class PickWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PickWebSocketHandler.class);

    // sessionId → associateId
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final PickTaskService pickTaskService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PickWebSocketHandler(PickTaskService pickTaskService) {
        this.pickTaskService = pickTaskService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Pick device connected sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);
        String action = (String) payload.get("action");

        switch (action) {
            case "connect" -> {
                String associateId = (String) payload.get("associateId");
                sessions.put(session.getId(), associateId);
                // Send next pending task for this associate
                var task = pickTaskService.nextTaskForAssociate(associateId);
                if (task != null) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(task)));
                }
            }
            case "scan" -> {
                String pickListId = (String) payload.get("pickListId");
                int itemSeq = (int) payload.get("itemSeq");
                String scannedBarcode = (String) payload.get("barcode");
                int quantity = (int) payload.get("quantity");

                var result = pickTaskService.confirmScan(pickListId, itemSeq, scannedBarcode, quantity);
                session.sendMessage(new TextMessage(mapper.writeValueAsString(result)));

                // Check if pick list is now complete
                if (pickTaskService.isPickListComplete(pickListId)) {
                    session.sendMessage(new TextMessage(
                        mapper.writeValueAsString(Map.of("status", "COMPLETE", "pickListId", pickListId))));
                }
            }
            default -> log.warn("Unknown action={} from sessionId={}", action, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Pick device disconnected sessionId={} status={}", session.getId(), status);
    }
}
