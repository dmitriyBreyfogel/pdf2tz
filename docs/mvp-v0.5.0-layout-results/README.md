# Maintenance document layout revision: real full-flow results

These artifacts were produced by `POST /api/v1/maintenance/document` after the document-layout and LLM-contract refinements. Every successful multipart response was split into:

- `document.docx` — generated maintenance specification;
- `plan.json` — normalized model answer used to create the document;
- `consumables.json` — consumables returned in the same response.

The input was `1375319.pdf` from the local `Инструкции` directory: a 73-page KARL STORZ C-CAM manual with German, English and Russian parallel text.

| Model | Result |
| --- | --- |
| `openai/gpt-6.1-terra` | 34 sequential maintenance rows, 14 consumables, one concise device name in every row, and a validated DOCX. |
| `openai/gpt-5.6-luna` | 17 sequential maintenance rows, 7 consumables, one concise device name in every row, and a validated DOCX. |

The final DOCX layout no longer contains an appendix label or JSON section rows. Its title is formed as `Перечень услуг по техническому обслуживанию медицинского изделия: <device name>`. The work type is the bold first line in the work-description cell; its detailed steps follow as dash-prefixed paragraphs. All table cells use internal top, bottom, left and right margins.

Both final plans use `Видеоголовка эндоскопическая KARL STORZ C-CAM` as the device name. Regional variants are normalized for the Russian Federation: a non-US procedure is selected where the manual distinguishes US and non-US markets, and country or market labels are not included in the resulting rows. The plans retain servicing of related cleaning brushes, but contain one ordinary daily procedure for each applicable cleaning, disinfection and sterilization activity.

`openai-gpt-6.1-terra-before-numbering-fix` is retained only as a diagnostic snapshot of the immediately preceding real run. It shows the original non-sequential numbering emitted by the model; it is not the final expected output.
