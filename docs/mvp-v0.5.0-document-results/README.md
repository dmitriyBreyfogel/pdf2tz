# MVP v0.5.0: full document-generation runs

Each run calls `POST /api/v1/maintenance/document` once. A successful `multipart/mixed` response is split into:

- `document.docx` — generated maintenance specification in the Word-table layout;
- `plan.json` — full normalized, source-grounded LLM response;
- `consumables.json` — the consumables part returned in the same response.

The input was `1375319.pdf` from the local `Инструкции` directory: a 73-page KARL STORZ C-CAM camera-head manual with German, English, and Russian parallel text. It is one device manual and exercises multilingual extraction.

| Model | HTTP result | Result |
| --- | --- | --- |
| `openai/gpt-6.1-terra` | 200 | 21 rows, 19 consumables; DOCX ZIP validated. |
| `anthropic/claude-sonnet-5.5` | 200 | 18 rows, 11 consumables; DOCX ZIP validated. |
| `qwen/qwen3.5-397b-a17b` | 200 | 10 rows, 14 consumables; DOCX ZIP validated. |
| `sber/gigachat-3.5-ultra` | 200 | 5 rows, 8 consumables; DOCX ZIP validated. |
| `deepseek/deepseek-v4.1-flash` | 502 | Model JSON failed the plan contract; no DOCX was produced. See `error.json`. |
| `yandex/gpt-lite-5` | 502 | RouterAI returned `402 Insufficient balance` before a model response; no DOCX was produced. See `error.json`. |

`inventoryNumber` is `null` in every successful plan, as required. Source page references remain in JSON evidence only and are intentionally not inserted into the Word table. The error artifacts are retained so the external-provider failures are reproducible and distinguishable from a generated plan.
