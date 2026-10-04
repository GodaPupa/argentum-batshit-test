# Announced target groups — implementation checkpoint

The generic cast action now accepts explicit per-requirement cardinalities, including zero for omitted optional groups. The values survive copied payment actions, spell placement, spell copying, target replacement and serialization in `TargetsComponent.announcedTargetCounts`.

Legacy flat announcements are partitioned only when the requirement cardinality constraints have exactly one solution. No target legality or current board state is consulted to guess group identity. Ambiguous flat announcements require explicit counts. Contested retargeting reads the stored group cardinalities instead of greedily expanding maximum counts.

This is not Spellskite admission. Interactive target-selection producers must supply explicit counts for ambiguous declarations, and activated/triggered ability announcement paths still need explicit-count plumbing where cardinality alone cannot identify the partition. The fixed-destination single-slot operation remains separate work. Existing modal/splice/divided allocation atomic updates remain part of this candidate.

Focused gate: RetargetStructureBoundaryTest (7), AnnouncedTargetGroupsTest (5). No registry, gameplay, or official counter change is authorized by this checkpoint.
