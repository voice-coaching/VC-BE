# Backend runtime environment

The backend process reads one private environment file, `/opt/alpha/.env`, beside
`/opt/alpha/app.jar`. Its owner is root and its permissions are `0600`. systemd
loads it before starting the service; the application does not automatically
read a repository `.env` file. The service working directory remains unchanged.

For local development, keep private settings in `VC-BE/.env` and inject them
through the IDE or process launcher. `.env` and `.env.*` are ignored by Git;
only `.env.example`, containing public placeholders, is tracked. The production
env is maintained on the server and is never uploaded by CI or included in a JAR.

## Initial migration

Run the deployment scripts from a trusted copy on the backend host as root:

```sh
python3 -B scripts/deployment/consolidate_runtime_env.py
python3 -B scripts/deployment/consolidate_runtime_env.py --apply
```

The default command checks without restarting the backend. Both commands compare
the effective legacy files and inline service variables with the running process,
then verify serialization with the real systemd parser in an isolated transient
unit. Values are never printed or passed as command arguments.

Apply acquires the deployment lock, creates a private backup under
`/var/lib/alpha-deploy/backups/runtime-env-*`, closes analysis admission and waits
for existing work to drain. It publishes the env without overwriting any existing
file and adds `99-runtime-env.conf`, which resets both `Environment=` and
`EnvironmentFile=` before selecting `/opt/alpha/.env`. It restarts the backend,
checks health, compares every migrated variable and verifies the release stayed
unchanged, then reopens admission. An already migrated, healthy installation is
verified without restarting again.

Legacy env files and earlier drop-ins stay in place as inactive recovery inputs;
they are not deleted. PEM keys and Google credential JSON files remain private
files at their existing locations; their path variables are consolidated.
`/etc/alpha-deploy/runtime.json` and systemd definitions remain deployment config.

## Deployment and recovery

Deployments preserve the consolidated env. The browser-video signing helper
validates its settings and existing keys without rewriting the env, changing an
operator-selected flag or regenerating a missing managed key.

If activation fails, the migration removes only its own unchanged override,
reloads systemd and restarts using the preserved legacy inputs. It verifies the
restored process before reporting rollback. If rollback cannot be verified,
`ENV_ROLLBACK_REQUIRES_OPERATOR` is returned and admission stays closed. Inspect
service health and the root-only backup before recovery; do not remove the gate
until the backend is healthy. Never copy private backups into Git or tickets.

For later manual recovery, coordinate with deployments and drain work first.
Remove only the migration's `99-runtime-env.conf` after verifying it has not been
modified, reload systemd, and restart using the still-present legacy files.
Validate health and settings before reopening admission. Later edits to the
consolidated env are not automatically copied back into those legacy files.
