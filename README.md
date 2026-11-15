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

## Getting Started

### Requirements

- Android Studio.
- Compatible Android SDK.
- Android device or emulator.
- Internet connection for Gradle dependencies and optional online AI functionality.

### Clone the Repository

```bash
git clone https://github.com/Zelycx/CIEL-HACKLaguna-2026.git
```

### Open the Project

1. Open Android Studio.
2. Select **Open**.
3. Choose the cloned CIEL project directory.
4. Allow Gradle to synchronize.
5. Connect an Android device or launch an emulator.
6. Run the `app` configuration.

### Build the Debug APK

For Windows Command Prompt:

```bat
gradlew.bat :app:assembleDebug
```

The generated APK can usually be found at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Online AI Setup

To use online AI functionality, configure a valid Groq API key through the application's online access settings, where supported by the current build.

Never commit personal API keys, signing credentials, or other secrets to the repository.

**Project Status:** CIEL is under active development. Some AI capabilities and release-build behavior are still being tested. A public release will be provided once the application has passed physical-device testing.

---

## Challenges I Faced

### The Hackathon Experience

Going into HACKLaguna, I expected a familiar kind of competition.

I was used to joining hackathons and game jams with the same team: the people I had experienced competitions with, built projects with, and shared wins and losses with. We had developed a certain pattern of working together, and I expected us to be competing as a team once again.

But things turned out differently.

Instead of competing alongside my usual teammates, participants were shuffled and paired with other people inside the hackathon. The people I was accustomed to fighting alongside suddenly became competitors, while I had to work with a completely new team.

It was an unfamiliar experience.

I had to adapt to different personalities, communication styles, working habits, and ways of approaching problems. I had to learn how to cooperate with people I had only just met, establish trust, and contribute effectively despite the unfamiliar environment.

And somehow, we made it.

We became a team, built something meaningful, and earned **1st Runner-Up**.

### The Development Challenge

The application itself was another challenge.

We had only **90 minutes** to turn an idea into an actual Android application. We chose to build CIEL, a project involving Bible data, AI integration, and a meaningful user experience.

Some of the hardest parts were implementing the Bible functionality and getting the Groq API integration working properly.

There were moments when things did not work as expected. I got stuck, encountered implementation problems, and had to make decisions under time pressure.

Despite those difficulties, I kept working through them with the support of my teammates.

Looking back, I'm genuinely happy that we managed to bring the idea to life within such a limited amount of time.

---

## Accomplishments We're Proud Of

Honestly, I'm really, really proud that we made it onto the podium.

Every win matters to me, and I genuinely appreciate the people who helped make this one possible.

I'm proud of the team I was placed with. We started as strangers in a competition, but we managed to cooperate, trust each other, and build something that earned recognition.

I'm also proud of the other people I have shared hackathons and game jams with. Seeing them build their own products and achieve recognition, including 2nd Runner-Up, made the experience even more meaningful.

For me, this achievement is not just about receiving an award. It's also about the people, the experience, and the opportunity to prove that a newly formed team can accomplish something meaningful together.

And, of course, I'm proud of CIEL itself. What started as an idea inspired by a personal experience became an actual application with the potential to help other people.

---

## What I Learned

One of my biggest takeaways from HACKLaguna was the importance of **trust and cooperation**.

I learned that listening to teammates is just as important as contributing my own ideas. Working with people I had never met before taught me that a team does not necessarily need a long history together to accomplish something meaningful.

There was also something that genuinely stayed with me.

My teammates trusted me with a major part of building the application, even though I was younger and below their year level. They entrusted me with something important, and that meant a lot.

There were moments when I got stuck, felt hopeless, or thought I wasn't making enough progress.

But instead of pressuring me, my teammates reassured me:

> "It's fine, we'll win this, you don't have to force yourself if you can't do it."

That meant more than they probably realized.

It reminded me that teamwork is not about forcing everyone to perform perfectly. It's about creating an environment where people can contribute, learn, make mistakes, and support one another.

I want to carry that lesson into my future hackathons and projects.

I also learned that trusting someone new can be a huge deal. Sometimes, giving someone an opportunity and believing in their ability can bring out something they did not even realize they could accomplish.

---

## What's Next for CIEL?

Winning 1st Runner-Up was a great milestone, but I don't want CIEL to end as just another hackathon project.

I'd like to share the application with churches and communities that could benefit from it, particularly those that want to make it easier for young people to explore their faith and ask questions.

I want CIEL to become something people can actually use beyond the competition.

Some of the things I hope to work on are:

- **Church collaboration:** Introduce CIEL to churches that may find it useful for their youth communities.
- **Real-world feedback:** Listen to young people, church leaders, and potential users to understand what should be improved.
- **AI improvements:** Improve response quality, context handling, and the reliability of both online and offline AI capabilities.
- **Offline optimization:** Make local AI functionality more practical for Android devices with different hardware capabilities.
- **Privacy improvements:** Continue reviewing how sensitive personal information and user credentials are handled.
- **Accessibility:** Make CIEL easier to use for people with different levels of technical experience.
- **Public availability:** Prepare a stable Android release and a simple website where users can learn about CIEL and download it.

My hope is to eventually make CIEL freely accessible to churches and individuals who want to use it.

I don't want the application to be remembered only because it won an award. I want it to become something that genuinely helps someone ask their first question, understand a Bible passage, or take another step in their faith.

---

## Developer

**John Ezekiel M. Yorong**

Developer of CIEL: Your Safe Space

- GitHub: [@Zelycx](https://github.com/Zelycx)
- Project: CIEL: Your Safe Space
- Competition: #HACKLaguna 2026
- Achievement: 1st Runner-Up

---

## Acknowledgments

A project like CIEL would not have happened without the people who trusted one another and worked together during the competition.

My sincere appreciation goes to:

- My teammates at HACKLaguna 2026, for their trust, patience, cooperation, and encouragement.
- The fellow participants who made the competition a memorable experience.
- The organizers and people behind HACKLaguna 2026 for providing the opportunity to build something meaningful.
- Everyone who continues to support the development of CIEL.

**Thank you for taking the time to explore CIEL.**

I hope it becomes a safe place for someone who has a question they have been afraid to ask.

---

*CIEL: Your Safe Space. A little more room for questions, faith, and hope.*
