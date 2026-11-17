# TakaRoute SMS Companion v0.2 (Android + optional Node.js API)

## What it does

For a phone you own or administer with consent, this Android app watches **new incoming SMS only** (permission required), accepts **exact configured sender IDs**, and recognizes *received-money* SMS patterns containing an amount and TrxID. When a matching message arrives, it shows a **local Android notification** with the potential amount/TrxID and stores a recent-match summary in the app. It never reads old SMS or contacts, and it excludes OTP/PIN message formats.

**A detected SMS is not payment confirmation.** SMS sender labels and content can be spoofed. Do **not** automatically approve orders or ship goods based on this signal. Verify the wallet balance/transaction in the official service or use authorized payment provider APIs before marking a payment successful.

**Local notifications work without a backend.** To additionally send minimal matched receipt details to a Node.js server, configure a secure HTTPS API URL and token. Without a server URL, the app doesn't make uploads. Raw SMS text, senders' phone numbers, balances and OTPs are not uploaded.

## Getting an APK (GitHub Actions, no local Android SDK required)

This workspace cannot currently compile Android binaries. A ready-to-run APK build workflow is included at `.github/workflows/build-android-apk.yml`:

1. Create a repository on GitHub and upload the **contents of this project folder** (including the hidden `.github` directory).
2. Open the repository's **Actions** tab → **Build Android APK** → **Run workflow**.
3. Open the successful workflow run → **Artifacts** → **TakaRoute-SMS-Companion-debug-APK** and download/unzip it to get `app-debug.apk`.
4. Transfer the APK to your own Android phone and install it (allow installation from that source when prompted). Verify that you trust the APK and source.

Alternatively, open `android/` in Android Studio (JDK 17, SDK Platform 35, Gradle 8.9, AGP 8.7.3) → **Build → Build APK(s)**.

The APK has **not** been built or tested on real hardware in this environment. The GitHub workflow is provided to build it, but has not been run here.

## Setup on Android

1. Open the app. The URL and token fields can remain **empty** for local notifications only.
2. Keep the `bKash` SMS sender label if your legitimate receiving messages use it, or enter the *exact real sender label* you see on your own phone.
3. Check the consent box → **Save and enable monitoring**.
4. Allow **SMS permission** and **Notifications permission** (Android 13+). If blocked at install-time, check **App Info → Permissions**: Android treats SMS as a restricted permission and some installation methods/phones may not allow granting it.
5. Tap **Test Android notification** and check the notification shade. This tests alerts without receiving an SMS or connecting a server.
6. Once an actual incoming SMS matches, the app displays a possible-receipt notification and updates **Most recent matching SMS**. Unrecognized formats are ignored.

## Important practical limits

- Requires Android 8.0+; device manufacturers can limit SMS broadcast delivery or background behavior.
- Android permission grants are not guaranteed, especially for SMS access (hard-restricted permission). Google Play heavily restricts SMS permissions; Play publication is not assured.
- The strict parser understands examples like `You have received Tk 1,800.00 from 01700000000. TrxID ABC1234567 at 10:00.`; actual bKash templates may differ. Send only **redacted** SMS samples when requesting parser adjustments.
- Only listens to **new messages**, not the existing inbox; needs an actual active SIM/device that receives the official messages.
- Stores the last amount/TrxID and a small local deduplication list in private app storage. It is not a durable accounting ledger.
- On Android 13+, notification permission must be allowed for alerts. On Android 8+, notification channel must remain enabled.
- This is a prototype; you must test on an actual device before using it operationally.

## Optional Node.js server

1. `cd server && npm install` (Node.js 20+).
2. Copy `server/.env.example` to `.env`, create long `INGEST_TOKEN` and `ADMIN_TOKEN`.
3. `npm start` then put the server behind HTTPS.
4. In the app enter the HTTPS base URL and ingest token, and enable monitoring again.
5. Events are stored with status `awaiting_manual_review` and duplicates are rejected.

Security: server requires tokens, but the example is not production hardened. Implement per-device credentials, revocation, rate limits, an audit trail, and formal reconciliation before using commercially.

This app was written independently of OwnPay and does not reuse its source code.
