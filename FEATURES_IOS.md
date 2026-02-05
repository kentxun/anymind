# iOS Features

Last updated: 2026-02-05

## UX Structure
- Main screen: tag search + selected tags + history list.
- Date filter in a bottom sheet (day/week/month + group list).
- Detail view opens in a separate page with back navigation.

## Record Management
- Create / edit / delete records with auto created/updated timestamps.
- Autosave + save-on-switch; manual save shows success feedback.
- Full-text search (SQLite FTS).
- Grouping by day / week / month (week default).

## Tags
- Tags parsed from `#tag` in content.
- Clickable tag chips in detail view add filter on the list.
- AND/OR tag filter modes for multi-tag search.
- Tag list with search and usage counts.

## Sync (Optional)
- Local-first; each record has its own sync toggle (default off).
- Manual sync button and optional sync on launch.
- Sync status icon: blue = synced, gray = not synced, red = sync disabled.
- Turning off sync removes cloud record (local keeps).
- Local delete removes cloud record for synced items.
- Cloud deletes are ignored locally.
- Conflict handling: generates conflict copy with `#conflict` tag.

## Settings
- Server URL + Space ID/Secret + device ID.
- Create Space from client.
- Show/Hide secret.
