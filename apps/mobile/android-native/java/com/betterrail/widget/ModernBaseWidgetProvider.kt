package com.betterrail.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.betterrail.R
import com.betterrail.widget.data.WidgetData
import com.betterrail.widget.data.WidgetTrainItem
import com.betterrail.widget.data.StationsData
import com.betterrail.widget.repository.TrainScheduleRepository
import com.betterrail.widget.repository.ModernWidgetPreferencesRepository
import com.betterrail.widget.repository.Resource
import com.betterrail.widget.data.WidgetScheduleData
import com.betterrail.widget.lifecycle.WidgetCoroutineManager
import com.betterrail.widget.state.WidgetState
import com.betterrail.widget.scheduler.WidgetUpdateScheduler
import com.betterrail.widget.commute.CommuteOffice
import com.betterrail.widget.commute.CommutePlanner
import com.betterrail.widget.commute.PlannedTrain
import com.betterrail.widget.commute.ResolvedCommute
import com.betterrail.widget.commute.isCommuteConfigured
import com.betterrail.widget.commute.toCommuteSettings
import com.betterrail.widget.state.CommuteGlance
import com.betterrail.widget.state.CommuteUpcoming
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Modern widget provider base class using Repository pattern
 * Uses EntryPoint pattern for dependency access since AppWidgetProvider doesn't support @AndroidEntryPoint
 */
