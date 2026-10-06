# DLPC GOP registration

Adds the immutable DLPC CUDA GOP tuple and matching v4/v5 schemas. The merged GOP extraction and GPT rubric are preserved. User authorized production migration and actual CUDA/TTS sample execution on 2026-10-07. Compilation and operator sample observations are recorded separately; no automated regression QA is run.

## Production observation — 2026-10-07 01:45 KST

- Live Backend source: `66f8ea8aaf4325cd87f1b4e783257f9f9ea3fedf`; JAR SHA-256: `d5eb1a370d5eba1e78a1335f570a3783a0fefde038caa2562636e23c42d417d7`.
- AI source: `e5c612f51850a0a3f51e901b53d4d806667eb3c3`; bundle: `906df1334b8482a6c1fade401534dd8ea977edec8130da61acca9818b29f344c`.
- AWS frozen verifier bundle: `65bb6af462f61d4ac59ecf5e7c073f7f7e756279fa05cffd7675132f448eb09b`. All nine packaged schema digests matched.
- `bootJar --offline --no-daemon -x test -x check` succeeded. No automated regression tests ran.
- Admission was held and analysis/transaction/TTS drain was checked twice before activation. Backend release state is `COMPLETE`; maintenance hold is removed.
- TTS revision is `melo-ko-practice-dlpc-r1`; fingerprint is `ace97bc52df31f304513bc5f18177669723fe996f5cd19235691a5449d9f3766`.
- AWS nginx routes analysis/direct to `127.0.0.1:18080` and TTS to `127.0.0.1:18083`, through DLPC's independent reverse SSH connection.
- One authorized audio sample completed actual CUDA/GPT analysis, B2 archival and Backend history synchronization. Public TTS synthesis returned 200 with matching audio digest. Human listening and broad load/quality coverage were not performed.
- RunPod was stopped, not deleted. After its API state became `EXITED`, public analysis/direct readiness and authenticated analysis/TTS health remained 200. The retained 120 GB volume is separate from compute shutdown.
- Frontend already deployed the stable AWS direct URL; no additional frontend PR was necessary.

Full evidence, immutable tuple hashes, private rollback paths and recovery limitations are in intelligentAI `docs/canonical-integration/dlpc-gop-cutover-20261007.md`. B2 is a backup source, not a POSIX journal disk; after container recreation, diagnostic recovery requires operator reconciliation before production admission. Full recreation was not executed during this deployment. Later documentation commits do not change the live JAR identity above.
