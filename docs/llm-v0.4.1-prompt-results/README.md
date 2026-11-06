# v0.4.1 LLM prompt comparison

Each JSON file is a real end-to-end response to the same English Cell Saver 5+ manual (`1373694.pdf`) after the v0.4.1 prompt and response-contract changes. The API response is preserved under `response`; the actual maintenance plan is `response.plan`.

| Model | Rows | Consumables |
| --- | ---: | ---: |
| GPT-6.1 Terra | 11 | 10 |
| Claude Sonnet 5.5 | 12 | 6 |
| GigaChat 3.5 Ultra | 7 | 5 |
| YandexGPT Lite 5 | 7 | 5 |
| DeepSeek V4.1 Flash | 10 | 7 |
| Qwen 3.5 397B A17B | 9 | 11 |

All six files passed the server-side response normalizer and full plan-contract validation. Counts are not quality scores: the original instruction must be used to review completeness and whether a model has merged or omitted distinct works.
