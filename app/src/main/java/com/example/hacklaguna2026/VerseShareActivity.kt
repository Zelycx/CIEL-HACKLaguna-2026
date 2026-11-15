package com.example.hacklaguna2026

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.hacklaguna2026.bible.DailyVerse
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal data class VerseImageTheme(val id: String, val label: String, val background: Int, val panel: Int, val accent: Int)
internal data class VerseImageExpression(val id: String, val label: String, val description: String, val drawable: Int)

class VerseShareActivity : AppCompatActivity() {
    private var scripture = ""
    private var reference = ""
    private var themeLabel = ""
    private var reflection = ""
    private var selectedTheme = "sky"
    private var selectedExpression = "happy"
    private var includeReflection = true
    private var imagePreview: ImageView? = null
    private var previewBitmap: Bitmap? = null
    private var expressionDescription: TextView? = null
    private val expressionTiles = mutableMapOf<String, LinearLayout>()
    private val expressionLabels = mutableMapOf<String, TextView>()
    private val themeButtons = mutableMapOf<String, MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scripture = intent.getStringExtra(EXTRA_VERSE).orEmpty()
        reference = intent.getStringExtra(EXTRA_REFERENCE).orEmpty()
        themeLabel = intent.getStringExtra(EXTRA_THEME).orEmpty()
        reflection = intent.getStringExtra(EXTRA_REFLECTION).orEmpty()
        selectedExpression = intent.getStringExtra(EXTRA_EXPRESSION)?.lowercase(Locale.ROOT)
            ?.takeIf { id -> EXPRESSIONS.any { it.id == id } } ?: "happy"
        if (scripture.isBlank() || reference.isBlank()) {
            Toast.makeText(this, "This verse image could not be opened.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        cleanOldFiles()
        setContentView(screen())
        updatePreview()
    }

    override fun onDestroy() {
        imagePreview?.setImageDrawable(null)
        previewBitmap?.recycle()
        previewBitmap = null
        super.onDestroy()
    }

    private fun screen(): View {
        expressionTiles.clear()
        expressionLabels.clear()
        themeButtons.clear()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(246, 249, 251))
            setPadding(dp(15), dp(9), dp(15), dp(12))
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(button("Back", outline = true, compact = true) { finish() })
        toolbar.addView(text("Share a verse", 20f, Color.rgb(28, 46, 61), true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(toolbar, LinearLayout.LayoutParams(-1, dp(43)).apply { bottomMargin = dp(5) })

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = MaterialCardView(this).apply {
            radius = dp(15).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(Color.WHITE)
        }
        imagePreview = ImageView(this).apply {
            contentDescription = "Generated Bible verse image preview"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(223, 242, 253))
        }
        val previewWidth = resources.displayMetrics.widthPixels - dp(30)
        val previewHeight = (previewWidth * VerseImageRenderer.HEIGHT.toFloat() / VerseImageRenderer.WIDTH).toInt()
        card.addView(imagePreview, android.view.ViewGroup.LayoutParams(-1, previewHeight))
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        body.addView(text("Character expression", 14f, Color.rgb(40, 54, 67), true))
        expressionDescription = text("", 12f, Color.rgb(102, 114, 125)).apply {
            setPadding(0, dp(3), 0, dp(4))
        }
        body.addView(expressionDescription)
        val expressionScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val expressions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        EXPRESSIONS.forEach { exp ->
            val choice = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(3), dp(5), dp(3))
                contentDescription = exp.label + ": " + exp.description
                setOnClickListener {
                    selectedExpression = exp.id
                    updateChoiceStyles()
                    updatePreview()
                }
            }
            choice.addView(ImageView(this).apply {
                setImageResource(exp.drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = exp.description
            }, LinearLayout.LayoutParams(dp(49), dp(49)))
            val choiceLabel = text(exp.label, 10f, Color.rgb(50, 60, 70), selectedExpression == exp.id).apply {
                gravity = Gravity.CENTER
            }
            choice.addView(choiceLabel)
            expressionTiles[exp.id] = choice
            expressionLabels[exp.id] = choiceLabel
            expressions.addView(choice, LinearLayout.LayoutParams(dp(76), dp(78)).apply { marginEnd = dp(4) })
        }
        expressionScroll.addView(expressions)
        body.addView(expressionScroll)

        body.addView(text("Background", 14f, Color.rgb(40, 54, 67), true).apply {
            setPadding(0, dp(7), 0, dp(3))
        })
        val themes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        THEMES.forEach { option ->
            val themeButton = button(option.label, outline = true, compact = true) {
                selectedTheme = option.id
                updateChoiceStyles()
                updatePreview()
            }
            themeButtons[option.id] = themeButton
            themes.addView(themeButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        }
        updateChoiceStyles()
        body.addView(themes)

        body.addView(CheckBox(this).apply {
            text = "Include the reflection"
            textSize = 13f
            setTextColor(Color.rgb(53, 66, 77))
            isChecked = includeReflection
            setOnCheckedChangeListener { _, checked ->
                includeReflection = checked
                updatePreview()
            }
        }, LinearLayout.LayoutParams(-1, dp(40)))
        body.addView(button("Regenerate preview", outline = true) { updatePreview() },
            LinearLayout.LayoutParams(-1, dp(43)).apply { topMargin = dp(4) })
        body.addView(button("Share image") { share() },
            LinearLayout.LayoutParams(-1, dp(47)).apply { topMargin = dp(8) })
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun updatePreview() {
        val expression = EXPRESSIONS.first { it.id == selectedExpression }
        runCatching {
            VerseImageRenderer.render(
                this, scripture, reference, themeLabel,
                reflection.takeIf { includeReflection }, expression.drawable,
                THEMES.first { it.id == selectedTheme }
            )
        }.onSuccess { bitmap ->
            val previous = previewBitmap
            previewBitmap = bitmap
            imagePreview?.setImageBitmap(bitmap)
            if (previous !== bitmap) previous?.recycle()
            expressionDescription?.text = expression.description
        }.onFailure {
            Toast.makeText(this, it.message ?: "The preview could not be generated.", Toast.LENGTH_LONG).show()
        }
    }

    private fun share() {
        val expression = EXPRESSIONS.first { it.id == selectedExpression }
        val bitmap = runCatching {
            VerseImageRenderer.render(
                this, scripture, reference, themeLabel,
                reflection.takeIf { includeReflection }, expression.drawable,
                THEMES.first { it.id == selectedTheme }
            )
        }.getOrElse {
            Toast.makeText(this, it.message ?: "The image could not be generated.", Toast.LENGTH_LONG).show()
            return
        }
        val directory = File(cacheDir, SHARE_DIR)
        val file = File(directory, "ciel-verse-" + System.currentTimeMillis() + ".png")
        try {
            directory.mkdirs()
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "The image could not be saved." }
            }
            val uri: Uri = FileProvider.getUriForFile(this, packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newUri(contentResolver, "Ciel verse image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share your verse image"))
        } catch (error: Exception) {
            file.delete()
            Toast.makeText(this, error.message ?: "The image could not be shared.", Toast.LENGTH_LONG).show()
        } finally {
            bitmap.recycle()
        }
    }

