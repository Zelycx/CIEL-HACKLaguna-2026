package com.example.hacklaguna2026

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hacklaguna2026.ai.ChatReplyCodec
import com.example.hacklaguna2026.ai.ChatViewModel
import com.example.hacklaguna2026.ai.GroqChatService
import com.example.hacklaguna2026.ai.OfflineBibleAssistant
import com.example.hacklaguna2026.bible.BibleRepository
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.BookmarkEntity
import com.example.hacklaguna2026.data.ConversationEntity
import com.example.hacklaguna2026.data.ConversationRepository
import com.example.hacklaguna2026.data.MessageEntity
import com.example.hacklaguna2026.data.ReflectionEntity
import com.example.hacklaguna2026.data.UserSessionEntity
import com.example.hacklaguna2026.stats.AppStatsRepository
import com.example.hacklaguna2026.stats.StatisticsSnapshot
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var database: AppDatabase
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var bibleRepository: BibleRepository
    private lateinit var statsRepository: AppStatsRepository

    private var account: UserSessionEntity? = null
    private var isSessionLoading = true
    private var selectedTab = TAB_CIEL
    private var currentConversationId: String? = null
    private var conversationMessages: List<MessageEntity> = emptyList()
    private var historyConversations: List<ConversationEntity> = emptyList()
    private var historyLoaded = false
    private var settingsSection = SETTINGS_HOME
    private var failedQuestion: String? = null
    private var isConversationLoading = false
    private var chatNotice = ""
    private var isSending = false
    private var bibleNotice = ""
    private var bibleBooks: List<String> = emptyList()
    private var selectedBook: String? = null
    private var selectedChapter = 1
    private var selectedVerse: Int? = null
    private var availableChapters: List<Int> = emptyList()
    private var chapterVerses = emptyList<com.example.hacklaguna2026.data.BibleVerseEntity>()
    private var isNewReflection = false
    private var selectedPrompts = mutableSetOf<String>()

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        database = AppDatabase.get(this)
        bibleRepository = BibleRepository(this, database)
        conversationRepository = ConversationRepository(database)
        chatViewModel = ViewModelProvider(this, ChatViewModel.Factory {
            ChatViewModel(
                conversationRepository,
                bibleRepository,
                GroqChatService(BuildConfig.GROQ_API_KEY),
                OfflineBibleAssistant()
            )
        })[ChatViewModel::class.java]
        statsRepository = AppStatsRepository(database)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (account != null && selectedTab != TAB_CIEL) {
                    selectTab(TAB_CIEL)
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
                bibleNotice = "The bundled Bible could not be loaded: ${error.message ?: "unknown error"}"
            } finally {
                isSessionLoading = false
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (account != null) {
            startActivitySegment()
            recordAppSessionForResume()
        }
    }

    override fun onPause() {
        flushActivitySegment()
        activityResumed = false
        appSessionRecordedForResume = false
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
        currentConversationId = state.third?.id
        conversationMessages = currentConversationId?.let { id ->
            withContext(Dispatchers.IO) { database.messages().forConversation(id) }
        }.orEmpty()
        if (selectedBook == null) selectedBook = bibleBooks.firstOrNull()
        selectedBook?.let { book ->
            val chapterState = withContext(Dispatchers.IO) {
                database.bible().chapter(book, selectedChapter) to database.bible().chapters(book)
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
            TAB_CIEL -> renderChatScreen()
            TAB_BIBLE -> renderBibleScreen()
            TAB_HISTORY -> renderHistoryScreen()
            TAB_SETTINGS -> renderSettingsScreen()
            TAB_STATS -> renderStatisticsScreen()
            TAB_KUMPISAL -> renderKumpisalScreen()
            else -> renderSettingsScreen()
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
            Triple(TAB_CIEL, "Ciel", R.drawable.ic_nav_chat),
            Triple(TAB_BIBLE, "Bible", R.drawable.ic_nav_bible),
            Triple(TAB_HISTORY, "History", R.drawable.ic_nav_stats),
            Triple(TAB_SETTINGS, "Settings", R.drawable.ic_nav_profile)
        )
        tabs.forEach { (tab, label, icon) ->
            val active = selectedTab == tab
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(5), 0, dp(4))
                background = if (active) rounded(color(R.color.sage), dp(14).toFloat()) else null
                setOnClickListener { selectTab(tab) }
            }
            val image = ImageView(this).apply {
                setImageResource(icon)
                setColorFilter(if (active) color(R.color.forest) else color(R.color.muted))
                contentDescription = label
            }
            item.addView(image, LinearLayout.LayoutParams(dp(22), dp(22)))
            item.addView(text(label, 10f, if (active) color(R.color.forest) else color(R.color.muted), bold = active).apply {
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
        if (selectedTab == tab) return
        flushActivitySegment()
        selectedTab = tab
        if (tab == TAB_SETTINGS) settingsSection = SETTINGS_HOME
        startActivitySegment()
        if (tab == TAB_BIBLE) selectedBook?.let { openChapter(it, selectedChapter, selectedVerse, redraw = false) }
        if (tab == TAB_HISTORY && !historyLoaded) loadConversationHistory()
        renderMain()
    }

    private fun currentStatsModule(): String? = when (selectedTab) {
        TAB_CIEL -> "Ciel"
        TAB_BIBLE -> "Bible"
        TAB_HISTORY, TAB_SETTINGS, TAB_STATS -> "Other"
        else -> null // Kumpisal notes are deliberately excluded from usage statistics.
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
        val avatar = text("C", 19f, color(R.color.white), bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(color(R.color.forest), dp(18).toFloat())
        }
        header.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
        val title = vertical()
        title.addView(text("Ciel", 21f, color(R.color.black), bold = true))
        title.addView(text("Your Christian AI companion", 12f, color(R.color.muted)))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("Clear", filled = false, compact = true, enabled = conversationMessages.isNotEmpty()) {
            clearCurrentConversation()
        })
        header.addView(actionButton("New", compact = true) { startNewConversation() }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(5) }
        })
        page.addView(header, marginBottom(dp(10)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val messages = vertical().apply { setPadding(0, dp(6), 0, dp(10)) }
        val visibleMessages = conversationMessages.filter { it.role != "system" }
        if (visibleMessages.isEmpty()) {
            val welcome = vertical().apply { setPadding(dp(18), dp(18), dp(18), dp(16)) }
            welcome.addView(text("Welcome. What’s on your heart today?", 18f, color(R.color.black), bold = true))
            welcome.addView(text("Ask about Scripture, faith, or what you’re walking through. Ciel is AI generated and isn’t a substitute for a pastor or professional support.", 14f, color(R.color.muted)).apply {
                setPadding(0, dp(8), 0, dp(12))
            })
            welcome.addView(actionButton("Help me find hope in a difficult week", filled = false) {
                sendMessage("Help me find hope in a difficult week.")
            })
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
                    setLineSpacing(dp(2).toFloat(), 1f)
                })
                if (reply != null && reply.provider != "Ciel") {
                    bubble.addView(text(reply.provider, 11f, color(R.color.sage_text), bold = true).apply {
                        setPadding(0, dp(7), 0, 0)
                    })
                    reply.fallbackReason?.let { reason ->
                        bubble.addView(text("Online service unavailable: $reason", 11f, color(R.color.muted)).apply {
                            setPadding(0, dp(4), 0, 0)
                        })
                    }
                }
                val bubbleCard = card(bubble, tinted = userMessage)
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
            messages.addView(text("Ciel is thinking…", 13f, color(R.color.sage_text)).apply {
                setPadding(dp(8), dp(4), 0, dp(10))
            })
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
                    sendMessage(question, persistUserQuestion = false)
                }.apply { layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply { topMargin = dp(5) } })
            }
            messages.addView(card(notice, tinted = true), marginBottom(dp(8)))
        }
        scroll.addView(messages)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val input = editField("Message Ciel…", minLines = 1, maxLines = 4).apply {
            gravity = Gravity.CENTER_VERTICAL
            isEnabled = !isSending
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        composer.addView(actionButton(if (isSending) "…" else "Send", enabled = !isSending) {
            val messageSnapshot = input.text.toString()
            if (messageSnapshot.isNotBlank()) {
                input.text.clear()
                sendMessage(messageSnapshot)
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(-2, dp(50)) })
        page.addView(composer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return page
    }

    private fun sendMessage(rawMessage: String, persistUserQuestion: Boolean = true) {
        val content = rawMessage.trim()
        if (content.isBlank() || isSending) return
        isSending = true
        chatNotice = ""
        failedQuestion = null
        renderMain()
        lifecycleScope.launch {
            var targetId = currentConversationId
            try {
                if (targetId == null) {
                    targetId = withContext(Dispatchers.IO) { conversationRepository.createConversation(content) }
                    currentConversationId = targetId
                }
                val result = chatViewModel.send(targetId, content, persistUserQuestion)
                currentConversationId = result.conversationId
                conversationMessages = result.messages
                failedQuestion = null
                historyLoaded = false
            } catch (error: Exception) {
                chatNotice = error.message?.takeIf { it.isNotBlank() } ?: "Ciel couldn’t complete that reply. Please try again."
                failedQuestion = content
                targetId?.let { id ->
                    conversationMessages = withContext(Dispatchers.IO) { conversationRepository.messages(id) }
                }
            } finally {
                currentConversationId = targetId
                isSending = false
                renderMain()
            }
        }
    }

    private fun startNewConversation() {
        currentConversationId = null
        conversationMessages = emptyList()
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
                        chatNotice = ""
                        failedQuestion = null
                        renderMain()
                    }
                }
            }
            .show()
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
                Toast.makeText(this@MainActivity, "That reference isn’t available in the imported BSB file.", Toast.LENGTH_SHORT).show()
            } else {
                selectedTab = TAB_BIBLE
                openChapter(book, chapter, verse)
            }
        }
    }

    private fun renderBibleScreen(): View = bibleScreen()

    private fun bibleScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Bible", "Berean Standard Bible · read offline")
        if (bibleBooks.isEmpty()) {
            val content = vertical().apply { setPadding(dp(18), dp(18), dp(18), dp(18)) }
            content.addView(text("Your Bible, close at hand", 18f, color(R.color.black), bold = true))
            content.addView(text(
                bibleNotice.ifBlank { "The built-in BSB could not be loaded. Restart the app after restoring its bundled Bible data." },
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
        footer.addView(actionButton("Mark chapter complete") { markChapterComplete() }, LinearLayout.LayoutParams(0, dp(46), 1.5f))
        page.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (bibleNotice.isNotBlank()) page.addView(text(bibleNotice, 12f, color(R.color.sage_text)).apply { setPadding(0, dp(5), 0, 0) })
        return page
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
            .show()
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
            .show()
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
                bibleNotice = "That chapter isn’t in the imported Bible file."
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
                    .show()
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
                .show()
        }
    }

    private fun markChapterComplete() {
        val book = selectedBook ?: return
        val lastVerse = chapterVerses.maxOfOrNull { it.verse } ?: return
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                statsRepository.markChapterCompleted(book, selectedChapter, lastVerse)
            }
            Toast.makeText(this@MainActivity, if (updated) "Chapter marked complete." else "You’ve already completed this chapter today.", Toast.LENGTH_SHORT).show()
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
        addPageHeader(page, "Your rhythm", "A gentle look at how you spend time here")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(4), 0, dp(14)) }
        content.addView(card(text("Gathering your local activity…", 14f, color(R.color.muted)).apply {
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
            "Other modules" to stats.otherTimeMs
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
        val progressLabel = if (totalChapters == 0) "Import the BSB to see overall progress." else "$progress% of Bible chapters completed"
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
        addPageHeader(page, "Kumpisal", "A quiet place to prepare for pastoral guidance")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(3), 0, dp(12)) }
        val intro = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        intro.addView(text("Personal preparation notes", 16f, color(R.color.black), bold = true))
        intro.addView(text("Use this space to reflect and prepare what you may want to discuss with a pastor or spiritual adviser. Your note and prompt choices stay on this device. They aren’t sent to Ciel or included in activity statistics, and this isn’t a sacramental confession or absolution.", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(card(intro, tinted = true), marginBottom(dp(11)))

        if (isNewReflection) {
            val editor = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(15)) }
            editor.addView(text("A few prompts", 16f, color(R.color.black), bold = true))
            reflectionPrompts.forEach { prompt ->
                val checkbox = CheckBox(this).apply {
                    text = prompt
                    textSize = 13f
                    setTextColor(color(R.color.black))
                    isChecked = prompt in selectedPrompts
                    buttonTintList = ColorStateList.valueOf(color(R.color.forest))
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedPrompts.add(prompt) else selectedPrompts.remove(prompt)
                    }
                }
                editor.addView(checkbox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            }
            val note = editField("Write what you’d like to remember or ask about…", minLines = 5, maxLines = 10).apply {
                gravity = Gravity.TOP or Gravity.START
                isSingleLine = false
                setPadding(dp(14), dp(13), dp(14), dp(13))
            }
            editor.addView(note, LinearLayout.LayoutParams(-1, dp(142)).apply { topMargin = dp(8) })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("Save reflection") {
                val textValue = note.text.toString().trim()
                if (textValue.isBlank() && selectedPrompts.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Write a note or choose a prompt first.", Toast.LENGTH_SHORT).show()
                    return@actionButton
                }
                val now = System.currentTimeMillis()
                val prompts = selectedPrompts.toList()
                lifecycleScope.launch(Dispatchers.IO) {
                    database.reflections().save(ReflectionEntity(
                        content = textValue,
                        selectedPrompts = prompts.joinToString("\n"),
                        createdAt = now,
                        updatedAt = now
                    ))
                    withContext(Dispatchers.Main) {
                        isNewReflection = false
                        selectedPrompts.clear()
                        renderMain()
                        Toast.makeText(this@MainActivity, "Reflection saved on this device.", Toast.LENGTH_SHORT).show()
                    }
                }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(7) })
            actions.addView(actionButton("Cancel", filled = false) {
                isNewReflection = false
                selectedPrompts.clear()
                renderMain()
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            editor.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            content.addView(card(editor), marginBottom(dp(12)))
        } else {
            content.addView(actionButton("Start a new reflection") {
                selectedPrompts.clear()
                isNewReflection = true
                renderMain()
            }, marginBottom(dp(16)))
            content.addView(text("PREVIOUS REFLECTIONS", 11f, color(R.color.sage_text), bold = true).apply {
                setPadding(dp(3), 0, 0, dp(8))
            })
            val loading = text("Loading your private notes…", 13f, color(R.color.muted)).apply { setPadding(dp(3), 0, 0, 0) }
            content.addView(loading)
            lifecycleScope.launch {
                val reflections = withContext(Dispatchers.IO) { database.reflections().all() }
                content.removeView(loading)
                if (reflections.isEmpty()) {
                    content.addView(text("Your saved reflections will appear here.", 13f, color(R.color.muted)).apply {
                        setPadding(dp(3), dp(2), 0, 0)
                    })
                } else {
                    reflections.forEach { reflection ->
                        content.addView(reflectionCard(reflection), marginBottom(dp(9)))
                    }
                }
            }
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun reflectionCard(reflection: ReflectionEntity): View {
        val body = vertical().apply { setPadding(dp(15), dp(13), dp(15), dp(12)) }
        val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(reflection.updatedAt))
        body.addView(text(date, 11f, color(R.color.sage_text), bold = true))
        val promptText = reflection.selectedPrompts.lineSequence().filter(String::isNotBlank).joinToString(" · ")
        if (promptText.isNotBlank()) body.addView(text(promptText, 12f, color(R.color.muted)).apply { setPadding(0, dp(5), 0, 0) })
        body.addView(text(reflection.content.ifBlank { "Reflection prompts selected" }, 14f, color(R.color.black)).apply {
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, dp(7))
        })
        body.addView(actionButton("Review or delete", filled = false, compact = true) {
            val message = buildString {
                if (promptText.isNotBlank()) append("Prompts\n$promptText\n\n")
                append(reflection.content.ifBlank { "No written note." })
            }
            AlertDialog.Builder(this)
                .setTitle("Private reflection")
                .setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Delete") { _, _ -> deleteReflection(reflection.id) }
                .show()
        })
        return card(body)
    }

    private fun deleteReflection(id: Long) {
        AlertDialog.Builder(this)
            .setTitle("Delete this reflection?")
            .setMessage("This removes the note from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.reflections().delete(id)
                    withContext(Dispatchers.Main) { renderMain() }
                }
            }
            .show()
    }

    private fun renderSettingsScreen(): View = when (settingsSection) {
        SETTINGS_STATS -> renderSettingsSubpage(renderStatisticsScreen())
        SETTINGS_REFLECTIONS -> renderSettingsSubpage(renderKumpisalScreen())
        else -> profileScreen()
    }

    private fun renderSettingsSubpage(content: View): View = vertical().apply {
        setPadding(dp(18), dp(7), dp(18), dp(0))
        addView(actionButton("‹ Settings", filled = false, compact = true) {
            settingsSection = SETTINGS_HOME
            renderMain()
        }, LinearLayout.LayoutParams(-2, dp(39)))
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
    }

    private fun profileScreen(): View {
        val page = pageColumn()
        addPageHeader(page, "Settings", "Ciel: Your Safe Space")
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = vertical().apply { setPadding(0, dp(4), 0, dp(14)) }

        val aiCard = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        aiCard.addView(text("Ciel online and offline", 17f, color(R.color.black), bold = true))
        aiCard.addView(text("When connected, Ciel sends chat messages and selected local BSB passages directly to Groq over HTTPS. Without a connection, the app switches to Offline Bible Assistant and searches the BSB stored on this device.", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(card(aiCard, tinted = true), marginBottom(dp(10)))

        content.addView(actionButton("Statistics", filled = false) {
            settingsSection = SETTINGS_STATS
            renderMain()
        }, marginBottom(dp(8)))
        content.addView(actionButton("Private reflections", filled = false) {
            settingsSection = SETTINGS_REFLECTIONS
            renderMain()
        }, marginBottom(dp(12)))

        val privacy = vertical().apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        privacy.addView(text("Your privacy", 16f, color(R.color.black), bold = true))
        privacy.addView(text("Private reflection notes and prompt choices stay local and are never sent to the AI provider or included in activity statistics. Chat messages and selected BSB passages are sent directly to Groq over HTTPS when online chat is available. Ciel cannot replace pastoral or professional support.", 13f, color(R.color.muted)).apply {
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(card(privacy, tinted = true), marginBottom(dp(15)))
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

    private fun renderKumpisalScreen(): View = kumpisalScreen()

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

    private fun color(resource: Int): Int = androidx.core.content.ContextCompat.getColor(this, resource)

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

    private fun normalizeBook(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    companion object {
        private const val TAB_CIEL = "ciel"
        private const val TAB_BIBLE = "bible"
        private const val TAB_HISTORY = "history"
        private const val TAB_SETTINGS = "settings"
        private const val TAB_STATS = "stats"
        private const val TAB_KUMPISAL = "kumpisal"
        private const val SETTINGS_HOME = "home"
        private const val SETTINGS_STATS = "stats"
        private const val SETTINGS_REFLECTIONS = "reflections"
        private const val IDLE_GRACE_MS = 2 * 60 * 1000L
    }
}
