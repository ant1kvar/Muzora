# F-Droid packaging

Draft build metadata for a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
Store listing (descriptions, icon, screenshots, feature graphic) lives in
[`fastlane/metadata/android/`](../../fastlane/metadata/android/) and is picked up
automatically from the GitHub source repo.

## Checklist before MR

1. Push this repository to GitHub: `https://github.com/ant1kvar/Muzora`
2. Tag a release F-Droid can build **without** a local keystore:
   ```bash
   git tag -a v1.2.2 -m "v1.2.2"
   git push origin main --tags
   ```
   (or create the tag on GitHub Releases UI)
3. Confirm HTTPS clone works:
   ```bash
   git ls-remote https://github.com/ant1kvar/Muzora.git
   ```
4. Fork https://gitlab.com/fdroid/fdroiddata
5. Copy [`com.muzora.yml`](com.muzora.yml) to `metadata/com.muzora.yml` in your fork
   (set `commit:` to the tagged release SHA or keep `v1.2.2` if the tag exists)
6. Open an MR titled **New App: Muzora** and watch the CI build

Optional local check (needs [fdroidserver](https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/)):

```bash
fdroid build -v -l com.muzora
```

## Notes

- **AntiFeatures:** `UsesCleartextTraffic` — LAN Navidrome and many radio streams still use HTTP (`network_security_config` permits cleartext).
- **Signing:** F-Droid signs with its own key. Upstream `assembleRelease` is unsigned unless `keystore.properties` or `MUZORA_USE_DEBUG_SIGNING=1` is set.
- **Auto-update:** Git tags + `versionCode` / `versionName` in `app/build.gradle.kts`.
- **Metadata:** `en-US` and `ru-RU` under `fastlane/metadata/android/` (title, descriptions, changelogs, icon, screenshots, featureGraphic).
