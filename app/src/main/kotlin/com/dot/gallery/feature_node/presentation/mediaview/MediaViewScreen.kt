/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.core.BottomSheet
import com.composables.core.SheetDetent.Companion.FullyExpanded
import com.composables.core.rememberBottomSheetState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.Constants.DEFAULT_LOW_VELOCITY_SWIPE_DURATION
import com.dot.gallery.core.Constants.DEFAULT_TOP_BAR_ANIMATION_DURATION
import com.dot.gallery.core.Constants.Target.TARGET_TRASH
import com.dot.gallery.core.Settings.Misc.rememberAutoHideOnVideoPlay
import com.dot.gallery.core.Settings.Misc.rememberDateHeaderFormat
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaState
import com.dot.gallery.feature_node.domain.model.Vault
import com.dot.gallery.feature_node.domain.model.VaultState
import com.dot.gallery.feature_node.domain.use_case.MediaHandleUseCase
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewActions2
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewAppBar
import com.dot.gallery.feature_node.presentation.mediaview.components.MediaViewDetails
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.components.video.VideoPlayerController
import com.dot.gallery.feature_node.presentation.util.FullBrightnessWindow
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.BOTTOM_BAR_HEIGHT
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.BOTTOM_BAR_HEIGHT_SLIM
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.FullyExpanded
import com.dot.gallery.feature_node.presentation.util.ViewScreenConstants.ImageOnly
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.normalize
import com.dot.gallery.feature_node.presentation.util.printWarning
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import com.dot.gallery.feature_node.presentation.util.setHdrMode
import com.dot.gallery.feature_node.presentation.util.toggleSystemBars
import com.dot.gallery.ui.theme.BlackScrim
import com.github.panpf.sketch.BitmapImage
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.sketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@Composable
fun <T> rememberedDerivedState(
    key: Any? = Unit,
    block: @DisallowComposableCalls () -> T
): State<T> {
    return remember(key) {
        derivedStateOf(block)
    }
}

@Composable
fun <T> rememberedDerivedState(
    vararg keys: Any? = arrayOf(Unit),
    block: @DisallowComposableCalls () -> T
): State<T> {
    return remember(keys) {
        derivedStateOf(block)
    }
}

