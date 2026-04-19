package com.lexibridge.operations.modules.booking.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QR image rendering. Extracted from BookingService to isolate the zxing/ImageIO dependency
 * from reservation-lifecycle logic.
 */
@Service
public class BookingQrRenderer {

    private static final int QR_SIZE_PX = 220;

    public byte[] renderPng(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("QR token is required.");
        }
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Unable to generate QR image.", ex);
        }
    }
}
