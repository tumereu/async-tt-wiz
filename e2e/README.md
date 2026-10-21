# Host-side E2E boundary

Kotlin/JUnit host runner. The runtime test only qualifies the Java toolchain.
The [I01e1 synthetic adapters](adapters/README.md) supply qualified separate HTTP
catalog/art and SMTP sink services. I01e2/I01f still add the isolated real backend
Compose environment and Appium/remote-ADB adapter.
Future gameplay setup must use the normal Android UI, not linked domain processors
or injected game rows. This module intentionally has no product-module dependency.
