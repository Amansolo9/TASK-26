package com.lexibridge.operations.modules.booking.service;

import com.lexibridge.operations.governance.AuditLogService;
import com.lexibridge.operations.modules.booking.repository.BookingRepository;
import com.lexibridge.operations.modules.content.model.FileValidationResult;
import com.lexibridge.operations.modules.content.service.MediaValidationService;
import com.lexibridge.operations.storage.service.BinaryStorageService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Booking-attachment lifecycle (upload validation, storage, retrieval). Extracted from
 * BookingService so the core reservation/transition lifecycle stays focused.
 */
@Service
public class BookingAttachmentService {

    private final BookingRepository bookingRepository;
    private final MediaValidationService mediaValidationService;
    private final BinaryStorageService binaryStorageService;
    private final AuditLogService auditLogService;

    public BookingAttachmentService(BookingRepository bookingRepository,
                                    MediaValidationService mediaValidationService,
                                    BinaryStorageService binaryStorageService,
                                    AuditLogService auditLogService) {
        this.bookingRepository = bookingRepository;
        this.mediaValidationService = mediaValidationService;
        this.binaryStorageService = binaryStorageService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> addAttachment(long bookingOrderId,
                                             String filename,
                                             byte[] bytes,
                                             long actorUserId) {
        FileValidationResult validation = mediaValidationService.validate(filename, bytes);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Attachment rejected: " + validation.reason());
        }

        bookingRepository.bookingLocation(bookingOrderId)
            .orElseThrow(() -> new IllegalArgumentException("Booking order not found."));

        String storagePath = "booking-attachments/" + bookingOrderId + "/" + validation.checksumSha256();
        binaryStorageService.store(storagePath, validation.checksumSha256(), validation.detectedMime(), bytes);

        long attachmentId = bookingRepository.insertAttachment(
            bookingOrderId,
            storagePath,
            validation.detectedMime(),
            bytes.length,
            validation.checksumSha256(),
            actorUserId
        );
        auditLogService.logUserEvent(actorUserId, "BOOKING_ATTACHMENT_ADDED", "booking_attachment",
            String.valueOf(attachmentId), null, Map.of("bookingId", bookingOrderId));
        return Map.of("attachmentId", attachmentId, "bookingId", bookingOrderId, "status", "UPLOADED");
    }

    public List<Map<String, Object>> attachments(long bookingOrderId) {
        return bookingRepository.attachments(bookingOrderId);
    }

    public BinaryStorageService.DownloadedBinary downloadAttachment(long bookingOrderId, long attachmentId) {
        Map<String, Object> attachment = bookingRepository.attachmentById(attachmentId);
        if (attachment == null) {
            throw new IllegalArgumentException("Booking attachment not found.");
        }
        long actualBookingId = ((Number) attachment.get("booking_order_id")).longValue();
        if (actualBookingId != bookingOrderId) {
            throw new IllegalArgumentException("Booking attachment does not belong to requested booking.");
        }
        return binaryStorageService.read(String.valueOf(attachment.get("storage_path")));
    }
}