    private fun cleanOldFiles() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        File(cacheDir, SHARE_DIR).listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach(File::delete)
    }

    private fun updateChoiceStyles() {
        expressionTiles.forEach { (id, tile) ->
            val selected = id == selectedExpression
            tile.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (selected) Color.rgb(225, 242, 252) else Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(if (selected) 2 else 1), if (selected) Color.rgb(2, 119, 189) else Color.rgb(225, 232, 237))
            }
            expressionLabels[id]?.setTypeface(
                expressionLabels[id]?.typeface,
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
        }
        val themeMap = THEMES.associateBy { it.id }
        themeButtons.forEach { (id, control) ->
            val selected = id == selectedTheme
            val theme = themeMap.getValue(id)
            control.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) theme.accent else Color.WHITE
            )
            control.setTextColor(if (selected) Color.WHITE else Color.rgb(2, 119, 189))
            control.strokeWidth = if (selected) 0 else dp(1)
            control.strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(210, 222, 231))
        }
    }

    private fun text(value: String, size: Float, tint: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(tint)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(value: String, outline: Boolean = false, compact: Boolean = false, onClick: () -> Unit) =
        MaterialButton(this).apply {
            text = value
            isAllCaps = false
            textSize = if (compact) 10f else 14f
            cornerRadius = dp(13)
            minHeight = dp(if (compact) 34 else 44)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(7), 0, dp(7), 0)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (outline) Color.WHITE else Color.rgb(2, 119, 189)
            )
            setTextColor(if (outline) Color.rgb(2, 119, 189) else Color.WHITE)
            if (outline) {
                strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(210, 222, 231))
                strokeWidth = dp(1)
            }
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_VERSE = "ciel.share.verse"
        private const val EXTRA_REFERENCE = "ciel.share.reference"
        private const val EXTRA_THEME = "ciel.share.theme"
        private const val EXTRA_REFLECTION = "ciel.share.reflection"
        private const val EXTRA_EXPRESSION = "ciel.share.expression"
        private const val SHARE_DIR = "verse-shares"

        internal val THEMES = listOf(
            VerseImageTheme("sky", "Sky", Color.rgb(223, 242, 253), Color.WHITE, Color.rgb(2, 119, 189)),
            VerseImageTheme("cream", "Cream", Color.rgb(247, 240, 225), Color.rgb(255, 252, 244), Color.rgb(157, 112, 62)),
            VerseImageTheme("sage", "Sage", Color.rgb(226, 239, 230), Color.rgb(251, 255, 251), Color.rgb(74, 119, 90)),
            VerseImageTheme("lavender", "Lavender", Color.rgb(237, 232, 248), Color.rgb(253, 251, 255), Color.rgb(111, 92, 153))
        )

        internal val EXPRESSIONS = listOf(
            VerseImageExpression("happy", "Happy", "Joyful and cheerful", R.drawable.avatar_happy),
            VerseImageExpression("neutral", "Neutral", "Calm and relaxed", R.drawable.avatar_neutral),
            VerseImageExpression("thinking", "Thinking", "Curious and pondering", R.drawable.avatar_thinking),
            VerseImageExpression("worried", "Worried", "Nervous or concerned", R.drawable.avatar_worried),
            VerseImageExpression("sleepy", "Sleepy", "Tired or drowsy", R.drawable.avatar_sleepy),
            VerseImageExpression("surprised", "Surprised", "Shocked or startled", R.drawable.avatar_surprised),
            VerseImageExpression("proud", "Proud", "Encouraging and confident", R.drawable.avatar_proud),
            VerseImageExpression("angry", "Angry", "Annoyed or frustrated", R.drawable.avatar_angry),
            VerseImageExpression("worshipping", "Worshipping", "Praying or praising God", R.drawable.avatar_worshipping)
        )

        fun launch(context: Context, verse: DailyVerse) {
            context.startActivity(Intent(context, VerseShareActivity::class.java).apply {
                putExtra(EXTRA_VERSE, verse.passage.text)
                putExtra(EXTRA_REFERENCE, verse.reference)
                putExtra(EXTRA_THEME, verse.theme)
                putExtra(EXTRA_REFLECTION, verse.reflection)
                putExtra(EXTRA_EXPRESSION, verse.defaultExpression.lowercase(Locale.ROOT))
            })
        }
    }
}

