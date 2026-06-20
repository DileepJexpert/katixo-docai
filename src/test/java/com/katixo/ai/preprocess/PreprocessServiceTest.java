package com.katixo.ai.preprocess;

import com.katixo.ai.config.AiProperties;
import com.katixo.ai.support.BadInputException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreprocessServiceTest {

    private final PreprocessService service = new PreprocessService(new AiProperties());

    @Test
    void preprocessesGoldenPngIntoOneValidPage() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("eval/golden/sample-01.png"));

        PreprocessedDocument doc = service.preprocess(bytes, "sample-01.png", "image/png");

        assertThat(doc.pageCount()).isEqualTo(1);
        assertThat(doc.wasPdf()).isFalse();
        BufferedImage page = ImageIO.read(new ByteArrayInputStream(doc.pagesPng().get(0)));
        assertThat(page).isNotNull();
        assertThat(page.getWidth()).isGreaterThan(0);
    }

    @Test
    void rendersPdfToImages() throws Exception {
        byte[] pdf = onePagePdf();

        PreprocessedDocument doc = service.preprocess(pdf, "doc.pdf", "application/pdf");

        assertThat(doc.wasPdf()).isTrue();
        assertThat(doc.pageCount()).isEqualTo(1);
        assertThat(ImageIO.read(new ByteArrayInputStream(doc.pagesPng().get(0)))).isNotNull();
    }

    @Test
    void emptyInputIsBadRequest() {
        assertThatThrownBy(() -> service.preprocess(new byte[0], "x.png", "image/png"))
                .isInstanceOf(BadInputException.class);
    }

    @Test
    void garbageImageIsBadRequest() {
        assertThatThrownBy(() -> service.preprocess(new byte[]{1, 2, 3, 4}, "x.png", "image/png"))
                .isInstanceOf(BadInputException.class);
    }

    private static byte[] onePagePdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
