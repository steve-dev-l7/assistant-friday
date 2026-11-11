<div align="center">

# 🎙️ FRIDAY

### A fully offline, on-device AI voice assistant for Android

*No cloud. No API keys. No data leaving your phone. Fully offline after a one-time setup.*

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Offline](https://img.shields.io/badge/mode-Offline-blueviolet)
![TFLite](https://img.shields.io/badge/ML-TensorFlow%20Lite-FF6F00?logo=tensorflow&logoColor=white)
![Status](https://img.shields.io/badge/status-complete-brightgreen)

</div>

---

## ✨ What is Friday?

Friday is a voice assistant that lives entirely on your Android device. You speak, it understands, it acts, and it talks back, all without an internet connection.

It has a personality too: sharp, witty, and a little sarcastic.

> **You:** Hello Friday!
> **Friday:** Well, hello there. Ready to assist, Captain. Just make sure your inquiries are sharp, because I find slow thinking deeply inefficient.

---

## 🚀 Features

- 🔒 **Fully offline**: after the one-time setup, everything runs on-device and nothing is sent to a server
- 🤖 **On-device LLM**: a Gemma model (`.litertlm`, about 2.5 GB) runs locally through LiteRT-LM for natural, in-character conversation
- 🗣️ **Speech-to-Text (STT)**: talk to Friday naturally
- 🔊 **Text-to-Speech (TTS)**: Friday talks back in voice
- 🧠 **On-device intent classification**: a custom-trained TensorFlow Lite model with 5 intent classes
- 🧩 **Slot extraction**: rule-based extraction of `target`, `task`, `time`, and `timeType` from your command
- 😎 **Character**: a witty personality that makes every reply feel alive
- ⚡ **Fast and lightweight**: no network latency, runs smoothly on a phone

---

## 🏗️ How it works

```
 🎤 Voice  ──►  STT  ──►  Intent Classifier (TFLite)  ──►  Slot Extraction  ──►  Action
                                                                                   │
 🔊 Speaker ◄──  TTS  ◄──────────────  Response Generator  ◄───────────────────────┘
```

1. **Listen**: your speech is converted to text on-device
2. **Understand**: the TFLite model classifies what you want (intent)
3. **Extract**: rules pull out the details, like who, what, and when
4. **Act**: Friday performs the task
5. **Respond**: the reply is spoken back to you in Friday's style

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Platform | Android |
| Language | Kotlin |
| Intent model | TensorFlow Lite (custom 5-class classifier) |
| Slot extraction | Rule-based |
| LLM | Gemma (`.litertlm`, about 2.5 GB) via LiteRT-LM, CPU backend |
| Wake word | VOSK (offline) |
| Speech-to-Text | Google SpeechRecognizer (offline language pack) |
| Text-to-Speech | Android system speech synthesis (offline voice data) |

---

## 📦 Getting started

### Prerequisites
- Android Studio (latest stable)
- An Android device or emulator
- Android 9 (API 28) or higher

### Installation

```bash
# 1. Clone the repo
git clone https://github.com/steve-dev-l7/assistant-friday.git

# 2. Open in Android Studio
#    File → Open → select the project folder

# 3. Let Gradle sync, then run on your device
```

### Permissions
- **Microphone**: so Friday can hear you. Grant it on first launch.
- **Internet**: used only once, to download the AI model on first launch. After that, Friday does not need a connection.

---

## ⚙️ First-time setup (internet needed once)

Friday is fully offline, but three things must be downloaded the first time.

### 1. Download the AI model (automatic)
- On the **first app launch**, Friday downloads its Gemma model (about **2.5 GB**).
- Connect to **Wi-Fi** and keep at least **3 GB of free storage**.
- Wait until the download finishes. This happens only once, and the model is stored on your phone.

### 2. Install the offline voice for speech (manual)
Friday speaks using your phone's built-in speech synthesis. For it to work **without internet**, download the offline voice data:

1. Open your phone's **Settings**
2. Go to **Language & input** (on some phones: **System** or **Additional settings**) → **Text-to-speech output**
3. Open the settings of your preferred engine (for example, **Speech Recognition & Synthesis by Google**)
4. Tap **Install voice data** (or **Voice**)
5. Select **English (India)** and download it

> 💡 Menu names differ slightly between phone brands. If you cannot find it, search for "text-to-speech" or "voice" in your Settings search bar.

### 3. Download offline speech recognition (manual)
Friday listens to your commands using Google's speech recognizer. Without the offline language pack, it may send your audio to Google's servers, so download it:

1. Open your phone's **Settings** and search for **Offline speech recognition**
   (or open the **Google** app → **Settings** → **Voice** → **Offline speech recognition**)
2. Download **English (India)**

After all three steps, turn off your internet and Friday will keep working. ✈️

---

## 💬 Example commands

| You say | Friday understands |
|---|---|
| `"Hello Friday"` | Greeting intent |
| `"Remind me to call Mom at 6 PM"` | Task: call, Target: Mom, Time: 6 PM |
| `"Who are you to me"` | Conversation intent |

> ⚠️ Update this table with your 5 real intents and sample commands.

---

## 📁 Project structure

```
assistant-friday/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/friday/   # App source code
│   │   ├── assets/                    # TFLite model + vocab files
│   │   └── res/                       # UI resources
│   └── build.gradle
├── README.md
└── ...
```

> ⚠️ Adjust this to match your actual folders.

---

## 🗺️ Roadmap

- [x] On-device STT and TTS
- [x] 5-class intent classifier (TFLite)
- [x] Rule-based slot extraction
- [x] Character personality
- [ ] More intent classes
- [ ] More response variety

---

⚠️ **Play Protect warning:** Google Play Protect may show "unrecognized app" because Friday is not distributed through the Play Store. Tap **More details → Install anyway**. The app is open source, so you can read every line of code in this repo.

🔐 **Display over other apps is greyed out?** Android restricts sensitive permissions for apps installed from outside the Play Store. Go to **Settings → Apps → Friday → ⋮ (top right) → Allow restricted settings**, then enable **Display over other apps**.

## 🤝 Contributing

Suggestions and pull requests are welcome. Open an issue first to discuss big changes.

---

## 👨‍💻 Author

**Sakthivel B (Steve)**
MCA student, B.S. Abdur Rahman Crescent Institute of Science and Technology, Chennai

🔗 GitHub: [@steve-dev-l7](https://github.com/steve-dev-l7)

---

<div align="center">

*Built with ❤️ and a lot of late nights*

⭐ Star this repo if you like it!

</div>
