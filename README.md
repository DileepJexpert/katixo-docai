# Katixo Local AI Service — Milestone 1

Local-only document extraction for the Katixo ERP. Turns a photographed/scanned **invoice / GRN /
purchase bill** into **validated structured JSON** (header + line items) — with **zero paid AI calls
at runtime**.

> **Privacy by construction.** All inference runs locally: an LLM via **Ollama** and OCR via a
> **PaddleOCR sidecar**, both on `localhost`. No document bytes ever leave the host, and the service
> *refuses to start* if any AI endpoint is not loopback (`katixo.ai.privacy.enforce-localhost`).
> Designed for an **NVIDIA RTX 4060, 8 GB VRAM** — one large model resident at a time.

```
PDF/IMG ─► preprocess (PDF→img 300dpi, deskew) ─► OCR (PaddleOCR) ─► route
        ─► local LLM (Ollama, JSON-mode) ─► parse (+1 repair) ─► deterministic validation
        ─► confidence + field-level exceptions ─► persist + per-call log  ─► JSON
```

OCR-first, **VLM-fallback**: a small text model structures OCR text by default; a small vision model
is used only when OCR confidence is low.

---

## Prerequisites

- **Java 21** and **Maven 3.9+**
- **Ollama** (native install recommended on the GPU box) or Docker
- **Docker + Docker Compose** (for Postgres + OCR sidecar; optionally Ollama)
- For GPU Ollama in Docker: the **NVIDIA Container Toolkit**

---

## 1. Start the local dependencies

`docker-compose.yml` runs **Postgres + Ollama + OCR sidecar**, each published to `127.0.0.1` only
(nothing is reachable off-host). The Spring app runs **natively** on the host and reaches them via
`localhost` — keeping the privacy guarantee intact.

```bash
docker compose up -d --build         # postgres:5432, ollama:11434, ocr-sidecar:8000 (all loopback)
docker compose ps
```

Prefer Ollama natively on the GPU box (best performance)? Run `ollama serve` instead and drop the
`ollama` service from compose. Either way the endpoint stays `http://localhost:11434`.

## 2. Pull the models (quantized, fits 8 GB)

```bash
ollama pull qwen2.5:7b-instruct      # primary text model (~4.7 GB Q4)
ollama pull minicpm-v                # vision fallback (~5.5 GB Q4)
# if Ollama runs in compose:  docker compose exec ollama ollama pull qwen2.5:7b-instruct
```

The OCR sidecar bakes its PaddleOCR models into the image at build time, so it runs **offline** after
the first `--build`.

## 3. Run the service

```bash
mvn spring-boot:run
# or: mvn -DskipTests package && java -jar target/katixo-ai-service-0.1.0-M1.jar
```

## 4. Extract a document

```bash
curl -s -F "file=@eval/golden/sample-01.png" -F "docType=INVOICE" \
     http://localhost:8080/api/v1/extract | jq .
```

Example (abridged) response — see the full contract in `model/ExtractionResult`:

```json
{
  "id": "…", "docType": "INVOICE", "modelPath": "text-llm",
  "header": { "supplierGstin": "29ABCDE1234F1ZW", "grandTotal": 10024.0, "currency": "INR", "…": "…" },
  "lineItems": [ { "description": "Paracetamol 500mg", "hsn": "3004", "taxableValue": 4500.0, "…": "…" } ],
  "confidence": 0.98,
  "exceptions": [],
  "needsHumanReview": false
}
```

### API

| Method & path | Purpose |
|---|---|
| `POST /api/v1/extract` | multipart `file` (+ optional `docType=INVOICE\|GRN\|BILL`) → extraction result |
| `GET /api/v1/extract/{id}` | fetch a past result |
| `GET /api/v1/health` | service + Ollama + OCR reachability and the loaded/configured model |

A downed Ollama/OCR sidecar returns **503** with a clear message — never a 500/stacktrace.

---

## Running fully offline (air-gapped)

Once images and models are present (steps 1–2 done once with network), the running product needs
**no internet**:

- Both AI endpoints are loopback and enforced at startup (`PrivacyGuard`).
- The OCR sidecar has its models baked in.
- No telemetry, no external logging — every call is stored locally in Postgres (`call_log`).

This is **proven by tests**, not just claimed: `OfflineEndToEndTest` runs the whole pipeline against
loopback-only stubs (JDK `HttpServer`), and `PrivacyGuardTest` asserts non-local endpoints are
rejected.

```bash
mvn test            # 36 tests, no external network required
```

---

## Eval harness (measure accuracy, don't guess it)

Golden set lives in `eval/golden/` (`<name>.png` + `<name>.expected.json`). **4 synthetic samples
ship so the harness runs out of the box.** Replace them with **≥30 real, hand-labelled** documents.

```bash
# with Ollama + OCR sidecar running:
mvn spring-boot:run -Dspring-boot.run.profiles=eval
```

Prints field-level accuracy (header + line items), grand-total exact-match rate, exception
precision/recall/F1, mean latency, % needing review, and a single **composite score** — the gate for
any prompt/model change (and any future decision to fine-tune). Regenerate synthetic samples with
`java eval/tools/GenGolden.java`.

---

## Configuration (all in `src/main/resources/application.yml`, prefix `katixo.ai`)

| Key | Default | Meaning |
|---|---|---|
| `privacy.enforce-localhost` | `true` | refuse to start if an AI endpoint isn't loopback |
| `ollama.base-url` | `http://localhost:11434` | Ollama endpoint |
| `ollama.text-model` | `qwen2.5:7b-instruct` | primary text model (raise to 14B on a 16 GB card) |
| `ollama.vision-model` | `minicpm-v` | low-OCR-confidence fallback |
| `ollama.temperature` / `seed` | `0` / `42` | determinism |
| `ollama.keep-alive` | `5m` | short, so the resident model releases before a swap |
| `ocr.base-url` | `http://localhost:8000` | OCR sidecar |
| `ocr.threshold` | `0.75` | below this OCR confidence → VLM path |
| `preprocess.pdf-dpi` / `max-edge-px` | `300` / `2200` | render DPI / downscale cap |
| `review.threshold` | `0.80` | below this confidence (or any exception) → human review |

Models and thresholds are **config, not code** — the service is model-agnostic.

---

## Repo layout

```
src/main/java/com/katixo/ai/{web,preprocess,ocr,llm,extraction,validation,exception,persistence,eval,privacy,config,model}
src/main/resources/{application.yml, prompts/, schema/extraction.schema.json}
ocr-sidecar/                 # FastAPI + PaddleOCR (CPU); models baked in for offline use
eval/golden/                 # synthetic labelled docs + expected JSON (replace with real ones)
eval/tools/GenGolden.java    # regenerates the synthetic golden set
docker-compose.yml           # postgres + ollama + ocr-sidecar (all bound to 127.0.0.1)
CLAUDE.md                    # build notes + answered open decisions
```

Milestone 1 only. M2 (reconciliation + RAG) and M3 (tax-notice drafting) are out of scope but the
architecture leaves room for them (e.g. `pgvector` reserved for M2).
