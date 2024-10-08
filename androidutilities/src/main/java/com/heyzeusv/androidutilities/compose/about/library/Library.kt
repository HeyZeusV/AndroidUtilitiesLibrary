@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.heyzeusv.androidutilities.compose.about.library

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.heyzeusv.androidutilities.R
import com.heyzeusv.androidutilities.compose.about.library.LibrarySharedContentKeyPrefix.*
import com.heyzeusv.androidutilities.compose.about.overview.AboutOverview
import com.heyzeusv.androidutilities.compose.pagerindicator.HorizontalPagerIndicator
import com.heyzeusv.androidutilities.compose.about.formatContent
import com.heyzeusv.androidutilities.compose.about.ifNullOrBlank
import com.heyzeusv.androidutilities.compose.util.sRes
import com.mikepenz.aboutlibraries.entity.Library

/**
 *  Full screen Composable that displays full library information including its description and full
 *  license.
 *
 *  @param animatedContentScope Scope used to animate shared elements between screens.
 *  @param backOnClick Action to navigate back to [AboutOverview].
 *  @param library The [Library] to be shown.
 *  @param colors Colors to be used.
 *  @param padding Padding to be used.
 *  @param extras Additional values to be used.
 *  @param textStyles Text styles to be used.
 */
@Composable
internal fun SharedTransitionScope.AboutLibrary(
    animatedContentScope: AnimatedContentScope,
    backOnClick: () -> Unit,
    library: Library,
    colors: LibraryColors,
    padding: LibraryPadding,
    extras: LibraryExtras,
    textStyles: LibraryTextStyles,
) {
    LibraryDetails(
        animatedContentScope = animatedContentScope,
        modifier = Modifier.fillMaxSize(),
        isFullscreen = true,
        actionOnClick = backOnClick,
        library = library,
        colors = colors,
        padding = padding,
        extras = extras,
        textStyles = textStyles,
    )
}

/**
 *  Composable that is used on both [AboutOverview] and [AboutLibrary].
 *
 *  On [AboutOverview], it is displayed as an item in its lazy list to display all libraries.
 *  On [AboutLibrary], it is displayed as a full screen that displays a single library and all its
 *  content.
 *
 *  @param animatedContentScope Scope used to animate shared elements between screens.
 *  @param modifier Modifier to be applied to the layout corresponding to the surface.
 *  @param isFullscreen Determines if all information should be displayed.
 *  @param actionOnClick Action to navigate between [AboutOverview] and [AboutLibrary].
 *  @param library The [Library] to be shown.
 *  @param colors Colors to be used.
 *  @param padding Padding to be used.
 *  @param extras Additional values to be used.
 *  @param textStyles Text styles to be used.
 */
@Composable
internal fun SharedTransitionScope.LibraryDetails(
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean,
    actionOnClick: () -> Unit,
    library: Library,
    colors: LibraryColors,
    padding: LibraryPadding,
    extras: LibraryExtras,
    textStyles: LibraryTextStyles,
) {
    val developers = library.developers.map { it.name }.joinToString(separator = ", ")
        .ifBlank { sRes(R.string.library_developer_empty) }
    val description = library.description.ifNullOrBlank(sRes(R.string.library_description_empty))
    val version = library.artifactVersion.ifNullOrBlank(sRes(R.string.library_version_empty))
    val license = library.licenses.firstOrNull()
    val licenseContent = license?.licenseContent.ifNullOrBlank(sRes(R.string.library_license_empty))
    val licenseName = license?.name.ifNullOrBlank(sRes(R.string.library_license_empty))

    Surface(
        modifier = modifier
            .padding(padding.outerPadding)
            .fillMaxWidth()
            .sharedElement(
                state = librarySCS(SURFACE, library.uniqueId),
                animatedVisibilityScope = animatedContentScope,
            ),
        shape = extras.shape,
        color = colors.backgroundColor,
        border = BorderStroke(width = extras.borderWidth, color = colors.borderColor),
    ) {
        Column(
            modifier = Modifier.padding(padding.innerPadding),
            verticalArrangement = Arrangement.spacedBy(extras.contentSpacedBy),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = library.name,
                    modifier = Modifier
                        .padding(padding.namePadding)
                        .weight(1f)
                        .sharedElement(
                            state = librarySCS(NAME, library.uniqueId),
                            animatedVisibilityScope = animatedContentScope,
                        ),
                    style = textStyles.nameStyle,
                )
                Icon(
                    painter = extras.actionIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(padding.actionIconPadding)
                        .size(extras.actionIconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                bounded = false,
                                radius = LibraryDefaults.ActionIconRippleRadius,
                            ),
                            onClick = actionOnClick,
                        ),
                    tint = colors.contentColor,
                )
            }
            Text(
                text = developers,
                modifier = Modifier
                    .padding(padding.developerPadding)
                    .fillMaxWidth()
                    .sharedElement(
                        state = librarySCS(DEVELOPER, library.uniqueId),
                        animatedVisibilityScope = animatedContentScope,
                    ),
                style = textStyles.developerStyle,
            )
            if (isFullscreen) {
                val pagerState = rememberPagerState(pageCount = { 2 })
                val scaleSpring = spring<Float>(stiffness = Spring.StiffnessMedium)

                with(animatedContentScope) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .animateEnterExit(
                                enter = scaleIn(scaleSpring),
                                exit = scaleOut(scaleSpring),
                            ),
                        verticalArrangement = Arrangement.spacedBy(extras.contentSpacedBy),
                    ) {
                        HorizontalDivider(
                            thickness = extras.dividerThickness,
                            color = colors.dividerColor,
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val body: String = if (page == 0) description else licenseContent
                            Text(
                                text = body.formatContent(),
                                modifier = Modifier
                                    .padding(padding.bodyPadding)
                                    .fillMaxSize()
                                    .skipToLookaheadSize()
                                    .verticalScroll(rememberScrollState()),
                                color = colors.contentColor,
                                style = textStyles.bodyStyle,
                            )
                        }
                        HorizontalPagerIndicator(
                            pagerState = pagerState,
                            pageCount = 2,
                            modifier = Modifier
                                .padding(padding.pageIndicatorPadding)
                                .align(Alignment.CenterHorizontally),
                            activeColor = colors.pagerIndicatorColors.activeColor,
                            inactiveColor = colors.pagerIndicatorColors.inactiveColor,
                            indicatorWidth = extras.pagerIndicatorExtras.indicatorWidth,
                            indicatorHeight = extras.pagerIndicatorExtras.indicatorHeight,
                            indicatorSpacing = extras.pagerIndicatorExtras.indicatorSpacing,
                            indicatorShape = extras.pagerIndicatorExtras.indicatorShape,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.sharedElement(
                    state = librarySCS(BOTTOM_DIVIDER, library.uniqueId),
                    animatedVisibilityScope = animatedContentScope,
                    zIndexInOverlay = 2f,
                ),
                thickness = extras.dividerThickness,
                color = colors.dividerColor,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedElement(
                        state = librarySCS(FOOTER, library.uniqueId),
                        animatedVisibilityScope = animatedContentScope,
                        zIndexInOverlay = 2f,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = licenseName,
                    modifier = Modifier.padding(padding.footerPadding),
                    color = colors.contentColor,
                    style = textStyles.footerStyle,
                )
                Text(
                    text = version,
                    modifier = Modifier.padding(padding.footerPadding),
                    color = colors.contentColor,
                    style = textStyles.footerStyle,
                )
            }
        }
    }
}