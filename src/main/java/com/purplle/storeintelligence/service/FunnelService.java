package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.response.FunnelResponse;

import java.time.LocalDateTime;

/**
 * Computes the store conversion funnel for {@code GET /stores/{storeId}/funnel}.
 *
 * <p>Funnel stages: Entry → Zone Visit → Billing Queue → Purchase.
 * Session is the unit — REENTRY events do NOT double-count a visitor.
 */
public interface FunnelService {

    FunnelResponse getFunnel(String storeId);

    FunnelResponse getFunnel(String storeId, LocalDateTime start, LocalDateTime end);
}
