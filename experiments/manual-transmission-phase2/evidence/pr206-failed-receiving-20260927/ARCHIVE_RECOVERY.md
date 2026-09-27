# Recovering the complete original failure packet

This directory retains the unchanged33,916,511-byte archive as five contiguous raw parts because the complete archive exceeds the32MiB transfer limit. The parts are not separate ZIPs. `archive-parts.json` binds every part, its exact offset/size and the whole archive SHA-256 `a24c895c150f03c8f271183caa8280e996705d5184d22c1f3bcf116d034df029`.

Download this directory's five `seven-failed-attempts.zip.partNN` files, `archive-parts.json` and `reconstruct_archive.py` into one directory, then run:

```sh
python reconstruct_archive.py
```

The helper validates every part and the complete byte stream before creating `seven-failed-attempts.zip`; it refuses to overwrite an existing output. `--output PATH` selects another new file. It reads/writes archive bytes only and executes none of the preserved repository source or tests. An actual local reconstruction matched the entire original byte stream; `reconstruction-check.json` records that check and the interrupted transport history.

`README.md` describes the seven original failed workflows and bounded partial results. `preservation-manifest.json` lists every preserved inner file. `package-receipt.json` is the immutable packaging-time record, whose pending-review/publication fields refer to that earlier creation point; accompanying subsequent peer/root reviews and publication checkpoint supply later dispositions. The original archive and its manifests are not rewritten after review.

The separate `static-dependency-review-correction-pr206.json` preserves the non-author reviewer's correction to the earlier test-dependency closure claim. A one-file donor fixture proposal is separately reviewed; it does not close the other failed assertions or confer admission. No official gameplay evidence changed.
