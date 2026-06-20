package com.katixo.ai.preprocess;

import com.katixo.ai.config.AiProperties;
import com.katixo.ai.support.BadInputException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 1 of the pipeline: turn an uploaded PDF/image into clean, OCR-ready PNG page images.
 *
 * <p>PDF pages are rendered at 300 DPI (PDFBox). Every page is then converted to grayscale,
 * deskewed (projection-profile angle search) and downscaled to a max edge so OCR/VLM stay fast and
 * within the latency/VRAM budget. Rotation/orientation of photos is additionally handled downstream
 * by PaddleOCR's angle classifier.
 */
@Service
public class PreprocessService {

    private static final Logger log = LoggerFactory.getLogger(PreprocessService.class);

    /** Image used purely to estimate the skew angle is capped at this edge for speed. */
    private static final int DESKEW_ESTIMATION_EDGE = 1000;
    private static final double DESKEW_STEP_DEG = 0.5;
    private static final int DARK_THRESHOLD = 128;

    private final AiProperties props;

    public PreprocessService(AiProperties props) {
        this.props = props;
    }

    public PreprocessedDocument preprocess(byte[] bytes, String filename, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new BadInputException("Uploaded file is empty.");
        }
        boolean pdf = isPdf(bytes, filename, contentType);
        List<BufferedImage> rawPages = pdf ? renderPdf(bytes) : List.of(readImage(bytes));

        List<byte[]> pages = new ArrayList<>(rawPages.size());
        for (BufferedImage page : rawPages) {
            BufferedImage cleaned = clean(page);
            pages.add(toPng(cleaned));
        }
        log.debug("Preprocessed {} page(s) (pdf={})", pages.size(), pdf);
        return new PreprocessedDocument(pages, pdf);
    }

    // --- input decoding ---

    private boolean isPdf(byte[] bytes, String filename, String contentType) {
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private List<BufferedImage> renderPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int n = doc.getNumberOfPages();
            if (n == 0) {
                throw new BadInputException("PDF has no pages.");
            }
            List<BufferedImage> pages = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                pages.add(renderer.renderImageWithDPI(i, props.getPreprocess().getPdfDpi(), ImageType.RGB));
            }
            return pages;
        } catch (IOException e) {
            throw new BadInputException("Could not read the PDF: " + e.getMessage());
        }
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new BadInputException("Unsupported or corrupt image. Supported: pdf, jpg, png, webp.");
            }
            return img;
        } catch (IOException e) {
            throw new BadInputException("Could not read the image: " + e.getMessage());
        }
    }

    // --- cleanup pipeline ---

    private BufferedImage clean(BufferedImage src) {
        BufferedImage gray = toGray(src);
        BufferedImage scaled = downscale(gray, props.getPreprocess().getMaxEdgePx());
        return deskew(scaled);
    }

    private BufferedImage toGray(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null);
        g.dispose();
        return gray;
    }

    private BufferedImage downscale(BufferedImage src, int maxEdge) {
        int edge = Math.max(src.getWidth(), src.getHeight());
        if (edge <= maxEdge) {
            return src;
        }
        double scale = (double) maxEdge / edge;
        int nw = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int nh = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    /** Deskew via projection-profile variance maximisation. Best-effort: failures return the input. */
    private BufferedImage deskew(BufferedImage gray) {
        try {
            double maxAngle = props.getPreprocess().getDeskewMaxAngleDeg();
            if (maxAngle <= 0) {
                return gray;
            }
            BufferedImage estimation = downscale(gray, DESKEW_ESTIMATION_EDGE);
            double bestAngle = 0;
            double bestScore = -1;
            for (double a = -maxAngle; a <= maxAngle + 1e-9; a += DESKEW_STEP_DEG) {
                double score = projectionVariance(rotate(estimation, a));
                if (score > bestScore) {
                    bestScore = score;
                    bestAngle = a;
                }
            }
            if (Math.abs(bestAngle) < 0.3) {
                return gray;
            }
            log.debug("Deskew: correcting {} deg", bestAngle);
            return rotate(gray, bestAngle);
        } catch (RuntimeException e) {
            log.debug("Deskew skipped: {}", e.getMessage());
            return gray;
        }
    }

    private double projectionVariance(BufferedImage gray) {
        Raster raster = gray.getRaster();
        int w = gray.getWidth();
        int h = gray.getHeight();
        long[] rowDark = new long[h];
        for (int y = 0; y < h; y++) {
            long dark = 0;
            for (int x = 0; x < w; x++) {
                if (raster.getSample(x, y, 0) < DARK_THRESHOLD) {
                    dark++;
                }
            }
            rowDark[y] = dark;
        }
        double mean = 0;
        for (long v : rowDark) {
            mean += v;
        }
        mean /= h;
        double var = 0;
        for (long v : rowDark) {
            double d = v - mean;
            var += d * d;
        }
        return var / h;
    }

    private BufferedImage rotate(BufferedImage src, double deg) {
        double rad = Math.toRadians(deg);
        double sin = Math.abs(Math.sin(rad));
        double cos = Math.abs(Math.cos(rad));
        int w = src.getWidth();
        int h = src.getHeight();
        int nw = (int) Math.floor(w * cos + h * sin);
        int nh = (int) Math.floor(h * cos + w * sin);
        BufferedImage dst = new BufferedImage(Math.max(1, nw), Math.max(1, nh), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate((dst.getWidth() - w) / 2.0, (dst.getHeight() - h) / 2.0);
        g.rotate(rad, w / 2.0, h / 2.0);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    private byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode PNG", e);
        }
    }
}
