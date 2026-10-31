package com.gallery.android.ui.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gallery.android.domain.model.Album
import com.gallery.android.ui.components.ApertureHeader
import com.gallery.android.ui.components.FrameCorners
import com.gallery.android.ui.components.GalleryMotion
import com.gallery.android.ui.components.HairlineIconButton
import com.gallery.android.ui.components.SectionKicker
import com.gallery.android.ui.components.scaleOnPress
import java.util.Locale

@Composable
fun AlbumsScreen(
    onAlbumClick: (Album) -> Unit,
    onFavoritesClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onSafeClick: () -> Unit = {},
    onCollageClick: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val generated = remember(albums) { albums.filter { !it.isUserCreated } }
    val mine = remember(albums) { albums.filter { it.isUserCreated } }
    val pinned = remember(generated) {
        val camera = generated.find { it.name.contains("camera", ignoreCase = true) }
        val shots = generated.find { it.name.contains("screenshot", ignoreCase = true) }
        listOfNotNull(camera, shots).ifEmpty { generated.take(2) }
    }
    val restGenerated = remember(generated, pinned) {
        generated.filterNot { album -> pinned.any { it.bucketId == album.bucketId && it.id == album.id } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ApertureHeader(
            title = "Albums",
            subtitle = "${albums.size} albums",
            actions = {
                HairlineIconButton(
                    onClick = { showCreateDialog = true },
                    icon = Icons.Outlined.Add,
                    contentDescription = "New album",
                )
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = pinned,
                key = { "pinned-${it.bucketId}-${it.id}" },
            ) { album ->
                PinnedAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = GalleryMotion.Medium,
                        placementSpec = GalleryMotion.Placement,
                        fadeOutSpec = GalleryMotion.Fast,
                    ),
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionKicker("Generated", modifier = Modifier.padding(top = 14.dp, bottom = 0.dp))
            }

            items(restGenerated, key = { "gen-${it.bucketId}" }) { album ->
                CompactAlbumRow(album = album, onClick = { onAlbumClick(album) })
            }
            item {
                CompactUtilityRow(
                    title = "Favourites",
                    subtitle = "Starred",
                    onClick = onFavoritesClick,
                )
            }
            item {
                CompactUtilityRow(
                    title = "Recently Deleted",
                    subtitle = "30d",
                    onClick = onTrashClick,
                )
            }
            item {
                CompactUtilityRow(
                    title = "Private Safe",
                    subtitle = "Encrypted",
                    onClick = onSafeClick,
                )
            }
            item {
                CompactUtilityRow(
                    title = "Collage",
                    subtitle = "Layout",
                    onClick = onCollageClick,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionKicker("Mine", modifier = Modifier.padding(top = 14.dp, bottom = 0.dp))
            }

            items(mine, key = { "mine-${it.id}" }) { album ->
                CompactAlbumRow(album = album, onClick = { onAlbumClick(album) })
            }
            item {
                NewAlbumTile(onClick = { showCreateDialog = true })
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onConfirm = { name ->
                viewModel.createAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun PinnedAlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scaleOnPress(0.98f)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (album.coverUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.coverUri)
                        .crossfade(180)
                        .build(),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoAlbum,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FrameCorners()
        }
        Text(
            text = album.name.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.04.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 8.dp),
        )
        Text(
            text = "${album.mediaCount} items".uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.1.em,
            modifier = Modifier.padding(start = 2.dp, top = 1.dp),
        )
    }
}

@Composable
private fun CompactAlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (album.coverUri != null) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.mediaCount} items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.1.em,
            )
        }
    }
}

@Composable
private fun CompactUtilityRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.1.em,
            )
        }
    }
}

@Composable
private fun NewAlbumTile(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "NEW ALBUM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun CreateAlbumDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Album") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Album name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