internal object VerseImageRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    fun render(
        context: Context,
        scripture: String,
        reference: String,
        themeLabel: String,
        reflection: String?,
        expressionResource: Int,
        theme: VerseImageTheme
    ): Bitmap {
        require(scripture.isNotBlank() && reference.isNotBlank())
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
        canvas.drawColor(theme.background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.color = Color.argb(34, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
        canvas.drawCircle(1050f, 95f, 210f, paint)
        canvas.drawCircle(30f, 1290f, 145f, paint)
        paint.color = theme.panel
        canvas.drawRoundRect(RectF(48f, 48f, 1032f, 1302f), 38f, 38f, paint)

        drawText(canvas, "CIEL", 42f, theme.accent, Typeface.create("sans-serif", Typeface.BOLD), 96f, 124f, 888)
        drawText(canvas, "YOUR SAFE SPACE", 20f, Color.rgb(105, 119, 130),
            Typeface.create("sans-serif-medium", Typeface.NORMAL), 96f, 159f, 888)
        val avatar = BitmapFactory.decodeResource(context.resources, expressionResource)
            ?: error("The selected Ciel expression could not be loaded.")
        try {
            val bounds = RectF(430f, 184f, 650f, 386f)
            val scale = minOf(bounds.width() / avatar.width, bounds.height() / avatar.height)
            val w = avatar.width * scale
            val h = avatar.height * scale
            val target = RectF(bounds.centerX() - w / 2, bounds.centerY() - h / 2, bounds.centerX() + w / 2, bounds.centerY() + h / 2)
            canvas.drawBitmap(avatar, null, target, paint)
        } finally {
            avatar.recycle()
        }

        drawCentered(canvas, themeLabel.uppercase(Locale.ROOT), 19f, theme.accent,
            Typeface.create("sans-serif-medium", Typeface.NORMAL), 540f, 420f, 840)
        val quoteTop = 458f
        val quoteHeight = if (reflection == null) 590 else 460
        val quote = fitLayout(scripture, 58f, 25f, 850, quoteHeight, Color.rgb(35, 47, 57), Typeface.create("serif", Typeface.NORMAL))
        canvas.save()
        canvas.translate(115f, quoteTop)
        quote.draw(canvas)
        canvas.restore()
        val refY = quoteTop + quote.height + 34f
        require(refY <= 1050f) { "The verse is too long to fit on this image." }
        drawCentered(canvas, reference, 27f, theme.accent,
            Typeface.create("sans-serif-medium", Typeface.NORMAL), 540f, refY, 850)

        if (reflection != null) {
            drawCentered(canvas, "A MOMENT TO REFLECT", 16f, Color.rgb(105, 119, 130),
                Typeface.create("sans-serif-medium", Typeface.NORMAL), 540f, refY + 78f, 850)
            val reflectionLayout = fitLayout(reflection, 28f, 19f, 820, 155,
                Color.rgb(73, 84, 94), Typeface.create("sans-serif", Typeface.NORMAL))
            canvas.save()
            canvas.translate(130f, refY + 98f)
            reflectionLayout.draw(canvas)
            canvas.restore()
            require(refY + 98f + reflectionLayout.height <= 1190f) { "The verse and reflection do not fit together." }
        }
        paint.color = Color.argb(80, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
        canvas.drawRoundRect(RectF(422f, 1220f, 658f, 1225f), 3f, 3f, paint)
        drawCentered(canvas, "Ciel: Your Safe Space", 21f, Color.rgb(71, 87, 99),
            Typeface.create("sans-serif-medium", Typeface.NORMAL), 540f, 1265f, 850)
        return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun fitLayout(text: String, start: Float, minimum: Float, width: Int, maxHeight: Int, color: Int, face: Typeface): StaticLayout {
        var size = start
        while (size >= minimum) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = face
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(7f, 1f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
            if (layout.height <= maxHeight) return layout
            size -= 1f
        }
        throw IllegalArgumentException("The verse text is too long for a readable image.")
    }

    private fun drawText(canvas: Canvas, text: String, size: Float, color: Int, face: Typeface, x: Float, baseline: Float, width: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        canvas.save()
        canvas.translate(x, baseline - size)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCentered(canvas: Canvas, text: String, size: Float, color: Int, face: Typeface, center: Float, baseline: Float, width: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build()
        canvas.save()
        canvas.translate(center - width / 2f, baseline - size)
        layout.draw(canvas)
        canvas.restore()
    }
}
