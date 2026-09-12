# Inkora storage

Inkora keeps document metadata in SQLDelight and document payloads in an application-managed directory. Imported PDFs and other assets are copied into managed storage before a document row is committed, so a user moving or deleting the original file cannot break an Inkora document.

The `documents`, `folders`, `pages`, `annotations`, `bookmarks`, `recents`, `settings`, `session_state`, and `trash_metadata` tables are defined in `composeApp/src/commonMain/sqldelight/com/inkora/database/Inkora.sq`. Schema upgrades are additive SQLDelight migrations (`1.sqm` and `2.sqm`); destructive migration is not part of normal startup.

Page ink is stored as a versioned stroke blob per page. A single blob avoids millions of SQL rows for point samples while keeping page metadata queryable. The blob codec can be replaced without changing the drawing or repository contracts.

Trash is a metadata state. Moving a document to trash sets `is_trashed` and records the original folder in `trash_metadata`, allowing restore. Permanent deletion is an explicit operation that removes metadata and managed assets through the platform file store.

Backups should contain a manifest with `InkoraConfig.backupFormatVersion`, database metadata, and managed assets. The backup extension is centralized as `.inkorabackup`.