@Composable
fun <T : Media> MediaViewScreen(
    navigateUp: () -> Unit,
    toggleRotate: () -> Unit,
    paddingValues: PaddingValues,
    isStandalone: Boolean = false,
    mediaId: Long,
    target: String? = null,
    mediaState: State<MediaState<out T>>,
    albumsState: State<AlbumState> = remember { mutableStateOf(AlbumState()) },
    handler: MediaHandleUseCase?,
    vaultState: VaultState,
    addMedia: (Vault, T) -> Unit,
    restoreMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    deleteMedia: ((Vault, T, () -> Unit) -> Unit)? = null,
    currentVault: Vault? = null,
    navigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val windowInsetsController = rememberWindowInsetsController()

    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val initialPage by rememberedDerivedState(mediaId, mediaState.value) {
        var lastMediaPosition = mediaState.value.media.indexOfFirst { it.id == mediaId }
        if (currentPage != 0) {
            lastMediaPosition = currentPage
        }
        lastMediaPosition.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        initialPageOffsetFraction = 0f,
        pageCount = mediaState.value.media::size
    )
    LaunchedEffect(initialPage) {
        if (initialPage != currentPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    val currentMedia by rememberedDerivedState(mediaState.value) {
        mediaState.value.media.getOrNull(
            pagerState.currentPage
        )
    }

    val currentDateFormat by rememberDateHeaderFormat()

    val currentDate by rememberedDerivedState {
        currentMedia?.definedTimestamp?.getDate(currentDateFormat) ?: ""
    }
    val playWhenReady by rememberedDerivedState { currentMedia?.isVideo == true }
    val isReadOnly by rememberedDerivedState { currentMedia?.readUriOnly == true }
    val showInfo by rememberedDerivedState { currentMedia?.trashed == 0 && !isReadOnly }

    var showUI by rememberSaveable { mutableStateOf(true) }
    var lastIndex by remember { mutableIntStateOf(-1) }

    BackHandler(!showUI) {
        windowInsetsController.toggleSystemBars(show = true)
        navigateUp()
    }
    var sheetHeightDp by remember { mutableStateOf(1.dp) }
    var lastSheetHeightDp by rememberSaveable { mutableFloatStateOf(0f) }
    val sheetState = rememberBottomSheetState(
        initialDetent = ImageOnly,
        detents = listOf(ImageOnly, FullyExpanded { sheetHeightDp = it })
    )

    val userScrollEnabled by rememberedDerivedState { sheetState.currentDetent != FullyExpanded }

    var storedNormalizationTarget by rememberSaveable(
        mediaState.value,
        currentPage,
        lastSheetHeightDp
    ) { mutableFloatStateOf(0f) }
    val isNormalizationTargetSet by rememberedDerivedState(
        mediaState.value,
        storedNormalizationTarget
    ) {
        lastSheetHeightDp.dp == sheetHeightDp
                && currentPage == pagerState.currentPage
                && storedNormalizationTarget > 0f
                && storedNormalizationTarget < 1f
                && !((storedNormalizationTarget == 0f || storedNormalizationTarget > 0.9f) && sheetState.progress == 1f)
    }


    val normalizationTarget by rememberedDerivedState(
        mediaState.value,
        currentPage,
        sheetState.offset,
        storedNormalizationTarget
    ) {
        if (isNormalizationTargetSet) {
            storedNormalizationTarget
        } else {
            val newOffset = sheetState.offset
            if (newOffset > 0f && newOffset < 1f) {
                storedNormalizationTarget = newOffset
                newOffset
            } else storedNormalizationTarget
        }
    }

    LaunchedEffect(sheetHeightDp) {
        if (lastSheetHeightDp.dp != sheetHeightDp) {
            lastSheetHeightDp = sheetHeightDp.value
        }
    }

    val normalizedOffset by rememberedDerivedState(
        mediaState.value,
        sheetState,
        normalizationTarget,
        isNormalizationTargetSet
    ) {
        if (isNormalizationTargetSet) {
            sheetState.offset.normalize(minValue = normalizationTarget)
        } else 0f
    }

    LaunchedEffect(mediaState.value) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (lastIndex != -1) {
                val newIndex = lastIndex.coerceAtMost(pagerState.pageCount - 1)
                pagerState.scrollToPage(newIndex)
                lastIndex = -1
            }

            if (!mediaState.value.isLoading && mediaState.value.media.isEmpty() && !isStandalone) {
                windowInsetsController.toggleSystemBars(show = true)
                navigateUp()
            }
            if (!mediaState.value.isLoading) {
                currentPage = page
            }
        }
    }

    // set HDR Gain map
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        LaunchedEffect(mediaState.value) {
            snapshotFlow { pagerState.currentPage }.collectLatest {
                printWarning("Trying to set HDR mode for page $it")
                if (currentMedia?.isImage == true) {
                    val request = ImageRequest(context, currentMedia?.getUri().toString()) {
                        setExtra(
                            key = "mediaKey",
                            value = currentMedia.toString(),
                        )
                        setExtra(
                            key = "realMimeType",
                            value = currentMedia?.mimeType,
                        )
                    }
                    val result = withContext(Dispatchers.IO) {
                        context.sketch.execute(request)
                    }
                    (result.image as? BitmapImage)?.bitmap?.let { bitmap ->
                        val hasGainmap = bitmap.hasGainmap()
                        context.setHdrMode(hasGainmap)
                        printWarning("Setting HDR Mode to $hasGainmap")
                    } ?: printWarning("Resulting image null")
                } else {
                    context.setHdrMode(false)
                    printWarning("Not an image, skipping")
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                printWarning("Disposing HDR Mode")
                context.setHdrMode(false)
            }
        }
    }

    val bottomPadding = remember { paddingValues.calculateBottomPadding() }

    FullBrightnessWindow {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY =
                            -((sheetHeightDp - BOTTOM_BAR_HEIGHT - bottomPadding).toPx() * normalizedOffset)
                    },
                userScrollEnabled = userScrollEnabled,
                state = pagerState,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = tween(
                        easing = LinearOutSlowInEasing,
                        durationMillis = DEFAULT_LOW_VELOCITY_SWIPE_DURATION
                    ),
                    snapPositionalThreshold = 0.3f
                ),
                key = { index ->
                    mediaState.value.media.getOrNull(index) ?: "empty"
                },
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1
            ) { index ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val media by rememberedDerivedState { mediaState.value.media.getOrNull(index) }
                    AnimatedVisibility(
                        visible = media != null,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        MediaPreviewComponent(
                            media = media,
                            uiEnabled = showUI,
                            playWhenReady = playWhenReady,
                            onSwipeDown = {
                                windowInsetsController.toggleSystemBars(show = true)
                                navigateUp()
                            },
                            onItemClick = {
                                if (sheetState.currentDetent == ImageOnly) {
                                    showUI = !showUI
                                    windowInsetsController.toggleSystemBars(showUI)
                                }
                            }
                        ) { player, isPlaying, currentTime, totalTime, buffer, frameRate ->
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val hideUiOnPlay by rememberAutoHideOnVideoPlay()
                                LaunchedEffect(isPlaying.value, hideUiOnPlay) {
                                    if (isPlaying.value && showUI && hideUiOnPlay) {
                                        delay(2.seconds)
                                        showUI = false
                                        windowInsetsController.toggleSystemBars(false)
                                    }
                                }
                                val width =
                                    remember(context) { context.resources.displayMetrics.widthPixels }
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = width / 1.5f
                                        }
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onDoubleClick = {
                                                scope.launch {
                                                    currentTime.longValue += 10 * 1000
                                                    player.seekTo(currentTime.longValue)
                                                    delay(100)
                                                    player.play()
                                                }
                                            },
                                            onClick = {
                                                if (sheetState.currentDetent == ImageOnly) {
                                                    showUI = !showUI
                                                    windowInsetsController.toggleSystemBars(showUI)
                                                }
                                            }
                                        )
                                )

                                Spacer(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = -width / 1.5f
                                        }
                                        .align(Alignment.TopStart)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onDoubleClick = {
                                                scope.launch {
                                                    currentTime.longValue -= 10 * 1000
                                                    player.seekTo(currentTime.longValue)
                                                    delay(100)
                                                    player.play()
                                                }
                                            },
                                            onClick = {
                                                if (sheetState.currentDetent == ImageOnly) {
                                                    showUI = !showUI
                                                    windowInsetsController.toggleSystemBars(
                                                        showUI
                                                    )
                                                }
                                            }
                                        )
                                )

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showUI,
                                    enter = enterAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                                    exit = exitAnimation(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    VideoPlayerController(
                                        paddingValues = paddingValues,
                                        player = player,
                                        isPlaying = isPlaying,
                                        currentTime = currentTime,
                                        totalTime = totalTime,
                                        buffer = buffer,
                                        toggleRotate = toggleRotate,
                                        frameRate = frameRate
                                    )
                                }
                            }
                        }
                    }
                }
            }
            MediaViewAppBar(
                showUI = showUI,
                showInfo = showInfo,
                showDate = remember(currentMedia) {
                    currentMedia?.timestamp != 0L
                },
                currentDate = currentDate,
                paddingValues = paddingValues,
                onShowInfo = {
                    scope.launch {
                        if (showUI) {
                            if (sheetState.currentDetent == ImageOnly) {
                                sheetState.animateTo(FullyExpanded)
                            } else {
                                sheetState.animateTo(ImageOnly)
                            }
                        }
                    }
                },
                onGoBack = {
                    scope.launch {
                        if (sheetState.currentDetent == FullyExpanded) {
                            sheetState.animateTo(ImageOnly)
                        } else {
                            navigateUp()
                        }
                    }
                }
            )
            LaunchedEffect(showUI) {
                if (!showUI && (sheetState.currentDetent == FullyExpanded || sheetState.targetDetent == FullyExpanded)) {
                    sheetState.animateTo(ImageOnly)
                }
            }
            BackHandler(sheetState.currentDetent == FullyExpanded) {
                scope.launch {
                    sheetState.animateTo(ImageOnly)
                }
            }
            val bottomSheetAlpha by animateFloatAsState(
                targetValue = if (showUI) 1f else 0f,
                animationSpec = tween(DEFAULT_TOP_BAR_ANIMATION_DURATION),
                label = "MediaViewActionsAlpha"
            )
            BottomSheet(
                state = sheetState,
                enabled = showUI && target != TARGET_TRASH,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = bottomSheetAlpha
                    }
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val actionsAlpha by animateFloatAsState(
                        targetValue = 1f - normalizedOffset,
                        label = "MediaViewActions2Alpha"
                    )
                    AnimatedVisibility(
                        visible = currentMedia != null,
                        enter = enterAnimation,
                        exit = exitAnimation
                    ) {
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = actionsAlpha
                                    translationY =
                                        BOTTOM_BAR_HEIGHT_SLIM.toPx() * normalizedOffset
                                }
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, BlackScrim)
                                    )
                                )
                                .padding(
                                    bottom = bottomPadding
                                )
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            MediaViewActions2(
                                currentIndex = pagerState.currentPage,
                                currentMedia = currentMedia,
                                handler = handler,
                                onDeleteMedia = { lastIndex = it },
                                showDeleteButton = !isReadOnly,
                                enabled = showUI,
                                deleteMedia = deleteMedia,
                                currentVault = currentVault
                            )
                        }
                    }

                    MediaViewDetails(
                        albumsState = albumsState,
                        vaultState = vaultState,
                        currentMedia = currentMedia,
                        handler = handler,
                        addMediaToVault = addMedia,
                        restoreMedia = restoreMedia,
                        currentVault = currentVault,
                        navigate = navigate,
                    )
                }
            }
        }
    }

}