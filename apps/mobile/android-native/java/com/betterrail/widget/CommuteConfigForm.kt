package com.betterrail.widget

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.betterrail.R
import com.betterrail.widget.data.WidgetData
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home station, two offices, scooter minutes, and the weekday map on the widget config screen.
 */
class CommuteConfigForm(
    private val activity: AppCompatActivity,
    private val stationIds: List<String>,
    private val stationNames: List<String>,
    private val stationAdapter: ArrayAdapter<String>,
) {
    private val checkbox: CheckBox = activity.findViewById(R.id.commute_mode_checkbox)
    private val section: LinearLayout = activity.findViewById(R.id.commute_section)
    private val homeSearch: AutoCompleteTextView = activity.findViewById(R.id.home_station_search)
    private val officeASearch: AutoCompleteTextView = activity.findViewById(R.id.office_a_search)
    private val officeBSearch: AutoCompleteTextView = activity.findViewById(R.id.office_b_search)
    private val officeALabel: EditText = activity.findViewById(R.id.office_a_label)
    private val officeBLabel: EditText = activity.findViewById(R.id.office_b_label)
    private val homeToStation: EditText = activity.findViewById(R.id.scooter_home_to_station)
    private val stationToHome: EditText = activity.findViewById(R.id.scooter_station_to_home)
    private val aStationToOffice: EditText = activity.findViewById(R.id.scooter_a_station_to_office)
    private val aOfficeToStation: EditText = activity.findViewById(R.id.scooter_a_office_to_station)
    private val bStationToOffice: EditText = activity.findViewById(R.id.scooter_b_station_to_office)
    private val bOfficeToStation: EditText = activity.findViewById(R.id.scooter_b_office_to_station)
    private val weekdayRow: LinearLayout = activity.findViewById(R.id.weekday_row)
    private val weekdayButtons = mutableListOf<TextView>()
    private val weekdayOffices = CharArray(7) { 'A' }

    var homeId: String = ""
        private set
    var officeAId: String = ""
        private set
    var officeBId: String = ""
        private set

    val enabled: Boolean
        get() = checkbox.isChecked

    fun bind(onChanged: () -> Unit) {
        listOf(homeSearch, officeASearch, officeBSearch).forEach { field ->
            field.setAdapter(stationAdapter)
            field.threshold = 0
        }
        bindStation(homeSearch, { homeId = it }, onChanged)
        bindStation(officeASearch, { officeAId = it }, onChanged)
        bindStation(officeBSearch, { officeBId = it }, onChanged)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onChanged() }
        }
        listOf(homeToStation, stationToHome, aStationToOffice, aOfficeToStation, bStationToOffice, bOfficeToStation, officeALabel, officeBLabel)
            .forEach { it.addTextChangedListener(watcher) }

        buildWeekdayRow(onChanged)
        checkbox.setOnCheckedChangeListener { _, _ ->
            section.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
            onChanged()
        }
        section.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun load(data: WidgetData) {
        checkbox.isChecked = data.commuteEnabled
        homeId = data.homeStationId
        officeAId = data.officeAStationId
        officeBId = data.officeBStationId
        setStationText(homeSearch, homeId)
        setStationText(officeASearch, officeAId)
        setStationText(officeBSearch, officeBId)
        officeALabel.setText(data.officeALabel)
        officeBLabel.setText(data.officeBLabel)
        setMinutes(homeToStation, data.scooterHomeToStation)
        setMinutes(stationToHome, data.scooterStationToHome)
        setMinutes(aStationToOffice, data.scooterAStationToOffice)
        setMinutes(aOfficeToStation, data.scooterAOfficeToStation)
        setMinutes(bStationToOffice, data.scooterBStationToOffice)
        setMinutes(bOfficeToStation, data.scooterBOfficeToStation)
        val flags = data.weekdayOffices.padEnd(7, 'A')
        for (index in weekdayOffices.indices) {
            weekdayOffices[index] = if (flags[index] == 'B' || flags[index] == 'b') 'B' else 'A'
            refreshWeekday(index)
        }
    }

    fun prefill(originId: String, destinationId: String) {
        if (originId.isNotEmpty()) {
            homeId = originId
            setStationText(homeSearch, originId)
        }
        if (destinationId.isNotEmpty()) {
            officeAId = destinationId
            setStationText(officeASearch, destinationId)
        }
    }

    fun isValid(): Boolean {
        if (!enabled) return false
        return homeId.isNotEmpty() && officeAId.isNotEmpty() && officeBId.isNotEmpty() &&
            homeId != officeAId && homeId != officeBId && officeAId != officeBId
    }

    fun applyTo(base: WidgetData): WidgetData {
        return base.copy(
            originId = homeId,
            destinationId = officeAId,
            originName = "",
            destinationName = "",
            commuteEnabled = true,
            homeStationId = homeId,
            officeAStationId = officeAId,
            officeBStationId = officeBId,
            officeALabel = officeALabel.text.toString().trim(),
            officeBLabel = officeBLabel.text.toString().trim(),
            scooterHomeToStation = minutes(homeToStation),
            scooterStationToHome = minutes(stationToHome),
            scooterAStationToOffice = minutes(aStationToOffice),
            scooterAOfficeToStation = minutes(aOfficeToStation),
            scooterBStationToOffice = minutes(bStationToOffice),
            scooterBOfficeToStation = minutes(bOfficeToStation),
            weekdayOffices = String(weekdayOffices),
            officeOverride = "",
            officeOverrideDate = "",
            autoReverseRoute = true,
        )
    }

    private fun buildWeekdayRow(onChanged: () -> Unit) {
        weekdayRow.removeAllViews()
        weekdayButtons.clear()
        val locale = Locale.getDefault()
        val gap = (4 * activity.resources.displayMetrics.density).toInt()
        DayOfWeek.entries.forEachIndexed { index, day ->
            val button = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (index == 6) 0 else gap
                }
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
                textSize = 12f
                setTextColor(activity.getColor(R.color.label))
                setOnClickListener {
                    weekdayOffices[index] = if (weekdayOffices[index] == 'A') 'B' else 'A'
                    refreshWeekday(index)
                    onChanged()
                }
                tag = day.getDisplayName(TextStyle.NARROW, locale)
            }
            weekdayButtons.add(button)
            weekdayRow.addView(button)
            refreshWeekday(index)
        }
    }

    private fun refreshWeekday(index: Int) {
        val button = weekdayButtons[index]
        val dayLabel = button.tag as String
        button.text = "$dayLabel\n${weekdayOffices[index]}"
        button.setBackgroundColor(
            if (weekdayOffices[index] == 'B') activity.getColor(R.color.lightPinky) else activity.getColor(R.color.lightGrey)
        )
    }

    private fun bindStation(field: AutoCompleteTextView, assign: (String) -> Unit, onChanged: () -> Unit) {
        field.setOnItemClickListener { _, _, position, _ ->
            val selectedName = stationAdapter.getItem(position) ?: return@setOnItemClickListener
            val selectedIndex = stationNames.indexOf(selectedName)
            assign(if (selectedIndex >= 0) stationIds[selectedIndex] else "")
            onChanged()
        }
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) field.post { if (field.isAttachedToWindow) field.showDropDown() }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val exact = stationNames.indexOf(s?.toString().orEmpty())
                assign(if (exact >= 0) stationIds[exact] else "")
                onChanged()
            }
        })
    }

    private fun setStationText(field: AutoCompleteTextView, stationId: String) {
        val index = stationIds.indexOf(stationId)
        if (index >= 0) field.setText(stationNames[index], false)
    }

    private fun setMinutes(field: EditText, value: Int) {
        if (value > 0) field.setText(value.toString())
    }

    private fun minutes(field: EditText): Int {
        return field.text.toString().toIntOrNull()?.coerceIn(0, 180) ?: 0
    }
}
