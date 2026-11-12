# Camera HT baseline verification — 2026-11-12

Development base: `4bee65fdfa859bd49b72c768b0cc2ddecb958f9d`, branch `fix/camera-ht-android-ios`.

- iOS workflow run 42448546912, run number 62, passed build, stamp tests, hosted purchase tests and UI screenshots; signed archive and App Store Connect upload also passed. Signed workflow sets CFBundleVersion = 100 + GITHUB_RUN_NUMBER, producing 162, marketing version 1.1.0. App Store approval/release is reported by the owner; repository evidence independently confirms the uploaded build, not approval.
- Android run 42381838999 passed for `dfe86b8921b9af289d3a515d71168bee289afabf`, versionCode 7. `git diff dfe86b8 4bee65f -- android` is empty. Google Play release identity has not been independently verified.
- main (`a3985df`) is an older 1.0 source and is not the development base.
- Baseline still wires iOS advertising and StoreKit; only purchase settings UI was hidden. New branch excludes this code from production compilation and removes runtime startup, dependencies, and ad metadata. Historical product ID definitions remain unchanged in excluded files.
- No store upload, signing, publishing or store configuration changes are authorized by this work. Run validation workflows with appstore=false and upload=false only.

Evidence: https://github.com/datnguyenvip/datvip-cam/actions/runs/42448546912 and https://github.com/datnguyenvip/datvip-cam/actions/runs/42381838999.
