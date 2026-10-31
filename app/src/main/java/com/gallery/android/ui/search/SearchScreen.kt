package com.gallery.android.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.ui.components.ApertureHeader
import com.gallery.android.ui.components.GalleryEmptyState
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.components.HairlineChip
import com.gallery.android.ui.components.MediaThumbnail
import com.gallery.android.ui.components.SectionKicker
import java.util.Locale

private val SearchChips = listOf(
    "People" to MediaCategory.PEOPLE,
    "Places" to MediaCategory.TRAVEL,
    "Screenshots" to MediaCategory.SCREENSHOTS,
    "Videos" to MediaCategory.VIDEOS,
    "Selfies" to MediaCategory.PEOPLE,
    "Documents" to MediaCategory.DOCUMENTS,
)

private val PeopleShortcuts = listOf("Aarav", "Meera", "Dev", "Sana")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onMediaClick: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val categoryResults by viewModel.categoryResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val moments by viewModel.moments.collectAsStateWithLifecycle()
    val pendingIndexCount by viewModel.pendingIndexCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.ensureIndexing() }

    val browsingResults = query.isNotBlank() || selectedCategory != null
    val displayedResults = when {
        selectedCategory != null -> categoryResults
        query.isNotBlank() -> results
        else -> emptyList()
    }

    BackHandler(enabled = browsingResults) {
        viewModel.clearSearch()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ApertureHeader(
            title = if (selectedCategory != null) selectedCategory!!.displayName else "Search",
            subtitle = if (pendingIndexCount > 0) {
                "Indexing $pendingIndexCount photos"
            } else {
                "On-device vision + text"
            },
            actions = {
                if (browsingResults) {
                    HairlineChip(onClick = viewModel::clearSearch) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "BACK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(42.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = selectedCategory?.displayName ?: "my wife beside the tree",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            if (browsingResults) {
                Icon(
                    Icons.Outlined.Clear,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = viewModel::clearSearch),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        ) {
            SearchChips.forEach { (label, category) ->
                val selected = selectedCategory == category
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.08.em,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            RectangleShape,
                        )
                        .clickable { viewModel.selectCategory(category) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                displayedResults.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(displayedResults, key = { it.id }) { media ->
                            MediaThumbnail(
                                media = media,
                                isSelected = false,
                                onClick = { onMediaClick(media.id) },
                                cornerRadius = 8,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = GalleryMotion.Medium,
                                    placementSpec = GalleryMotion.Placement,
                                    fadeOutSpec = GalleryMotion.Fast,
                                ),
                            )
                        }
                    }
                }
                browsingResults -> {
                    GalleryEmptyState(
                        icon = Icons.Outlined.Search,
                        title = if (query.isNotBlank()) "No results for \"$query\"" else "No matching items",
                        subtitle = "Tap Back or a chip again to return",
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                SectionKicker("People", modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(PeopleShortcuts) { name ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(58.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                                    .clickable { viewModel.onQueryChange(name) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Person,
                                                    contentDescription = name,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                SectionKicker("Moments")
                            }
                        }
                        items(moments.take(12), key = { it.id }) { media ->
                            MediaThumbnail(
                                media = media,
                                isSelected = false,
                                onClick = { onMediaClick(media.id) },
                                cornerRadius = 8,
                            )
                        }
                    }
                }
            }
        }
    }
}
