# CLAUDE.md — Katixo Local AI Service (Milestone 1, build notes)

This is the trimmed build guide for the repo. The full product spec lives in the original
requirements doc; this file is what you need to work in the code.

> **Golden rule:** no paid/external AI calls at runtime. All inference is local (Ollama + OCR
> sidecar on `localhost`). Claude Code is used to *build* this; it is never called by the product.

---

## What this is

A Spring Boot microservice that turns one photographed/scanned **invoice / GRN / purchase bill**
into **validated structured JSON** (header + line items) the ERP can post against a PO. Pipeline:

```
preprocess (PDF→img, deskew) → OCR (PaddleOCR sidecar) → route(text|vlm)
  → local LLM via Ollama (JSON-mode) → parse(+1 repair) → deterministic validation
  → confidence + field-level exceptions → persist + per-call log
```

OCR-first, VLM-fallback: a small **text** model structures OCR text by default; a small **vision**
model is used only when OCR confidence is low. This keeps us inside 8 GB and is more reliable for
printed invoices.

---

## Open decisions (spec §12) — ANSWERED for M1

| # | Decision | Choice for M1 | Where |
|---|----------|---------------|-------|
| 1 | GPU VRAM | **4060, 8 GB** (design ceiling). One large model resident at a time. 16 GB switch left in config. | `application.yml` `ollama.*` |
| 2 | Language | **English** (`PaddleOCR lang=en`). Bilingual Hindi/English is a later switch (sidecar `lang`). | `ocr-sidecar/app.py` |
| 3 | Volume | **Tens/day** → synchronous processing + Ollama model-swap is acceptable. Service layer is queue-ready. | `ExtractionPipeline` |
| 4 | Priority doc type | **All three** (`INVOICE` primary). Same schema/validation covers GRN & BILL. | `DocType` |
| 5 | OCR engine | **PaddleOCR sidecar** (default, recommended). Tess4J is a documented future fallback (implement `OcrClient`). | `ocr/OcrClient` |

These are also marked in code with `DECISION` comments. Grep: `grep -rn "DECISION" src ocr-sidecar`.

---

## Model strategy (must fit 8 GB)

| Role | Default (config) | ~VRAM (Q4) |
|------|------------------|-----------|
| Text (primary) | `qwen2.5:7b-instruct` | ~4.7 GB |
| Vision fallback | `minicpm-v` | ~5.5 GB |

Rules respected by the code:
- **Only one large model resident at a time.** Text and vision never co-load; the VLM path pays a
  swap cost (rare path). Tune via `ollama.keep-alive` (default `5m`).
- Model names come **only** from `application.yml`; code is model-agnostic (`LlmClient` +
  `LlmRole.TEXT|VISION` → resolved to a model name in `OllamaLlmClient`). Never hard-code a model.
- **16 GB card:** raise `katixo.ai.ollama.text-model` to a 14B-class instruct model; text + VLM may
  co-reside. Default stays 8 GB.

---

## Architecture map (package → responsibility)

| Package | Role |
|---------|------|
| `web` | `IngestController` (REST, thin), `HealthController`, `GlobalExceptionHandler` |
| `preprocess` | `PreprocessService`: PDF→PNG (PDFBox 300 DPI), grayscale, deskew, downscale |
| `ocr` | `OcrClient` (interface) + `PaddleOcrClient` (HTTP to sidecar) |
| `llm` | `LlmClient` (interface) + `OllamaLlmClient`; `LlmRole`, `LlmRequest/Response/Health` |
| `extraction` | `ExtractionPipeline` (orchestrator), `ExtractionService` (prompt+parse+1 repair), `PromptService`, `ExtractionSchemaValidator` |
| `validation` | `ValidationService` (arithmetic/date/HSN), `GstinValidator` (format + mod-36 checksum) |
| `exception` | `ConfidenceCalculator`, `ExceptionService` (confidence + review decision) |
| `persistence` | `ExtractionRecord`, `CallLog` (+ repos), `RecordService`, `CallLogger` |
| `eval` | `EvalHarness` (pure scoring), `EvalRunner` (CLI, `eval` profile), `EvalReport` |
| `privacy` | `PrivacyGuard` (fail-fast if endpoints aren't localhost), `LocalhostEndpoints` |
| `config` | `AiProperties` (all tunables), `HttpClientConfig`, `AppConfig` |

**Key invariants**
- Controller holds **no logic** → can move behind a queue later.
- Validation runs **after** the LLM, in plain Java, and **never edits numbers** — only records
  field-level exceptions (this is the moat).
- **Every** call is logged (`CallLog`, spec §5.7): file hash, OCR text, model, prompt version, raw
  output, parsed result, validation outcome, latency, path. Nothing leaves the host.

---

## Prompts & schema

- Prompts are versioned resource files: `src/main/resources/prompts/extraction_v1.txt`,
  `extraction_vlm_v1.txt`, `repair_v1.txt`. Version comes from `katixo.ai.prompts.version`.
- Strict JSON Schema: `src/main/resources/schema/extraction.schema.json` (validated by
  `ExtractionSchemaValidator`). Determinism: `temperature=0`, fixed `seed`, Ollama `format: json`.

## Output contract
`com.katixo.ai.model.ExtractionResult` — exactly the spec §5.4 shape (`modelPath` serializes to
`text-llm` / `vlm`).

---

## Build / test / run

```bash
mvn test                                   # 36 tests incl. offline end-to-end proof (no network)
mvn spring-boot:run                        # needs Ollama + OCR sidecar (see README)
mvn spring-boot:run -Dspring-boot.run.profiles=eval   # run the eval harness over eval/golden/
```

The offline guarantee is proven by `OfflineEndToEndTest` (whole pipeline against loopback-only
stubs using the JDK HttpServer) and `PrivacyGuardTest`.

---

## Eval (spec §8)

- Golden set: `eval/golden/*.png` + `*.expected.json`. Ships **4 synthetic** samples so the harness
  runs out of the box; regenerate with `java eval/tools/GenGolden.java`.
- **Operators must add ≥30 real, hand-labelled documents** before trusting the score.
- Metrics: header & line field accuracy, grand-total exact-match, exception precision/recall/F1,
  mean latency, % needing review, and one **composite score** (the gate for any prompt/model change,
  including any future decision to fine-tune).

---

## Out of scope for M1 (do NOT build)
Fine-tuning/training; bank-statement / GSTN-GSP integration; tax-notice drafting; RAG/vector search
(pgvector reserved for M2); auth/multi-tenant; UI. Architecture must not block these.
