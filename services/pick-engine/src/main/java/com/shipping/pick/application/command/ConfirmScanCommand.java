package com.shipping.pick.application.command;

import com.shipping.cqrs.Command;

/**
 * Write-side intent: validate a barcode scan and mark the pick-task confirmed.
 *
 * @param pickListId    the pick list owning this task
 * @param itemSeq       line-item ordinal within the pick list
 * @param scannedBarcode barcode read from the associate's scanner
 * @param quantity      quantity the associate confirmed
 */
public record ConfirmScanCommand(
        String pickListId,
        int itemSeq,
        String scannedBarcode,
        int quantity) implements Command {}
