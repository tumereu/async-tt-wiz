# Android distribution and compatibility

## Selected direction — D-273

- Versioned signed APK downloads initially; no required Play Store distribution.
  GitHub Releases is a possible host, not a selected external service.
- Manual upgrades through Android's normal package installer; no custom in-app
  updater or store-specific update SDK is required for initial delivery.
- Stable application package/signing identity, with signing secrets outside Git.
- Minimum **Android 10 / API 29**; phones/tablets and a non-Google device/emulator
  belong in verification. Exact reference devices/resources remain to define.
- Compatible upgrades preserve login/downloads/recovery. Protocol versions are
  independent of APK versions; compatible different app versions can share a game.
- Clearly report an incompatible-client update requirement and refuse unsupported
  requests safely, without discarding pending data or inventing prior outcomes.

The [Android contract](../03-contracts/android-experience.md#android-support-distribution-and-upgrades--d-273)
owns observable behavior. This selects release/support policy, not application,
build/signing pipeline implementation or a delivery-stage transition.

## Application-side completion

Specify monotonically increasing install version codes and human-readable release
versions alongside explicit HTTP/WebSocket schema/protocol compatibility metadata.
Wire decoding alone is not proof of compatibility: command semantics, shared-engine
behavior and recorded-history formats must remain mutually supported. Do not assume
every library upgrade preserves deterministic random/replay algorithms or request
payload meaning. Exact negotiation/capability/error schemas remain design work.

Pin/test dependencies against API 29 rather than silently raising the floor. Keep
compile/target SDK choices separate from minimum SDK and comply with distribution
requirements when releasing. Direct APK distribution is not an exemption from
Android installation/signing/developer-verification rules. Play services/FCM remain
optional under D-269; device support is not conditional on Google Sign-In.

Android update/signature verification requires a valid signing continuity path;
do not tell users to uninstall or clear data to work around a bad release. The
normal compatible-upgrade guarantee does not cover manually deleting app data,
losing Keystore keys or restoring to an unrelated signing identity. Protect release
keys separately from runtime session credentials and keep production secrets out
of artifacts/logs. Exact key custody/distribution automation remains to arrange,
not permission to provision the external operations repository.

Define SQLDelight migration paths across retained supported installed versions,
including interrupted/failed migrations, original request/exposure obligations,
accepted definition snapshots, downloaded coverage and local personal state.
Keep pending request IDs, original payload semantics/random inputs and outcome
lookup context intact across upgrades. An incompatible request-format version
must not be silently transformed into a fresh invocation under the same identity.
The exact safe request compatibility/recovery mechanism remains protocol design.

An update-required response is distinct from invalid authentication, unknown
resources and a gameplay rejection. Negotiate/refuse incompatible operations
without partial mutation; after upgrade use normal idempotent result recovery.
Support old readable local data where already promised, not a blanket guarantee
that an old app can interpret new records/features. Define supported combinations
and failure/recovery behavior explicitly; no universal forever-support window.

D-272 keeps production service deployment/backups in a separate repository. This
project owns the application artifact/compatibility/migration contract and its
tests; it does not acquire production host or backup automation responsibilities
through this Android distribution decision.
