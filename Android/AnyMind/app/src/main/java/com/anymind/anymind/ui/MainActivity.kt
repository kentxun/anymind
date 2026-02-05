package com.anymind.anymind.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anymind.anymind.R
import com.anymind.anymind.data.GroupSummary
import com.anymind.anymind.data.TagSummary
import com.anymind.anymind.util.GroupingMode
import com.anymind.anymind.util.Prefs
import com.anymind.anymind.util.TagFilterMode
import com.anymind.anymind.util.TagParser
import com.anymind.anymind.util.SimpleTextWatcher
import com.anymind.anymind.ui.adapters.GroupAdapter
import com.anymind.anymind.ui.adapters.RecordAdapter
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var recordAdapter: RecordAdapter

    private lateinit var searchInput: TextInputEditText
    private lateinit var searchModeGroup: MaterialButtonToggleGroup
    private lateinit var selectedTagsGroup: ChipGroup
    private lateinit var tagModeGroup: MaterialButtonToggleGroup
    private lateinit var clearTagsButton: com.google.android.material.button.MaterialButton
    private lateinit var tagsGroup: FlexboxLayout
    private lateinit var tagsMore: TextView
    private lateinit var tagsScroll: androidx.core.widget.NestedScrollView
    private lateinit var dateFilterValue: TextView

    private var tagsExpanded = false
    private var tagsLimitEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isSwitchingSearchMode = false
    private var searchMode: SearchMode = SearchMode.TAG
    private var didSyncOnLaunch = false

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val tag = data?.getStringExtra(RecordDetailActivity.EXTRA_ADD_TAG)
        if (!tag.isNullOrBlank()) {
            addTag(tag)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        searchInput = findViewById(R.id.search_input)
        searchModeGroup = findViewById(R.id.search_mode_group)
        selectedTagsGroup = findViewById(R.id.selected_tags_group)
        tagModeGroup = findViewById(R.id.tag_mode_group)
        clearTagsButton = findViewById(R.id.clear_tags)
        tagsGroup = findViewById(R.id.tags_group)
        tagsMore = findViewById(R.id.tags_more)
        tagsScroll = findViewById(R.id.tags_scroll)
        dateFilterValue = findViewById(R.id.date_filter_value)

        val recordsList = findViewById<RecyclerView>(R.id.records_list)
        recordAdapter = RecordAdapter { record ->
            openDetail(record.id)
        }
        recordsList.layoutManager = LinearLayoutManager(this)
        recordsList.adapter = recordAdapter

        setupSwipeToDelete(recordsList)
        setupSearchInputs()
        setupSearchModeToggle()
        setupTagMode()
        setupTagActions()
        setupTagMoreToggle()
        setupDateFilter()

        viewModel.records.observe(this) { list ->
            recordAdapter.submitList(list)
        }
        viewModel.tagSummaries.observe(this) { list ->
            renderTagCandidates(list)
        }
        viewModel.groups.observe(this) { _ ->
            updateDateFilterLabel()
        }
        viewModel.syncStatus.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(recordsList, message, Snackbar.LENGTH_SHORT).show()
            }
        }

        renderSelectedTags()
        viewModel.reloadAll()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadAll()
        syncOnLaunchIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                lifecycleScope.launch {
                    val id = viewModel.createRecord()
                    openDetail(id)
                }
                true
            }
            R.id.action_sync -> {
                viewModel.syncNow()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupSwipeToDelete(list: RecyclerView) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val record = viewModel.records.value?.getOrNull(position)
                if (record != null) {
                    lifecycleScope.launch {
                        viewModel.deleteRecord(record.id)
                        viewModel.reloadAll()
                    }
                }
            }
        })
        helper.attachToRecyclerView(list)
    }

    private fun setupSearchInputs() {
        searchInput.addTextChangedListener(SimpleTextWatcher { text ->
            if (isSwitchingSearchMode) {
                return@SimpleTextWatcher
            }
            searchRunnable?.let { handler.removeCallbacks(it) }
            val delay = if (searchMode == SearchMode.TAG) 200L else 300L
            val runnable = Runnable {
                if (searchMode == SearchMode.TAG) {
                    viewModel.updateTagSearchText(text)
                } else {
                    viewModel.searchText = text
                    viewModel.reloadRecords()
                }
            }
            searchRunnable = runnable
            handler.postDelayed(runnable, delay)
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (searchMode == SearchMode.TAG &&
                (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO)
            ) {
                val tags = TagParser.parseFilterInput(searchInput.text?.toString() ?: "")
                tags.forEach { addTag(it) }
                searchInput.setText("")
                true
            } else {
                false
            }
        }
    }

    private fun setupSearchModeToggle() {
        searchModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            val nextMode = if (checkedId == R.id.search_mode_content) {
                SearchMode.CONTENT
            } else {
                SearchMode.TAG
            }
            switchSearchMode(nextMode)
        }
        searchModeGroup.check(R.id.search_mode_tag)
    }

    private fun setupTagMode() {
        tagModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            viewModel.tagFilterMode = when (checkedId) {
                R.id.tag_mode_or -> TagFilterMode.OR
                else -> TagFilterMode.AND
            }
            viewModel.reloadRecords()
        }
        tagModeGroup.check(R.id.tag_mode_and)
    }

    private fun setupTagActions() {
        clearTagsButton.setOnClickListener {
            viewModel.selectedTags.clear()
            renderSelectedTags()
            viewModel.reloadRecords()
        }
    }

    private fun setupTagMoreToggle() {
        tagsMore.setOnClickListener {
            tagsExpanded = !tagsExpanded
            updateTagsHeight()
        }
        updateTagsHeight()
    }

    private fun updateTagsHeight() {
        val params = tagsScroll.layoutParams
        params.height = when {
            !tagsLimitEnabled -> android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            tagsExpanded -> android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            else -> resources.getDimensionPixelSize(R.dimen.tags_collapsed_height)
        }
        tagsScroll.layoutParams = params
        tagsMore.text = if (tagsExpanded) "Less" else "More"
    }

    private fun setupDateFilter() {
        findViewById<TextView>(R.id.date_filter_value).setOnClickListener {
            showDateFilterDialog(viewModel.groups.value ?: emptyList())
        }
    }

    private fun showDateFilterDialog(groups: List<GroupSummary>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_date_filter, null)
        val groupList = view.findViewById<RecyclerView>(R.id.group_list)
        val modeGroup = view.findViewById<android.widget.RadioGroup>(R.id.group_mode_group)
        val day = view.findViewById<android.widget.RadioButton>(R.id.group_mode_day)
        val week = view.findViewById<android.widget.RadioButton>(R.id.group_mode_week)
        val month = view.findViewById<android.widget.RadioButton>(R.id.group_mode_month)

        when (viewModel.groupingMode) {
            GroupingMode.DAY -> day.isChecked = true
            GroupingMode.WEEK -> week.isChecked = true
            GroupingMode.MONTH -> month.isChecked = true
        }

        val adapter = GroupAdapter { selected ->
            viewModel.selectedGroupKey = selected?.id
            viewModel.reloadRecords()
            updateDateFilterLabel()
            dialog.dismiss()
        }
        groupList.layoutManager = LinearLayoutManager(this)
        groupList.adapter = adapter
        adapter.submitList(groups, includeAllOption = true)

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.groupingMode = when (checkedId) {
                R.id.group_mode_day -> GroupingMode.DAY
                R.id.group_mode_month -> GroupingMode.MONTH
                else -> GroupingMode.WEEK
            }
            viewModel.selectedGroupKey = null
            viewModel.reloadAll()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun renderTagCandidates(tags: List<TagSummary>) {
        tagsGroup.removeAllViews()
        val displayTags = tags
        displayTags.forEach { summary ->
            val chip = createTagChip(summary.name, closable = false) {
                addTag(summary.name)
            }
            tagsGroup.addView(chip)
        }
        tagsLimitEnabled = displayTags.size > 8
        tagsExpanded = tagsExpanded && tagsLimitEnabled
        tagsMore.isVisible = tagsLimitEnabled
        updateTagsHeight()
    }

    private fun renderSelectedTags() {
        selectedTagsGroup.removeAllViews()
        viewModel.selectedTags.forEach { tag ->
            val chip = createTagChip(tag, closable = true) {
                viewModel.selectedTags.remove(tag)
                renderSelectedTags()
                viewModel.reloadRecords()
            }
            selectedTagsGroup.addView(chip)
        }
        tagModeGroup.isVisible = viewModel.selectedTags.size >= 2
        if (viewModel.tagFilterMode == TagFilterMode.AND) {
            tagModeGroup.check(R.id.tag_mode_and)
        } else {
            tagModeGroup.check(R.id.tag_mode_or)
        }
    }

    private fun createTagChip(text: String, closable: Boolean, onClick: () -> Unit): Chip {
        val chip = Chip(this)
        chip.text = text
        chip.isCheckable = false
        chip.isClickable = true
        chip.setOnClickListener { onClick() }
        chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.tag_bg)
        chip.setTextColor(ContextCompat.getColor(this, R.color.tag_text))
        if (closable) {
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener { onClick() }
        }
        val params = FlexboxLayout.LayoutParams(
            FlexboxLayout.LayoutParams.WRAP_CONTENT,
            FlexboxLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = resources.getDimensionPixelSize(R.dimen.tag_chip_margin)
        params.setMargins(margin, margin, margin, margin)
        chip.layoutParams = params
        return chip
    }

    private fun addTag(tag: String) {
        if (searchMode != SearchMode.TAG) {
            switchSearchMode(SearchMode.TAG)
        }
        if (viewModel.searchText.isNotEmpty()) {
            viewModel.searchText = ""
        }
        if (viewModel.selectedGroupKey != null) {
            viewModel.selectedGroupKey = null
            updateDateFilterLabel()
        }
        if (!viewModel.selectedTags.contains(tag)) {
            viewModel.selectedTags.add(tag)
            viewModel.selectedTags.sort()
            renderSelectedTags()
            viewModel.reloadRecords()
        } else {
            viewModel.reloadRecords()
        }
    }

    private fun updateDateFilterLabel() {
        val key = viewModel.selectedGroupKey
        dateFilterValue.text = if (key.isNullOrEmpty()) {
            getString(R.string.date_all)
        } else {
            key
        }
    }

    private fun syncOnLaunchIfNeeded() {
        if (didSyncOnLaunch) {
            return
        }
        val prefs = Prefs.get(this)
        val enabled = prefs.getBoolean("sync_enabled", false)
        val syncOnLaunch = prefs.getBoolean("sync_on_launch", false)
        if (enabled && syncOnLaunch) {
            didSyncOnLaunch = true
            viewModel.syncNow()
        }
    }

    private fun openDetail(id: String) {
        val intent = Intent(this, RecordDetailActivity::class.java)
        intent.putExtra(RecordDetailActivity.EXTRA_RECORD_ID, id)
        detailLauncher.launch(intent)
    }

    private fun switchSearchMode(mode: SearchMode) {
        searchMode = mode
        val text = if (mode == SearchMode.TAG) viewModel.tagSearchText else viewModel.searchText
        val hint = if (mode == SearchMode.TAG) R.string.search_hint else R.string.search_content
        isSwitchingSearchMode = true
        searchInput.setText(text)
        searchInput.setSelection(searchInput.text?.length ?: 0)
        searchInput.setHint(hint)
        isSwitchingSearchMode = false
        if (mode == SearchMode.CONTENT) {
            viewModel.reloadRecords()
        } else {
            if (viewModel.searchText.isNotEmpty()) {
                viewModel.searchText = ""
            }
            viewModel.updateTagSearchText(searchInput.text?.toString() ?: "")
            viewModel.reloadRecords()
        }
    }

    private enum class SearchMode {
        TAG,
        CONTENT
    }
}
