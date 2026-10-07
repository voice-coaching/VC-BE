# Native-only production compatibility

Accept the exact Native v4 core/H5 identities built from the September 28 release. Preserve historical result verification. The shared v4/v5 result schemas include the new identity without changing historical result fields.

Deployment profiles may add the complete three-field Native pin group. Native activation uses the separate Python 3.12 / NumPy 2.4.6 verifier environment, switches its executable with the verifier bundle, and checks the active RunPod Native identity. Historical scored pins remain available for archived records.

Local validation: Java compilation and bootJar packaging; Python syntax and schema identity checks. Automated regression and browser QA were not run. Production activation and the user-requested audio measurement are recorded in intelligentAI `docs/canonical-integration/native-only-production-20261005.md`.
