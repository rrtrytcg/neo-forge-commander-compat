# Commander Compatibility Project Index

This folder contains the card-by-card native fixtures, paired exposure decks, and verification notes for opt-in Commander AI support. The supported public test baseline is NeoForge 1.0.28.

Run the portable native fixture bundle from the project root with `./scripts/Test-CommanderCompat.ps1 -NeoForgePath <NeoForge install>`. Each lab README describes its rule and records card-use evidence. Historical exposure and refresh scripts may depend on the full NeoForge development checkout and sealed build artifacts. The full gate counts, test-engine hash, and source/install pins are in `project-manifest.json`.

Coverage is summarized in [coverage/COVERAGE.md](coverage/COVERAGE.md); the card registry is [coverage/card-status.tsv](coverage/card-status.tsv). Internal gate scripts retain their original paths and may depend on build artifacts from the development checkout.
