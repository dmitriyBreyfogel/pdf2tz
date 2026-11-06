# Maintenance document layout revision: real full-flow results

These artifacts were produced by `POST /api/v1/maintenance/document` after the document-layout and LLM-contract refinements. Every successful multipart response was split into:

- `document.docx` — generated maintenance specification;
- `plan.json` — normalized model answer used to create the document;
- `consumables.json` — consumables returned in the same response.

The input was `1375319.pdf` from the local `Инструкции` directory: a 73-page KARL STORZ C-CAM manual with German, English and Russian parallel text.

| Model | Result |
| --- | --- |
| `openai/gpt-6.1-terra` | 37 sequential maintenance rows, 20 consumables, one device name in every row, and a validated DOCX. |
| `openai/gpt-5.6-luna` | 16 sequential maintenance rows, 17 consumables, one device name in every row, and a validated DOCX. |

The final DOCX layout no longer contains an appendix label or JSON section rows. Its title is formed as `Перечень услуг по техническому обслуживанию медицинского изделия: <device name>`. The work type is the bold first line in the work-description cell; its detailed steps follow as dash-prefixed paragraphs. All table cells use internal top, bottom, left and right margins.

The final Terra plan demonstrates sequential numbering from 1 through 37, including remapped consumable-to-row links. The Luna plan demonstrates that the strengthened prompt separates before/after conditions into distinct rows and returns explicitly named consumables.

`openai-gpt-6.1-terra-before-numbering-fix` is retained only as a diagnostic snapshot of the immediately preceding real run. It shows the original non-sequential numbering emitted by the model; it is not the final expected output.
