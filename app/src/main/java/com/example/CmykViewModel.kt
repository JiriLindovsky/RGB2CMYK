package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CmykViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CmykUiState())
    val uiState: StateFlow<CmykUiState> = _uiState.asStateFlow()

    init {
        // Load the beautiful generated calibration target immediately as default so they have a ready-made playground!
        loadDefaultCalibrationTarget()
    }

    fun loadDefaultCalibrationTarget() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val target = withContext(Dispatchers.Default) {
                CmykConverter.generateCalibrationTarget()
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    selectedUri = null,
                    originalBitmap = target,
                    fullSizeBitmap = target,
                    pinnedPixelInfo = null
                )
            }
            // Trigger processing immediately
            processCurrentBitmap()
        }
    }

    fun selectUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val results = withContext(Dispatchers.IO) {
                    val working = loadBitmapFromUri(context, uri, 600)
                    val full = loadBitmapFromUri(context, uri, 1500)
                    Pair(working, full)
                }
                if (results.first != null) {
                    _uiState.update {
                        it.copy(
                            selectedUri = uri,
                            originalBitmap = results.first,
                            fullSizeBitmap = results.second ?: results.first,
                            pinnedPixelInfo = null
                        )
                    }
                    processCurrentBitmap()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to read the chosen image file."
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Error loading image: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateSettings(settings: CmykSettings) {
        _uiState.update { it.copy(settings = settings) }
        // Process bitmap with new settings
        processCurrentBitmap()
    }

    fun setActiveChannel(channel: Int) {
        _uiState.update { it.copy(activeChannel = channel) }
    }

    /**
     * Set selected coordinate in the image to analyze color and CMYK distribution.
     */
    fun pinPixel(xNormalized: Float, yNormalized: Float) {
        val bitmap = _uiState.value.originalBitmap ?: return
        val w = bitmap.width
        val h = bitmap.height

        val xPixel = (xNormalized * w).toInt().coerceIn(0, w - 1)
        val yPixel = (yNormalized * h).toInt().coerceIn(0, h - 1)

        val colorInt = bitmap.getPixel(xPixel, yPixel)
        val r = android.graphics.Color.red(colorInt)
        val g = android.graphics.Color.green(colorInt)
        val b = android.graphics.Color.blue(colorInt)

        val info = CmykConverter.analyzePixel(r, g, b, xPixel, yPixel, _uiState.value.settings)
        _uiState.update { it.copy(pinnedPixelInfo = info) }
    }

    /**
     * Process currently active bitmap on thread pool.
     */
    private fun processCurrentBitmap() {
        val bitmap = _uiState.value.originalBitmap ?: return
        val settings = _uiState.value.settings

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val output = withContext(Dispatchers.Default) {
                    CmykConverter.separateBitmap(bitmap, settings)
                }

                // If a pixel is already pinned, re-analyze it with current settings and updated colors
                val currentPinned = _uiState.value.pinnedPixelInfo
                val rePinned = if (currentPinned != null) {
                    val colorInt = bitmap.getPixel(currentPinned.x, currentPinned.y)
                    CmykConverter.analyzePixel(
                        android.graphics.Color.red(colorInt),
                        android.graphics.Color.green(colorInt),
                        android.graphics.Color.blue(colorInt),
                        currentPinned.x,
                        currentPinned.y,
                        settings
                    )
                } else {
                    null
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        separationOutput = output,
                        pinnedPixelInfo = rePinned
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Processing failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Exports the selected plate at FULL scale to the device cache as a temporary sharable file,
     * returning its absolute file URI.
     */
    suspend fun exportCurrentPlate(context: Context): File? = withContext(Dispatchers.Default) {
        val fullSize = _uiState.value.fullSizeBitmap ?: return@withContext null
        val settings = _uiState.value.settings
        val channelIdx = _uiState.value.activeChannel

        try {
            // Process full scale separations
            val outputs = CmykConverter.separateBitmap(fullSize, settings)
            val selectedBitmap = when (channelIdx) {
                0 -> outputs.simulatedCombined
                1 -> outputs.cyanPlate
                2 -> outputs.magentaPlate
                3 -> outputs.yellowPlate
                4 -> outputs.blackPlate
                else -> outputs.simulatedCombined
            }

            val channelName = when (channelIdx) {
                0 -> "CMYK_Combined"
                1 -> "Cyan_Plate"
                2 -> "Magenta_Plate"
                3 -> "Yellow_Plate"
                4 -> "Black_Plate"
                else -> "Output"
            }

            // Write to local cache directory for Sharing ContentProvider
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "RGB2CMYK_${channelName}_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(file)
            selectedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        var input: InputStream? = null
        return try {
            input = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            var sampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            input = context.contentResolver.openInputStream(uri) ?: return null
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            input?.close()
        }
    }
}

data class CmykUiState(
    val selectedUri: Uri? = null,
    val isLoading: Boolean = false,
    val originalBitmap: Bitmap? = null,
    val fullSizeBitmap: Bitmap? = null,
    val settings: CmykSettings = CmykSettings(),
    val separationOutput: CmykConverter.SeparationOutput? = null,
    val activeChannel: Int = 0, // 0=Combined, 1=C, 2=M, 3=Y, 4=K
    val pinnedPixelInfo: PixelColorInfo? = null,
    val errorMessage: String? = null
)
