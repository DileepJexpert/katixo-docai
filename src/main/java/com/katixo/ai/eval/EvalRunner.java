package com.katixo.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.ai.config.AiProperties;
import com.katixo.ai.extraction.ExtractionPipeline;
import com.katixo.ai.model.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * CLI eval runner (spec section 8). Activated with the {@code eval} profile; it runs the FULL
 * pipeline over the golden set and prints field-level accuracy, grand-total exact-match rate,
 * exception precision/recall, mean latency, % needing review, and one composite score.
 *
 * <p>Run: {@code mvn spring-boot:run -Dspring-boot.run.profiles=eval}
 * (requires Ollama + the OCR sidecar to be running locally).
 */
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final String EXPECTED_SUFFIX = ".expected.json";
    private static final List<String> INPUT_EXTS = List.of(".png", ".jpg", ".jpeg", ".webp", ".pdf");

    private final ExtractionPipeline pipeline;
    private final ObjectMapper mapper;
    private final AiProperties props;
    private final ConfigurableApplicationContext context;

    public EvalRunner(ExtractionPipeline pipeline, ObjectMapper mapper, AiProperties props,
                      ConfigurableApplicationContext context) {
        this.pipeline = pipeline;
        this.mapper = mapper;
        this.props = props;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code;
        try {
            code = runEval();
        } catch (Exception e) {
            log.error("Eval run failed", e);
            code = 1;
        }
        final int exitCode = code;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private int runEval() throws IOException {
        Path dir = Path.of(props.getEval().getGoldenDir());
        if (!Files.isDirectory(dir)) {
            System.out.println("[eval] Golden directory not found: " + dir.toAbsolutePath());
            return 1;
        }

        List<Path> expectedFiles;
        try (Stream<Path> s = Files.list(dir)) {
            expectedFiles = s.filter(p -> p.getFileName().toString().endsWith(EXPECTED_SUFFIX)).sorted().toList();
        }
        if (expectedFiles.isEmpty()) {
            System.out.println("[eval] No *.expected.json files in " + dir.toAbsolutePath());
            return 1;
        }

        EvalHarness harness = new EvalHarness();
        List<EvalCaseResult> cases = new ArrayList<>();
        System.out.println("\n[eval] Running " + expectedFiles.size() + " golden sample(s) from " + dir.toAbsolutePath());
        System.out.println("--------------------------------------------------------------------");

        for (Path expectedFile : expectedFiles) {
            String base = expectedFile.getFileName().toString().replace(EXPECTED_SUFFIX, "");
            Optional<Path> input = findInput(dir, base);
            if (input.isEmpty()) {
                System.out.printf(Locale.ROOT, "  %-22s SKIPPED (no input file)%n", base);
                continue;
            }
            EvalExpected expected = mapper.readValue(expectedFile.toFile(), EvalExpected.class);
            byte[] bytes = Files.readAllBytes(input.get());

            long t0 = System.currentTimeMillis();
            ExtractionResult result = pipeline.extract(bytes, input.get().getFileName().toString(),
                    contentType(input.get()), expected.docType());
            long latency = System.currentTimeMillis() - t0;

            EvalCaseResult c = harness.compare(base, expected, result, latency);
            cases.add(c);
            System.out.printf(Locale.ROOT,
                    "  %-22s header=%5.1f%% lines=%5.1f%% grandTotal=%-3s exc(tp/fp/fn)=%d/%d/%d %5dms%n",
                    base, c.headerAccuracy() * 100, c.lineAccuracy() * 100,
                    c.grandTotalMatch() ? "OK" : "NO",
                    c.exceptionTruePositives(), c.exceptionFalsePositives(), c.exceptionFalseNegatives(),
                    c.latencyMs());
        }

        EvalMetrics metrics = harness.aggregate(cases);
        System.out.println(EvalReport.format(metrics));
        return 0;
    }

    private Optional<Path> findInput(Path dir, String base) {
        for (String ext : INPUT_EXTS) {
            Path p = dir.resolve(base + ext);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private String contentType(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