abstract class ModernBaseWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetProviderEntryPoint {
        fun scheduleRepository(): TrainScheduleRepository
        fun preferencesRepository(): ModernWidgetPreferencesRepository
        fun cacheRepository(): com.betterrail.widget.repository.ModernCacheRepository
    }

    private var applicationContext: android.content.Context? = null

    // Repositories accessed via EntryPoint
    private val scheduleRepository: TrainScheduleRepository by lazy {
        val context = applicationContext ?: throw IllegalStateException("Application context not initialized")
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context, WidgetProviderEntryPoint::class.java
        )
        hiltEntryPoint.scheduleRepository()
    }
    
    private val preferencesRepository: ModernWidgetPreferencesRepository by lazy {
        val context = applicationContext ?: throw IllegalStateException("Application context not initialized")
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context, WidgetProviderEntryPoint::class.java
        )
        hiltEntryPoint.preferencesRepository()
    }
    
    private val cacheRepository: com.betterrail.widget.repository.ModernCacheRepository by lazy {
        val context = applicationContext ?: throw IllegalStateException("Application context not initialized")
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context, WidgetProviderEntryPoint::class.java
        )
        hiltEntryPoint.cacheRepository()
    }
    
    private val coroutineManager = WidgetCoroutineManager.getInstance()
    
    companion object {
        private const val INVALID_WIDGET_ID = -1
    }

    // Abstract methods for subclasses to implement
    abstract fun getActionRefresh(): String
    abstract fun getActionWidgetUpdate(): String
    abstract fun getActionRouteReversal(): String
    abstract fun getActionOfficeToggle(): String
    abstract fun getLayoutResource(): Int
    abstract fun getWidgetContainerId(): Int
    abstract fun getLogTag(): String
    abstract fun getWidgetType(): String
    abstract fun getConfigActivityClass(): Class<*>

    // Abstract UI methods for state-driven updates
    abstract fun renderWidgetState(context: Context, state: WidgetState): RemoteViews
    
    // Common method to render widget state with stale detection
    protected fun renderWidgetStateWithStaleDetection(context: Context, state: WidgetState, appWidgetId: Int): RemoteViews {
        val views = if (this is UnifiedWidgetProvider) {
            renderWidgetStateResponsive(context, state, appWidgetId)
        } else {
            renderWidgetState(context, state)
        }
        
        // Store displayed train time for stale detection
        when (state) {
            is WidgetState.Schedule -> {
                storeDisplayedTrainTime(context, state.commute?.leaveByTime ?: state.nextTrain.departureTime)
            }
            is WidgetState.TomorrowSchedule -> {
                storeDisplayedTrainTime(context, state.commute?.leaveByTime ?: state.firstTrain.departureTime)
            }
            is WidgetState.FutureSchedule -> {
                storeDisplayedTrainTime(context, state.commute?.leaveByTime ?: state.firstTrain.departureTime)
            }
            else -> {
                // Other states don't show train times
            }
        }
        
        return views
    }
    
    private fun storeDisplayedTrainTime(context: Context, trainTime: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("widget_display_state", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putString("${getWidgetType()}_last_train_time", trainTime)
                .putLong("${getWidgetType()}_last_update", System.currentTimeMillis())
                .apply()
                
            Log.d(getLogTag(), "Stored displayed train time: $trainTime for widget type ${getWidgetType()}")
        } catch (e: Exception) {
            Log.e(getLogTag(), "Error storing displayed train time", e)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(getLogTag(), "onEnabled called - widget added")
        applicationContext = context.applicationContext
        
        // Ensure periodic updates are running
        WidgetUpdateScheduler.schedulePeriodicUpdates(context)
        super.onEnabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(getLogTag(), "onUpdate called for ${appWidgetIds.size} widgets")
        applicationContext = context.applicationContext
        
        // Ensure periodic updates are running whenever widgets update
        WidgetUpdateScheduler.schedulePeriodicUpdates(context)
        
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        applicationContext = context.applicationContext
        
        Log.d(getLogTag(), "onReceive: ${intent.action}")
        when (intent.action) {
            getActionRefresh() -> handleRefreshAction(context)
            getActionWidgetUpdate() -> handleWidgetUpdateAction(context, intent)
            getActionRouteReversal() -> handleRouteReversalAction(context, intent)
            getActionOfficeToggle() -> handleOfficeToggleAction(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(getLogTag(), "onDeleted called for ${appWidgetIds.size} widgets")
        applicationContext = context.applicationContext
        
        for (appWidgetId in appWidgetIds) {
            // Cancel widget coroutines
            coroutineManager.cancelWidget(appWidgetId, "Widget deleted")
            
            // Clear widget reversed state
            val sharedPrefs = context.getSharedPreferences("widget_route_state", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .remove("widget_${appWidgetId}_reversed")
                .apply()
            
            // Clear widget data using repository
            coroutineManager.launchInWidgetScope(appWidgetId) {
                try {
                    preferencesRepository.deleteWidgetData(appWidgetId)
                    scheduleRepository.clearCache(appWidgetId)
                    Log.d(getLogTag(), "Cleaned up data for deleted widget $appWidgetId")
                } catch (e: Exception) {
                    Log.e(getLogTag(), "Error cleaning up widget $appWidgetId", e)
                }
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        Log.d(getLogTag(), "onAppWidgetOptionsChanged for widget $appWidgetId")
        
        // Extract dimensions
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val maxHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        
        // Update widget when size changes - responsive rendering will handle layout selection
        updateWidget(context, appWidgetManager, appWidgetId)
        
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDisabled(context: Context) {
        Log.d(getLogTag(), "onDisabled called - all widgets removed")
        applicationContext = context.applicationContext
        
        // Only clean up cache data, keep periodic updates running in case onDisabled() 
        // was called incorrectly (Android bug where it triggers even when widgets remain)
        coroutineManager.launchInWidgetScope(-1) {
            try {
                scheduleRepository.clearAllCache()
                Log.d(getLogTag(), "Cleaned up all widget cache data")
            } catch (e: Exception) {
                Log.e(getLogTag(), "Error during cleanup", e)
            }
        }
        super.onDisabled(context)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        applicationContext = context.applicationContext

        coroutineManager.launchInWidgetScope(appWidgetId) {

            val storedWidgetData = preferencesRepository.getWidgetData(appWidgetId)
            val widgetData = getEffectiveWidgetData(storedWidgetData)
            
            if (widgetData == null) {
                Log.d(getLogTag(), "Widget $appWidgetId not configured, showing configuration state")
                showConfigurationState(context, appWidgetManager, appWidgetId)
            } else {
                // Check if widget is showing stale data and refresh from cache if needed
                if (isWidgetShowingDepartedTrain(context, appWidgetId)) {
                    Log.d(getLogTag(), "Widget $appWidgetId showing departed train - refreshing from cache")
                }
                
                Log.d(getLogTag(), "Widget $appWidgetId configured, loading schedule data")
                loadScheduleData(context, appWidgetManager, appWidgetId, widgetData)
            }
        }
    }

    private fun loadScheduleData(
        context: Context, 
        appWidgetManager: AppWidgetManager, 
        appWidgetId: Int, 
        widgetData: WidgetData
    ) {
        coroutineManager.launchInWidgetScope(appWidgetId) {
            try {
                scheduleRepository.getSchedule(appWidgetId, widgetData).collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> showLoadingState(context, appWidgetManager, appWidgetId, widgetData)
                        is Resource.Success -> handleSuccessfulSchedule(context, appWidgetManager, appWidgetId, widgetData, resource)
                        is Resource.Error -> {
                            Log.e(getLogTag(), "Error loading schedule for widget $appWidgetId: ${resource.message}")
                            showErrorState(context, appWidgetManager, appWidgetId, widgetData, resource.message)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(getLogTag(), "Exception in loadScheduleData for widget $appWidgetId", e)
                showErrorState(context, appWidgetManager, appWidgetId, widgetData, e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun handleSuccessfulSchedule(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetData: WidgetData,
        resource: Resource.Success<WidgetScheduleData>
    ) {
        Log.d(getLogTag(), "Successfully loaded ${resource.data.routes.size} routes for widget $appWidgetId (cache: ${resource.fromCache})")
        
        if (resource.data.routes.isEmpty()) {
            loadTomorrowSchedule(context, appWidgetManager, appWidgetId, widgetData)
            return
        }
        
        // Process all trains with unified date-aware logic
        processTrainsWithDateAwareLogic(context, appWidgetManager, appWidgetId, widgetData, resource.data.routes, false)
    }
    
    /**
     * Unified function that processes trains using date-aware logic and time-based filtering
     */
    private suspend fun processTrainsWithDateAwareLogic(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetData: WidgetData,
        allTrains: List<WidgetTrainItem>,
        isFromTomorrowRequest: Boolean
    ) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val selection = selectDisplayTrains(localeContext, widgetData, allTrains)
        val upcomingTrains = selection.trains

        if (upcomingTrains.isEmpty()) {
            if (isFromTomorrowRequest) {
                showNoTrainsState(context, appWidgetManager, appWidgetId, widgetData)
            } else {
                Log.d(getLogTag(), "No upcoming trains for today, loading tomorrow's schedule")
                loadTomorrowSchedule(context, appWidgetManager, appWidgetId, widgetData)
            }
            return
        }
        
        // Always use the first upcoming train to determine widget state based on its actual date
        val firstTrain = upcomingTrains.first()
        val daysAway = getDaysFromToday(firstTrain.departureTimestamp)

        val originName = StationsData.getStationName(localeContext, widgetData.originId)
        val destinationName = StationsData.getStationName(localeContext, widgetData.destinationId)
        
        Log.d(getLogTag(), "First upcoming train: ${firstTrain.departureTimestamp}, Days away: $daysAway")
        
        val glance = selection.glance?.let { base ->
            when (daysAway) {
                0 -> base
                1 -> base.copy(labelPrefix = localeContext.getString(R.string.tomorrow))
                else -> base.copy(
                    labelPrefix = localeContext.resources.getQuantityString(R.plurals.upcoming_in_days, daysAway, daysAway)
                )
            }
        }
        val laterTrains = upcomingTrains.drop(1)

        val state = when (daysAway) {
            0 -> {
                // Same date as today = NEXT TRAIN
                Log.d(getLogTag(), "First train ${firstTrain.departureTime} is today - showing as NEXT TRAIN")
                WidgetState.Schedule(widgetData.originId, originName, destinationName, firstTrain, laterTrains, glance)
            }
            1 -> {
                // +1 day from today = TOMORROW
                Log.d(getLogTag(), "First train ${firstTrain.departureTime} is tomorrow - showing as TOMORROW")
                WidgetState.TomorrowSchedule(widgetData.originId, originName, destinationName, firstTrain, laterTrains, glance)
            }
            else -> {
                // +2 or more days = UPCOMING IN X DAYS
                Log.d(getLogTag(), "First train ${firstTrain.departureTime} is $daysAway days away - showing as UPCOMING IN X DAYS")
                WidgetState.FutureSchedule(widgetData.originId, originName, destinationName, firstTrain, laterTrains, daysAway, glance)
            }
        }
        
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)

        if (isFromTomorrowRequest || daysAway != 0) return
        val fillCommute = widgetData.isCommuteConfigured()
        if (!fillCommute && !isEffective4x3(context, appWidgetId)) return

        val maxNeeded = if (fillCommute) {
            1 + CommutePlanner.EXTRA_TRAINS
        } else {
            WidgetSize.MAX_UPCOMING_TRAINS_4X3 + 1
        }
        if (upcomingTrains.size >= maxNeeded) return

        try {
            val tomorrowResource = scheduleRepository.getTomorrowSchedule(appWidgetId, widgetData)
                .filter { it !is Resource.Loading }
                .firstOrNull()

            if (tomorrowResource is Resource.Success && tomorrowResource.data.routes.isNotEmpty()) {
                val combined = selectDisplayTrains(
                    localeContext,
                    widgetData,
                    allTrains + tomorrowResource.data.routes,
                )
                if (combined.trains.size > upcomingTrains.size) {
                    val shown = combined.trains.take(maxNeeded)
                    val updatedState = WidgetState.Schedule(
                        widgetData.originId,
                        originName,
                        destinationName,
                        shown.first(),
                        shown.drop(1),
                        combined.glance,
                    )
                    updateWidgetUI(context, appWidgetManager, appWidgetId, updatedState)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(getLogTag(), "Could not append tomorrow trains for widget $appWidgetId: ${e.message}")
        }
    }

    private fun isEffective4x3(context: Context, appWidgetId: Int): Boolean {
        return if (this is UnifiedWidgetProvider) {
            getEffectiveWidgetSize(context, appWidgetId) == WidgetSize.COMPACT_4X3
        } else {
            getLayoutResource() == R.layout.widget_compact_4x3
        }
    }
    
    private data class DisplaySelection(
        val trains: List<WidgetTrainItem>,
        val glance: CommuteGlance?,
    )

    /**
     * Commute widgets keep trains you can still leave for. Everyone else keeps the next departures.
     */
    private fun selectDisplayTrains(
        context: Context,
        widgetData: WidgetData,
        allTrains: List<WidgetTrainItem>,
    ): DisplaySelection {
        if (!widgetData.isCommuteConfigured()) {
            return DisplaySelection(filterUpcomingTrains(allTrains), null)
        }
        val now = ZonedDateTime.now()
        val resolved = CommutePlanner.resolve(widgetData.toCommuteSettings(now), now)
        val planned = CommutePlanner.visiblePlans(allTrains, resolved, now.toLocalDateTime())
        if (planned.isEmpty()) return DisplaySelection(emptyList(), null)
        return DisplaySelection(planned.map { it.item }, buildGlance(context, widgetData, resolved, planned))
    }

    private fun buildGlance(
        context: Context,
        widgetData: WidgetData,
        resolved: ResolvedCommute,
        planned: List<PlannedTrain>,
    ): CommuteGlance {
        val first = planned.first().plan
        val officeLabel = when (resolved.office) {
            CommuteOffice.A -> widgetData.officeALabel.ifBlank { context.getString(R.string.office_a_name) }
            CommuteOffice.B -> widgetData.officeBLabel.ifBlank { context.getString(R.string.office_b_name) }
        }
        val upcoming = planned.drop(1).map { plannedTrain ->
            CommuteUpcoming(
                leaveByTime = CommutePlanner.formatClock(plannedTrain.plan.leaveBy),
                etaTime = CommutePlanner.formatClock(plannedTrain.plan.eta),
                trainTime = CommutePlanner.formatClock(plannedTrain.plan.trainDeparture),
            )
        }
        return CommuteGlance(
            leaveInMinutes = first.leaveInMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            leaveByTime = CommutePlanner.formatClock(first.leaveBy),
            trainTime = CommutePlanner.formatClock(first.trainDeparture),
            etaTime = CommutePlanner.formatClock(first.eta),
            officeLabel = officeLabel,
            firstLegMinutes = resolved.firstScooterMinutes,
            lastLegMinutes = resolved.lastScooterMinutes,
            upcoming = upcoming,
        )
    }

    /**
     * Filter trains to show only those that are upcoming based on current date and time
     */
    private fun filterUpcomingTrains(trains: List<WidgetTrainItem>): List<WidgetTrainItem> {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        
        return trains.filter { train ->
            try {
                if (train.departureTimestamp.isEmpty()) return@filter false
                
                val trainDateTime = LocalDateTime.parse(train.departureTimestamp, formatter)
                val isUpcoming = trainDateTime.isAfter(now)
                
                Log.d(getLogTag(), "Train ${train.departureTime} (${train.departureTimestamp}): upcoming = $isUpcoming")
                
                isUpcoming
            } catch (e: Exception) {
                Log.e(getLogTag(), "Error parsing train timestamp: ${train.departureTimestamp}", e)
                false
            }
        }
    }

    private suspend fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetData: WidgetData) {
        Log.d(getLogTag(), "Loading schedule for widget $appWidgetId")
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.Loading(
            widgetData.originId,
            StationsData.getStationName(localeContext, widgetData.originId),
            StationsData.getStationName(localeContext, widgetData.destinationId)
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
    }

    private suspend fun showErrorState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetData: WidgetData, errorMessage: String) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.Error(
            widgetData.originId,
            StationsData.getStationName(localeContext, widgetData.originId),
            StationsData.getStationName(localeContext, widgetData.destinationId),
            errorMessage
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
    }

    private fun loadTomorrowSchedule(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetData: WidgetData
    ) {
        coroutineManager.launchInWidgetScope(appWidgetId) {
            try {
                // Show tomorrow loading state first
                showTomorrowLoadingState(context, appWidgetManager, appWidgetId, widgetData)

                scheduleRepository.getTomorrowSchedule(appWidgetId, widgetData).collectLatest { resource ->
                    when (resource) {
                        is Resource.Success -> handleTomorrowScheduleSuccess(context, appWidgetManager, appWidgetId, widgetData, resource)
                        is Resource.Error -> showTomorrowFallbackState(context, appWidgetManager, appWidgetId, widgetData)
                        is Resource.Loading -> {
                            // Already handled above
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(getLogTag(), "Exception in loadTomorrowSchedule for widget $appWidgetId", e)
                showTomorrowFallbackState(context, appWidgetManager, appWidgetId, widgetData)
            }
        }
    }

    private suspend fun handleTomorrowScheduleSuccess(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetData: WidgetData,
        resource: Resource.Success<WidgetScheduleData>
    ) {
        Log.d(getLogTag(), "Successfully loaded ${resource.data.routes.size} tomorrow routes for widget $appWidgetId")
        
        if (resource.data.routes.isEmpty()) {
            showNoTrainsState(context, appWidgetManager, appWidgetId, widgetData)
            return
        }
        
        // Process tomorrow's trains with same unified date-aware logic
        processTrainsWithDateAwareLogic(context, appWidgetManager, appWidgetId, widgetData, resource.data.routes, true)
    }

    private suspend fun showTomorrowLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetData: WidgetData) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.TomorrowLoading(
            widgetData.originId,
            StationsData.getStationName(localeContext, widgetData.originId),
            StationsData.getStationName(localeContext, widgetData.destinationId)
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
    }

    private suspend fun showNoTrainsState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetData: WidgetData) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.NoTrains(
            widgetData.originId,
            StationsData.getStationName(localeContext, widgetData.originId),
            StationsData.getStationName(localeContext, widgetData.destinationId)
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
    }

    private suspend fun showTomorrowFallbackState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, widgetData: WidgetData) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.TomorrowFallback(
            widgetData.originId,
            StationsData.getStationName(localeContext, widgetData.originId),
            StationsData.getStationName(localeContext, widgetData.destinationId)
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
    }

    private fun showConfigurationState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)
        val state = WidgetState.Configuration(
            message = localeContext.getString(R.string.tap_to_configure),
            subtitle = localeContext.getString(R.string.select_your_route)
        )
        updateWidgetUI(context, appWidgetManager, appWidgetId, state)
        setupConfigurationClickIntent(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidgetUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: WidgetState
    ) {
        coroutineManager.launchInWidgetScope(appWidgetId) {
            try {
                val views = renderWidgetStateWithStaleDetection(context, state, appWidgetId)
                
                // Set up click intent based on widget state
                when (state) {
                    is WidgetState.Configuration -> {
                        // Keep existing configuration click behavior
                        setupConfigurationClickIntent(context, appWidgetManager, appWidgetId)
                        return@launchInWidgetScope // Early return since setupConfigurationClickIntent handles the widget update
                    }
                    
                    is WidgetState.Error -> {
                        // For error state, use refresh behavior to retry
                        setupRefreshClickIntent(context, views)
                    }
                    
                    is WidgetState.Schedule -> {
                        // Apply time-of-day reversal so the deep link matches the displayed route
                        val widgetData = getEffectiveWidgetData(preferencesRepository.getWidgetData(appWidgetId))
                        if (widgetData != null) {
                            setupClickIntentsBase(
                                context = context,
                                views = views,
                                appWidgetId = appWidgetId,
                                widgetData = widgetData,
                                clickTargetId = getWidgetContainerId(),
                                useDeeplink = true,
                                deeplinkPath = getWidgetType()
                            )
                            
                            if (widgetData.isCommuteConfigured()) {
                                setupOfficeToggleClickIntent(context, views, appWidgetId)
                                setupRouteReversalClickIntent(context, views, appWidgetId)
                                setupClickIntentsBase(
                                    context = context,
                                    views = views,
                                    appWidgetId = appWidgetId,
                                    widgetData = widgetData,
                                    clickTargetId = getWidgetContainerId(),
                                    useDeeplink = true,
                                    deeplinkPath = getWidgetType()
                                )
                            } else if (widgetData.allowRouteReversal) {
                                setupRouteReversalClickIntent(context, views, appWidgetId)
                            } else {
                                setupRouteDeepLinkClickIntent(context, views, appWidgetId, widgetData)
                            }
                        } else {
                            setupRefreshClickIntent(context, views)
                        }
                    }
                    
                    else -> {
                        // For TomorrowSchedule, FutureSchedule and other states, get the complete widget data from preferences for deeplink
                        val widgetData = getEffectiveWidgetData(preferencesRepository.getWidgetData(appWidgetId))
                        if (widgetData != null) {
                            setupClickIntentsBase(
                                context = context,
                                views = views,
                                appWidgetId = appWidgetId,
                                widgetData = widgetData,
                                clickTargetId = getWidgetContainerId(),
                                useDeeplink = true,
                                deeplinkPath = getWidgetType()
                            )
                            
                            if (widgetData.isCommuteConfigured()) {
                                setupOfficeToggleClickIntent(context, views, appWidgetId)
                                setupRouteReversalClickIntent(context, views, appWidgetId)
                                setupClickIntentsBase(
                                    context = context,
                                    views = views,
                                    appWidgetId = appWidgetId,
                                    widgetData = widgetData,
                                    clickTargetId = getWidgetContainerId(),
                                    useDeeplink = true,
                                    deeplinkPath = getWidgetType()
                                )
                            } else if (widgetData.allowRouteReversal) {
                                setupRouteReversalClickIntent(context, views, appWidgetId)
                            } else {
                                setupRouteDeepLinkClickIntent(context, views, appWidgetId, widgetData)
                            }
                        } else {
                            // Fallback to refresh if no widget data found
                            setupRefreshClickIntent(context, views)
                        }
                    }
                }
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(getLogTag(), "Updated widget $appWidgetId UI with state: ${state::class.simpleName}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(getLogTag(), "Error updating widget $appWidgetId UI", e)
            }
        }
    }

    /**
     * Creates a reusable deeplink intent for widget navigation
     */
    protected fun createDeeplinkIntent(
        context: Context,
        appWidgetId: Int,
        widgetData: WidgetData,
        deeplinkPath: String = "widget"
    ): PendingIntent {
        // The sideload preview is a different applicationId and must not steal betterrail://.
        val scheme = if (context.packageName == "com.aaronkatzin.rail") "aaronrail" else "betterrail"
        val safeDeeplinkPath = if (widgetData.isCommuteConfigured()) "compact" else deeplinkPath
        val deepLinkUri = "$scheme://$safeDeeplinkPath?originId=${widgetData.originId}&destinationId=${widgetData.destinationId}"

        val localeContext = com.betterrail.widget.utils.LocaleUtils.createLocaleContext(context)

        val openAppIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLinkUri)).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_origin_id", widgetData.originId)
            putExtra("widget_destination_id", widgetData.destinationId)
            // Pass localized station names using app's language setting
            putExtra("widget_origin_name", StationsData.getStationName(localeContext, widgetData.originId))
            putExtra("widget_destination_name", StationsData.getStationName(localeContext, widgetData.destinationId))
            putExtra("from_widget", true)
            putExtra("widget_type", deeplinkPath)
        }

        return PendingIntent.getActivity(
            context, appWidgetId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Sets up click intents for widgets - supports both deeplink and refresh modes
     */
    protected fun setupClickIntentsBase(
        context: Context, 
        views: RemoteViews, 
        appWidgetId: Int, 
        widgetData: WidgetData, 
        clickTargetId: Int,
        useDeeplink: Boolean = true,
        deeplinkPath: String = "widget"
    ) {
        if (widgetData.originId.isEmpty()) {
            // Subclasses will handle configuration intents
            return
        }
        
        val pendingIntent = if (useDeeplink) {
            createDeeplinkIntent(context, appWidgetId, widgetData, deeplinkPath)
        } else {
            // Set up tap-to-refresh for configured widgets
            val refreshIntent = Intent(context, this::class.java).apply {
                action = getActionRefresh()
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        views.setOnClickPendingIntent(clickTargetId, pendingIntent)
    }

    private fun setupRefreshClickIntent(context: Context, views: RemoteViews) {
        val intent = Intent(context, this::class.java).apply {
            action = getActionRefresh()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(getWidgetContainerId(), pendingIntent)
    }

    private fun setupOfficeToggleClickIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        val intent = Intent(context, this::class.java).apply {
            action = getActionOfficeToggle()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_office_toggle, pendingIntent)
    }

    private fun setupRouteReversalClickIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        val intent = Intent(context, this::class.java).apply {
            action = getActionRouteReversal()
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId + 1000, intent, // Use unique request code to avoid conflicts
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set click handler on the route display elements (station name and destination)
        views.setOnClickPendingIntent(R.id.widget_station_name, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_destination_container, pendingIntent)

        Log.d(getLogTag(), "Set up route reversal click intent for widget $appWidgetId")
    }

    private fun setupRouteDeepLinkClickIntent(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        widgetData: WidgetData
    ) {
        setupClickIntentsBase(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            widgetData = widgetData,
            clickTargetId = R.id.widget_station_name,
            useDeeplink = true,
            deeplinkPath = getWidgetType()
        )

        setupClickIntentsBase(
            context = context,
            views = views,
            appWidgetId = appWidgetId,
            widgetData = widgetData,
            clickTargetId = R.id.widget_destination_container,
            useDeeplink = true,
            deeplinkPath = getWidgetType()
        )

        Log.d(getLogTag(), "Set up route deep link click intent for widget $appWidgetId")
    }

    private fun isWidgetRouteReversed(context: Context, appWidgetId: Int): Boolean {
        val sharedPrefs = context.getSharedPreferences("widget_route_state", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("widget_${appWidgetId}_reversed", false)
    }

    private fun setWidgetRouteReversed(context: Context, appWidgetId: Int, isReversed: Boolean) {
        val sharedPrefs = context.getSharedPreferences("widget_route_state", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("widget_${appWidgetId}_reversed", isReversed)
            .apply()
    }



    private fun setupConfigurationClickIntent(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, getLayoutResource())
        val intent = Intent(context, getConfigActivityClass()).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(getWidgetContainerId(), pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun handleRefreshAction(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, this::class.java)
        )
        Log.d(getLogTag(), "Manual refresh for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun handleWidgetUpdateAction(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
        if (appWidgetId != INVALID_WIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            Log.d(getLogTag(), "Scheduled update for widget $appWidgetId")
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun handleOfficeToggleAction(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
        if (appWidgetId == INVALID_WIDGET_ID) return
        coroutineManager.launchInWidgetScope(appWidgetId) {
            try {
                val widgetData = preferencesRepository.getWidgetData(appWidgetId)
                if (widgetData == null || !widgetData.isCommuteConfigured()) return@launchInWidgetScope
                val now = ZonedDateTime.now()
                val current = CommutePlanner.resolve(widgetData.toCommuteSettings(now), now).office
                val next = CommutePlanner.toggleOffice(current)
                preferencesRepository.saveWidgetData(
                    appWidgetId,
                    widgetData.copy(
                        officeOverride = next.name,
                        officeOverrideDate = now.toLocalDate().toString(),
                    )
                )
                updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            } catch (e: Exception) {
                Log.e(getLogTag(), "Error toggling office for widget $appWidgetId", e)
            }
        }
    }

    private fun handleRouteReversalAction(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
        if (appWidgetId != INVALID_WIDGET_ID) {
            coroutineManager.launchInWidgetScope(appWidgetId) {
                try {
                    val widgetData = preferencesRepository.getWidgetData(appWidgetId)
                    if (widgetData?.allowRouteReversal == true || widgetData?.isCommuteConfigured() == true) {
                        if (widgetData.autoReverseRoute) {
                            // Smart Override Logic
                            val currentTimestamp = System.currentTimeMillis()
                            val isOverrideActive = currentTimestamp < widgetData.manualOverrideUntil
                            
                            // Toggle override: If active, clear it. If inactive, set it for current window.
                            val newOverrideUntil = if (isOverrideActive) {
                                0L
                            } else {
                                calculateNextWindowStart()
                            }
                            
                            Log.d(getLogTag(), "Smart Reversal Toggle for $appWidgetId: active=$isOverrideActive -> newUntil=$newOverrideUntil")
                            
                            preferencesRepository.saveWidgetData(appWidgetId, widgetData.copy(manualOverrideUntil = newOverrideUntil))
                            
                            // Trigger update
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            updateWidget(context, appWidgetManager, appWidgetId)
                        } else {
                            // Legacy Permanent Swap Logic
                            // Toggle reversed state
                            val isCurrentlyReversed = isWidgetRouteReversed(context, appWidgetId)
                            val newReversedState = !isCurrentlyReversed
                            setWidgetRouteReversed(context, appWidgetId, newReversedState)
                            
                            Log.d(getLogTag(), "Toggling route display for widget $appWidgetId: reversed=$newReversedState")
                            
                            // Always just swap the current route data
                            val swappedWidgetData = widgetData.copy(
                                originId = widgetData.destinationId,
                                destinationId = widgetData.originId,
                                originName = widgetData.destinationName,
                                destinationName = widgetData.originName
                            )
                            
                            // Save swapped data
                            preferencesRepository.saveWidgetData(appWidgetId, swappedWidgetData)
                            
                            // Update widget display with fresh data from API
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            updateWidget(context, appWidgetManager, appWidgetId)
                        }
                    } else {
                        Log.d(getLogTag(), "Route reversal not allowed for widget $appWidgetId")
                    }
                } catch (e: Exception) {
                    Log.e(getLogTag(), "Error reversing route for widget $appWidgetId", e)
                }
            }
        }
    }



    /**
     * Check if widget is currently showing a departed train (time has passed)
     */
    private fun isWidgetShowingDepartedTrain(context: Context, appWidgetId: Int): Boolean {
        return try {
            // Get the last displayed train time from preferences
            val sharedPrefs = context.getSharedPreferences("widget_display_state", Context.MODE_PRIVATE)
            val lastDisplayedTime = sharedPrefs.getString("${getWidgetType()}_last_train_time", "")
            
            if (lastDisplayedTime.isNullOrEmpty()) return false
            
            // Get current time
            val now = LocalTime.now()
            
            // Parse displayed time (HH:mm)
            val displayedTime = LocalTime.parse(lastDisplayedTime)
            
            // Train is considered departed if current time has passed the displayed time
            val isDeparted = now.isAfter(displayedTime)
            
            if (isDeparted) {
                val minutesPastDeparture = java.time.Duration.between(displayedTime, now).toMinutes()
                Log.d(getLogTag(), "Widget $appWidgetId detected stale data: train $lastDisplayedTime departed ${minutesPastDeparture}min ago")
            }
            
            return isDeparted
        } catch (e: Exception) {
            Log.e(getLogTag(), "Error checking for departed train", e)
            return false
        }
    }

    /**
     * Calculates the effective widget data based on auto-reverse rules and overrides
     */
    private fun getEffectiveWidgetData(widgetData: WidgetData?): WidgetData? {
        if (widgetData == null) return null
        if (widgetData.isCommuteConfigured()) {
            val now = ZonedDateTime.now()
            val resolved = CommutePlanner.resolve(widgetData.toCommuteSettings(now), now)
            Log.d(getLogTag(), "Commute route ${resolved.direction} office ${resolved.office}: ${resolved.originStationId} -> ${resolved.destinationStationId}")
            return widgetData.copy(
                originId = resolved.originStationId,
                destinationId = resolved.destinationStationId,
            )
        }
        if (!widgetData.autoReverseRoute) return widgetData

        val now = ZonedDateTime.now()
        val isEvening = CommutePlanner.isEvening(now.toLocalTime())
        val isOverrideActive = System.currentTimeMillis() < widgetData.manualOverrideUntil
        val shouldReverse = isEvening xor isOverrideActive

        return if (shouldReverse) {
             Log.d(getLogTag(), "Smart Reversal ACTIVE for widget (Evening=$isEvening, Override=$isOverrideActive)")
             widgetData.copy(
                originId = widgetData.destinationId,
                destinationId = widgetData.originId,
                originName = widgetData.destinationName,
                destinationName = widgetData.originName
            )
        } else {
             widgetData
        }
    }

    /**
     * Calculates the timestamp for the start of the next time window
     */
    private fun calculateNextWindowStart(): Long {
        return CommutePlanner.nextDirectionWindowStart(ZonedDateTime.now()).toInstant().toEpochMilli()
    }
            
    /**
     * Calculate how many days away a train is from today
     */
    private fun getDaysFromToday(fullTimestamp: String): Int {
        return try {
            if (fullTimestamp.isEmpty()) return 0
            
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val trainDateTime = LocalDateTime.parse(fullTimestamp, formatter)
            val today = LocalDate.now()
            
            val diffDays = ChronoUnit.DAYS.between(today, trainDateTime.toLocalDate()).toInt()
            
            Log.d(getLogTag(), "Date check - Today: $today, Train: ${trainDateTime.toLocalDate()}, Days away: $diffDays")
            
            return diffDays
        } catch (e: Exception) {
            Log.e(getLogTag(), "Error parsing train timestamp: $fullTimestamp", e)
            0
        }
    }
    
    /**
     * Check if a train is for tomorrow by comparing the actual dates
     * Uses the full timestamp from the API response instead of just time
     */
    private fun isTrainForTomorrow(fullTimestamp: String): Boolean {
        return getDaysFromToday(fullTimestamp) == 1
    }
}