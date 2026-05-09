package com.shipping.carrier.application;

/**
 * @deprecated Superseded by CQRS split:
 * <ul>
 *   <li>Write side: {@link com.shipping.carrier.application.command.TransmitManifestCommandHandler}</li>
 *   <li>Read  side: {@link com.shipping.carrier.application.query.GetTrackingQueryHandler}</li>
 * </ul>
 * This class is retained only to preserve git history; it is no longer wired into the application context.
 */
@Deprecated(forRemoval = true)
public final class ManifestService {}
