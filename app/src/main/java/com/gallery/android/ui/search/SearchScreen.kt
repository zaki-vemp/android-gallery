package com.gallery.android.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.MediaCategory

@OptIn(ExperimentalMaterial3Api::class)
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

    val displayedResults = when {
        selectedCategory != null -> categoryResults
        query.isNotBlank() -> results
        else -> emptyList()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search photos, text in images…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        AnimatedVisibility(visible = query.isBlank(), enter = fadeIn(), exit = fadeOut()) {
            Column {
                Text(
                    text = "Browse by Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.categories) { category ->
                        CategoryChip(
                            category = category,
                            isSelected = selectedCategory == category,
                            onClick = {
                                viewModel.selectCategory(
                                    if (selectedCategory == category) null else category
                                )
                            },
                        )
                    }
                }
            }
        }

        if (selectedCategory != null && query.isBlank()) {
            Text(
                text = selectedCategory!!.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                displayedResults.isEmpty() && (query.isNotBlank() || selectedCategory != null) -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isNotBlank()) "No results for \"$query\""
                            else "No ${selectedCategory?.displayName} found",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                displayedResults.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(displayedResults, key = { it.id }) { media ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(media.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = media.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onMediaClick(media.id) },
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Search photos, videos and text in images",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: MediaCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val icon = when (category) {
        MediaCategory.PEOPLE -> Icons.Default.Person
        MediaCategory.DOCUMENTS -> Icons.Default.Description
        MediaCategory.SCREENSHOTS -> Icons.Default.Smartphone
        MediaCategory.FOOD -> Icons.Default.Restaurant
        MediaCategory.TRAVEL -> Icons.Default.FlightTakeoff
        MediaCategory.VIDEOS -> Icons.Default.Videocam
        MediaCategory.FAVORITES -> Icons.Default.Favorite
        MediaCategory.OTHERS -> Icons.Default.Photo
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(category.displayName) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
    )
}
