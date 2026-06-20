package com.katixo.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * All tunables - model names, thresholds, endpoints - live here and are bound from
 * {@code application.yml} under the {@code katixo.ai} prefix.
 *
 * <p>No prompt or business code may hard-code a model name or endpoint; everything flows through
 * this type so the service is model-agnostic and swappable by config (spec section 3 & 6).
 */
@ConfigurationProperties(prefix = "katixo.ai")
public class AiProperties {

    @NestedConfigurationProperty
    private Privacy privacy = new Privacy();
    @NestedConfigurationProperty
    private Ollama ollama = new Ollama();
    @NestedConfigurationProperty
    private Ocr ocr = new Ocr();
    @NestedConfigurationProperty
    private Preprocess preprocess = new Preprocess();
    @NestedConfigurationProperty
    private Review review = new Review();
    @NestedConfigurationProperty
    private Prompts prompts = new Prompts();
    @NestedConfigurationProperty
    private Eval eval = new Eval();

    public Privacy getPrivacy() { return privacy; }
    public void setPrivacy(Privacy privacy) { this.privacy = privacy; }
    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }
    public Ocr getOcr() { return ocr; }
    public void setOcr(Ocr ocr) { this.ocr = ocr; }
    public Preprocess getPreprocess() { return preprocess; }
    public void setPreprocess(Preprocess preprocess) { this.preprocess = preprocess; }
    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }
    public Prompts getPrompts() { return prompts; }
    public void setPrompts(Prompts prompts) { this.prompts = prompts; }
    public Eval getEval() { return eval; }
    public void setEval(Eval eval) { this.eval = eval; }

    /** Privacy / offline guarantees. */
    public static class Privacy {
        /** If true, the app refuses to start unless all AI endpoints resolve to localhost. */
        private boolean enforceLocalhost = true;
        public boolean isEnforceLocalhost() { return enforceLocalhost; }
        public void setEnforceLocalhost(boolean enforceLocalhost) { this.enforceLocalhost = enforceLocalhost; }
    }

    /** Local Ollama inference engine - the ONLY inference engine. */
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        /** Primary text-structuring model. DECISION(8GB default): qwen2.5:7b-instruct (~4.7GB Q4). */
        private String textModel = "qwen2.5:7b-instruct";
        /** Vision fallback, used only on low OCR confidence. DECISION(8GB): minicpm-v (~5.5GB Q4). */
        private String visionModel = "minicpm-v";
        private double temperature = 0.0;
        private int seed = 42;
        /** Ollama keep_alive. Kept short so the resident large model is released before a model swap. */
        private String keepAlive = "5m";
        private int timeoutSeconds = 120;
        /** Ollama response format; "json" forces JSON-mode output. */
        private String format = "json";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getTextModel() { return textModel; }
        public void setTextModel(String textModel) { this.textModel = textModel; }
        public String getVisionModel() { return visionModel; }
        public void setVisionModel(String visionModel) { this.visionModel = visionModel; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getSeed() { return seed; }
        public void setSeed(int seed) { this.seed = seed; }
        public String getKeepAlive() { return keepAlive; }
        public void setKeepAlive(String keepAlive) { this.keepAlive = keepAlive; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }

    /** PaddleOCR FastAPI sidecar. */
    public static class Ocr {
        private String baseUrl = "http://localhost:8000";
        /** Per-document OCR confidence at/above which we use the text-LLM path; below it we fall back to VLM. */
        private double threshold = 0.75;
        private int timeoutSeconds = 60;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /** Image pre-processing. */
    public static class Preprocess {
        private int pdfDpi = 300;
        /** Longest edge (px) after downscale; keeps OCR/VLM fast and inside VRAM/latency budgets. */
        private int maxEdgePx = 2200;
        /** Max angle (degrees) searched during deskew. */
        private double deskewMaxAngleDeg = 10.0;

        public int getPdfDpi() { return pdfDpi; }
        public void setPdfDpi(int pdfDpi) { this.pdfDpi = pdfDpi; }
        public int getMaxEdgePx() { return maxEdgePx; }
        public void setMaxEdgePx(int maxEdgePx) { this.maxEdgePx = maxEdgePx; }
        public double getDeskewMaxAngleDeg() { return deskewMaxAngleDeg; }
        public void setDeskewMaxAngleDeg(double deskewMaxAngleDeg) { this.deskewMaxAngleDeg = deskewMaxAngleDeg; }
    }

    /** Human-review routing. */
    public static class Review {
        /** Below this overall confidence the document is flagged for human review. */
        private double threshold = 0.80;
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
    }

    /** Versioned prompt resources. */
    public static class Prompts {
        private String version = "v1";
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    /** Eval harness. */
    public static class Eval {
        private String goldenDir = "eval/golden";
        public String getGoldenDir() { return goldenDir; }
        public void setGoldenDir(String goldenDir) { this.goldenDir = goldenDir; }
    }
}
