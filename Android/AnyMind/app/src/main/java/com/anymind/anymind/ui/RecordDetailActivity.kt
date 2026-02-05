package com.anymind.anymind.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.anymind.anymind.R
import com.anymind.anymind.util.SyncStatus
import com.anymind.anymind.util.TagParser
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class RecordDetailActivity : AppCompatActivity() {
    private lateinit var viewModel: RecordDetailViewModel
    private lateinit var contentInput: EditText
    private lateinit var tagsGroup: ChipGroup
    private lateinit var syncToggle: SwitchMaterial
    private lateinit var syncIcon: ImageView
    private lateinit var syncLabel: TextView

    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var lastSavedContent: String = ""
    private var recordId: String = ""
    private var applyingSpans = false
    private var updatingToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_detail)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.detail_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recordId = intent.getStringExtra(EXTRA_RECORD_ID) ?: run {
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[RecordDetailViewModel::class.java]

        contentInput = findViewById(R.id.content_input)
        tagsGroup = findViewById(R.id.detail_tags_group)
        syncToggle = findViewById(R.id.sync_toggle)
        syncIcon = findViewById(R.id.detail_sync_icon)
        syncLabel = findViewById(R.id.detail_sync_label)

        contentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (applyingSpans) {
                    return
                }
                val text = s?.toString() ?: ""
                updateTagsFromContent(text)
                applyTagSpans(text)
                scheduleAutosave(text)
            }
        })

        syncToggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingToggle) {
                return@setOnCheckedChangeListener
            }
            viewModel.toggleSync(recordId, isChecked)
        }

        viewModel.record.observe(this) { record ->
            if (record == null) {
                finish()
                return@observe
            }
            if (contentInput.text.toString() != record.content) {
                contentInput.setText(record.content)
                contentInput.setSelection(record.content.length)
            }
            lastSavedContent = record.content
            updateTagsFromContent(record.content)
            updateSyncStatus(record.updatedAt, record.lastSyncAt, record.syncEnabled)
            updatingToggle = true
            syncToggle.isChecked = record.syncEnabled
            updatingToggle = false
        }

        viewModel.saveStatus.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.load(recordId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveRecord(true)
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        saveRecord(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleAutosave(text: String) {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        val runnable = Runnable { saveRecord(false) }
        saveRunnable = runnable
        saveHandler.postDelayed(runnable, 800)
    }

    private fun saveRecord(notify: Boolean) {
        val content = contentInput.text.toString()
        if (content == lastSavedContent && !notify) {
            return
        }
        lastSavedContent = content
        viewModel.save(recordId, content, notify)
    }

    private fun updateTagsFromContent(content: String) {
        val tags = TagParser.extractTags(content)
        val parsed = TagParser.splitTags(tags)
        val allTags = (parsed.first + parsed.second).distinct().sorted()
        tagsGroup.removeAllViews()
        allTags.forEach { tag ->
            val chip = Chip(this)
            chip.text = tag
            chip.isCheckable = false
            chip.isClickable = true
            chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.tag_bg)
            chip.setTextColor(ContextCompat.getColor(this, R.color.tag_text))
            chip.setOnClickListener {
                val data = Intent().putExtra(EXTRA_ADD_TAG, tag)
                setResult(RESULT_OK, data)
                finish()
            }
            tagsGroup.addView(chip)
        }
    }

    private fun updateSyncStatus(updatedAt: java.time.Instant, lastSyncAt: java.time.Instant?, syncEnabled: Boolean) {
        val icon = SyncStatus.icon(updatedAt, lastSyncAt, syncEnabled)
        syncIcon.setImageResource(icon.iconRes)
        syncIcon.imageTintList = ContextCompat.getColorStateList(this, icon.tintRes)
        syncLabel.text = icon.label
    }

    private fun applyTagSpans(content: String) {
        val editable = contentInput.text ?: return
        applyingSpans = true
        val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        spans.forEach { editable.removeSpan(it) }
        val tagColor = ContextCompat.getColor(this, R.color.primary)
        val regex = Regex("#([\\p{L}\\p{N}_-]+)")
        regex.findAll(content).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            editable.setSpan(
                ForegroundColorSpan(tagColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applyingSpans = false
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.delete(recordId)
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_ADD_TAG = "add_tag"
    }
}
