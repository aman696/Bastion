package com.aman.bastion.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aman.bastion.MainActivity
import com.aman.bastion.data.hardcorelock.dao.HardcoreLockDao
import com.aman.bastion.data.hardcorelock.entity.HardcoreLockEntity
import com.aman.bastion.domain.model.UnlockCondition
import com.aman.bastion.ui.theme.BastionColors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class OverlayEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardcoreLockDao: HardcoreLockDao
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeOverlays = mutableMapOf<String, View>()
    private val activeBoundedOverlays = mutableMapOf<String, List<View>>()
    private val overlayStates = mutableMapOf<String, MutableStateFlow<OverlayType>>()

    private var serviceLifecycleOwner = ServiceLifecycleOwner()
    private val activeLockState = MutableStateFlow<HardcoreLockEntity?>(null)
    private var currentLockUntilMs: Long = 0L
    private var activeLockJob: Job? = null
    private var started = false

    fun start(scope: CoroutineScope) {
        ensureLifecycleStarted()

        activeLockJob?.cancel()
        activeLockJob = scope.launch {
            hardcoreLockDao.observeActive().collectLatest { locks ->
                val activeLock = locks.maxByOrNull { it.lockedUntilMs }
                activeLockState.value = activeLock
                currentLockUntilMs = activeLock?.lockedUntilMs ?: 0L
            }
        }
    }

    fun stop() {
        activeLockJob?.cancel()
        activeLockJob = null
        activeLockState.value = null
        hideAllOverlays()
        if (started) {
            serviceLifecycleOwner.onDestroy()
            serviceLifecycleOwner = ServiceLifecycleOwner()
            started = false
        }
    }

    fun showOverlay(key: String, type: OverlayType) {
        if (!Settings.canDrawOverlays(context)) return

        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) return@post

            try {
                ensureLifecycleStarted()

                if ((type as? OverlayType.FeatureBlock)?.mode == FeatureBlockMode.BOUNDED) {
                    showBoundedOverlay(key, type)
                    return@post
                }

                activeBoundedOverlays.remove(key)?.forEach { boundedView ->
                    runCatching { windowManager.removeView(boundedView) }
                }

                val stateFlow = overlayStates.getOrPut(key) { MutableStateFlow(type) }
                if (activeOverlays.containsKey(key)) {
                    stateFlow.value = type
                    activeOverlays[key]?.let { view ->
                        runCatching { windowManager.updateViewLayout(view, buildParams()) }
                    }
                    return@post
                }

                val view = createComposeOverlay(stateFlow)
                windowManager.addView(view, buildParams())
                activeOverlays[key] = view
            } catch (_: WindowManager.BadTokenException) {
                overlayStates.remove(key)
            } catch (_: SecurityException) {
                overlayStates.remove(key)
            } catch (_: RuntimeException) {
                overlayStates.remove(key)
            }
        }
    }

    fun hideOverlay(key: String) {
        mainHandler.post {
            overlayStates.remove(key)
            activeOverlays.remove(key)?.let { view ->
                runCatching { windowManager.removeView(view) }
            }
            activeBoundedOverlays.remove(key)?.forEach { view ->
                runCatching { windowManager.removeView(view) }
            }
        }
    }

    fun hideAllOverlays() {
        mainHandler.post {
            activeOverlays.keys.toList().forEach { key ->
                overlayStates.remove(key)
                activeOverlays.remove(key)?.let { view ->
                    runCatching { windowManager.removeView(view) }
                }
            }
            activeBoundedOverlays.keys.toList().forEach { key ->
                activeBoundedOverlays.remove(key)?.forEach { view ->
                    runCatching { windowManager.removeView(view) }
                }
            }
        }
    }

    private fun ensureLifecycleStarted() {
        if (!started) {
            serviceLifecycleOwner.onCreate()
            started = true
        }
    }

    private fun createComposeOverlay(stateFlow: MutableStateFlow<OverlayType>): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setContent {
                com.aman.bastion.ui.theme.BastionTheme {
                    RenderOverlay(stateFlow.collectAsState())
                }
            }
        }
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun showBoundedOverlay(key: String, type: OverlayType.FeatureBlock) {
        overlayStates.remove(key)
        activeOverlays.remove(key)?.let { composeView ->
            runCatching { windowManager.removeView(composeView) }
        }
        activeBoundedOverlays.remove(key)?.forEach { view ->
            runCatching { windowManager.removeView(view) }
        }

        val views = type.boundsInScreen
            .filter { it.width() > 0 && it.height() > 0 }
            .take(5)
            .map { rect ->
                View(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    alpha = 1f
                }.also { view ->
                    windowManager.addView(view, buildBoundedParams(rect))
                }
            }

        if (views.isNotEmpty()) {
            activeBoundedOverlays[key] = views
        }
    }

    private fun buildBoundedParams(rect: Rect): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        return WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }
    }

    @Composable
    private fun RenderOverlay(state: State<OverlayType>) {
        when (val overlay = state.value) {
            is OverlayType.FeatureBlock -> FeatureBlockOverlayContent(overlay)
            is OverlayType.SoftBlock -> SoftBlockOverlayContent(overlay)
            is OverlayType.AppBlock -> AppBlockOverlayContent(overlay)
            OverlayType.AntiCircumvention -> {
                AntiCircumventionOverlayContent(
                    lockedUntilMs = currentLockUntilMs,
                    onCooldownExpired = { hideOverlay("anticircumvention") }
                )
            }
        }
    }

    @Composable
    private fun FeatureBlockOverlayContent(type: OverlayType.FeatureBlock) {
        if (type.mode == FeatureBlockMode.BOUNDED) {
            Box(modifier = Modifier.fillMaxSize())
            return
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "BASTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = BastionColors.AccentAmber,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${type.featureName} Blocked",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (type.allowedAction != null) {
                        "You blocked this. Your DMs are still open."
                    } else {
                        "You blocked this."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = BastionColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = ::exitToHome) {
                    Text(
                        text = "Go Home",
                        color = BastionColors.TextPrimary
                    )
                }
            }
        }
    }

    @Composable
    private fun AppBlockOverlayContent(type: OverlayType.AppBlock) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0505)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                if (type.lockedUntilMs > 0L) {
                    val remaining by produceState(
                        initialValue = type.lockedUntilMs - System.currentTimeMillis(),
                        key1 = type.lockedUntilMs
                    ) {
                        while (value > 0) {
                            delay(1_000)
                            value = type.lockedUntilMs - System.currentTimeMillis()
                        }
                    }

                    Text(
                        text = formatCountdown(remaining.coerceAtLeast(0L)),
                        style = MaterialTheme.typography.displayLarge,
                        color = BastionColors.AccentDanger,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Text(
                    text = type.appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You set this lock. Come back later.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BastionColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = ::exitToHome) {
                    Text(
                        text = "Go Home",
                        color = BastionColors.TextPrimary
                    )
                }
            }
        }
    }

    @Composable
    private fun SoftBlockOverlayContent(type: OverlayType.SoftBlock) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xED060606)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "BASTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = BastionColors.AccentAmber,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = type.appLabel,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = type.blockNote ?: "This app has a soft block.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BastionColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = unlockPrompt(type.unlockCondition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BastionColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BastionColors.AccentAmber)
                        .clickable { hideOverlay("block_${type.packageName}") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unlockCta(type.unlockCondition),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = ::exitToHome) {
                    Text(
                        text = "Go Home",
                        color = BastionColors.TextPrimary
                    )
                }
            }
        }
    }

    @Composable
    private fun AntiCircumventionOverlayContent(
        lockedUntilMs: Long,
        onCooldownExpired: () -> Unit
    ) {
        var remaining by remember(lockedUntilMs) {
            mutableLongStateOf(maxOf(0L, lockedUntilMs - System.currentTimeMillis()))
        }

        LaunchedEffect(lockedUntilMs) {
            while (remaining > 0) {
                delay(1_000)
                remaining = maxOf(0L, lockedUntilMs - System.currentTimeMillis())
            }
            if (lockedUntilMs > 0L) {
                onCooldownExpired()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "\uD83D\uDD12",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "STRICT MODE ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = BastionColors.AccentDanger,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "You locked this yourself.",
                    style = MaterialTheme.typography.titleLarge,
                    color = BastionColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "These settings are protected until your hardcore lock expires. This is working as intended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BastionColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                if (lockedUntilMs > 0L && remaining > 0L) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = "LOCK EXPIRES IN",
                        style = MaterialTheme.typography.labelSmall,
                        color = BastionColors.TextMuted,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatCountdown(remaining),
                        style = MaterialTheme.typography.displayMedium,
                        color = BastionColors.AccentDanger,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(48.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BastionColors.SurfaceElevated)
                        .border(
                            1.dp,
                            BastionColors.BorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Return to Bastion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BastionColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = ::exitToHome) {
                    Text(
                        text = "Go Home",
                        color = BastionColors.TextPrimary
                    )
                }
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            ).toString()
        }.getOrElse { packageName }
    }

    private fun formatCountdown(remainingMs: Long): String {
        val totalSecs = remainingMs.coerceAtLeast(0L) / 1_000
        val hours = totalSecs / 3_600
        val minutes = (totalSecs % 3_600) / 60
        val seconds = totalSecs % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun exitToHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun unlockPrompt(condition: UnlockCondition): String = when (condition) {
        UnlockCondition.COMPLETE_TASK -> "Complete your task before continuing."
        UnlockCondition.WAIT_10_MIN -> "Wait 10 minutes before continuing."
        UnlockCondition.DEEP_BREATHS -> "Take 5 deep breaths before continuing."
        UnlockCondition.STEP_GOAL -> "Hit your step goal before continuing."
    }

    private fun unlockCta(condition: UnlockCondition): String = when (condition) {
        UnlockCondition.COMPLETE_TASK -> "Mark Complete"
        UnlockCondition.WAIT_10_MIN -> "I Waited"
        UnlockCondition.DEEP_BREATHS -> "Done"
        UnlockCondition.STEP_GOAL -> "Done"
    }
}

enum class FeatureBlockMode {
    FULLSCREEN,
    BOUNDED
}

sealed class OverlayType {
    data class FeatureBlock(
        val packageName: String,
        val featureName: String,
        val mode: FeatureBlockMode = FeatureBlockMode.FULLSCREEN,
        val boundsInScreen: List<Rect> = emptyList(),
        val allowedAction: String? = null
    ) : OverlayType()

    data class SoftBlock(
        val packageName: String,
        val appLabel: String,
        val unlockCondition: UnlockCondition,
        val blockNote: String?
    ) : OverlayType()

    data class AppBlock(
        val packageName: String,
        val appLabel: String,
        val lockedUntilMs: Long,
        val reason: String
    ) : OverlayType()

    data object AntiCircumvention : OverlayType()
}
