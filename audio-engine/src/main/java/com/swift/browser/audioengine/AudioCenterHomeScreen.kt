package com.swift.browser.audioengine

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swift.browser.audioengine.model.AudioAlbum
import com.swift.browser.audioengine.model.AudioArtist
import com.swift.browser.audioengine.model.AudioFolder
import com.swift.browser.audioengine.model.AudioPlaylist
import com.swift.browser.audioengine.model.AudioTrackItem
import com.swift.browser.audioengine.model.SortOption
import com.swift.browser.audioengine.online.OnlineMusicEngineScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCenterHomeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember(context) { AudioPlayerEngine.getInstance(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTrackForFullPlayer by remember { mutableStateOf<AudioTrackItem?>(null) }
    var showFullPlayer by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    // Sub-view detail navigation (e.g., inside Album, Artist, Folder, or Playlist)
    var detailTitle by remember { mutableStateOf<String?>(null) }
    var detailTracks by remember { mutableStateOf<List<AudioTrackItem>?>(null) }

    val localTracks by (engine as AudioPlayerEngine).localLibrary.collectAsState()
    val favorites by engine.favorites.collectAsState()
    val playlists by engine.playlists.collectAsState()
    val albums by engine.albums.collectAsState()
    val artists by engine.artists.collectAsState()
    val folders by engine.folders.collectAsState()
    val activeSortOption by engine.currentSortOption.collectAsState()

    // Dialog States
    var contextTrack by remember { mutableStateOf<AudioTrackItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrackItem?>(null) }
    var showRenameDialogForTrack by remember { mutableStateOf<AudioTrackItem?>(null) }
    var showDeleteDialogForTrack by remember { mutableStateOf<AudioTrackItem?>(null) }

    var newPlaylistNameInput by remember { mutableStateOf("") }
    var renameTrackNameInput by remember { mutableStateOf("") }

    // Dialog Renderers
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistNameInput.isNotBlank()) {
                            engine.createPlaylist(newPlaylistNameInput)
                            showCreatePlaylistDialog = false
                            newPlaylistNameInput = ""
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddToPlaylistDialogForTrack != null) {
        val targetTrack = showAddToPlaylistDialogForTrack!!
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialogForTrack = null },
            title = { Text("Add '${targetTrack.title}' to Playlist", fontWeight = FontWeight.Bold) },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists created yet. Create a playlist first.")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    engine.addTrackToPlaylist(pl.id, targetTrack)
                                    showAddToPlaylistDialogForTrack = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pl.name, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialogForTrack = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRenameDialogForTrack != null) {
        val targetTrack = showRenameDialogForTrack!!
        AlertDialog(
            onDismissRequest = { showRenameDialogForTrack = null },
            title = { Text("Rename Song", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameTrackNameInput,
                    onValueChange = { renameTrackNameInput = it },
                    label = { Text("Song Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameTrackNameInput.isNotBlank()) {
                            engine.renameAudio(targetTrack, renameTrackNameInput)
                            showRenameDialogForTrack = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForTrack = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialogForTrack != null) {
        val targetTrack = showDeleteDialogForTrack!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogForTrack = null },
            title = { Text("Delete Track", fontWeight = FontWeight.Bold) },
            text = { Text("Delete '${targetTrack.title}' from local storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        engine.deleteAudio(targetTrack)
                        showDeleteDialogForTrack = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForTrack = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Context Menu Bottom Sheet / Dialog for Track Actions
    if (contextTrack != null) {
        val tr = contextTrack!!
        ModalBottomSheet(
            onDismissRequest = { contextTrack = null },
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = tr.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tr.artist ?: "Unknown"} • ${tr.album ?: "Unknown Album"}",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = Color(0xFF334155))

                ListItem(
                    headlineContent = { Text("Play Now", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    modifier = Modifier.clickable {
                        engine.playTrack(tr, localTracks)
                        selectedTrackForFullPlayer = tr
                        contextTrack = null
                        showFullPlayer = true
                    }
                )
                ListItem(
                    headlineContent = { Text("Add to Queue", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Queue, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        contextTrack = null
                    }
                )
                ListItem(
                    headlineContent = { Text(if (tr.isFavorite) "Remove from Favorites" else "Add to Favorites", color = Color.White) },
                    leadingContent = {
                        Icon(
                            if (tr.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (tr.isFavorite) Color(0xFFF43F5E) else Color.White
                        )
                    },
                    modifier = Modifier.clickable {
                        engine.toggleFavorite(tr)
                        contextTrack = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Add to Playlist", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        val selected = tr
                        contextTrack = null
                        showAddToPlaylistDialogForTrack = selected
                    }
                )
                ListItem(
                    headlineContent = { Text("Share Track", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, tr.title)
                            putExtra(Intent.EXTRA_TEXT, "Check out ${tr.title} by ${tr.artist}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                        contextTrack = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Rename", color = Color.White) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                    modifier = Modifier.clickable {
                        val selected = tr
                        contextTrack = null
                        renameTrackNameInput = selected.title
                        showRenameDialogForTrack = selected
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete Track", color = Color.Red) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable {
                        val selected = tr
                        contextTrack = null
                        showDeleteDialogForTrack = selected
                    }
                )
            }
        }
    }

    if (showFullPlayer) {
        AudioPlayerScreen(
            track = selectedTrackForFullPlayer,
            onBack = { showFullPlayer = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (detailTitle != null) {
                            Text(detailTitle!!, color = Color.White, fontWeight = FontWeight.Bold)
                        } else if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search title, artist, album...", color = Color.Gray) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Music Center", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (detailTitle != null) {
                                    detailTitle = null
                                    detailTracks = null
                                } else if (isSearchActive) {
                                    isSearchActive = false
                                    searchQuery = ""
                                } else {
                                    onBack()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (detailTitle == null) {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    formatSortName(option),
                                                    color = if (option == activeSortOption) Color(0xFF38BDF8) else Color.White
                                                )
                                            },
                                            onClick = {
                                                engine.setSortOption(option)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { engine.scanLocalAudio() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Scan Library", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
            },
            bottomBar = {
                MiniOfflineAudioPlayer(
                    onExpandPlayer = { showFullPlayer = true }
                )
            },
            containerColor = Color(0xFF0F172A),
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (detailTitle != null && detailTracks != null) {
                    // Category Track Detail Sub-view
                    TrackListView(
                        tracks = filterTracks(detailTracks!!, searchQuery),
                        onTrackClick = { track ->
                            engine.playTrack(track, detailTracks!!)
                            selectedTrackForFullPlayer = track
                            showFullPlayer = true
                        },
                        onLongClick = { track -> contextTrack = track }
                    )
                } else {
                    // Scrollable Category Tabs Header
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color.White,
                        edgePadding = 12.dp
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Online Stream") },
                            icon = { Icon(Icons.Default.Public, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Songs (${localTracks.size})") },
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Favorites (${favorites.size})") },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Playlists (${playlists.size})") },
                            icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text("Folders (${folders.size})") },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            text = { Text("Albums (${albums.size})") },
                            icon = { Icon(Icons.Default.Album, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedTab == 6,
                            onClick = { selectedTab = 6 },
                            text = { Text("Artists (${artists.size})") },
                            icon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                    }

                    when (selectedTab) {
                        0 -> OnlineMusicEngineScreen()
                        1 -> TrackListView(
                            tracks = filterTracks(localTracks, searchQuery),
                            onTrackClick = { track ->
                                engine.playTrack(track, localTracks)
                                selectedTrackForFullPlayer = track
                                showFullPlayer = true
                            },
                            onLongClick = { track -> contextTrack = track }
                        )
                        2 -> TrackListView(
                            tracks = filterTracks(favorites, searchQuery),
                            onTrackClick = { track ->
                                engine.playTrack(track, favorites)
                                selectedTrackForFullPlayer = track
                                showFullPlayer = true
                            },
                            onLongClick = { track -> contextTrack = track }
                        )
                        3 -> PlaylistsCategoryView(
                            playlists = playlists,
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onPlaylistClick = { pl ->
                                detailTitle = pl.name
                                detailTracks = pl.tracks
                            },
                            onDeletePlaylist = { plId -> engine.deletePlaylist(plId) }
                        )
                        4 -> FoldersCategoryView(
                            folders = folders,
                            onFolderClick = { folder ->
                                val fTracks = localTracks.filter { it.folderPath == folder.name || it.filePath.contains(folder.name) }
                                detailTitle = folder.name
                                detailTracks = fTracks
                            }
                        )
                        5 -> AlbumsCategoryView(
                            albums = albums,
                            onAlbumClick = { album ->
                                val aTracks = localTracks.filter { it.album == album.name }
                                detailTitle = album.name
                                detailTracks = aTracks
                            }
                        )
                        6 -> ArtistsCategoryView(
                            artists = artists,
                            onArtistClick = { artist ->
                                val arTracks = localTracks.filter { it.artist == artist.name }
                                detailTitle = artist.name
                                detailTracks = arTracks
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackListView(
    tracks: List<AudioTrackItem>,
    onTrackClick: (AudioTrackItem) -> Unit,
    onLongClick: (AudioTrackItem) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No audio tracks found", color = Color.Gray, fontSize = 16.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                AudioTrackRowItem(
                    track = track,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onLongClick(track) }
                )
            }
        }
    }
}

@Composable
fun AudioTrackRowItem(
    track: AudioTrackItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF38BDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist ?: "Unknown Artist"} • ${formatDuration(track.durationMs)}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onLongClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PlaylistsCategoryView(
    playlists: List<AudioPlaylist>,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (AudioPlaylist) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onCreatePlaylist,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No playlists yet. Tap above to create one.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${playlist.tracks.size} Songs", color = Color.Gray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersCategoryView(
    folders: List<AudioFolder>,
    onFolderClick: (AudioFolder) -> Unit
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No music folders found", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders, key = { it.name }) { folder ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folder) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(folder.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${folder.trackCount} Tracks", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsCategoryView(
    albums: List<AudioAlbum>,
    onAlbumClick: (AudioAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", color = Color.Gray)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = { it.name }) { album ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF334155)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Album, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = album.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist ?: "Unknown"} • ${album.trackCount} Songs",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsCategoryView(
    artists: List<AudioArtist>,
    onArtistClick: (AudioArtist) -> Unit
) {
    if (artists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists found", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${artist.trackCount} Songs • ${artist.albumCount} Albums", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        }
    }
}

private fun filterTracks(tracks: List<AudioTrackItem>, query: String): List<AudioTrackItem> {
    if (query.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(query, ignoreCase = true) ||
                (it.artist?.contains(query, ignoreCase = true) == true) ||
                (it.album?.contains(query, ignoreCase = true) == true) ||
                it.folderPath.contains(query, ignoreCase = true)
    }
}

private fun formatSortName(option: SortOption): String {
    return when (option) {
        SortOption.NAME_A_TO_Z -> "Name: A → Z"
        SortOption.NAME_Z_TO_A -> "Name: Z → A"
        SortOption.DATE_NEW_TO_OLD -> "Date: Newest First"
        SortOption.DATE_OLD_TO_NEW -> "Date: Oldest First"
        SortOption.SIZE_LARGE_TO_SMALL -> "Size: Large → Small"
        SortOption.SIZE_SMALL_TO_LARGE -> "Size: Small → Large"
        SortOption.LENGTH_LONG_TO_SHORT -> "Duration: Long → Short"
        SortOption.LENGTH_SHORT_TO_LONG -> "Duration: Short → Long"
    }
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return String.format("%d:%02d", min, sec)
}
