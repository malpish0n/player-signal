# Manual quality and release review

## Initial evidence (superseded runtime status in operations.md)

On 2026-10-01 a read-only local database inspection returned migration V7, Portal 2 with 1,000 imported reviews and Counter-Strike: Source with zero. Ollama was not found on the command PATH. New source and migrations V8–V16 were not run. These facts do not satisfy a 3–5-game quality gate, and no accuracy, semantic grouping quality, responsiveness or latency result is claimed.

The owner requested no automated tests or application builds in this batch. Static source inspection found and corrected Compose substitution errors, inconsistent workspace authorization reads, a local-rule negation error and retention's cached-analysis reference behavior. Type/lint/config checks are not substitutes for runtime acceptance.

## Review after an updated local runtime is available

Use 3–5 real games with different review volumes and problem categories. Record game IDs, import dates, sample boundaries, provider/model/prompt, embedding model/revision/threshold and grouping build time. Keep fixtures separate from imported data. Do not send source reviews to a paid provider.

For each game inspect at least 30 varied reviews, including actionable, positive, generic, negated/resolved, multilingual and unmatched examples. Record the exact source, predicted category/actionability, human judgment and a short reason. Inspect each quoted substring in context. Include skipped/unclassified reviews when assessing coverage; do not calculate accuracy only from the easiest successful matches. Local English phrase rules should remain explicitly described as rules, with unsupported languages skipped and uncertain text unclassified.

Inspect at least ten issue groups per game where available. Record false merges, false splits and supporting quotations, including groups with only one mention. Compare lexical and local semantic snapshots on the same source sample. Do not accept a high cosine score as proof that two complaints have the same cause. Tune the semantic threshold only on a separate reviewed sample; retain held-out examples for the final human check.

Walk through a known update using equal complete UTC windows. Verify the denominators, exact review links, NEW/GROWING/DECLINING labels and saved report excerpts. A rise after an update is temporal association, not a causal conclusion. Inspect weekly and patch reports with no data, missing baseline and stale grouping.

## Product and data controls, using disposable accounts/data

- OWNER can create/revoke a share and configure retention. ADMIN cannot perform owner-only actions; MEMBER cannot mutate game data. Cross-workspace IDs must return no private data.
- Shared links work without cookies, reveal only their explicit projection, expire/revoke, rotate old links, and stop working when the report/game is deleted. Previously downloaded copies cannot be recalled.
- Review retention clears issue grouping, removes matching analyses/embeddings and detaches cached-source references without deleting newer copied classifications. Report retention removes related shares. Unread notifications and usage counts remain.
- Busy import/analysis/grouping prevents deletion; disabled policies start no work. Dispatcher restart does not fabricate progress or silently invoke cloud services.
- Check source evidence, filtering, CSV, saved views, report creation/retry, browser print and mobile keyboard navigation in the updated app, including errors and empty states.

Record actual observations, failures and fixes with commit/model versions. Leave unmet criteria open; this document is a review procedure, not evidence that the checks passed.

## Updated runtime evidence — 2026-10-01

The owner later approved builds/restart. Images built, V8–V17 migrations applied after a backup, and core API/UI reads plus report creation and idempotent retry passed manual checks. Ollama qwen3:4b inference ran on imported Portal 2 reviews. The initial sample exposed a humor/category false positive; prompt v2 clarifies concrete actionability and technical performance. This small diagnostic sample is not an accuracy benchmark or completion of the 3–5-game review. Automated tests were not run.
