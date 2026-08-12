# connectedhomeip's JNI resolves these controller callbacks and models by name.
-keep class chip.devicecontroller.** { *; }
-keep class matter.onboardingpayload.** { *; }
-keep class matter.tlv.** { *; }

# Preserve public SDK model names for Java consumers and persisted enum values.
-keep public class com.example.matter.api.** { public protected *; }
