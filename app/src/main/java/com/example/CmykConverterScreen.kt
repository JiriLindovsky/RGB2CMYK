package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CmykConverterScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CmykViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectUri(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "PRO-CMYK",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Subtractive Print Proofing Studio",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Quick Action: Reset to Test Pattern
                    IconButton(
                        onClick = { viewModel.loadDefaultCalibrationTarget() },
                        modifier = Modifier.testTag("reset_target_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load Calibration Pattern",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("cmyk_converter_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            
            // ERROR BANNER
            uiState.errorMessage?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("error_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error Logo",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = errorMsg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // DUAL-PREVIEW / ACTIVE PLATE DISPLAY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .background(Color(0xFF141312)) // Deep darkroom proving-board
                    .testTag("viewport_container"),
                contentAlignment = Alignment.Center
            ) {
                val activeBitmap = when (uiState.activeChannel) {
                    0 -> uiState.separationOutput?.simulatedCombined
                    1 -> uiState.separationOutput?.cyanPlate
                    2 -> uiState.separationOutput?.magentaPlate
                    3 -> uiState.separationOutput?.yellowPlate
                    4 -> uiState.separationOutput?.blackPlate
                    else -> uiState.separationOutput?.simulatedCombined
                } ?: uiState.originalBitmap

                if (activeBitmap != null) {
                    var containerSize by remember { mutableStateOf(Size.Zero) }

                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .aspectRatio(activeBitmap.width.toFloat() / activeBitmap.height.toFloat())
                            .onGloballyPositioned { coordinates ->
                                containerSize = Size(
                                    coordinates.size.width.toFloat(),
                                    coordinates.size.height.toFloat()
                                )
                            }
                            .pointerInput(activeBitmap) {
                                detectTapGestures { offset ->
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val normX = (offset.x / containerSize.width).coerceIn(0f, 1f)
                                        val normY = (offset.y / containerSize.height).coerceIn(0f, 1f)
                                        viewModel.pinPixel(normX, normY)
                                    }
                                }
                            }
                            .pointerInput(activeBitmap) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    if (containerSize.width > 0 && containerSize.height > 0) {
                                        val normX = (change.position.x / containerSize.width).coerceIn(0f, 1f)
                                        val normY = (change.position.y / containerSize.height).coerceIn(0f, 1f)
                                        viewModel.pinPixel(normX, normY)
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = activeBitmap.asImageBitmap(),
                            contentDescription = "Active color plate separation panel",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // If user has pinned/selected a coordinate, overlay the targets crosshair
                        uiState.pinnedPixelInfo?.let { pin ->
                            if (containerSize.width > 0 && containerSize.height > 0) {
                                val xPos = (pin.x.toFloat() / activeBitmap.width) * containerSize.width
                                val yPos = (pin.y.toFloat() / activeBitmap.height) * containerSize.height

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Circular magnifier target line
                                    drawCircle(
                                        color = Color.White,
                                        radius = 24f,
                                        center = Offset(xPos, yPos),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                                    )
                                    drawCircle(
                                        color = Color.Black,
                                        radius = 26f,
                                        center = Offset(xPos, yPos),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )
                                    // Crosshairs
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(xPos - 35f, yPos),
                                        end = Offset(xPos - 12f, yPos),
                                        strokeWidth = 3f
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(xPos + 12f, yPos),
                                        end = Offset(xPos + 35f, yPos),
                                        strokeWidth = 3f
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(xPos, yPos - 35f),
                                        end = Offset(xPos, yPos - 12f),
                                        strokeWidth = 3f
                                    )
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(xPos, yPos + 12f),
                                        end = Offset(xPos, yPos + 35f),
                                        strokeWidth = 3f
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Empty loading/import slot
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Placeholder",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No image loaded",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import from gallery or load a built-in separation calibration pattern.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SEPARATION CHANNEL SELECTOR TABS (Simulate physical darkroom inspection slider)
            Column(modifier = Modifier.fillMaxWidth()) {
                val channels = listOf(
                    "CMYK" to Color(0xFFF3E9DD),
                    "Cyan" to Color(0xFF00E5FF),
                    "Magenta" to Color(0xFFFF007F),
                    "Yellow" to Color(0xFFFFD600),
                    "Black" to Color(0xFF1E1E1E)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    channels.forEachIndexed { idx, item ->
                        val isSelected = uiState.activeChannel == idx
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setActiveChannel(idx) },
                            label = {
                                Text(
                                    text = item.first,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(item.second)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("channel_chip_$idx")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LIVE COLOR SCANNER METRICS (Displays whenever user has clicked on image)
            AnimatedVisibility(
                visible = uiState.pinnedPixelInfo != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.pinnedPixelInfo?.let { pin ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                            .testTag("metrics_panel")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pixel Inspector Readings",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = "Coord: ${pin.x}x, ${pin.y}y",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Selected Swatch Block
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(pin.r, pin.g, pin.b))
                                        .border(
                                            2.dp,
                                            if ((pin.r + pin.g + pin.b) / 3 > 180) Color.DarkGray else Color.White,
                                            RoundedCornerShape(12.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pin.hex,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Ink Separation Bars
                            Column(modifier = Modifier.weight(1f)) {
                                val specInfo = listOf(
                                    Triple("Cyan", pin.c, Color(0xFF00E5FF)),
                                    Triple("Magenta", pin.m, Color(0xFFFF007F)),
                                    Triple("Yellow", pin.yCmyk, Color(0xFFFFD600)),
                                    Triple("Black (K)", pin.k, Color(0xFF1E1E1E))
                                )

                                specInfo.forEach { (name, percent, color) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(75.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .height(8.dp)
                                                .weight(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(percent / 100f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(color)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$percent%",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.width(35.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Total Ink Coverage Reading
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Ink Coverage (TAC):",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                            val isExceeded = pin.tac > uiState.settings.tacLimit
                            val tacLabelColor = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                            Text(
                                text = "${pin.tac}%" + if (isExceeded) " (EXCEEDS LIMIT!)" else " (Safe)",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = tacLabelColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PRINT DIAGNOSTICS & ENTIRE IMAGE STATISTICS
            uiState.separationOutput?.let { stats ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ink Separation Diagnostics",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Average Ink consumption distribution
                        Text(
                            text = "Average Plate Ink Density",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val statBars = listOf(
                            Triple("Cyan Plate", stats.averageC, Color(0xFF00E5FF)),
                            Triple("Magenta Plate", stats.averageM, Color(0xFFFF007F)),
                            Triple("Yellow Plate", stats.averageY, Color(0xFFFFD600)),
                            Triple("Black Plate (K)", stats.averageK, Color(0xFF1E1E1E))
                        )

                        statBars.forEach { (plate, avgVal, color) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = plate, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = String.format("%.1f%%", avgVal),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(avgVal / 100f)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(color)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Total Ink metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Global Average TAC:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format("%.1f%%", stats.averageTac),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Peak Color Point Ink:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${stats.peakTac}%",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.peakTac > uiState.settings.tacLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        if (stats.exceededTacCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ink Coverage limit of ${uiState.settings.tacLimit}% is exceeded in ${stats.exceededTacCount} shadow pixels. Adjust GCR black generation or apply TAC corrections.",
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PROFILE SELECTOR & CALIBRATION KNOBS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("controls_panel")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ink Calibration & Profile Engine",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Select standard profile presets
                Text(
                    text = "ICC COLOR PROFILE / STANDARD PRESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                var expandedDropdown by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = !expandedDropdown }
                    ) {
                        OutlinedTextField(
                            value = uiState.settings.profileMode.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("profile_dropdown"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            ProfileMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = mode.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "Max TAC: ${mode.defaultTacLimit}% | ${mode.description}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateSettings(
                                            uiState.settings.copy(
                                                profileMode = mode,
                                                tacLimit = mode.defaultTacLimit
                                            )
                                        )
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TAC Sliders & Film Positive options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TAC Limits: ${uiState.settings.tacLimit}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = uiState.settings.tacLimit.toFloat(),
                    onValueChange = {
                        viewModel.updateSettings(uiState.settings.copy(tacLimit = it.toInt()))
                    },
                    valueRange = 200f..400f,
                    steps = 19,
                    modifier = Modifier.testTag("tac_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Black generation start threshold slider (for GCR modes)
                val isGcr = uiState.settings.profileMode == ProfileMode.CUSTOM_GCR ||
                        uiState.settings.profileMode == ProfileMode.CUSTOM_UCR

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Black (K) Generation Start: ${(uiState.settings.blackStartThreshold * 100).toInt()}% grey",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGcr) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Slider(
                    value = uiState.settings.blackStartThreshold,
                    onValueChange = {
                        viewModel.updateSettings(uiState.settings.copy(blackStartThreshold = it))
                    },
                    valueRange = 0f..0.8f,
                    enabled = isGcr,
                    modifier = Modifier.testTag("black_start_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Black strength slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Black Generation Strength (GCR): ${(uiState.settings.blackStrength * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGcr) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Slider(
                    value = uiState.settings.blackStrength,
                    onValueChange = {
                        viewModel.updateSettings(uiState.settings.copy(blackStrength = it))
                    },
                    valueRange = 0.2f..1.5f,
                    enabled = isGcr,
                    modifier = Modifier.testTag("black_strength_slider")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Toggle between Tinted separations and Grayscale negatives
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Visual Color Separation Tints",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tint plates C/M/Y, otherwise display classical grayscale film positives.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.settings.tintSeparations,
                        onCheckedChange = {
                            viewModel.updateSettings(uiState.settings.copy(tintSeparations = it))
                        },
                        modifier = Modifier.testTag("tint_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BIG ACTION BUTTONS (Upload from camera/gallery, export plates)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { imageLauncher.launch("image/*") },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("import_image_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Import Image", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val file = viewModel.exportCurrentPlate(context)
                            if (file != null) {
                                // Save to public Gallery/Photos
                                val savedUri = CmykConverter.saveBitmapToGallery(
                                    context = context,
                                    bitmap = BitmapFactory.decodeFile(file.absolutePath),
                                    displayName = "Plate_${uiState.activeChannel}_${System.currentTimeMillis()}"
                                )
                                if (savedUri != null) {
                                    Toast.makeText(
                                        context,
                                        "Saved Plate to Photos/Gallery successfully!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Saved locally. Cache path details: ${file.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(context, "Export failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("export_plate_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    val label = when (uiState.activeChannel) {
                        0 -> "Export Combined"
                        1 -> "Export Cyan"
                        2 -> "Export Magenta"
                        3 -> "Export Yellow"
                        4 -> "Export Black"
                        else -> "Export File"
                    }
                    Text(text = label, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Saves a Bitmap file securely into the user's local Public pictures folder using modern Scoped Storage
 * content resolvers.
 */
object CmykFileSaver {
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val imageDetails = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/CMYK_Separations")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, imageDetails) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, imageDetails, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
            null
        }
    }
}

// Attach auxiliary save method to object for usage in VM / Coroutine Action
fun CmykConverter.saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
    return CmykFileSaver.saveBitmapToGallery(context, bitmap, displayName)
}
