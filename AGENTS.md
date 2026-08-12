# Repository Guidelines

## Project Structure & Module Organization

This repository is the independent workspace for an Android Matter controller Demo. Read `plan.md` before implementation; it defines the staged local-controller and Hub-controller architecture. Android production code belongs under `android/`, with the product application in `android/app/` and the UI-independent Matter facade in `android/matter-app-sdk/`. Cloud and Hub contracts belong under `docs/`; local Hub development configuration belongs under `hub/`.

The sibling `../connectedhomeip` checkout is the authoritative Matter SDK source. Do not modify it as an incidental part of Demo work. `references/` contains local, read-only upstream applications used for design research. Its nested repositories are intentionally ignored. Record each reference URL, pinned commit, and license in `references/README.md` and `THIRD_PARTY_NOTICES.md`.

## Architecture Boundaries

Keep Compose UI, navigation, account state, and camera integration in the App module. Keep Matter commissioning, controller lifecycle, capability discovery, interaction operations, and transport abstractions in `matter-app-sdk`. CHIP/JNI classes must not leak through the public SDK API. The App should depend on repository interfaces so local Matter and cloud/Hub transports can be switched without changing screens or ViewModels.

Do not copy CHIPTool screens or tool-oriented flows. CHIPTool is only a reference for low-level Android Matter calls. ESP RainMaker is a product-flow reference; its cloud client and device model are not project dependencies.

## Security & Attribution

Never commit Wi-Fi passwords, setup payloads, Fabric credentials, signing keys, tokens, `.env` files, or Android keystores. Preserve upstream copyright headers for adapted code and update `THIRD_PARTY_NOTICES.md` in the same change. Do not commit generated APKs, AARs, Gradle output, or complete reference repositories.
