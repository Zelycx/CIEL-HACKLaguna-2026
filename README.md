# CIEL: Your Safe Space

> **A gentle companion for faith and life.**  
> An offline-first Android application built to make room for honest questions, meaningful Bible reading, and conversations without judgment.

<p align="left">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/AI-Offline--first%20%7C%20Hybrid-1687B8" alt="Offline-first hybrid AI" />
  <img src="https://img.shields.io/badge/Event-%23HACKLaguna%202026-0A8FCA" alt="#HACKLaguna 2026" />
  <img src="https://img.shields.io/badge/Result-1st%20Runner--Up-F5B942" alt="1st Runner-Up" />
</p>

**CIEL received 1st Runner-Up at the #HACKLaguna 2026 competition.** It was developed during a 90-minute build challenge with a newly formed team.

---

## Contents

- [About Ciel](#about-ciel)
- [Why the name Ciel?](#why-the-name-ciel)
- [The problem](#the-problem)
- [The inspiration](#the-inspiration)
- [Features](#features)
- [How the hybrid AI works](#how-the-hybrid-ai-works)
- [Technology and tools](#technology-and-tools)
- [Privacy and responsible use](#privacy-and-responsible-use)
- [Getting started](#getting-started)
- [Challenges I faced](#challenges-i-faced)
- [Accomplishments we're proud of](#accomplishments-were-proud-of)
- [What I learned](#what-i-learned)
- [What's next for Ciel?](#whats-next-for-ciel)
- [Developer](#developer)

---

## About Ciel

**Ciel: Your Safe Space** is an offline-first Christian companion for Android. It gives teens and young adults a welcoming place to explore questions about God, Christianity, Scripture, and life, while making Bible reading and personal reflection part of their everyday routine.

Ciel is designed around a simple idea: people should be able to ask honest questions without feeling embarrassed, judged, or pressured to already know the answer. It can explain Bible passages, point readers to relevant verses, keep track of reading progress, and offer a friendly conversation when someone simply needs to talk.

What makes Ciel different is its **offline-first, hybrid AI architecture**. The Bible and core app experiences are available on-device. Users can also download a compact local language model for offline conversations, or optionally connect their own Groq API key for online AI responses. Ciel does not require every user to create an account or provide an API key just to start using the app.

Ciel is intended to support personal Bible exploration and church communities. It is not a replacement for pastors, trusted people, professional care, or a user's own study of Scripture.

## Why the name Ciel?

*Ciel* is a French word meaning **“sky”** or **“heaven.”** The name felt right for a faith-focused companion because it brings to mind openness, hope, peace, and looking beyond the worries of the present moment.

I wanted the application to feel like a calm space: somewhere a person can pause, reflect, ask a difficult question, and take another step in their faith journey. The name is short, gentle, and connected to the sense of hope I wanted the product to represent. The French Academy traces *ciel* to Latin *caelum*, meaning the celestial vault or the abode of the divine ([Dictionnaire de l’Académie française](https://www.dictionnaire-academie.fr/article/A9C2280)).

## The problem

The challenge behind Ciel was expressed in one sentence:

> Teens and young adults have real questions about God, doubt, and life, but they will not ask a pastor face to face. They search online, find mixed answers, and the church never hears the question.

A young person might have questions about faith, relationships, suffering, forgiveness, or a Bible passage but feel uncomfortable bringing them up in person. Searching the internet can lead to answers without context or guidance. Sometimes, the question is not even looking for a complicated explanation; the person just needs a safe place to begin talking.

Ciel aims to lower that first barrier. It provides a private, approachable starting point for exploring Scripture and reflecting on questions, while encouraging meaningful connection with a church community and trusted people when human guidance is needed.

## The inspiration

The inspiration is personal.

There was a time when I did not believe. My perspective began to change when I had questions about God and eventually found answers that helped me understand and believe. That experience made me realize how important it can be for someone to have their questions heard and taken seriously.

When I encountered the challenge statement, I thought about people who might be in that same position: curious, uncertain, struggling, or afraid to ask. I wanted to build something that could help them take that first step.

Ciel is my attempt to turn that experience into something useful for other people and for churches that want to make space for honest conversations.

---

## Features

The screenshots for each feature can be added in the marked locations below. Suggested filenames are included so the images can be organized consistently in the repository.

### 1. Home and Verse of the Day

A calm home screen that welcomes the user and highlights a selected Bible passage for the day. Verses come from the bundled Berean Standard Bible (BSB), with a theme and short reflection to help the reader consider the passage in context.

- Daily Scripture selection with a meaningful theme.
- Short reflection connected to the selected passage.
- Quick access to read the complete passage.
- Shareable verse-image option.
- A simple continuation point for the user's Bible reading journey.

**Screenshot:** Home screen  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/223a0eca-a4f1-41be-b6e6-5bdaa6ed3743" />

### 2. Ciel Chat

A faith-focused companion for questions about God, Christianity, Scripture, and everyday life. Ciel can also take part in ordinary conversation and listen when someone wants to talk through a difficult day. It is designed to be warm and respectful without turning every conversation into a sermon.

- Conversational responses to faith and Bible questions.
- A friendly space for everyday conversation and personal reflection.
- Relevant BSB passages when they help answer the question.
- Conversation history and the ability to start a new chat.
- Ciel's expressive character avatars to give the conversation more personality.
- Offline and online provider support.

**Screenshot:** Chat  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/f998125d-ee25-43fd-b55a-b65aa1fc2a08" />

### 3. Offline-first hybrid AI

Ciel is designed to remain useful without an internet connection. The bundled Bible and saved app data remain on the device. Users can download **Qwen3 0.6B** as the on-device companion model for local chat generation. Online conversations are optional and can use the user's own Groq API key.

- **Offline mode:** run the downloaded local model on the Android device.
- **Online mode:** connect a personal Groq API key from Settings.
- **Provider fallback:** continue locally when an online connection is unavailable or the online service reaches its own limits, where applicable.
- **No required account:** users can use the Bible and offline app features without signing in.
- **No custom backend requirement:** the online path communicates with Groq directly.

The local model is an optional first-time download. The current in-app setup information lists a download of approximately **498 MB** and recommends around **550 MB of free storage** and **1.5 GB of available memory while chatting**. Actual performance depends on the Android device. The Bible and saved app data remain available even before the model download is complete.

**Screenshot:** Offline companion and online access settings  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/c6e9493e-9d44-4c66-ad8e-869b6da913b0" />

### 4. Bible reader and search

A built-in Bible reader powered by the bundled **Berean Standard Bible (BSB)** data. Users can explore Scripture without having to upload a Bible file or stay connected to the internet.

- Browse Bible books and chapters.
- Search for a book or enter a reference such as `John 3:16`.
- Read verse text from the local BSB data.
- Navigate between chapters.
- Bookmark passages for later.
- Mark a chapter as read.
- Open Bible references from Ciel's responses and the Verse of the Day.

**Screenshot:** Bible reader  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/25bbdb2e-ec04-456e-8d33-3328896ce20a" />

### 5. Reading progress and streaks

Ciel helps users continue where they left off instead of making them remember their last chapter.

- Saves the user's reading position.
- Recommends the next chapter in Bible order.
- Tracks explicitly completed chapters.
- Tracks reading days and current streaks.
- Provides daily Bible reminders.
- Keeps reading progress on the device.

A chapter is counted as completed when the user marks it as read, rather than simply opening it.

**Screenshot:** Continue Reading and reading progress  
<img width="352" height="275" alt="image" src="https://github.com/user-attachments/assets/ee149c84-9337-45ba-9862-02430868fa22" />
<img width="352" height="275" alt="image" src="https://github.com/user-attachments/assets/65fd8b4d-b54a-45b2-a748-b2a13937316e" />

### 6. Kumpisal — Private Reflections

*Kumpisal* is a personal space for writing confessions, thoughts, struggles, and reflections. It is designed to remain separate from Ciel's AI conversations.

Privacy is a core part of this feature. Users can protect their entries with a password, allowing them to write honestly without worrying about someone casually opening and reading their personal reflections.

- Password-protected access.
- Create, edit, and delete personal entries.
- Entries include dates and times.
- Stored locally on the device.
- Completely separate from AI conversations.
- Kumpisal content is never automatically sent to Groq or the local AI model.

**Privacy note:** Screenshots of Kumpisal are intentionally omitted to respect the private nature of personal confessions and reflections.

### 7. Conversation history

Saved conversations help users return to earlier discussions without losing the context of their previous chats.

- Keeps conversations available on the device.
- Lets users revisit previous discussions.
- Supports starting a separate new conversation.

**Screenshot:** Conversation History  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/1cf44286-de9b-4854-bcb6-943f3952e62f" />

### 8. Statistics

A personal overview of how the user spends time in Ciel and how their Bible reading is progressing.

- Daily app activity.
- Time spent reading the Bible and chatting with Ciel.
- Reading streak and chapter completion progress.
- Books visited and chapters opened or completed.
- Weekly activity overview.

**Screenshot:** Statistics  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/3e1932ce-a107-4c27-b34d-9180cff86a58" />

### 9. Daily reminders

Optional reminders encourage users to spend a moment with Scripture. Users can enable or disable the reminder and choose a preferred time. Notifications open the Bible area so the user can continue reading.

**Screenshot:** Bible reminder settings  
<img width="361" height="197" alt="image" src="https://github.com/user-attachments/assets/f1ba0c3c-eb01-4a36-90de-d6df244f5bc3" />

### 10. Shareable Scripture cards

The Verse of the Day can be turned into a shareable image rather than just copied as plain text. The card is designed around the selected Scripture, its reference, Ciel's branding, and an expressive Ciel character image.

- Generate a verse card on-device.
- Choose from Ciel's available character expressions.
- Preview the design before sharing.
- Share the resulting image through Android's sharing menu.

**Screenshot:** Verse sharing and expression selection  
<img width="540" height="1200" alt="image" src="https://github.com/user-attachments/assets/c51ccc5a-5e02-4038-881f-e08e4c770298" />

### 11. Themes and appearance

Users can personalize the app while keeping the interface calm and readable.

- Sky Blue as the default accent color.
- Additional Sage, Lavender, Teal, and Rose themes.
- Light and Dark appearance modes.
- Saved preferences across app restarts.

**Screenshot:** Settings and themes  
<img width="362" height="382" alt="image" src="https://github.com/user-attachments/assets/4c69c089-8d06-447d-8c65-abc57f3a3743" />

---

## How the hybrid AI works

Ciel uses a two-provider approach so online access is optional rather than a requirement.

| Mode | What it uses | When it is useful |
|---|---|---|
| Offline | Qwen3 0.6B running on-device after its first download | No internet connection, privacy-conscious use, or continuing when online access is unavailable |
| Online | Groq API using the user's own API key | When the user chooses to connect and has online access |

The bundled BSB is used as a local Scripture source. When a Bible-related question needs supporting passages, Ciel can retrieve relevant verses and use them as context for the response. This is a retrieval-grounded approach: the app finds passages from its local Bible data instead of sending the entire Bible with every request.

The online mode is opt-in. Each user supplies their own Groq API key in Settings. Groq's own account and model limits apply; Ciel should not impose an unrelated fixed daily message quota.

**Important:** The local model must be downloaded once before on-device generative chat is available. Bible reading and other locally stored features do not depend on that model download.

## Technology and tools

| Area | Technology / tool | Purpose |
|---|---|---|
| Android development | Kotlin, Android Studio, Android SDK | Native Android application development |
| User interface | Android Views and XML layouts | Existing view-based Android interface |
| Local data | Room | Store app data and conversation-related records locally |
| Bible source | Berean Standard Bible (BSB) JSON | Local Scripture text, references, and lookup |
| On-device AI | Qwen3 0.6B | Optional local generative chat model |
| Online AI | Groq API | Optional online AI responses using the user's own key |
| Credential protection | Android Keystore-backed encryption | Protect the user's saved online API credential on the device |
| Notifications | Android notification and scheduling APIs | Daily Bible reminders and reading prompts |
| Image sharing | Android bitmap/canvas rendering and Sharesheet | Create and share Scripture cards |
| Version control | Git and GitHub | Source control and collaboration |

## Privacy and responsible use

Ciel is designed to keep core features available on the device and to make online access optional.

- The Bible data is bundled locally.
- Reading progress and saved app data are stored on the device.
- Kumpisal is separate from AI chat and is not automatically submitted to either provider.
- The user's Groq key is configured by the user and protected using Android's Keystore-backed storage approach.
- When online mode is enabled, conversation messages and relevant Bible context are sent to Groq to generate a response.
- Ciel is a companion for reflection and Bible exploration, not a replacement for a pastor, trusted person, qualified professional, or emergency support.

Users should review the app's in-app privacy information before enabling online responses. As with any Android application that stores sensitive information locally, device security and access to the device remain important.

## Getting started

### Requirements

- Android Studio with a compatible Android SDK.
- A compatible Android device or emulator.
- Internet access for the initial project setup and, if desired, online Groq conversations.
- Additional storage and memory if downloading the Qwen3 0.6B offline model.

### Open the project

1. Clone this repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Connect an Android device or start an emulator.
5. Build and run the `app` configuration.

For a Windows command prompt, the build can also be run from the project directory:

```bat
gradlew.bat :app:assembleDebug

```

The generated debug APK can usually be found at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Running the application

You can run CIEL directly through Android Studio:

1. Open the project.
2. Allow Gradle to finish syncing.
3. Select an available Android device or emulator.
4. Select the `app` run configuration.
5. Click **Run**.

For local development, use the debug build. A debug APK is intended for testing and is not the recommended distribution build.

### Downloading CIEL

A public installation package is not available yet. Once the release build has been tested and is ready for distribution, the APK will be published through the repository's GitHub Releases.

---

## Challenges I faced

Developing CIEL came with several challenges, especially considering the limited time available during the competition.

One of the biggest challenges was turning a broad and personal problem into an actual application. I wanted CIEL to be more than a chatbot. It needed to provide a meaningful experience around faith, Scripture, and personal reflection.

Other challenges included:

- **Scope and time management:** Developing a concept and its core application experience during a 90-minute competition.
- **AI integration:** Designing an approach that supports both local and online AI without making internet access a requirement for the entire application.
- **User privacy:** Considering how to keep personal reflections separate from conversations with AI providers.
- **User experience:** Creating an interface that feels welcoming and easy to understand rather than overly technical.
- **Responsible AI:** Making sure a faith-focused companion can acknowledge its limitations and encourage human guidance when appropriate.

These challenges helped shape the direction of CIEL beyond its original competition prototype.

## Accomplishments we're proud of

CIEL became more than just an idea developed for a competition.

Some accomplishments include:

- Achieving **1st Runner-Up at #HACKLaguna 2026**.
- Developing a native Android application using Kotlin.
- Bringing Bible reading and faith-focused conversations together in one application.
- Designing an offline-first approach with optional online AI access.
- Building personal reflection features that remain separate from AI conversations.
- Turning a personal experience and a real problem into a project with a meaningful purpose.

The recognition at HACKLaguna was a meaningful milestone, but the bigger accomplishment was being able to turn the concept into something that can continue to grow.

## What I learned

Building CIEL taught me that developing an application is not only about writing code or making features work.

It is also about understanding the people who will use it.

I learned the importance of:

- Starting with a real problem instead of immediately choosing a technology.
- Prioritizing features based on what users actually need.
- Balancing functionality, privacy, and usability.
- Designing applications that remain useful even with limited internet access.
- Understanding the limitations of AI and avoiding unrealistic expectations.
- Treating feedback and testing as part of the development process.

Most importantly, I learned that a project can have a meaningful purpose beyond the technology used to build it.

## What's next for CIEL?

CIEL is still a work in progress. There are several areas I want to improve before making it available to more people.

Planned improvements include:

- **AI improvements:** Continue improving the quality, reliability, and responsiveness of online and local AI conversations.
- **Offline functionality:** Test and optimize the local language model across different Android devices.
- **Release stability:** Investigate release-build crashes and test the application on physical devices.
- **Privacy and security:** Review local data protection, credential handling, and personal reflection storage.
- **Bible experience:** Improve Scripture search, navigation, reading progress, and contextual references.
- **Accessibility:** Make the application more comfortable and accessible to a wider range of users.
- **Community feedback:** Gather feedback from young people and church communities to understand what should be improved.
- **Public distribution:** Prepare a stable Android release and a dedicated website for downloading CIEL.

The long-term goal is to make CIEL a genuinely useful companion for people who want to explore their faith, ask questions, and reflect without feeling judged.

## Developer

**John Ezekiel M. Yorong**

Developer of CIEL: Your Safe Space

- GitHub: [@Zelycx](https://github.com/Zelycx)
- Project: CIEL: Your Safe Space
- Event: #HACKLaguna 2026
- Recognition: 1st Runner-Up

Built with the intention of making space for honest questions, meaningful conversations, and faith exploration.

---

*CIEL is a personal faith companion and a starting point for reflection. It is not a substitute for Scripture study, pastoral guidance, trusted relationships, professional care, or emergency services.*
