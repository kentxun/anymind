# Supported Features (Summary)

Last updated: 2026-02-05

## Overall
- Local-first storage; all search and tag operations are on-device.
- Tags parsed from content using `#tag` syntax; supports AND/OR tag filtering.
- Full-text search via SQLite FTS.
- Grouping by day / week / month (week default).
- Sync is optional per record; local is source of truth.

## macOS Client (MVP)
- Create / edit / delete records with auto timestamps.
- Tag parsing, tag list with counts, clickable tags to filter.
- Tag filter chips + AND/OR mode.
- Sync status icon (blue synced / gray unsynced / red disabled).
- Sync settings (server URL, space ID/secret, device ID), manual sync, optional sync on launch.
- Conflict handling: conflict copy with `#conflict` tag.

## iOS Client
- See `FEATURES_IOS.md` for the full iOS feature list.

## Android Client (MVP)
- Main screen: content search, tag search, selected tags, tag candidates (expand/collapse), history list.
- Tag filters with AND/OR mode (shown when 2+ tags selected).
- Bottom-sheet date filter for day/week/month and group selection.
- Record detail screen with autosave, manual save, tag highlight, tag chips, sync toggle + status.
- Swipe-to-delete in history list.
- Settings: sync enable, sync-on-launch, server URL, space ID/secret, device ID, create space, show secret.
- Manual sync from toolbar.

## Server (Java, sync-only)
- Create space (space_id + space_secret).
- Push / pull sync endpoints with conflict flag.
- SQLite storage per space and incremental changes table.

## Sync Rules
- Records default to not syncing; user must enable sync per record.
- Turning off sync removes the cloud record (local keeps).
- Local delete removes the cloud record for synced items; cloud deletes never remove local data.
