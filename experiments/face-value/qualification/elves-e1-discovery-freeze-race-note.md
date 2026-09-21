# Elves E1 discovery freeze publication race — provenance note

Date: 2026-09-21

Workflow run `35621144779` generated a valid 32-seed E1 discovery vector locally, but the final push was rejected because `face-value/lab` advanced concurrently.

Disposition:
- The unpublished vector from run `35621144779` is permanently retired.
- No discovery game was executed from that vector.
- No outcome was exposed.
- The vector was never committed to the repository and will not be reconstructed or reused.
- The freeze workflow was amended only to rebase its local freeze commit onto the current remote branch before publication.
- The next successful freeze run must generate a new fresh vector and becomes the sole authoritative E1 discovery vector.
