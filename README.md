<h1 align="center">Rain Tales</h1>

<p align="center">A companion camera that keeps nothing — except you company.</p>

<p align="center">
  [<a href="docs/README.zh-CN.md">简体中文</a>]
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/code-MIT-green" alt="Code: MIT"></a>
  <a href="ASSETS_LICENSE.md"><img src="https://img.shields.io/badge/assets-All%20Rights%20Reserved-orange" alt="Assets: All Rights Reserved"></a>
  <a href="https://github.com/Rainpalette/raintale/actions"><img src="https://github.com/Rainpalette/raintale/actions/workflows/build.yml/badge.svg" alt="build"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-blue" alt="Android 10+">
</p>

> ⚠️ **Experimental (v0.x)** — a personal side project. Maintained in good faith, with no guaranteed response times. Mina's final character art and dedicated voice model are not ready yet (placeholder silhouette + the device's system voice for now); everything else works.

---

## What is this

**Rain Tales is an Android camera app where a virtual character stands in your shot — and then talks about the photo you just took together.**

Point the camera, drag the character where you want her, pick a pose, hit the shutter. The app composites her into a full-resolution photo and saves it to your gallery. Then an AI (using **your own API key**) looks at that photo and lets her say something about the moment, saved into a shared memory log if you want to keep it.

The project is currently has no server and account management. No data is collected by this project.

<details>
<summary><b>And a story, if you want one</b></summary>

<br/>

Somewhere past the edge of things, a bus carries away what people have given up on. Nobody knows what waits at the last stop — those who ride to the end are never seen again.

A girl named Mina got off halfway. The space around her grew into a small bus stop, a transparent umbrella, quiet rows of trees, and rain. She has been waiting there for a while now, watching others step off and walk on.

Now you are here, carring a camera. She recognizes you as a visitor, and the space bends into your memories. She will share these moments with you, before you leave this mysterious place.


</details>

## Why it's different

**Zero collection** No backend, no analytics, no telemetry, no ads; system cloud backup is switched off. Your photos, notes, settings and API key never leave your phone unless a feature you turned on sends them. Every network destination is listed below, and the source is right here for you to audit.

**Real depth, on-device.** ML Kit selfie segmentation lets a real person stand *in front of* the character instead of always being covered by her — the usual "sticker on top" problem. Segmentation runs entirely on your phone.

**Consent that stays honest.** Every outbound data path — AI upload, Wikipedia lookup, location — has its own notice the first time you reach it, and consent is **versioned**: if the wording materially changes, everyone is asked again.


## Features

- [x] **Photo composition** — drag / pinch-zoom the character, switch poses and characters, composite at full resolution
- [x] **AI commentary** — she says a line about the photo (structured output, safety directives, and a mental-health safeguard)
- [x] **Speech** — currently project only supports system TTS
- [x] **Caption mode** — write or dictate a note with the photo and she responds to it
- [x] **Portrait mode** — put the person in front of or behind the character, switched live
- [x] **Live commentary** — she walks with you and speaks only when the scene actually changes (on-device change detection saves API quota)
- [x] **Companion mode & custom characters** — text-only personas you write yourself
- [x] **Gallery & memory log** — what she said is archived with each photo, and you can add your own notes; export / import as JSON
- [x] **Knowledge links** — recognized landmarks and brands can link to a Wikipedia summary (off by default)
- [x] **Visual-novel intro** — meeting Mina, as a short interactive scene
- [x] **Offline lines** — with no key and no network, she still has something to say
- [ ] Mina's final artwork and dedicated voice (in progress)
- [ ] English UI (i18n groundwork is in place)
- [ ] Demo mode — try the full loop without an API key

## Getting started

### You'll need

- An Android 10+ phone (minSdk 29)
- A **Google Gemini API key** — free from [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
  - Google's terms require users to be 18+, or the age of digital consent where you live
  - ⚠️ On the **free tier, your content may be used by Google to train their models** (paid tiers don't by default). The app tells you this again before the first upload.
- No key? Photo composition, the gallery and the intro all still work — she just speaks pre-written offline lines.

### Install

```bash
git clone https://github.com/Rainpalette/raintale.git
# open in Android Studio and Run, or:
./gradlew installDebug
```

> During development you can put `GEMINI_API_KEY=your_key` in `local.properties` as a fallback. That file is gitignored, and **release builds never contain a key** — the injection only exists for debug builds.

### Configure

In the app: **Settings (gear, top right) → Account / BYOK → paste your key → Save.** It is stored only on your device.

Not sure which setting does what? Tap **"☂️ Ask Mina"** at the bottom of Settings and ask her in plain language — she answers strictly from the app's own settings documentation.
Note that **"☂️ Ask Mina"** functionality requires an API key to be set, and will not work in offline mode.

## Privacy

In one line: **the developer has no server and collects nothing.**

| Data | Where it goes |
|---|---|
| Photos, memory notes, settings, API key | Your device only (system cloud backup is disabled) |
| When AI commentary is on | The photo goes to Google Gemini, through **your own key** |
| When knowledge links are on (default: off) | Only the recognized **name text** goes to Wikipedia — never the photo |
| When location is on (default: off) | Coordinates are geocoded to a city-level name; Gemini only receives that name |
| Crash reports | Stored locally; they leave the device only when you share them yourself (keys are auto-redacted) |

Full policy: [docs/privacy-policy.md](docs/privacy-policy.md) — also reachable in-app under Settings → About.



## Characters & licensing

Characters come in three tiers, and the line that matters is **whether the project provides it as a feature**:

| Tier | How it's made | The project's position |
|---|---|---|
| Official characters | Shipped with the app (Mina and others) | ✅ Endorsed and supported |
| Custom characters | Text-only personas written inside the app | ✅ Endorsed and supported |
| Unofficial characters | Added by modifying the source | ❌ Unrelated to this project; entirely the modifier's responsibility |

Because of that, licensing here is **layered**:

- **Code** — [MIT](LICENSE). Use it, change it, ship it.
- **Official art & voice assets** — [All Rights Reserved](ASSETS_LICENSE.md). **Not** covered by MIT: official builds only, no redistribution in modified or derivative builds, and no use as material for other characters, products, or AI training.
- **Third-party components** — see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) (AndroidX, Compose, CameraX, Room, Coil, ML Kit, and others).

If you fork and distribute a modified build, please **rename it and remove the official assets** so it isn't confused with the official version.


---

<p align="center"><sub>In all that grey rain, the light at the bus stop is the only warm thing.</sub></p>
