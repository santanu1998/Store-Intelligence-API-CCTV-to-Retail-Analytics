package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.request.IngestEventsRequest;
import com.purplle.storeintelligence.dto.request.IngestPosRequest;
import com.purplle.storeintelligence.dto.response.IngestResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Handles batch ingestion of CCTV detection events and POS transactions.
 *
 * <p>Key contract:
 * <ul>
 *   <li>Idempotent — the same event_id ingested twice counts as one duplicate, never an error.</li>
 *   <li>Partial success — valid events in a batch are stored even when others fail validation.</li>
 *   <li>Store auto-registration — new store_ids are created automatically on first ingest.</li>
 * </ul>
 */
public interface EventIngestionService {

    /**
     * Ingests a batch of up to 500 CCTV detection events.
     *
     * @param request batch request with 1–500 events
     * @return result with accepted/rejected/duplicate counts and error details
     */
    IngestResponse ingestEvents(IngestEventsRequest request);

    /**
     * Ingests a batch of POS transaction records.
     *
     * @param request batch of POS transactions
     * @return result counts
     */
    IngestResponse ingestPosTransactions(IngestPosRequest request);

    /**
     * Parses and ingests a POS CSV file in the Brigade Bangalore format.
     * Columns used: order_id, order_date, order_time, store_id, total_amount,
     * invoice_number, invoice_type, customer_number, salesperson_id.
     *
     * @param storeId override store_id (if not present in CSV)
     * @param csvFile uploaded CSV file
     * @return result counts
     */
    IngestResponse importPosCsv(String storeId, MultipartFile csvFile) throws IOException;
}
