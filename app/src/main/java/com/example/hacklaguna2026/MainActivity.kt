package com.example.hacklaguna2026

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hacklaguna2026.ai.ChatReplyCodec
import com.example.hacklaguna2026.ai.ChatViewModel
import com.example.hacklaguna2026.ai.ChatSpamGuard
import com.example.hacklaguna2026.ai.AvatarEmotion
import com.example.hacklaguna2026.ai.classifyAvatarEmotion
import com.example.hacklaguna2026.ai.GroqCredentialStore
import com.example.hacklaguna2026.ai.GroqKeyValidation
import com.example.hacklaguna2026.ai.LocalModelManager
import com.example.hacklaguna2026.ai.LocalModelInferenceException
import com.example.hacklaguna2026.ai.LocalModelUnavailableException
import com.example.hacklaguna2026.ai.LocalModelStatus
import com.example.hacklaguna2026.ai.LiteRtLocalAIProvider
import com.example.hacklaguna2026.ai.ModelDownloadProgress
import com.example.hacklaguna2026.ai.OnlineConversationSettings
import com.example.hacklaguna2026.ai.GroqApiException
import com.example.hacklaguna2026.ai.validateGroqKey
import com.example.hacklaguna2026.bible.BibleRepository
import com.example.hacklaguna2026.bible.DailyVerse
import com.example.hacklaguna2026.bible.DailyVerseSelector
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.BookmarkEntity
import com.example.hacklaguna2026.data.ConversationEntity
import com.example.hacklaguna2026.data.ConversationRepository
import com.example.hacklaguna2026.data.MessageEntity
import com.example.hacklaguna2026.data.KumpisalAccessState
import com.example.hacklaguna2026.data.KumpisalEntry
import com.example.hacklaguna2026.data.KumpisalPasswordException
import com.example.hacklaguna2026.data.KumpisalSecurityManager
import com.example.hacklaguna2026.data.UserSessionEntity
import com.example.hacklaguna2026.stats.AppStatsRepository
import com.example.hacklaguna2026.stats.StatisticsSnapshot
import com.example.hacklaguna2026.stats.ContinueReadingTarget
import com.example.hacklaguna2026.notifications.BibleReminderScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var database: AppDatabase
    private lateinit var kumpisalSecurity: KumpisalSecurityManager
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var bibleRepository: BibleRepository
    private lateinit var statsRepository: AppStatsRepository

    private var account: UserSessionEntity? = null
    private var isSessionLoading = true
    private var selectedTab = TAB_HOME
    private lateinit var appearancePreferences: SharedPreferences
    private var selectedTheme = THEME_SKY
    private var darkMode = false
    private var dailyReminderEnabled = false
    private var reminderHour = 8
    private var reminderMinute = 0
    private var reminderPermissionMessage = ""
    private var pendingReminderEnable = false
    private var currentAvatarEmotion = AvatarEmotion.NEUTRAL
    private var currentConversationId: String? = null
    private var conversationMessages: List<MessageEntity> = emptyList()
    private var historyConversations: List<ConversationEntity> = emptyList()
    private var historyLoaded = false
    private var failedQuestion: String? = null
    private var isConversationLoading = false
    private var chatNotice = ""
    private var isSending = false
    private var chatSendJob: Job? = null
    private var bibleNotice = ""
    private var bibleBooks: List<String> = emptyList()
    private var selectedBook: String? = null
    private var selectedChapter = 1
    private var selectedVerse: Int? = null
    private var availableChapters: List<Int> = emptyList()
    private var chapterVerses = emptyList<com.example.hacklaguna2026.data.BibleVerseEntity>()
    private var isNewReflection = false
    private var reflectionDraft = ""
    private var editingReflectionId: Long? = null
    private var selectedPrompts = mutableSetOf<String>()
    private var kumpisalAccessState: KumpisalAccessState? = null
    private var kumpisalAccessLoading = false
    private var kumpisalEntries: List<KumpisalEntry>? = null
    private var kumpisalEntriesLoading = false
    private var kumpisalNotice = ""
    private lateinit var groqCredentials: GroqCredentialStore
    private lateinit var onlineConversationSettings: OnlineConversationSettings
    private lateinit var localModelManager: LocalModelManager
    private var modelDownloadJob: Job? = null
    private var modelDownloadProgress: ModelDownloadProgress? = null
    private var modelDownloadMessage = ""
    private var localModelFailure = ""
    private var onlineConnectionMessage = ""
    private var maskedSavedKey: String? = null
    private var streamingResponseView: TextView? = null
    private var chatModeLabel = "Offline"
    private val chatSpamGuard = ChatSpamGuard()
    private var dashboardStats: StatisticsSnapshot? = null
    private var dashboardVerse: DailyVerse? = null
    private var dashboardTarget: ContinueReadingTarget? = null
    private var dashboardLoading = false
    private var dashboardNeedsReload = false
    private var dashboardDayKey: String? = null
    private var settingsShowingAbout = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingReminderEnable && granted) {
            setReminderState(true)
            reminderPermissionMessage = ""
        } else if (pendingReminderEnable) {
            dailyReminderEnabled = false
            setReminderState(false)
            reminderPermissionMessage = "Notifications are off. You can allow them in your phone settings anytime."
        }
        pendingReminderEnable = false
        renderMain()
    }

    private var activityResumed = false
    private var appSessionRecordedForResume = false
    private var trackedModule: String? = null
    private var segmentStartedElapsed = 0L
    private var segmentLastInteraction = 0L
    private var segmentStartedWall = 0L

    private val reflectionPrompts = listOf(
        "Have I hurt someone through my words or actions?",
        "Is there someone I need to forgive?",
        "What do I need guidance or help with?",
        "What would I like to change moving forward?"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        appearancePreferences = getSharedPreferences(THEME_PREFERENCES, Context.MODE_PRIVATE)
        selectedTheme = appearancePreferences.getString(KEY_THEME_COLOR, THEME_SKY)
            ?.takeIf { option -> THEME_OPTIONS.any { it.id == option } } ?: THEME_SKY
        darkMode = appearancePreferences.getBoolean(KEY_DARK_MODE, false)
        selectedTab = if (intent.getBooleanExtra(BibleReminderScheduler.EXTRA_OPEN_BIBLE, false)) TAB_BIBLE else TAB_HOME
        val reminderPreferences = getSharedPreferences(BibleReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
        dailyReminderEnabled = reminderPreferences.getBoolean(BibleReminderScheduler.KEY_ENABLED, false)
        reminderHour = reminderPreferences.getInt(BibleReminderScheduler.KEY_HOUR, 8)
        reminderMinute = reminderPreferences.getInt(BibleReminderScheduler.KEY_MINUTE, 0)
        BibleReminderScheduler.createChannel(this)

        database = AppDatabase.get(this)
        kumpisalSecurity = KumpisalSecurityManager(database)
        bibleRepository = BibleRepository(this, database)
        conversationRepository = ConversationRepository(database)
        groqCredentials = GroqCredentialStore(this)
        onlineConversationSettings = OnlineConversationSettings(this)
        localModelManager = LocalModelManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val lastFour = runCatching { groqCredentials.read()?.takeLast(4) }.getOrNull()
            if (lastFour != null) withContext(Dispatchers.Main) {
                maskedSavedKey = lastFour
                if (selectedTab == TAB_SETTINGS) renderMain()
            }
        }
        chatViewModel = ViewModelProvider(this, ChatViewModel.Factory {
            ChatViewModel(
                conversationRepository,
                bibleRepository,
                LiteRtLocalAIProvider(this@MainActivity, localModelManager),
                groqCredentials,
                onlineConversationSettings
            )
        })[ChatViewModel::class.java]
        statsRepository = AppStatsRepository(database)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (settingsShowingAbout) {
                    settingsShowingAbout = false
                    renderMain()
                } else if (account != null && selectedTab != TAB_HOME) {
                    selectTab(TAB_HOME)
                } else {
                    finish()
                }
            }
        })

        render()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { bibleRepository.ensureBundledBibleLoaded() }
                account = withContext(Dispatchers.IO) {
                    database.userSession().account()?.copy(isLoggedIn = true)?.also {
                        database.userSession().save(it)
                    } ?: UserSessionEntity(
                        username = "Guest",
                        passwordSalt = "",
                        passwordHash = "",
                        isLoggedIn = true
                    ).also {
                        database.userSession().save(it)
                    }
                }
                loadMainState()
                startActivitySegment()
                recordAppSessionForResume()
            } catch (error: Exception) {
                android.util.Log.e("CielBible", "Could not prepare the Bible", error)
                bibleNotice = "Your Bible isn’t ready right now. Please try again later."
            } finally {
                isSessionLoading = false
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (::kumpisalSecurity.isInitialized && selectedTab == TAB_KUMPISAL) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            kumpisalAccessState = null
            kumpisalEntries = null
            kumpisalEntriesLoading = false
            if (account != null) renderMain()
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        val dashboardExpired = account != null && dashboardDayKey != localDayKey(System.currentTimeMillis())
        if (dashboardExpired) {
            dashboardStats = null
            dashboardVerse = null
            dashboardTarget = null
        }
        if (account != null) {
            startActivitySegment()
            recordAppSessionForResume()
            if (dashboardExpired && selectedTab == TAB_HOME) renderMain()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BibleReminderScheduler.EXTRA_OPEN_BIBLE, false)) {
            val book = intent.getStringExtra(BibleReminderScheduler.EXTRA_BOOK)
            val chapter = intent.getIntExtra(BibleReminderScheduler.EXTRA_CHAPTER, 0)
            if (!book.isNullOrBlank() && chapter > 0) {
                selectedTab = TAB_BIBLE
                openChapter(book, chapter, null)
            } else selectTab(TAB_BIBLE)
        }
    }

    override fun onPause() {
        flushActivitySegment()
        activityResumed = false
        appSessionRecordedForResume = false
        if (::kumpisalSecurity.isInitialized && selectedTab == TAB_KUMPISAL) {
            kumpisalSecurity.lock()
            kumpisalAccessState = null
            kumpisalEntries = null
            kumpisalEntriesLoading = false
        }
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && activityResumed && trackedModule != null) {
            segmentLastInteraction = SystemClock.elapsedRealtime()
        }
        return super.dispatchTouchEvent(event)
    }

    private suspend fun loadMainState() {
        val state = withContext(Dispatchers.IO) {
            Triple(
                database.bible().books(),
                database.readingProgress().recent().firstOrNull(),
                database.conversations().latestConversation()
            )
        }
        bibleBooks = state.first
        state.second?.let {
            selectedBook = it.book
            selectedChapter = it.chapter
            selectedVerse = it.lastVerse.takeIf { verse -> verse > 0 }
        }
        val notificationBook = intent.getStringExtra(BibleReminderScheduler.EXTRA_BOOK)
        val notificationChapter = intent.getIntExtra(BibleReminderScheduler.EXTRA_CHAPTER, 0)
        if (!notificationBook.isNullOrBlank() && notificationChapter > 0 && notificationBook in bibleBooks) {
            selectedBook = notificationBook
            selectedChapter = notificationChapter
            selectedVerse = null
        }
        currentConversationId = state.third?.id
        conversationMessages = currentConversationId?.let { id ->
            withContext(Dispatchers.IO) { database.messages().forConversation(id) }
        }.orEmpty()
        currentAvatarEmotion = avatarEmotionFor(conversationMessages)
        if (selectedBook == null) selectedBook = bibleBooks.firstOrNull()
        selectedBook?.let { book ->
            val chapterState = withContext(Dispatchers.IO) {
                database.bible().chapter(book, selectedChapter) to database.bible().chapters(book)
            }
            if (chapterState.first.isNotEmpty() && !notificationBook.isNullOrBlank() && notificationChapter > 0 && notificationBook == book) {
                withContext(Dispatchers.IO) { statsRepository.recordChapterOpened(book, selectedChapter) }
            }
            chapterVerses = chapterState.first
            availableChapters = chapterState.second
        }
    }

    private fun render() {
        if (isSessionLoading) renderSessionLoading()
        else renderMain()
    }

    private fun renderSessionLoading() {
        val root = vertical().apply { gravity = Gravity.CENTER }
        root.addView(text("Ciel: Your Safe Space", 25f, color(R.color.forest), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(ProgressBar(this).apply { isIndeterminate = true })
        installRoot(root)
    }

    private fun installRoot(root: LinearLayout) {
        root.id = R.id.main
        root.setBackgroundColor(color(R.color.cream))
        window.statusBarColor = color(R.color.cream)
        window.navigationBarColor = color(R.color.cream)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, bars.top, 0, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun renderMain() {
        val root = vertical()
        val page = when (selectedTab) {
            TAB_HOME -> renderHomeScreen()
            TAB_CIEL -> renderChatScreen()
            TAB_BIBLE -> renderBibleScreen()
            TAB_HISTORY -> renderHistoryScreen()
            TAB_SETTINGS -> renderSettingsScreen()
            TAB_STATS -> renderStatisticsScreen()
            TAB_KUMPISAL -> renderKumpisalScreen()
            else -> renderHomeScreen()
        }
        root.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(renderBottomNavigation())
        installRoot(root)
    }

    private fun renderBottomNavigation(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(6), dp(5), dp(4))
            setBackgroundColor(color(R.color.surface_card))
            elevation = dp(8).toFloat()
        }
        val tabs = listOf(
            Triple(TAB_HOME, "Home", R.drawable.ic_nav_home),
            Triple(TAB_CIEL, "Chat", R.drawable.ic_nav_chat),
            Triple(TAB_BIBLE, "Bible", R.drawable.ic_nav_bible),
            Triple(TAB_HISTORY, "History", R.drawable.ic_nav_history),
            Triple(TAB_KUMPISAL, "Kumpisal", R.drawable.ic_nav_kumpisal),
            Triple(TAB_STATS, "Stats", R.drawable.ic_nav_stats),
            Triple(TAB_SETTINGS, "Settings", R.drawable.ic_nav_settings)
        )
        tabs.forEach { (tab, label, icon) ->
            val active = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(4))
                background = if (active) rounded(themeAccent(), dp(14).toFloat()) else null
                setOnClickListener { selectTab(tab) }
            }
            val image = ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(if (active) Color.WHITE else color(R.color.muted))
                contentDescription = label
            }
            item.addView(image, LinearLayout.LayoutParams(dp(22), dp(22)))
            item.addView(text(label, 8.5f, if (active) Color.WHITE else color(R.color.muted), bold = active).apply {
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(2), dp(2), 0)
            })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return nav
    }

    private fun selectTab(tab: String) {
        if (selectedTab == tab && !(tab == TAB_SETTINGS && settingsShowingAbout)) return
        flushActivitySegment()
        if (selectedTab == TAB_KUMPISAL && tab != TAB_KUMPISAL && ::kumpisalSecurity.isInitialized) {
            kumpisalSecurity.lock()
            kumpisalAccessState = null
            kumpisalEntries = null
            kumpisalEntriesLoading = false
            isNewReflection = false
            reflectionDraft = ""
            editingReflectionId = null
            selectedPrompts.clear()
        }
        selectedTab = tab
        if (tab == TAB_KUMPISAL) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        startActivitySegment()
        if (tab == TAB_BIBLE) selectedBook?.let { openChapter(it, selectedChapter, selectedVerse, redraw = false) }
        if (tab == TAB_HISTORY && !historyLoaded) loadConversationHistory()
        if (tab == TAB_SETTINGS) settingsShowingAbout = false
        renderMain()
    }

    private fun currentStatsModule(): String? = when (selectedTab) {
        TAB_HOME -> "Other"
        TAB_CIEL -> "Ciel"
        TAB_BIBLE -> "Bible"
        TAB_HISTORY, TAB_SETTINGS, TAB_STATS -> "Other"
        else -> null // Kumpisal notes are deliberately excluded from usage statistics.
    }

    private fun renderHomeScreen(): View {
        val page = pageColumn().apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, 0, 0, dp(12)) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ciel)
            contentDescription = "Ciel"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.TRANSPARENT, dp(16).toFloat())
            clipToOutline = true
        }
        content.addView(logo, LinearLayout.LayoutParams(dp(94), dp(94)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(7)
            bottomMargin = dp(10)
        })
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) { in 5..11 -> "Good morning!"; in 12..16 -> "Good afternoon!"; else -> "Good evening!" }
        content.addView(text(greeting, 21f, color(R.color.forest), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(13))
        })

        val verseCard = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(14)) }
        verseCard.addView(text("VERSE OF THE DAY", 10f, color(R.color.sage_text), bold = true))
        val verse = dashboardVerse
        if (verse == null) {
            verseCard.addView(text(if (dashboardLoading) "Finding today's verse…" else "Your bundled Bible is getting ready.", 14f, color(R.color.muted)).apply {
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            verseCard.addView(text(verse.theme.uppercase(Locale.ROOT), 11f, color(R.color.muted), bold = true).apply {
                setPadding(0, dp(8), 0, 0)
            })
            verseCard.addView(text(verse.reference, 14f, color(R.color.forest), bold = true).apply {
                setPadding(0, dp(7), 0, dp(4))
            })
            verseCard.addView(text("“" + verse.passage.text + "”", 17f, color(R.color.black)).apply {
                setLineSpacing(dp(4).toFloat(), 1.04f)
                setTextIsSelectable(true)
            })
            verseCard.addView(text(verse.reflection, 12f, color(R.color.muted)).apply {
                setPadding(0, dp(8), 0, dp(11))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("Read verse", filled = true) {
                openReference(verse.passage.book, verse.passage.chapter, verse.passage.verse)
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(7) })
            actions.addView(actionButton("Share image", filled = false) {
                VerseShareActivity.launch(this, verse)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            verseCard.addView(actions)
        }
        content.addView(card(verseCard, tinted = true), marginBottom(dp(10)))

        val continueCard = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(14)) }
        continueCard.addView(text("CONTINUE READING", 11f, color(R.color.sage_text), bold = true))
        val target = dashboardTarget
        if (target == null) {
            val stats = dashboardStats
            val completedEverything = stats != null && stats.totalBibleChapters > 0 && stats.chaptersCompleted >= stats.totalBibleChapters
            continueCard.addView(text(
                if (completedEverything) "Every chapter read. Choose a passage to revisit." else "Your next chapter will appear here.",
                13f, color(R.color.muted)
            ).apply { setPadding(0, dp(7), 0, dp(8)) })
            continueCard.addView(actionButton("Explore Bible", filled = false, compact = true) { selectTab(TAB_BIBLE) },
                LinearLayout.LayoutParams(-1, dp(38)))
        } else {
            val wasOpened = dashboardStats?.recentChapters?.any {
                it.book == target.book && it.chapter == target.chapter && it.completedAt == null
            } == true
            continueCard.addView(text(if (wasOpened) "Currently reading" else "Your next chapter", 13f, color(R.color.muted)).apply {
                setPadding(0, dp(6), 0, 0)
            })
            continueCard.addView(text(target.book + " " + target.chapter, 20f, color(R.color.black), bold = true).apply {
                setPadding(0, dp(2), 0, dp(9))
            })
            continueCard.addView(actionButton("Continue reading", filled = true, compact = true) {
                selectedTab = TAB_BIBLE
                openChapter(target.book, target.chapter, null)
            }, LinearLayout.LayoutParams(-1, dp(38)))
        }
        content.addView(card(continueCard), marginBottom(dp(9)))

        val streakCount = dashboardStats?.readingStreak ?: 0
        val streakText = when {
            streakCount > 1 -> streakCount.toString() + " day reading streak"
            streakCount == 1 -> "1 day reading streak"
            else -> "Your reading streak starts with one chapter"
        }
        val streakRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(color(R.color.surface_card), dp(14).toFloat())
        }
        streakRow.addView(text("✦", 17f, color(R.color.forest), bold = true).apply { setPadding(0, 0, dp(9), 0) })
        streakRow.addView(text(streakText, 12f, color(R.color.black), bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        dashboardStats?.let { stats ->
            if (stats.longestReadingStreak > 0) streakRow.addView(text("Best " + stats.longestReadingStreak, 11f, color(R.color.muted)))
        }
        content.addView(streakRow)
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (!dashboardLoading && dashboardDayKey != localDayKey(System.currentTimeMillis())) loadDashboard()
        return page
    }

    private fun statTile(value: String, label: String): View = vertical().apply {
        setPadding(0, dp(9), 0, dp(5))
        addView(text(value, 21f, color(R.color.forest), bold = true))
        addView(text(label, 11f, color(R.color.muted)))
    }

    private fun weeklyReadingStrip(week: List<com.example.hacklaguna2026.stats.WeekDayActivity>): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val max = week.maxOfOrNull { it.chapters }?.coerceAtLeast(1) ?: 1
        week.forEach { day ->
            val column = vertical().apply { gravity = Gravity.CENTER; setPadding(dp(2), 0, dp(2), 0) }
            column.addView(text(day.chapters.takeIf { it > 0 }?.toString().orEmpty(), 10f, color(R.color.sage_text), bold = day.chapters > 0).apply {
                gravity = Gravity.CENTER
            })
            val barFrame = FrameLayout(this)
            val bar = View(this).apply {
                background = rounded(if (day.chapters > 0) themeAccent() else color(R.color.sage), dp(4).toFloat())
            }
            val barHeight = if (day.chapters == 0) dp(3) else (dp(7) + dp(27) * day.chapters / max).coerceAtMost(dp(34))
            barFrame.addView(bar, FrameLayout.LayoutParams(-1, barHeight, Gravity.BOTTOM))
            column.addView(barFrame, LinearLayout.LayoutParams(-1, dp(36)))
            column.addView(text(day.label.take(1), 10f, color(R.color.muted)).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
            row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return row
    }

    private fun loadDashboard() {
        if (dashboardLoading) return
        dashboardLoading = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Triple(statsRepository.snapshot(), DailyVerseSelector.select(database), statsRepository.continueReadingTarget())
                }
                dashboardStats = result.first
                dashboardVerse = result.second
                dashboardTarget = result.third
                dashboardDayKey = localDayKey(System.currentTimeMillis())
            } catch (error: Exception) {
                android.util.Log.w("CielHome", "Could not load the home dashboard", error)
                dashboardDayKey = localDayKey(System.currentTimeMillis())
            } finally {
                dashboardLoading = false
                if (dashboardNeedsReload) {
                    dashboardNeedsReload = false
                    loadDashboard()
                    return@launch
                }
                if (selectedTab == TAB_HOME) renderMain()
            }
        }
    }

    private fun refreshDashboard() {
        dashboardStats = null
        dashboardVerse = null
        dashboardTarget = null
        dashboardDayKey = null
        if (selectedTab == TAB_HOME) renderMain()
        if (account != null) {
            if (dashboardLoading) dashboardNeedsReload = true else loadDashboard()
        }
    }

    private fun startActivitySegment() {
        if (!activityResumed || account == null || trackedModule != null) return
        val module = currentStatsModule() ?: run { trackedModule = null; return }
        trackedModule = module
        segmentStartedElapsed = SystemClock.elapsedRealtime()
        segmentLastInteraction = segmentStartedElapsed
        segmentStartedWall = System.currentTimeMillis()
    }

    private fun recordAppSessionForResume() {
        if (!activityResumed || account == null || appSessionRecordedForResume) return
        appSessionRecordedForResume = true
        lifecycleScope.launch(Dispatchers.IO) { statsRepository.beginSession() }
    }

    private fun flushActivitySegment() {
        val module = trackedModule ?: return
        val now = SystemClock.elapsedRealtime()
        val activeUntil = minOf(now, segmentLastInteraction + IDLE_GRACE_MS)
        val duration = (activeUntil - segmentStartedElapsed).coerceAtLeast(0L)
        val startedAt = segmentStartedWall
        trackedModule = null
        if (duration > 0L) lifecycleScope.launch(Dispatchers.IO) {
            statsRepository.recordModuleTime(module, startedAt, duration)
        }
    }

    private fun renderChatScreen(): View {
        val page = vertical().apply { setPadding(dp(20), dp(16), dp(20), dp(10)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val avatar = ImageView(this).apply {
            setImageResource(R.drawable.ciel)
            contentDescription = "Ciel"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(12).toFloat()
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(8) })
        val title = vertical()
        title.addView(text("Ciel", 21f, color(R.color.black), bold = true))
        title.addView(text("A gentle companion for faith and life", 11f, color(R.color.muted)))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("Clear Chat", filled = false, compact = true, enabled = conversationMessages.isNotEmpty()) {
            clearCurrentConversation()
        })
        header.addView(actionButton("New Chat", filled = false, compact = true) { startNewConversation() }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(5) }
        })
        page.addView(header, marginBottom(dp(10)))
        val onlineReady = onlineConversationSettings.isEnabled() && groqCredentials.hasKey()
        val modeText = when {
            chatModeLabel == "Switching to offline" -> "Connection lost • continuing offline"
            chatModeLabel == "Online" -> "Online • connected with your Groq key"
            onlineReady -> "Online is on • eligible messages go to Groq"
            else -> "Offline • Your messages stay on this device"
        }
        page.addView(text(modeText, 11f, color(R.color.sage_text)).apply {
            setPadding(dp(3), 0, dp(3), dp(7))
            contentDescription = modeText
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val messages = vertical().apply { setPadding(0, dp(6), 0, dp(10)) }
        val visibleMessages = conversationMessages.filter { it.role != "system" }
        if (visibleMessages.isEmpty()) {
            val welcome = vertical().apply { setPadding(dp(18), dp(18), dp(18), dp(16)) }
            welcome.addView(text("Welcome. What’s on your heart today?", 18f, color(R.color.black), bold = true))
            welcome.addView(text("Ask about Scripture, faith, or what you’re walking through. Ciel is here to listen and help you reflect.", 14f, color(R.color.muted)).apply {
                setPadding(0, dp(8), 0, dp(12))
            })
            welcome.addView(actionButton("Help me find hope in a difficult week", filled = false) {
                sendMessage("Help me find hope in a difficult week.")
            })
            if (localModelManager.status() != LocalModelStatus.READY) {
                welcome.addView(text(
                    "Set up the offline companion once to generate chat replies without internet. Your Bible and saved data remain available offline.",
                    12f, color(R.color.sage_text)
                ).apply { setPadding(0, dp(11), 0, dp(6)) })
                welcome.addView(actionButton("Set up offline companion", filled = false) {
                    selectTab(TAB_SETTINGS)
                })
            }
            messages.addView(card(welcome), marginBottom(dp(10)))
        } else {
            visibleMessages.forEach { message ->
                val userMessage = message.role == "user"
                val reply = if (userMessage) null else ChatReplyCodec.decode(message.content)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = if (userMessage) Gravity.END else Gravity.START
                    setPadding(0, 0, 0, dp(9))
                }
                val bubble = vertical().apply { setPadding(dp(14), dp(11), dp(14), dp(11)) }
                bubble.addView(text(if (userMessage) "You" else "Ciel", 11f,
                    if (userMessage) color(R.color.forest) else color(R.color.sage_text), bold = true))
                bubble.addView(text(if (userMessage) message.content else reply!!.answer, 15f, color(R.color.black)).apply {
                    setPadding(0, dp(5), 0, 0)
                    setTextIsSelectable(true)
                    setLineSpacing(dp(4).toFloat(), 1.06f)
                })
                val bubbleCard = card(bubble, tinted = userMessage)
                bubbleCard.alpha = 0f
                bubbleCard.animate().alpha(1f).setDuration(160L).start()
                val available = (resources.displayMetrics.widthPixels - dp(74)).coerceAtLeast(dp(180))
                row.addView(bubbleCard, LinearLayout.LayoutParams((available * 0.9f).toInt(), -2))
                messages.addView(row)
                if (reply != null) {
                    messages.addView(actionButton("Copy response", filled = false, compact = true) {
                        copyText(reply.answer)
                        Toast.makeText(this, "Response copied.", Toast.LENGTH_SHORT).show()
                    }.apply { layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { bottomMargin = dp(6) } })
                    reply.references.forEach { reference ->
                        val verseCardContent = vertical().apply { setPadding(dp(13), dp(10), dp(13), dp(9)) }
                        verseCardContent.addView(text(reference.label, 13f, color(R.color.forest), bold = true))
                        if (reference.preview.isNotBlank()) {
                            verseCardContent.addView(text(reference.preview, 13f, color(R.color.black)).apply {
                                setPadding(0, dp(4), 0, dp(6))
                                maxLines = 3
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            })
                        }
                        verseCardContent.addView(actionButton("Check the verse", filled = false, compact = true) {
                            openReference(reference.book, reference.chapter, reference.startVerse)
                        })
                        messages.addView(card(verseCardContent), marginBottom(dp(8)))
                    }
                }
            }
        }
        if (isSending) {
            streamingResponseView = text("Ciel is thinking…", 13f, color(R.color.sage_text)).apply {
                setPadding(dp(8), dp(4), 0, dp(10))
                setLineSpacing(dp(3).toFloat(), 1.04f)
            }
            messages.addView(streamingResponseView)
        } else {
            streamingResponseView = null
        }
        if (isConversationLoading) {
            messages.addView(text("Loading conversation…", 13f, color(R.color.sage_text)).apply {
                setPadding(dp(8), dp(4), 0, dp(10))
            })
        }
        if (chatNotice.isNotBlank()) {
            val notice = vertical().apply { setPadding(dp(13), dp(11), dp(13), dp(11)) }
            notice.addView(text(chatNotice, 13f, color(R.color.forest_dark)))
            failedQuestion?.let { question ->
                notice.addView(actionButton("Retry", filled = false, compact = true) {
                    sendMessage(question, persistUserQuestion = false, checkSpam = false)
                }.apply { layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { topMargin = dp(5) } })
            }
            messages.addView(card(notice, tinted = true), marginBottom(dp(8)))
        }
        scroll.addView(messages)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.post { scroll.smoothScrollTo(0, messages.height) }

        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val emotionAvatar = ImageView(this).apply {
            setImageResource(avatarResource(currentAvatarEmotion))
            contentDescription = "Ciel’s expression"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            alpha = 0f
            animate().alpha(1f).setDuration(180L).start()
        }
        composer.addView(emotionAvatar, LinearLayout.LayoutParams(dp(38), dp(42)).apply { marginEnd = dp(6) })
        val input = editField("Message Ciel…", minLines = 1, maxLines = 4).apply {
            gravity = Gravity.CENTER_VERTICAL
            isEnabled = !isSending
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        val sendButton = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = if (isSending) "Stop Ciel's reply" else "Send message"
            isEnabled = true
            setOnClickListener {
                if (isSending) {
                    chatNotice = "Stopping Ciel's reply…"
                    chatSendJob?.cancel()
                    return@setOnClickListener
                }
                val messageSnapshot = input.text.toString()
                if (messageSnapshot.isNotBlank()) {
                    if (sendMessage(messageSnapshot)) input.text.clear()
                }
            }
        }
        val sendVisual = FrameLayout(this).apply {
            background = rounded(color(R.color.forest), dp(13).toFloat())
        }
        if (isSending) {
            sendVisual.addView(text("Stop", 10f, color(R.color.white), bold = true).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        } else {
            sendVisual.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_send)
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        }
        sendButton.addView(sendVisual, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(54), dp(48)))
        page.addView(composer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return page
    }

    private fun sendMessage(rawMessage: String, persistUserQuestion: Boolean = true, checkSpam: Boolean = true): Boolean {
        val content = rawMessage.trim()
        if (content.isBlank() || isSending) return false
        if (checkSpam) {
            val rejection = chatSpamGuard.rejection(content, SystemClock.elapsedRealtime())
            if (rejection != null) {
                Toast.makeText(this, rejection, Toast.LENGTH_SHORT).show()
                return false
            }
        }
        isSending = true
        currentAvatarEmotion = classifyAvatarEmotion(content)
        chatModeLabel = "Offline"
        chatNotice = ""
        failedQuestion = null
        renderMain()
        chatSendJob = lifecycleScope.launch {
            var targetId = currentConversationId
            try {
                if (targetId == null) {
                    targetId = withContext(Dispatchers.IO) { conversationRepository.createConversation(content) }
                    currentConversationId = targetId
                }
                val result = chatViewModel.send(targetId, content, persistUserQuestion) { partial ->
                    runOnUiThread { streamingResponseView?.text = partial }
                }
                currentConversationId = result.conversationId
                conversationMessages = result.messages
                currentAvatarEmotion = result.reply.avatarEmotion ?: classifyAvatarEmotion(content)
                if (chatViewModel.localModelLoaded) localModelFailure = ""
                chatModeLabel = when (result.mode) {
                    ChatViewModel.ChatMode.ONLINE -> "Online"
                    ChatViewModel.ChatMode.SWITCHING_TO_OFFLINE -> "Switching to offline"
                    ChatViewModel.ChatMode.OFFLINE -> "Offline"
                }
                chatNotice = result.notice.orEmpty()
                failedQuestion = null
                historyLoaded = false
            } catch (error: CancellationException) {
                chatNotice = "Reply stopped. Your message is saved; you can retry it when you're ready."
                failedQuestion = content
                targetId?.let { id ->
                    conversationMessages = withContext(NonCancellable + Dispatchers.IO) { conversationRepository.messages(id) }
                }
            } catch (error: Exception) {
                android.util.Log.w("CielChat", "Chat reply failed (${error.javaClass.simpleName}).")
                chatNotice = when (error) {
                    is LocalModelUnavailableException, is LocalModelInferenceException, is GroqApiException ->
                        error.message ?: "Ciel couldn't complete the reply. Please try again."
                    else -> "Ciel couldn't complete the reply right now. You can still explore the Bible and your saved reflections."
                }
                if (error is LocalModelUnavailableException || error is LocalModelInferenceException) {
                    localModelFailure = error.message.orEmpty()
                }
                failedQuestion = content
                targetId?.let { id ->
                    conversationMessages = withContext(Dispatchers.IO) { conversationRepository.messages(id) }
                }
            } finally {
                currentConversationId = targetId
                isSending = false
                chatSendJob = null
                streamingResponseView = null
                renderMain()
            }
        }
        return true
    }

    private fun startNewConversation() {
        currentConversationId = null
        conversationMessages = emptyList()
        currentAvatarEmotion = AvatarEmotion.NEUTRAL
        chatNotice = ""
        failedQuestion = null
        selectedTab = TAB_CIEL
        renderMain()
    }

    private fun clearCurrentConversation() {
        val id = currentConversationId ?: return
        AlertDialog.Builder(this)
            .setTitle("Clear this conversation?")
            .setMessage("This removes the conversation and its messages from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    conversationRepository.deleteConversation(id)
                    val remaining = conversationRepository.conversations().firstOrNull()
                    val remainingMessages = remaining?.let { conversationRepository.messages(it.id) }.orEmpty()
                    withContext(Dispatchers.Main) {
                        currentConversationId = remaining?.id
                        conversationMessages = remainingMessages
                    currentAvatarEmotion = avatarEmotionFor(remainingMessages)
                        chatNotice = ""
                        failedQuestion = null
                        renderMain()
                    }
                }
            }
            .show().also(::styleDialog)
    }

    private fun loadConversationHistory() {
        isConversationLoading = true
        lifecycleScope.launch {
            historyConversations = withContext(Dispatchers.IO) { conversationRepository.conversations() }
            historyLoaded = true
            isConversationLoading = false
            if (selectedTab == TAB_HISTORY) renderMain()
        }
    }

    private fun renderHistoryScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Conversation history", "Your saved conversations stay on this device")
        if (!historyLoaded) {
            page.addView(ProgressBar(this).apply { isIndeterminate = true })
            return page
        }
        if (historyConversations.isEmpty()) {
            page.addView(card(text("Your conversations will appear here after you chat with Ciel.", 15f, color(R.color.muted)).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }))
            return page
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val list = vertical()
        historyConversations.forEach { conversation ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(10), dp(12)) }
            val details = vertical()
            details.addView(text(conversation.title, 15f, color(R.color.black), bold = true).apply { maxLines = 2 })
            details.addView(text(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(conversation.updatedAt)), 11f, color(R.color.muted)).apply { setPadding(0, dp(4), 0, 0) })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("Open", filled = false, compact = true) {
                lifecycleScope.launch {
                    currentConversationId = conversation.id
                    conversationMessages = withContext(Dispatchers.IO) { conversationRepository.messages(conversation.id) }
                    currentAvatarEmotion = avatarEmotionFor(conversationMessages)
                    chatNotice = ""
                    failedQuestion = null
                    selectedTab = TAB_CIEL
                    renderMain()
                }
            })
            list.addView(card(row), marginBottom(dp(9)))
        }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun openReference(book: String, chapter: Int, verse: Int?) {
        lifecycleScope.launch {
            val actual = withContext(Dispatchers.IO) {
                val verses = database.bible().chapter(book, chapter)
                if (verse != null && verses.none { it.verse == verse }) emptyList() else verses
            }
            if (actual.isEmpty()) {
                Toast.makeText(this@MainActivity, "That verse isn’t available right now.", Toast.LENGTH_SHORT).show()
            } else {
                selectedTab = TAB_BIBLE
                openChapter(book, chapter, verse)
            }
        }
    }

    private fun renderBibleScreen(): View = bibleScreen()

    private fun bibleScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Bible", "Read and explore Scripture anytime")
        page.addView(dailyReminderCard(), marginBottom(dp(10)))
        if (bibleBooks.isEmpty()) {
            val content = vertical().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
            content.addView(text("Your Bible, close at hand", 18f, color(R.color.black), bold = true))
            content.addView(text(
                bibleNotice.ifBlank { "Your Bible is always here, even when you’re offline." },
                14f, color(R.color.muted)
            ).apply { setPadding(0, dp(8), 0, 0) })
            page.addView(card(content), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            return page
        }
        val search = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val searchField = editField("Search a book or enter John 3:16")
        search.addView(searchField, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(7) })
        search.addView(actionButton("Go", compact = true) { searchBible(searchField.text.toString()) })
        page.addView(search, marginBottom(dp(11)))

        val pickerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val bookName = selectedBook ?: bibleBooks.first()
        pickerRow.addView(actionButton(bookName, filled = false) { chooseBook() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(7) })
        pickerRow.addView(actionButton("Chapter $selectedChapter", filled = false) { chooseChapter() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        page.addView(pickerRow, marginBottom(dp(9)))

        val chapterTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chapterTop.addView(actionButton("‹ Previous", filled = false, compact = true) { stepChapter(-1) }, LinearLayout.LayoutParams(-2, dp(38)))
        chapterTop.addView(text("$bookName  $selectedChapter", 15f, color(R.color.forest), bold = true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        chapterTop.addView(actionButton("Next ›", filled = false, compact = true) { stepChapter(1) }, LinearLayout.LayoutParams(-2, dp(38)))
        page.addView(chapterTop, marginBottom(dp(10)))

        val readingScroll = ScrollView(this).apply { clipToPadding = false }
        val verseList = vertical().apply { setPadding(0, 0, 0, dp(8)) }
        var selectedVerseCard: View? = null
        if (chapterVerses.isEmpty()) {
            verseList.addView(text("Loading this chapter…", 14f, color(R.color.muted)).apply { setPadding(dp(4), dp(16), 0, 0) })
        } else {
            chapterVerses.forEach { verse ->
                val selected = selectedVerse == verse.verse
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                }
                content.addView(text(verse.verse.toString(), 12f, color(R.color.forest), bold = true).apply {
                    setPadding(0, dp(2), dp(11), 0)
                })
                content.addView(text(verse.text, 16f, color(R.color.black)).apply {
                    setLineSpacing(dp(3).toFloat(), 1.04f)
                    setTextIsSelectable(true)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val verseCard = card(content, tinted = selected)
                if (selected) selectedVerseCard = verseCard
                verseCard.setOnClickListener { showVerseActions(verse) }
                verseCard.setOnLongClickListener { showVerseActions(verse); true }
                verseList.addView(verseCard, marginBottom(dp(7)))
            }
        }
        readingScroll.addView(verseList)
        page.addView(readingScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        selectedVerseCard?.let { target ->
            readingScroll.post { readingScroll.smoothScrollTo(0, target.top) }
        }

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        footer.addView(actionButton("Bookmarks", filled = false, compact = true) { openBookmarks() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(7) })
        footer.addView(actionButton("Mark as Read") { markChapterComplete() }, LinearLayout.LayoutParams(0, dp(46), 1.5f))
        page.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (bibleNotice.isNotBlank()) page.addView(text(bibleNotice, 12f, color(R.color.sage_text)).apply { setPadding(0, dp(5), 0, 0) })
        return page
    }

    private fun dailyReminderCard(): View {
        val content = vertical().apply { setPadding(dp(14), dp(11), dp(14), dp(11)) }
        content.addView(text("A moment with God", 15f, color(R.color.black), bold = true))
        val status = if (dailyReminderEnabled) {
            "Daily reminder on · ${formatReminderTime()}"
        } else {
            "A gentle reminder to spend time in the Word"
        }
        content.addView(text(status, 12f, color(R.color.muted)).apply { setPadding(0, dp(3), 0, dp(8)) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val toggle = Switch(this).apply {
            text = "Daily reminder"
            textSize = 13f
            setTextColor(color(R.color.black))
            isChecked = dailyReminderEnabled
            thumbTintList = ColorStateList.valueOf(if (dailyReminderEnabled) themeAccent() else color(R.color.muted))
            setOnCheckedChangeListener { _, checked -> changeReminderEnabled(checked) }
        }
        controls.addView(toggle, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(actionButton(formatReminderTime(), filled = false, compact = true) {
            TimePickerDialog(this, { _, hour, minute ->
                reminderHour = hour
                reminderMinute = minute
                saveReminderTime()
                renderMain()
            }, reminderHour, reminderMinute, false).apply {
                show()
                styleDialog(this)
            }
        }, LinearLayout.LayoutParams(-2, dp(42)))
        content.addView(controls)
        if (reminderPermissionMessage.isNotBlank()) {
            content.addView(text(reminderPermissionMessage, 11f, color(R.color.muted)).apply {
                setPadding(0, dp(5), 0, 0)
            })
        }
        return card(content)
    }

    private fun changeReminderEnabled(enabled: Boolean) {
        if (!enabled) {
            reminderPermissionMessage = ""
            setReminderState(false)
            renderMain()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingReminderEnable = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        reminderPermissionMessage = ""
        setReminderState(true)
        renderMain()
    }

    private fun setReminderState(enabled: Boolean) {
        dailyReminderEnabled = enabled
        getSharedPreferences(BibleReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(BibleReminderScheduler.KEY_ENABLED, enabled).apply()
        if (enabled) BibleReminderScheduler.schedule(this, reminderHour, reminderMinute)
        else BibleReminderScheduler.cancel(this)
    }

    private fun saveReminderTime() {
        getSharedPreferences(BibleReminderScheduler.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt(BibleReminderScheduler.KEY_HOUR, reminderHour)
            .putInt(BibleReminderScheduler.KEY_MINUTE, reminderMinute)
            .apply()
        if (dailyReminderEnabled) BibleReminderScheduler.schedule(this, reminderHour, reminderMinute)
    }

    private fun formatReminderTime(): String {
        val hour = if (reminderHour % 12 == 0) 12 else reminderHour % 12
        return String.format(Locale.getDefault(), "%d:%02d %s", hour, reminderMinute, if (reminderHour < 12) "AM" else "PM")
    }

    private fun chooseBook() {
        AlertDialog.Builder(this)
            .setTitle("Choose a book")
            .setItems(bibleBooks.toTypedArray()) { _, index ->
                val book = bibleBooks[index]
                lifecycleScope.launch {
                    val firstChapter = withContext(Dispatchers.IO) { database.bible().chapters(book).firstOrNull() } ?: 1
                    openChapter(book, firstChapter, null)
                }
            }
            .setNegativeButton("Close", null)
            .show().also(::styleDialog)
    }

    private fun chooseChapter() {
        val book = selectedBook ?: return
        if (availableChapters.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("$book · choose a chapter")
            .setItems(availableChapters.map(Int::toString).toTypedArray()) { _, index ->
                openChapter(book, availableChapters[index], null)
            }
            .setNegativeButton("Close", null)
            .show().also(::styleDialog)
    }

    private fun searchBible(raw: String) {
        val query = raw.trim()
        if (query.isBlank()) return
        val match = bibleBooks.sortedByDescending(String::length).firstOrNull { query.equals(it, true) || query.startsWith(it, true) }
        if (match == null) {
            bibleNotice = "No matching Bible book was found."
            renderMain()
            return
        }
        val remainder = query.drop(match.length).trim().removePrefix(":").trim()
        if (remainder.isBlank()) {
            openChapter(match, 1, null)
            return
        }
        val chapterMatch = Regex("^(\\d{1,3})(?:\\s*:\\s*(\\d{1,3}))?$").matchEntire(remainder)
        if (chapterMatch == null) {
            bibleNotice = "Use a reference such as John 3:16."
            renderMain()
            return
        }
        openChapter(match, chapterMatch.groupValues[1].toInt(), chapterMatch.groupValues[2].toIntOrNull())
    }

    private fun openChapter(book: String, chapter: Int, verse: Int?, redraw: Boolean = true, recordVisit: Boolean = true) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val rows = database.bible().chapter(book, chapter)
                if (rows.isNotEmpty() && recordVisit) statsRepository.recordChapterOpened(book, chapter)
                rows to database.bible().chapters(book)
            }
            if (data.first.isEmpty()) {
                bibleNotice = "That chapter isn’t available right now."
                if (redraw) renderMain()
                return@launch
            }
            selectedBook = book
            selectedChapter = chapter
            selectedVerse = verse?.takeIf { number -> data.first.any { it.verse == number } }
            chapterVerses = data.first
            availableChapters = data.second
            bibleNotice = ""
            if (redraw) {
                selectedTab = TAB_BIBLE
                renderMain()
            }
        }
    }

    private fun stepChapter(direction: Int) {
        val book = selectedBook ?: return
        val bookIndex = bibleBooks.indexOf(book)
        val chapterIndex = availableChapters.indexOf(selectedChapter)
        val nextChapter = chapterIndex + direction
        if (nextChapter in availableChapters.indices) {
            openChapter(book, availableChapters[nextChapter], null)
            return
        }
        val otherBook = bibleBooks.getOrNull(bookIndex + direction) ?: return
        lifecycleScope.launch {
            val chapters = withContext(Dispatchers.IO) { database.bible().chapters(otherBook) }
            val chapter = if (direction > 0) chapters.firstOrNull() else chapters.lastOrNull()
            if (chapter != null) openChapter(otherBook, chapter, null)
        }
    }

    private fun showVerseActions(verse: com.example.hacklaguna2026.data.BibleVerseEntity) {
        selectedVerse = verse.verse
        lifecycleScope.launch(Dispatchers.IO) {
            database.readingProgress().savePosition(verse.book, verse.chapter, verse.verse)
            val saved = database.bookmarks().contains(verse.book, verse.chapter, verse.verse)
            withContext(Dispatchers.Main) {
                val reference = "${verse.book} ${verse.chapter}:${verse.verse}"
                val actions = if (saved) arrayOf("Remove bookmark", "Copy verse", "Share verse")
                    else arrayOf("Bookmark verse", "Copy verse", "Share verse")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(reference)
                    .setMessage(verse.text)
                    .setItems(actions) { _, index ->
                        when (index) {
                            0 -> lifecycleScope.launch(Dispatchers.IO) {
                                if (saved) database.bookmarks().remove(verse.book, verse.chapter, verse.verse)
                                else database.bookmarks().add(BookmarkEntity(
                                    book = verse.book,
                                    chapter = verse.chapter,
                                    verse = verse.verse,
                                    text = verse.text,
                                    createdAt = System.currentTimeMillis()
                                ))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, if (saved) "Bookmark removed." else "Verse bookmarked.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            1 -> copyText("$reference — ${verse.text}")
                            2 -> shareText("$reference — ${verse.text}")
                        }
                    }
                    .show().also(::styleDialog)
            }
        }
    }

    private fun openBookmarks() {
        lifecycleScope.launch {
            val bookmarks = withContext(Dispatchers.IO) { database.bookmarks().all() }
            if (bookmarks.isEmpty()) {
                Toast.makeText(this@MainActivity, "Your saved verses will appear here.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Bookmarked verses")
                .setItems(bookmarks.map { "${it.book} ${it.chapter}:${it.verse}" }.toTypedArray()) { _, index ->
                    val mark = bookmarks[index]
                    selectedTab = TAB_BIBLE
                    openChapter(mark.book, mark.chapter, mark.verse)
                }
                .setNegativeButton("Close", null)
                .show().also(::styleDialog)
        }
    }

    private fun markChapterComplete() {
        val book = selectedBook ?: return
        val lastVerse = chapterVerses.maxOfOrNull { it.verse } ?: return
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                statsRepository.markChapterCompleted(book, selectedChapter, lastVerse)
            }
            Toast.makeText(this@MainActivity, if (updated) "Chapter marked as read." else "You’ve already marked this chapter as read today.", Toast.LENGTH_SHORT).show()
            refreshDashboard()
            if (selectedTab == TAB_STATS) renderMain()
        }
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bible verse", value))
        Toast.makeText(this, "Verse copied.", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(value: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        startActivity(Intent.createChooser(send, "Share verse"))
    }

    private fun renderStatisticsScreen(): View = statisticsScreen()

    private fun statisticsScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Statistics", "A gentle look at how you spend time here")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(4), 0, dp(14)) }
        content.addView(card(text("Loading your statistics…", 14f, color(R.color.muted)).apply {
            setPadding(dp(16), dp(17), dp(16), dp(17))
        }))
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { statsRepository.snapshot() }
            content.removeAllViews()
            populateStatistics(content, snapshot)
        }
        return page
    }

    private fun populateStatistics(content: LinearLayout, stats: StatisticsSnapshot) {
        val today = vertical().apply { setPadding(dp(17), dp(16), dp(17), dp(16)) }
        today.addView(text("TODAY", 11f, color(R.color.sage_text), bold = true))
        today.addView(text(formatDuration(stats.totalTimeMs), 28f, color(R.color.forest), bold = true).apply { setPadding(0, dp(4), 0, 0) })
        today.addView(text("${stats.sessions} app session${if (stats.sessions == 1) "" else "s"}", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(2), 0, dp(12))
        })
        val moduleTimes = listOf(
            "Bible reading" to stats.bibleTimeMs,
            "Chatting with Ciel" to stats.chatTimeMs,
            "Other time" to stats.otherTimeMs
        )
        moduleTimes.forEach { (label, duration) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, 13f, color(R.color.black)), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text(formatDuration(duration), 13f, color(R.color.sage_text), bold = true))
            today.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        content.addView(card(today), marginBottom(dp(11)))

        val reading = vertical().apply { setPadding(dp(17), dp(16), dp(17), dp(15)) }
        reading.addView(text("BIBLE READING", 11f, color(R.color.sage_text), bold = true))
        reading.addView(text("${stats.readingStreak} day${if (stats.readingStreak == 1) "" else "s"}", 25f, color(R.color.forest), bold = true).apply {
            setPadding(0, dp(4), 0, 0)
        })
        reading.addView(text("reading streak", 12f, color(R.color.muted)).apply { setPadding(0, 0, 0, dp(12)) })
        val totalChapters = stats.totalBibleChapters
        val progress = if (totalChapters == 0) 0 else (stats.chaptersCompleted * 100 / totalChapters).coerceAtMost(100)
        val progressLabel = if (totalChapters == 0) "Your reading progress will appear here as you read." else "$progress% of Bible chapters completed"
        reading.addView(text(progressLabel, 13f, color(R.color.black), bold = true))
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = progress
            progressTintList = ColorStateList.valueOf(color(R.color.forest))
            progressBackgroundTintList = ColorStateList.valueOf(color(R.color.sage))
            minHeight = dp(8)
        }
        reading.addView(progressBar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(9); bottomMargin = dp(11) })
        val metrics = listOf(
            "Books visited" to stats.booksVisited.toString(),
            "Chapters opened" to stats.chaptersOpened.toString(),
            "Chapters completed" to stats.chaptersCompleted.toString(),
            "Verses in completed chapters" to stats.versesRead.toString()
        )
        metrics.forEach { (label, value) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(label, 13f, color(R.color.muted)), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text(value, 14f, color(R.color.black), bold = true))
            reading.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        content.addView(card(reading), marginBottom(dp(11)))

        val weekly = vertical().apply { setPadding(dp(17), dp(16), dp(17), dp(16)) }
        weekly.addView(text("THIS WEEK", 11f, color(R.color.sage_text), bold = true))
        weekly.addView(text("Activity", 18f, color(R.color.black), bold = true).apply { setPadding(0, dp(4), 0, dp(10)) })
        val maxDuration = stats.week.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
        stats.week.forEach { day ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(day.label, 12f, color(R.color.muted)), LinearLayout.LayoutParams(dp(34), -2))
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = ((day.durationMs * 100 / maxDuration).toInt()).coerceAtMost(100)
                progressTintList = ColorStateList.valueOf(color(R.color.forest))
                progressBackgroundTintList = ColorStateList.valueOf(color(R.color.sage))
            }
            row.addView(bar, LinearLayout.LayoutParams(0, dp(7), 1f).apply { marginEnd = dp(9) })
            val readingMark = if (day.chapters > 0) " · ${day.chapters} ch" else ""
            row.addView(text(formatDuration(day.durationMs) + readingMark, 11f, color(R.color.sage_text)))
            weekly.addView(row, LinearLayout.LayoutParams(-1, dp(25)))
        }
        content.addView(card(weekly), marginBottom(dp(11)))

        val recent = vertical().apply { setPadding(dp(17), dp(16), dp(17), dp(14)) }
        recent.addView(text("RECENT CHAPTERS", 11f, color(R.color.sage_text), bold = true))
        if (stats.recentChapters.isEmpty()) {
            recent.addView(text("Chapters you visit will appear here. Mark a chapter complete when you’ve finished reading it.", 13f, color(R.color.muted)).apply {
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            stats.recentChapters.forEach { progressRow ->
                val status = if (progressRow.completedAt != null) "Completed" else "Opened"
                val item = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                item.addView(text("${progressRow.book} ${progressRow.chapter}", 14f, color(R.color.black), bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                item.addView(text(status, 12f, color(R.color.sage_text)))
                item.setOnClickListener {
                    selectedTab = TAB_BIBLE
                    openChapter(progressRow.book, progressRow.chapter, progressRow.lastVerse.takeIf { it > 0 })
                }
                recent.addView(item, LinearLayout.LayoutParams(-1, dp(38)))
            }
        }
        content.addView(card(recent))
    }

    private fun kumpisalScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Kumpisal", "Your thoughts stay private and on this device")
        when (kumpisalAccessState) {
            null -> {
                page.addView(kumpisalMessageCard("Opening your private space…", "Your entries stay hidden while Kumpisal checks its lock."))
                loadKumpisalAccessState()
            }
            KumpisalAccessState.SETUP_REQUIRED -> page.addView(kumpisalSetupScreen())
            KumpisalAccessState.LOCKED -> page.addView(kumpisalUnlockScreen())
            KumpisalAccessState.UNLOCKED -> {
                if (kumpisalEntries == null && !kumpisalEntriesLoading) loadKumpisalEntries()
                page.addView(
                    if (kumpisalEntries == null) {
                        kumpisalMessageCard("Unlocking your private space…", "Your entries will appear after they are verified.")
                    } else if (isNewReflection) {
                        kumpisalEditorScreen()
                    } else {
                        kumpisalEntriesScreen(kumpisalEntries.orEmpty())
                    },
                    LinearLayout.LayoutParams(-1, 0, 1f)
                )
            }
        }
        return page
    }

    private fun loadKumpisalAccessState() {
        if (kumpisalAccessLoading) return
        kumpisalAccessLoading = true
        lifecycleScope.launch {
            val state = runCatching { withContext(Dispatchers.IO) { kumpisalSecurity.accessState() } }
            kumpisalAccessLoading = false
            if (selectedTab != TAB_KUMPISAL) return@launch
            kumpisalAccessState = state.getOrElse {
                kumpisalNotice = "Kumpisal could not be opened. Please try again."
                KumpisalAccessState.LOCKED
            }
            if (kumpisalAccessState == KumpisalAccessState.UNLOCKED && !kumpisalSecurity.isUnlocked()) {
                kumpisalAccessState = KumpisalAccessState.LOCKED
            }
            renderMain()
        }
    }

    private fun loadKumpisalEntries() {
        if (kumpisalEntriesLoading || !kumpisalSecurity.isUnlocked()) return
        kumpisalEntriesLoading = true
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { kumpisalSecurity.entries() } }
            kumpisalEntriesLoading = false
            if (selectedTab != TAB_KUMPISAL || !kumpisalSecurity.isUnlocked()) return@launch
            result.onSuccess { kumpisalEntries = it }
                .onFailure {
                    kumpisalSecurity.lock()
                    kumpisalAccessState = KumpisalAccessState.LOCKED
                    kumpisalEntries = null
                    kumpisalNotice = "An entry could not be verified. Kumpisal remains locked."
                }
            renderMain()
        }
    }

    private fun kumpisalSetupScreen(): View {
        val content = vertical().apply { setPadding(dp(3), dp(3), dp(3), dp(14)) }
        val cardContent = vertical().apply { setPadding(dp(18), dp(19), dp(18), dp(18)) }
        cardContent.addView(text("Your private space", 20f, color(R.color.black), bold = true))
        cardContent.addView(text(
            "Create a password to keep your confessions personal and protected.",
            14f, color(R.color.muted)
        ).apply { setPadding(0, dp(7), 0, dp(8)) })
        cardContent.addView(text(
            "Use at least 8 characters. Existing entries will be encrypted before they are shown.",
            12f, color(R.color.sage_text)
        ).apply { setPadding(0, 0, 0, dp(11)) })
        val password = privatePasswordField("Create password")
        val confirmation = privatePasswordField("Confirm password")
        cardContent.addView(password, marginBottom(dp(8)))
        cardContent.addView(confirmation, marginBottom(dp(8)))
        val show = CheckBox(this).apply {
            text = "Show passwords"
            textSize = 13f
            setTextColor(color(R.color.muted))
            buttonTintList = ColorStateList.valueOf(themeAccent())
            isSaveEnabled = false
            setOnCheckedChangeListener { _, visible ->
                val method = if (visible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()
                password.transformationMethod = method
                confirmation.transformationMethod = method
                password.setSelection(password.text?.length ?: 0)
                confirmation.setSelection(confirmation.text?.length ?: 0)
            }
        }
        cardContent.addView(show, marginBottom(dp(9)))
        cardContent.addView(actionButton("Create password", filled = true) {
            val first = password.text?.toString()?.toCharArray() ?: charArrayOf()
            val second = confirmation.text?.toString()?.toCharArray() ?: charArrayOf()
            password.text?.clear()
            confirmation.text?.clear()
            if (!first.contentEquals(second)) {
                first.fill('\u0000')
                second.fill('\u0000')
                kumpisalNotice = "Those passwords do not match. Please try again."
                renderMain()
                return@actionButton
            }
            lifecycleScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { kumpisalSecurity.createPassword(first) } }
                second.fill('\u0000')
                if (selectedTab != TAB_KUMPISAL) return@launch
                result.onSuccess {
                    kumpisalNotice = ""
                    kumpisalAccessState = KumpisalAccessState.UNLOCKED
                    kumpisalEntries = null
                    isNewReflection = false
                }.onFailure {
                    first.fill('\u0000')
                    kumpisalNotice = it.message ?: "Kumpisal could not be protected. Please try again."
                }
                renderMain()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        if (kumpisalNotice.isNotBlank()) {
            cardContent.addView(text(kumpisalNotice, 13f, color(R.color.error)).apply { setPadding(0, dp(8), 0, 0) })
        }
        content.addView(card(cardContent, tinted = true))
        return content
    }

    private fun kumpisalUnlockScreen(): View {
        val content = vertical().apply { setPadding(dp(3), dp(3), dp(3), dp(14)) }
        val cardContent = vertical().apply { setPadding(dp(18), dp(19), dp(18), dp(18)) }
        cardContent.addView(text("Welcome back", 20f, color(R.color.black), bold = true))
        cardContent.addView(text("Enter your Kumpisal password to unlock your private entries.", 14f, color(R.color.muted)).apply {
            setPadding(0, dp(7), 0, dp(11))
        })
        val password = privatePasswordField("Password")
        cardContent.addView(password, marginBottom(dp(8)))
        val show = CheckBox(this).apply {
            text = "Show password"
            textSize = 13f
            setTextColor(color(R.color.muted))
            buttonTintList = ColorStateList.valueOf(themeAccent())
            isSaveEnabled = false
            setOnCheckedChangeListener { _, visible ->
                password.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance()
                    else PasswordTransformationMethod.getInstance()
                password.setSelection(password.text?.length ?: 0)
            }
        }
        cardContent.addView(show, marginBottom(dp(9)))
        cardContent.addView(actionButton("Unlock Kumpisal", filled = true) {
            val secret = password.text?.toString()?.toCharArray() ?: charArrayOf()
            password.text?.clear()
            lifecycleScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { kumpisalSecurity.unlock(secret) } }
                secret.fill('\u0000')
                if (selectedTab != TAB_KUMPISAL) return@launch
                result.onSuccess {
                    kumpisalNotice = ""
                    kumpisalAccessState = KumpisalAccessState.UNLOCKED
                    kumpisalEntries = null
                    isNewReflection = false
                }.onFailure {
                    kumpisalAccessState = KumpisalAccessState.LOCKED
                    kumpisalNotice = it.message ?: "That password did not match. Your entries remain locked."
                }
                renderMain()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        cardContent.addView(actionButton("Forgot password?", filled = false, compact = true) {
            confirmKumpisalReset()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })
        if (kumpisalNotice.isNotBlank()) {
            cardContent.addView(text(kumpisalNotice, 13f, color(R.color.error)).apply { setPadding(0, dp(8), 0, 0) })
        }
        content.addView(card(cardContent, tinted = true))
        return content
    }

    private fun privatePasswordField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 15f
        setTextColor(color(R.color.black))
        setHintTextColor(color(R.color.muted))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        transformationMethod = PasswordTransformationMethod.getInstance()
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = outlinedBox()
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    private fun kumpisalMessageCard(title: String, bodyText: String): View {
        val box = vertical().apply { setPadding(dp(17), dp(18), dp(17), dp(18)) }
        box.addView(text(title, 16f, color(R.color.black), bold = true))
        box.addView(text(bodyText, 13f, color(R.color.muted)).apply { setPadding(0, dp(6), 0, 0) })
        return card(box, tinted = true)
    }

    private fun confirmKumpisalReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Kumpisal password?")
            .setMessage("Resetting your Kumpisal password will permanently delete your existing encrypted confessions because they cannot be recovered without the original password.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete entries and reset") { _, _ ->
                lifecycleScope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { kumpisalSecurity.resetAndDeleteEntries() } }
                    if (selectedTab != TAB_KUMPISAL) return@launch
                    result.onSuccess {
                        kumpisalAccessState = KumpisalAccessState.SETUP_REQUIRED
                        kumpisalEntries = null
                        kumpisalNotice = ""
                    }.onFailure {
                        kumpisalNotice = "The reset could not be completed. Your entries have not been opened."
                    }
                    renderMain()
                }
            }
            .show().also(::styleDialog)
    }

    private fun kumpisalEditorScreen(): View {
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(3), 0, dp(12)) }
        val editor = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(15)) }
        editor.addView(text(if (editingReflectionId == null) "Take your time" else "Edit your entry", 16f, color(R.color.black), bold = true))
        reflectionPrompts.forEach { prompt ->
            val checkbox = CheckBox(this).apply {
                text = prompt
                textSize = 13f
                setTextColor(color(R.color.black))
                isChecked = prompt in selectedPrompts
                buttonTintList = ColorStateList.valueOf(themeAccent())
                isSaveEnabled = false
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPrompts.add(prompt) else selectedPrompts.remove(prompt)
                }
            }
            editor.addView(checkbox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        val note = editField("Write freely. This space is yours…", minLines = 5, maxLines = 10).apply {
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            isSaveEnabled = false
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setText(reflectionDraft)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    reflectionDraft = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        editor.addView(note, LinearLayout.LayoutParams(-1, dp(170)).apply { topMargin = dp(8) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Save entry") {
            val textValue = note.text.toString().trim()
            if (textValue.isBlank() && selectedPrompts.isEmpty()) {
                Toast.makeText(this@MainActivity, "Write a few words or choose a prompt first.", Toast.LENGTH_SHORT).show()
                return@actionButton
            }
            val prompts = selectedPrompts.toList().joinToString("\n")
            val entryId = editingReflectionId
            lifecycleScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        if (entryId == null) kumpisalSecurity.save(textValue, prompts)
                        else kumpisalSecurity.update(entryId, textValue, prompts)
                        kumpisalSecurity.entries()
                    }
                }
                if (selectedTab != TAB_KUMPISAL || !kumpisalSecurity.isUnlocked()) return@launch
                result.onSuccess { entries ->
                    kumpisalEntries = entries
                    isNewReflection = false
                    reflectionDraft = ""
                    editingReflectionId = null
                    selectedPrompts.clear()
                    kumpisalNotice = ""
                    Toast.makeText(this@MainActivity, "Your entry is encrypted and saved on this device.", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    kumpisalNotice = "Your entry could not be saved. Kumpisal remains locked."
                    kumpisalSecurity.lock()
                    kumpisalAccessState = KumpisalAccessState.LOCKED
                    kumpisalEntries = null
                }
                renderMain()
            }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(7) })
        actions.addView(actionButton("Cancel", filled = false) {
            isNewReflection = false
            reflectionDraft = ""
            editingReflectionId = null
            selectedPrompts.clear()
            renderMain()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        editor.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(card(editor), marginBottom(dp(12)))
        scroll.addView(content)
        return scroll
    }

    private fun kumpisalEntriesScreen(entries: List<KumpisalEntry>): View {
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(3), 0, dp(12)) }
        content.addView(kumpisalMessageCard(
            "Your private journal",
            "Entries are encrypted on this device and are not sent to Ciel or either AI provider."
        ), marginBottom(dp(11)))
        content.addView(actionButton("Write a private entry") {
            reflectionDraft = ""
            editingReflectionId = null
            selectedPrompts.clear()
            isNewReflection = true
            renderMain()
        }, marginBottom(dp(16)))
        content.addView(text("YOUR ENTRIES", 11f, color(R.color.sage_text), bold = true).apply {
            setPadding(dp(3), 0, 0, dp(8))
        })
        if (entries.isEmpty()) {
            content.addView(kumpisalMessageCard(
                "This space is yours.",
                "Write a few words whenever you’re ready. You can return and edit or delete them anytime."
            ))
        } else {
            entries.forEach { entry -> content.addView(reflectionCard(entry), marginBottom(dp(9))) }
        }
        scroll.addView(content)
        return scroll
    }

    private fun reflectionCard(reflection: KumpisalEntry): View {
        val body = vertical().apply { setPadding(dp(15), dp(13), dp(15), dp(12)) }
        val date = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(reflection.updatedAt))
        body.addView(text(date, 11f, color(R.color.sage_text), bold = true))
        val promptText = reflection.selectedPrompts.lineSequence().filter(String::isNotBlank).joinToString(" · ")
        if (promptText.isNotBlank()) body.addView(text(promptText, 12f, color(R.color.muted)).apply { setPadding(0, dp(5), 0, 0) })
        body.addView(text(reflection.content.ifBlank { "A saved reflection prompt" }, 14f, color(R.color.black)).apply {
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, dp(7))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Edit", filled = false, compact = true) {
            reflectionDraft = reflection.content
            editingReflectionId = reflection.id
            selectedPrompts = reflection.selectedPrompts.lineSequence().filter(String::isNotBlank).toMutableSet()
            isNewReflection = true
            renderMain()
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(7) })
        actions.addView(actionButton("Delete", filled = false, compact = true) {
            deleteReflection(reflection.id)
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        body.addView(actions)
        return card(body)
    }

    private fun deleteReflection(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage("This removes the entry from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { kumpisalSecurity.delete(id) }
                    val entries = runCatching { kumpisalSecurity.entries() }
                    withContext(Dispatchers.Main) {
                        if (kumpisalSecurity.isUnlocked()) {
                            kumpisalEntries = entries.getOrNull()
                        }
                        renderMain()
                    }
                }
            }
            .show().also(::styleDialog)
    }

    private fun renderSettingsScreen(): View {
        if (settingsShowingAbout) return aboutScreen()
        val page = pageColumn()
        addPageHeader(page, "Settings", "Choose the colors and appearance you like")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(4), 0, dp(14)) }

        val themeCard = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        themeCard.addView(text("Theme Color", 17f, color(R.color.black), bold = true))
        themeCard.addView(text("Choose a calm accent color", 13f, color(R.color.muted)).apply { setPadding(0, dp(4), 0, dp(12)) })
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        THEME_OPTIONS.forEach { option ->
            val choice = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(1), dp(4), dp(1), dp(4))
                isClickable = true
                isFocusable = true
                contentDescription = "${option.name}${if (selectedTheme == option.id) ", selected" else ""}"
                setOnClickListener {
                    selectedTheme = option.id
                    appearancePreferences.edit().putString(KEY_THEME_COLOR, selectedTheme).apply()
                    renderMain()
                }
            }
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(option.color))
                    setStroke(dp(if (selectedTheme == option.id) 3 else 1), if (selectedTheme == option.id) color(R.color.black) else color(R.color.line))
                }
            }
            choice.addView(swatch, LinearLayout.LayoutParams(dp(34), dp(34)))
            choice.addView(text(option.shortName, 9f, if (selectedTheme == option.id) color(R.color.black) else color(R.color.muted), bold = selectedTheme == option.id).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, 0)
            })
            swatches.addView(choice, LinearLayout.LayoutParams(0, -2, 1f))
        }
        themeCard.addView(swatches)
        content.addView(card(themeCard), marginBottom(dp(12)))

        val appearanceCard = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        appearanceCard.addView(text("Appearance Mode", 17f, color(R.color.black), bold = true))
        appearanceCard.addView(text("Choose light or dark", 13f, color(R.color.muted)).apply { setPadding(0, dp(4), 0, dp(12)) })
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modes.addView(actionButton("Light", filled = !darkMode) {
            darkMode = false
            appearancePreferences.edit().putBoolean(KEY_DARK_MODE, false).apply()
            renderMain()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        modes.addView(actionButton("Dark", filled = darkMode) {
            darkMode = true
            appearancePreferences.edit().putBoolean(KEY_DARK_MODE, true).apply()
            renderMain()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        appearanceCard.addView(modes)
        content.addView(card(appearanceCard))
        content.addView(onlineConversationCard(), marginBottom(dp(12)))
        content.addView(localModelCard(), marginBottom(dp(12)))
        content.addView(card(actionButton("About Ciel", filled = false) {
            settingsShowingAbout = true
            renderMain()
        }), LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun onlineConversationCard(): View {
        val body = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        body.addView(text("Online Conversations", 17f, color(R.color.black), bold = true))
        body.addView(text(
            if (groqCredentials.hasKey()) "You're connected!" else "Want to go online?",
            15f, color(R.color.forest), bold = true
        ).apply { setPadding(0, dp(7), 0, dp(3)) })
        body.addView(text(
            "Ciel works offline by default. Connect your own Groq API key to enable online conversations. The key is encrypted with Android Keystore and excluded from backups.",
            13f, color(R.color.muted)
        ).apply { setPadding(0, dp(5), 0, dp(10)) })
        body.addView(text(
            if (groqCredentials.hasKey()) "Online access is ready. Saved key: gsk_••••••${maskedSavedKey ?: "••••"}"
            else "No online key is saved. Continue using Ciel offline anytime.",
            13f, color(R.color.sage_text), bold = true
        ).apply { setPadding(0, 0, 0, dp(8)) })
        val onlineSwitch = Switch(this).apply {
            text = "Allow online responses"
            textSize = 14f
            setTextColor(color(R.color.black))
            isChecked = onlineConversationSettings.isEnabled()
            setOnCheckedChangeListener { _, enabled -> onOnlineModeToggled(enabled) }
        }
        body.addView(onlineSwitch)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val keyAction = if (groqCredentials.hasKey()) "Change API Key" else "Add API Key"
        actions.addView(actionButton(keyAction, filled = true, compact = true) {
            showKeyDisclosure { openGroqKeyEditor() }
        }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginEnd = dp(6) })
        if (groqCredentials.hasKey()) {
            actions.addView(actionButton("Remove API Key", filled = false, compact = true) {
                confirmRemoveGroqKey()
            }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginEnd = dp(6) })
            actions.addView(actionButton("Check", filled = false, compact = true) {
                checkGroqConnection()
            }, LinearLayout.LayoutParams(0, dp(43), 1f))
        }
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val moreActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        moreActions.addView(actionButton("Learn how to get an API key", filled = false, compact = true) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }
                .onFailure { Toast.makeText(this@MainActivity, "Couldn't open the key guide.", Toast.LENGTH_SHORT).show() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        moreActions.addView(actionButton("Continue offline", filled = false, compact = true) {
            onlineConversationSettings.setEnabled(false)
            chatModeLabel = "Offline"
            renderMain()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        body.addView(moreActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        if (onlineConnectionMessage.isNotBlank()) {
            body.addView(text(onlineConnectionMessage, 12f, color(R.color.sage_text)).apply { setPadding(0, dp(8), 0, 0) })
        }
        return card(body)
    }

    private fun localModelCard(): View {
        val body = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        body.addView(text("Offline Companion", 17f, color(R.color.black), bold = true))
        body.addView(text(
            "Ciel runs Qwen3 0.6B on this device when offline. The first download is ${LocalModelManager.MODEL_DISPLAY_SIZE}; allow about 550 MB of free storage and 1.5 GB of available memory while chatting. The model is stored privately on your phone. License: ${LocalModelManager.MODEL_LICENSE}.",
            13f, color(R.color.muted)
        ).apply { setPadding(0, dp(5), 0, dp(9)) })
        when (localModelManager.status()) {
            LocalModelStatus.READY -> body.addView(text(
                if (chatViewModel.localModelLoaded) "Offline companion is loaded and ready."
                else "Offline model downloaded. Ciel loads it the first time you start a chat.",
                13f, color(R.color.forest), bold = true
            ))
            LocalModelStatus.INCOMPLETE_DOWNLOAD -> body.addView(text(
                "Download paused at ${formatModelBytes(localModelManager.partialBytes())} of ${LocalModelManager.MODEL_DISPLAY_SIZE}. You can resume it.",
                13f, color(R.color.sage_text)
            ))
            LocalModelStatus.UNAVAILABLE -> body.addView(text("The offline model is unavailable or damaged. Remove it and download a fresh copy.", 13f, color(R.color.sage_text)))
            LocalModelStatus.DOWNLOAD_REQUIRED -> body.addView(text("The offline model has not been downloaded yet.", 13f, color(R.color.sage_text)))
        }
        if (localModelFailure.isNotBlank()) {
            body.addView(text("Last offline model error: $localModelFailure", 12f, color(R.color.sage_text)).apply { setPadding(0, dp(7), 0, 0) })
        }
        val progress = modelDownloadProgress
        if (modelDownloadJob?.isActive == true) {
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                this.progress = progress?.let { ((it.downloadedBytes * 100) / it.totalBytes).toInt() } ?: 0
                progressTintList = ColorStateList.valueOf(themeAccent())
            }
            body.addView(bar, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(9) })
            body.addView(text(
                progress?.let { "Downloading ${formatModelBytes(it.downloadedBytes)} of ${formatModelBytes(it.totalBytes)}…" } ?: "Preparing download…",
                12f, color(R.color.muted)
            ).apply { setPadding(0, dp(5), 0, 0) })
            body.addView(actionButton("Pause download", filled = false, compact = true) {
                modelDownloadJob?.cancel()
                modelDownloadMessage = "Download paused. Your saved progress can be resumed here."
                renderMain()
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7) } })
        } else if (localModelManager.status() == LocalModelStatus.READY) {
            body.addView(actionButton("Remove downloaded model", filled = false, compact = true) {
                confirmRemoveLocalModel()
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7) } })
        } else {
            body.addView(actionButton(
                if (localModelManager.status() == LocalModelStatus.INCOMPLETE_DOWNLOAD) "Resume download" else "Download offline companion",
                filled = true,
                compact = true
            ) { startLocalModelDownload() }.apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) }
            })
        }
        if (modelDownloadMessage.isNotBlank()) {
            body.addView(text(modelDownloadMessage, 12f, color(R.color.sage_text)).apply { setPadding(0, dp(7), 0, 0) })
        }
        return card(body)
    }

    private fun onOnlineModeToggled(enabled: Boolean) {
        if (!enabled) {
            onlineConversationSettings.setEnabled(false)
            chatModeLabel = "Offline"
            renderMain()
            return
        }
        if (!groqCredentials.hasKey()) {
            onlineConversationSettings.setEnabled(false)
            Toast.makeText(this, "Add and check a Groq key first.", Toast.LENGTH_SHORT).show()
            renderMain()
            return
        }
        if (localModelManager.status() != LocalModelStatus.READY) {
            onlineConversationSettings.setEnabled(false)
            Toast.makeText(this, "Download the offline companion first so Ciel can switch offline automatically.", Toast.LENGTH_LONG).show()
            renderMain()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Allow online responses?")
            .setMessage(onlinePrivacyDisclosure())
            .setNegativeButton("Keep offline") { _, _ ->
                onlineConversationSettings.setEnabled(false)
                chatModeLabel = "Offline"
                renderMain()
            }
            .setPositiveButton("Allow online") { _, _ ->
                onlineConversationSettings.setEnabled(true)
                chatModeLabel = "Online"
                renderMain()
            }
            .show().also(::styleDialog)
    }

    private fun onlinePrivacyDisclosure(): String =
        "When online replies are enabled, Ciel sends eligible chat messages, up to 24 recent in-scope turns, and up to four locally retrieved Bible passages to Groq for a reply. Kumpisal entries are not sent. Groq is a third-party service and handles requests under its own privacy terms. Groq may apply its own rate limits. You can turn online replies off at any time."

    private fun showKeyDisclosure(onContinue: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Before adding a Groq key")
            .setMessage("The key is sent to Groq when Ciel checks it and when online replies are used. It is encrypted on this device with Android Keystore, excluded from backups, and can be removed in Settings. Do not enter a key you do not own or have permission to use.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> onContinue() }
            .show().also(::styleDialog)
    }

    private fun openGroqKeyEditor() {
        val content = vertical().apply { setPadding(dp(20), dp(8), dp(20), dp(4)) }
        content.addView(text("Ciel checks that the key can access the selected chat model. It is not shown again after saving.", 13f, color(R.color.muted)))
        val keyField = editField("Groq API key", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        keyField.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        keyField.transformationMethod = PasswordTransformationMethod.getInstance()
        keyField.isSaveEnabled = false
        content.addView(keyField, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val fieldActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var keyVisible = false
        var showKeyButton: MaterialButton? = null
        val showButton = actionButton("Show", filled = false, compact = true) {
            keyVisible = !keyVisible
            val selection = keyField.selectionStart.coerceAtLeast(0)
            keyField.transformationMethod = if (keyVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
            keyField.setSelection(selection.coerceAtMost(keyField.text.length))
            showKeyButton?.text = if (keyVisible) "Hide" else "Show"
        }.also { showKeyButton = it }
        fieldActions.addView(showButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(7) })
        fieldActions.addView(actionButton("Paste", filled = false, compact = true) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
            if (pasted.isNullOrBlank()) {
                Toast.makeText(this@MainActivity, "There is no API key in the clipboard.", Toast.LENGTH_SHORT).show()
            } else {
                keyField.setText(pasted.trim())
                keyField.setSelection(keyField.text.length)
            }
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        content.addView(fieldActions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        val status = text("", 12f, color(R.color.sage_text)).apply { setPadding(0, dp(5), 0, 0) }
        content.addView(status)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Groq key")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Check and save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = keyField.text.toString().trim()
                if (!com.example.hacklaguna2026.ai.isBasicGroqKeyFormatValid(key)) {
                    status.text = "This doesn't look like a Groq key. Check the value and try again."
                    return@setOnClickListener
                }
                val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                saveButton.isEnabled = false
                status.text = "Checking key and model access…"
                lifecycleScope.launch {
                    val validation = withContext(Dispatchers.IO) { validateGroqKey(key) }
                    if (validation == GroqKeyValidation.CONNECTED) {
                        val saved = runCatching { withContext(Dispatchers.IO) { groqCredentials.save(key) } }
                        if (saved.isSuccess) {
                            maskedSavedKey = key.takeLast(4)
                            keyField.text?.clear()
                            onlineConversationSettings.setEnabled(false)
                            onlineConnectionMessage = "Key checked and saved. Online replies are still off until you enable them."
                            dialog.dismiss()
                            renderMain()
                        } else {
                            saveButton.isEnabled = true
                            status.text = "The key couldn't be saved securely. Please try again."
                        }
                    } else {
                        saveButton.isEnabled = true
                        status.text = when (validation) {
                            GroqKeyValidation.INVALID_FORMAT, GroqKeyValidation.INVALID_KEY -> "Groq couldn't verify this key. Check it and try again."
                            GroqKeyValidation.MODEL_UNAVAILABLE -> "This key doesn't have access to Ciel's selected model."
                            GroqKeyValidation.RATE_LIMITED -> "Groq is rate limited right now. Try checking the key later."
                            GroqKeyValidation.NETWORK_UNAVAILABLE -> "Couldn't reach Groq. Check your connection and try again."
                            GroqKeyValidation.SERVICE_UNAVAILABLE -> "Groq couldn't check the key right now. Please try again later."
                            GroqKeyValidation.CONNECTED -> "The key is ready."
                        }
                    }
                }
            }
        }
        dialog.show()
        styleDialog(dialog)
    }

    private fun confirmRemoveGroqKey() {
        AlertDialog.Builder(this)
            .setTitle("Remove saved Groq key?")
            .setMessage("Online replies will turn off. Your Bible, saved conversations, and Kumpisal entries will stay on this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove key") { _, _ ->
                onlineConversationSettings.setEnabled(false)
                val removed = runCatching { groqCredentials.delete() }.isSuccess
                if (removed) maskedSavedKey = null
                onlineConnectionMessage = if (removed) "Saved key removed. Ciel is offline." else "The key could not be removed from secure storage. Please try again."
                chatModeLabel = "Offline"
                renderMain()
            }
            .show().also(::styleDialog)
    }

    private fun checkGroqConnection() {
        onlineConnectionMessage = "Checking Groq connection…"
        renderMain()
        lifecycleScope.launch {
            val key = withContext(Dispatchers.IO) { runCatching { groqCredentials.read() }.getOrNull() }
            val validation = key?.let { withContext(Dispatchers.IO) { validateGroqKey(it) } }
                ?: GroqKeyValidation.INVALID_KEY
            onlineConnectionMessage = when (validation) {
                GroqKeyValidation.CONNECTED -> "Connected. Ciel's online model is available."
                GroqKeyValidation.INVALID_FORMAT, GroqKeyValidation.INVALID_KEY -> "Groq couldn't verify the saved key. Change or remove it in Settings."
                GroqKeyValidation.MODEL_UNAVAILABLE -> "The saved key cannot access Ciel's selected model."
                GroqKeyValidation.RATE_LIMITED -> "Groq is rate limited right now. Try again later."
                GroqKeyValidation.NETWORK_UNAVAILABLE -> "Couldn't reach Groq. Check your connection."
                GroqKeyValidation.SERVICE_UNAVAILABLE -> "Groq couldn't check the connection right now."
            }
            renderMain()
        }
    }

    private fun confirmRemoveLocalModel() {
        AlertDialog.Builder(this)
            .setTitle("Remove the offline model?")
            .setMessage("This removes the downloaded model and frees about ${LocalModelManager.MODEL_DISPLAY_SIZE}. Saved chats, Bible data, and Kumpisal entries stay on this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove model") { _, _ ->
                onlineConversationSettings.setEnabled(false)
                chatModeLabel = "Offline"
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            chatViewModel.unloadLocalModel()
                            localModelManager.remove()
                        }
                        modelDownloadMessage = "Offline model removed. Download it again any time."
                    } catch (_: Exception) {
                        modelDownloadMessage = "The offline model couldn't be removed. Please try again."
                    }
                    renderMain()
                }
            }
            .show().also(::styleDialog)
    }

    private fun startLocalModelDownload() {
        if (modelDownloadJob?.isActive == true) return
        modelDownloadMessage = ""
        modelDownloadProgress = null
        var lastReportedBucket = -1L
        modelDownloadJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    localModelManager.download { progress ->
                        val bucket = progress.downloadedBytes / (5L * 1024 * 1024)
                        if (bucket != lastReportedBucket || progress.downloadedBytes == progress.totalBytes) {
                            lastReportedBucket = bucket
                            runOnUiThread {
                                modelDownloadProgress = progress
                                if (selectedTab == TAB_SETTINGS) renderMain()
                            }
                        }
                    }
                }
                modelDownloadMessage = "Offline model downloaded and checksum verified. Start a chat to load it on this device."
                localModelFailure = ""
                modelDownloadProgress = null
            } catch (error: kotlinx.coroutines.CancellationException) {
                modelDownloadMessage = "Download paused. Your saved progress can be resumed here."
                modelDownloadProgress = null
                throw error
            } catch (error: Exception) {
                modelDownloadMessage = error.message ?: "The offline model download stopped. You can resume it from Settings."
                modelDownloadProgress = null
            } finally {
                modelDownloadJob = null
                if (selectedTab == TAB_SETTINGS) renderMain()
            }
        }
        renderMain()
    }

    private fun formatModelBytes(bytes: Long): String = "${((bytes + 999_999) / 1_000_000).coerceAtLeast(if (bytes > 0) 1 else 0)} MB"

    private fun aboutScreen(): View {
        val page = pageColumn()
        page.addView(actionButton("‹ Settings", filled = false, compact = true) {
            settingsShowingAbout = false
            renderMain()
        }, LinearLayout.LayoutParams(-2, dp(40)))
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(12), 0, dp(15)) }
        val intro = vertical().apply { gravity = Gravity.CENTER; setPadding(dp(18), dp(20), dp(18), dp(18)) }
        intro.addView(ImageView(this).apply {
            setImageResource(R.drawable.ciel)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ciel application logo"
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(12).toFloat()
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(84), dp(84)))
        intro.addView(text("CIEL: Your Safe Space", 22f, color(R.color.forest), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(11), 0, dp(5))
        })
        intro.addView(text("A safe space for questions, faith, and growing closer to God.", 14f, color(R.color.muted)).apply {
            gravity = Gravity.CENTER
        })
        content.addView(card(intro, tinted = true), marginBottom(dp(10)))

        val description = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        description.addView(text("About Ciel", 17f, color(R.color.black), bold = true))
        description.addView(text("The Problem", 12f, color(R.color.sage_text), bold = true).apply { setPadding(0, dp(8), 0, 0) })
        description.addView(text(
            "Teens and young adults have real questions about God, doubt, and life, but they will not ask a pastor face to face. They search online, find mixed answers, and the church never hears the question.",
            14f, color(R.color.black)
        ).apply { setPadding(0, dp(8), 0, dp(8)) })
        description.addView(text(
            "Ciel was developed to help address this gap by giving young people a welcoming place to explore difficult faith questions and helping them feel more comfortable connecting with the church community.",
            13f, color(R.color.muted)
        ))
        content.addView(card(description), marginBottom(dp(10)))

        val event = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        event.addView(text("Made for #HACKLAGUNA 2026", 16f, color(R.color.black), bold = true))
        event.addView(text("Developed for the #HACKLAGUNA 2026 Hackathon. Ciel aims to bridge the gap between young people with difficult faith questions and the church community.", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(card(event), marginBottom(dp(10)))

        val developer = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        developer.addView(text("Developer", 12f, color(R.color.sage_text), bold = true))
        developer.addView(text("John Ezekiel M. Yorong", 16f, color(R.color.black), bold = true).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(card(developer), marginBottom(dp(10)))

        val churches = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        churches.addView(text("Free for churches to use.", 16f, color(R.color.forest), bold = true))
        churches.addView(text("Ciel's purpose is to help churches create a more welcoming environment for questions about God, faith, and life.", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(5), 0, 0)
        })
        content.addView(card(churches))
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun addPageHeader(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(text(title, 25f, color(R.color.black), bold = true))
        parent.addView(text(subtitle, 13f, color(R.color.muted)).apply {
            setPadding(0, dp(4), 0, dp(14))
        })
    }

    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(color(R.color.surface_card)))
        dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(color(R.color.black))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(color(R.color.black))
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
            dialog.getButton(which)?.setTextColor(themeAccent())
        }
        dialog.listView?.apply {
            setBackgroundColor(color(R.color.surface_card))
            post {
                for (index in 0 until childCount) {
                    (getChildAt(index) as? TextView)?.setTextColor(color(R.color.black))
                }
            }
        }
    }

    private fun renderKumpisalScreen(): View = kumpisalScreen()

    private fun avatarResource(emotion: AvatarEmotion): Int = when (emotion) {
        AvatarEmotion.HAPPY -> R.drawable.avatar_happy
        AvatarEmotion.NEUTRAL -> R.drawable.avatar_neutral
        AvatarEmotion.THINKING -> R.drawable.avatar_thinking
        AvatarEmotion.WORRIED -> R.drawable.avatar_worried
        AvatarEmotion.SLEEPY -> R.drawable.avatar_sleepy
        AvatarEmotion.SURPRISED -> R.drawable.avatar_surprised
        AvatarEmotion.PROUD -> R.drawable.avatar_proud
        AvatarEmotion.ANGRY -> R.drawable.avatar_angry
        AvatarEmotion.WORSHIPPING -> R.drawable.avatar_worshipping
    }

    private fun avatarEmotionFor(messages: List<MessageEntity>): AvatarEmotion {
        val lastQuestionIndex = messages.indexOfLast { it.role == "user" }
        if (lastQuestionIndex < 0) return AvatarEmotion.NEUTRAL
        val replyEmotion = messages.drop(lastQuestionIndex + 1)
            .lastOrNull { it.role == "assistant" }
            ?.let { ChatReplyCodec.decode(it.content).avatarEmotion }
        return replyEmotion ?: classifyAvatarEmotion(messages[lastQuestionIndex].content)
    }

    private fun themeAccent(): Int = Color.parseColor(
        THEME_OPTIONS.firstOrNull { it.id == selectedTheme }?.color ?: THEME_OPTIONS.first().color
    )

    private fun vertical(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun pageColumn(): LinearLayout = vertical().apply { setPadding(dp(21), dp(18), dp(21), dp(18)) }

    private fun text(value: String, size: Float, tint: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(tint)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun editField(
        hintText: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        minLines: Int = 1,
        maxLines: Int = 1
    ): EditText = EditText(this).apply {
        hint = hintText
        this.inputType = inputType
        setTextColor(color(R.color.black))
        setHintTextColor(color(R.color.muted))
        textSize = 15f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = outlinedBox()
        this.minLines = minLines
        this.maxLines = maxLines
        isSingleLine = maxLines == 1
    }

    private fun actionButton(
        label: String,
        filled: Boolean = true,
        enabled: Boolean = true,
        compact: Boolean = false,
        onClick: () -> Unit
    ): MaterialButton = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        textSize = if (compact) 12f else 14f
        cornerRadius = dp(if (compact) 13 else 15)
        insetTop = 0
        insetBottom = 0
        minHeight = dp(if (compact) 38 else 48)
        setPadding(dp(if (compact) 9 else 15), 0, dp(if (compact) 9 else 15), 0)
        backgroundTintList = ColorStateList.valueOf(color(if (filled) R.color.forest else R.color.surface_card))
        setTextColor(color(if (filled) R.color.white else R.color.forest))
        if (!filled) {
            strokeColor = ColorStateList.valueOf(color(R.color.line))
            strokeWidth = dp(1)
        }
        this.isEnabled = enabled
        setOnClickListener { onClick() }
    }

    private fun card(content: View, tinted: Boolean = false): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(1).toFloat()
        strokeWidth = dp(1)
        strokeColor = color(R.color.line)
        setCardBackgroundColor(color(if (tinted) R.color.sage else R.color.surface_card))
        addView(content)
    }

    private fun rounded(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
    }

    private fun outlinedBox(): GradientDrawable = GradientDrawable().apply {
        setColor(color(R.color.surface_card))
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), color(R.color.line))
    }

    private fun color(resource: Int): Int = when (resource) {
        R.color.black -> if (darkMode) Color.WHITE else Color.rgb(23, 33, 43)
        R.color.white -> Color.WHITE
        R.color.cream, R.color.surface_card, R.color.sage -> if (darkMode) Color.BLACK else Color.WHITE
        R.color.forest, R.color.forest_dark, R.color.sage_text -> themeAccent()
        R.color.muted -> if (darkMode) Color.rgb(190, 198, 205) else Color.rgb(102, 114, 125)
        R.color.line -> if (darkMode) Color.rgb(48, 54, 59) else Color.rgb(225, 232, 237)
        else -> ContextCompat.getColor(this, resource)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun marginBottom(bottom: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = bottom
    }

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).toInt()
        if (totalMinutes < 1) return "< 1 min"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
    }

    private fun localDayKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

    private fun formatReadingDate(value: String): String = runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parser.parse(value) ?: return value)
    }.getOrDefault(value)

    private fun normalizeBook(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private data class ThemeOption(val id: String, val name: String, val shortName: String, val color: String)

    companion object {
        private const val TAB_HOME = "home"
        private const val TAB_CIEL = "ciel"
        private const val TAB_BIBLE = "bible"
        private const val TAB_HISTORY = "history"
        private const val TAB_SETTINGS = "settings"
        private const val TAB_STATS = "stats"
        private const val TAB_KUMPISAL = "kumpisal"
        private const val THEME_PREFERENCES = "ciel_appearance"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val THEME_SKY = "sky_blue"
        private const val IDLE_GRACE_MS = 2 * 60 * 1000L
        private val THEME_OPTIONS = listOf(
            ThemeOption(THEME_SKY, "Sky Blue", "Sky", "#0277BD"),
            ThemeOption("sage", "Sage Green", "Sage", "#4F8064"),
            ThemeOption("lavender", "Lavender", "Lavender", "#7453A5"),
            ThemeOption("teal", "Teal", "Teal", "#00796B"),
            ThemeOption("soft_rose", "Soft Rose", "Rose", "#B34F70")
        )
    }
}
