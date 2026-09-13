# Import and search

The native picker accepts PDF, DOC/DOCX, and PPT/PPTX files on Windows and
Android. DOCX/PPTX (including macro-enabled DOCM/PPTM) are read directly from
their Open XML package and become searchable text documents. Legacy binary
DOC/PPT files use a best-effort text run extractor; saving them as DOCX/PPTX
keeps headings and layout intact.

Library search now searches titles, source filenames, imported text, notes,
bookmarks, and text annotations. Search remains local-first and never uploads
document contents.

On Android, scanned PDFs are rendered locally and passed to the bundled ML Kit
Latin text recognizer. OCR results are cached for the open session, and the
PDF **Find text** action searches those results. No page image or OCR text is
sent to a server.
