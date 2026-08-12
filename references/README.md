# Reference Sources

Reference repositories in this directory are local research inputs. They are excluded from the parent repository and must not be imported as untracked vendor source.

## ESP RainMaker Android

- Upstream: https://github.com/espressif/esp-rainmaker-android.git
- Pinned commit: `04f3b3748ff7c500cf5f8e53ee8b47e3db74122f`
- Commit date: 2026-08-06
- License: Apache License 2.0
- Local path: `references/esp-rainmaker-android`
- Intended use: product information architecture, Matter user flows, and implementation research

To reproduce the reference checkout:

```bash
git clone https://github.com/espressif/esp-rainmaker-android.git references/esp-rainmaker-android
git -C references/esp-rainmaker-android checkout 04f3b3748ff7c500cf5f8e53ee8b47e3db74122f
```

If project code is later copied or adapted from this reference, preserve the original copyright header and update `THIRD_PARTY_NOTICES.md` with the exact source files.
