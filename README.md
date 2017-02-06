# CopperPDF

## Description

CopperPDF is a PDF viewer based on [pdf.js](https://mozilla.github.io/pdf.js/) in a WebView. It doesn’t require any permissions since it relies on content providers via `ACTION_VIEW` and `ACTION_OPEN_DOCUMENT` for the application/pdf mime type rather than requesting storage access. It only has access to data that the user explicitly provides to it.

This is a fork  of [PDF Viewer](https://github.com/CopperheadOS/platform_packages_apps_PdfViewer) from CopperheadOS made before the license [was changed](https://github.com/CopperheadOS/platform_packages_apps_PdfViewer/commit/158fe6c80a4e83334d7ea6d24c689709855d9963) from Apache 2.0 to Creative Commons Attribution-NonCommercial-ShareAlike, which is not Free Software due to the NonCommercial clause.

## Technical Overview

(Taken from Copperhead's [technical overview](https://copperhead.co/android/docs/technical_overview).)

Since JavaScript is memory safe, memory safety bugs in the PDF implementation itself are not a concern and the performance is still tolerable. The app exposes a very small subset of the Chromium attack surface and the WebView sandbox acts as an additional layer of isolation that’s significantly stronger than the usual app sandbox. It renders via a 2D canvas and loads fonts via the FontFace API using the hardened Chromium font rendering stack (font sanitization, etc.).

The app uses Content Security Policy (CSP) to permit only static content from the app assets, connections to content: URIs and image `blob:` data. The policy prevents dynamic and inline JS/CSS and provides some attack surface reduction for the DOM. JS API attack surface is limited indirectly due to the code being static. File access and network access are also disabled for the WebView, so other than `content:` URIs provided to the app it can only access apk assets and resources.

An alternative to this would be a custom isolatedProcess / seccomp-bpf sandbox to contain a pure Java PDF library and some associated native code, but pdf.js was deemed to be a more secure and viable option than any other existing open source libraries. The widely used libraries are written in C and C++ (mupdf, poppler, pdfium). The pdf.js solution is also nearly ideal when the source of the PDFs is Chromium or a Chromium-based browser due to presenting only a small subset of the same attack surface.

## Contributions

Pull requests are welcome. Please don't submit feature requests if you're not willing to contribute with code; I'm not an Android developer, so I won't implement them. Do not rip off non-free code from Copperhead's repository: this fork is about freedom, not about features. For a some ideas about things that would be nice to implement see their [issue tracker](https://github.com/CopperheadOS/platform_packages_apps_PdfViewer/issues?q=is%3Aopen+is%3Aissue+label%3Aenhancement).
