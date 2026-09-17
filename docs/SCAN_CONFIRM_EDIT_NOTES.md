# Scan confirmation editing

Per-face confirmation now supports explicit user color overrides before a face is committed. Overrides are stored separately from camera guesses and are enforced during final center-calibrated classification when the corresponding center color is known.

Finalization and validation are guarded so malformed/invalid scans should stay in the UI with an error instead of crashing the app.
