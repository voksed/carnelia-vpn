package com.carnelia.vpn.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carnelia.vpn.R
import com.carnelia.vpn.ui.theme.CarheliaTheme
import com.carnelia.vpn.utils.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MinerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Force TON Theme (Index 14 or similar, need to check Theme.kt enum order)
            // But actually we can just use the specific color scheme directly or define it locally
            // Let's use CarheliaTheme with a specific index if we know it, or just pass colors.
            // Since we added TON to Enum, we need its ordinal. It was added just before SYSTEM.
            // Let's assume ordinal usage is tricky if not dynamically found.
            // Instead, we can just hardcode the style here or rely on the theme wrapper.
            
            // Let's check Theme.kt again. We added TON after DARK.
            // CARNELIA(0), CYBERPUNK(1), MATRIX(2), PURPLE(3), LIGHT_BLUE(4), LIGHT_GREEN(5), LIGHT_PINK(6), LIGHT_PURPLE(7), LIGHT(8), DARK(9), TON(10), SYSTEM(11), SECRET(12)
            // So index is 10.
            
            CarheliaTheme(themeIndex = 10) {
                MinerGameScreen()
            }
        }
    }
}

@Composable
fun MinerGameScreen() {
    val context = LocalContext.current
    var score by remember { mutableStateOf(PrefsManager.getMinerScore(context)) }
    val scope = rememberCoroutineScope()
    
    // Floating Texts
    val floatingTexts = remember { mutableStateListOf<FloatingTextData>() }

    // Save score periodically or on change
    LaunchedEffect(score) {
        PrefsManager.setMinerScore(context, score)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Carnelia Miner",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "$score TON",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground, // Whiteish
                fontWeight = FontWeight.ExtraBold
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Coin Button
            Box(contentAlignment = Alignment.Center) {
                CoinButton(onClick = {
                    score++
                    // Add floating text
                    val id = System.nanoTime()
                    floatingTexts.add(FloatingTextData(id, "+1", Random.nextFloat() * 100 - 50, 0f))
                    
                    // Remove after animation
                    scope.launch {
                        delay(1000)
                        floatingTexts.removeAll { it.id == id }
                    }
                })
                
                // Render floating texts
                floatingTexts.forEach { data ->
                    FloatingText(data)
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = "Tap to mine Toncoin logic!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CoinButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100), label = "scale"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disable default ripple
            ) { onClick() }
            .background(Color(0xFF0088CC), CircleShape),
        contentAlignment = Alignment.Center
    ) {
         // Placeholder for TON Logo (Diamond shape)
         // Drawing a diamond just for fun
         androidx.compose.foundation.Canvas(modifier = Modifier.size(100.dp)) {
             val path = androidx.compose.ui.graphics.Path().apply {
                 moveTo(size.width / 2, 0f)
                 lineTo(size.width, size.height / 3)
                 lineTo(size.width / 2, size.height)
                 lineTo(0f, size.height / 3)
                 close()
             }
             drawPath(path, Color.White)
         }
    }
}

data class FloatingTextData(val id: Long, val text: String, val offsetX: Float, val startY: Float)

@Composable
fun FloatingText(data: FloatingTextData) {
    var yOffset by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(1f) }
    
    LaunchedEffect(Unit) {
        // Animate up
        val startTime = System.currentTimeMillis()
        while(alpha > 0) {
            val elapsed = System.currentTimeMillis() - startTime
            yOffset -= 2f
            if (elapsed > 500) alpha -= 0.05f
            delay(16)
        }
    }

    Text(
        text = data.text,
        color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset(x = data.offsetX.dp, y = yOffset.dp)
    )
}
