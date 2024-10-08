package com.dawinder.btnjc.ui.composables.tabs

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.dawinder.btnjc.ui.data.UserData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    userData: UserData,
    fusedLocationClient: FusedLocationProviderClient,
) {
    val database = Firebase.database
    val postsRef = database.getReference("posts")

    val userLocationState = remember { mutableStateOf<LatLng?>(null) }
    val showDialog = remember { mutableStateOf(false) }
    val postsWithPositions = remember { mutableStateOf<Map<LatLng, Post>>(emptyMap()) }
    val bottomSheetState = rememberModalBottomSheetState()
    val selectedPost = remember { mutableStateOf<Post?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()

    val updateLocation: () -> Unit = {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                userLocationState.value = latLng
                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                Log.d("Location", "Lat: ${it.latitude}, Long: ${it.longitude}")
            }
        }
    }

    LaunchedEffect(Unit) {
        updateLocation()
    }

    postsRef.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val positions = snapshot.children.mapNotNull { postSnapshot ->
                val post = postSnapshot.getValue(Post::class.java)
                post?.latitude?.let { lat ->
                    post.longitude?.let { lng ->
                        LatLng(lat, lng) to post
                    }
                }
            }.toMap()
            postsWithPositions.value = positions
        }

        override fun onCancelled(error: DatabaseError) {
            Log.w("Firebase", "loadPost:onCancelled", error.toException())
        }
    })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 75.dp)
    ) {
        MyMap(
            modifier = Modifier.fillMaxSize(),
            postsWithPositions = postsWithPositions.value,
            onMarkerClick = { post ->
                selectedPost.value = post
                coroutineScope.launch {
                    bottomSheetState.show()
                }
            },
            cameraPositionState = cameraPositionState
        )
        FloatingActionButton(
            onClick = {
                updateLocation()
                showDialog.value = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }

        if (showDialog.value) {
            userData.username?.let {
                PostCreationDialog(
                    userName = it,
                    userLocation = userLocationState.value,
                    userProfilePictureUrl = userData.profilePictureUrl,
                    onDismiss = { showDialog.value = false },
                    onPost = { title, description, userName, userProfilePictureUrl, lat, long, imageUri ->
                        val storageRef = Firebase.storage.reference
                        val imageRef = storageRef.child("images/${UUID.randomUUID()}")
                        imageUri?.let {
                            imageRef.putFile(it)
                                .addOnSuccessListener { taskSnapshot ->
                                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                                        val newPostRef = postsRef.push()
                                        newPostRef.setValue(
                                            Post(
                                                title,
                                                description,
                                                userName,
                                                userProfilePictureUrl,
                                                lat,
                                                long,
                                                uri.toString()
                                            )
                                        )
                                    }
                                }
                        } ?: run {
                            val newPostRef = postsRef.push()
                            newPostRef.setValue(
                                Post(
                                    title,
                                    description,
                                    userName,
                                    userProfilePictureUrl,
                                    lat,
                                    long,
                                    null
                                )
                            )
                        }
                        showDialog.value = false
                    }
                )
            }
        }
        selectedPost.value?.let { post ->
            LaunchedEffect(bottomSheetState, selectedPost.value) {
                coroutineScope.launch {
                    if (bottomSheetState.isVisible) {
                        bottomSheetState.hide()
                    } else {
                        bottomSheetState.show()
                    }
                }
            }
            ModalBottomSheet(
                sheetState = bottomSheetState,
                onDismissRequest = {
                    coroutineScope.launch {
                        bottomSheetState.hide()
                        selectedPost.value = null
                    }
                }
            ) {
                PostDetails(post = post)
            }
        }

    }
}

@Composable
fun MyMap(
    modifier: Modifier,
    postsWithPositions: Map<LatLng, Post>,
    onMarkerClick: (Post) -> Unit,
    cameraPositionState: CameraPositionState
) {
    val uiSettings by remember { mutableStateOf(MapUiSettings()) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings
            .copy(zoomControlsEnabled = false),
        properties = MapProperties(isMyLocationEnabled = true)
    ) {
        postsWithPositions.forEach { (latLng, post) ->
            Marker(
                state = MarkerState(position = latLng),
                title = post.title,
                onClick = {
                    onMarkerClick(post)
                    true
                }
            )
        }
    }
}

@Composable
fun PostCreationDialog(
    userName: String,
    userLocation: LatLng?,
    userProfilePictureUrl: String?,
    onDismiss: () -> Unit,
    onPost: (String, String, String, String?, Double?, Double?, Uri?) -> Unit
) {
    val title = remember { mutableStateOf("") }
    val description = remember { mutableStateOf("") }
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val imageName = remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri.value = uri
        imageName.value = uri?.lastPathSegment
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Create Post") },
        text = {
            Column {
                OutlinedTextField(
                    value = title.value,
                    onValueChange = { title.value = it },
                    label = { Text("Title") }
                )
                OutlinedTextField(
                    value = description.value,
                    onValueChange = { description.value = it },
                    label = { Text("Description") }
                )
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Text("Pick Image")
                }
                imageName.value?.let {
                    Text(text = "Selected image: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPost(
                        title.value,
                        description.value,
                        userName,
                        userProfilePictureUrl,
                        userLocation?.latitude,
                        userLocation?.longitude,
                        imageUri.value
                    )
                }
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

data class Post(
    val title: String = "",
    val description: String = "",
    val userName: String = "",
    val userProfilePictureUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUrl: String? = null
)

@Composable
fun PostDetails(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            post.userProfilePictureUrl?.let {
                Image(
                    painter = rememberAsyncImagePainter(model = it),
                    contentDescription = "User Profile Picture",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Posted by: ${post.userName}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        post.imageUrl?.let {
            Image(
                painter = rememberAsyncImagePainter(model = it),
                contentDescription = "Post Image",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(2.dp, RoundedCornerShape(8.dp))
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Justify,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
