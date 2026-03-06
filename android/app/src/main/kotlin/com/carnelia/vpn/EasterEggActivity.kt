package com.carnelia.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.carnelia.vpn.utils.PrefsManager

class EasterEggActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpaceDodgerGame()
        }
    }
}

@Composable
fun SpaceDodgerGame() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    
    var playerX by remember { mutableStateOf(0.5f) } // 0.0 to 1.0
    var score by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    // OPTIMIZATION: Use mutableStateListOf to avoid re-allocating list wrapper on every frame.
    // However, if we replace elements, it's still heavy.
    // Better: Just use a SnapshotStateList
    val enemies = remember { mutableStateListOf<Enemy>() }
    
    // FORCE REDRAW: We need a state that changes every frame to notify Canvas to redraw
    // because modifying 'Enemy.y' (plain kotlin properties) won't trigger recomposition.
    var gameTick by remember { mutableStateOf(0L) }
    
    // Cache the unlock status to avoid disk I/O in the game loop
    var isSecretThemeUnlocked by remember { mutableStateOf(PrefsManager.isSecretThemeUnlocked(context)) }

    // Reusable objects to avoid GC pressure
    val shipPath = remember { androidx.compose.ui.graphics.Path() }

    // Game Loop - Optimized using withFrameNanos (smoother, synced to display)
    LaunchedEffect(Unit) {
        var lastTime = 0L
        var spawnTimer = 0f
        
        while(true) {
            withFrameNanos { time ->
                if (lastTime == 0L) lastTime = time
                val delta = (time - lastTime) / 1_000_000_000f // Delta time in seconds
                lastTime = time

                if (!gameOver) {
                    // Time-based spawning
                    spawnTimer += delta
                    if (spawnTimer > 0.8f) { // Spawn every ~0.8s
                        spawnTimer = 0f
                        enemies.add(Enemy(
                             x = Random.nextFloat(),
                             y = -0.1f,
                             speed = 0.008f + (score * 0.0003f) 
                        ))
                    }
                    
                    // Move enemies
                    val iterator = enemies.iterator()
                    while (iterator.hasNext()) {
                         val e = iterator.next()
                         e.y += e.speed
                         
                         if (e.y < 1.2f) {
                             // Collision check
                             if (e.y > 0.8f && e.y < 0.9f && 
                                 e.x > (playerX - 0.08f) && e.x < (playerX + 0.08f)) {
                                 gameOver = true
                             }
                         } else {
                             iterator.remove()
                             score++
                             if (score >= 20 && !isSecretThemeUnlocked) {
                                 isSecretThemeUnlocked = true
                                 PrefsManager.unlockSecretTheme(context)
                             }
                         }
                    }
                    // Trigger redraw
                    gameTick = time
                }
            }
        }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background == Color(0xFF121212) || 
                      MaterialTheme.colorScheme.background == Color(0xFF0D0D0D) ||
                      MaterialTheme.colorScheme.background == Color(0xFF000000) ||
                      MaterialTheme.colorScheme.background == Color(0xFF0F1419)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDarkTheme) {
                        listOf(Color(0xFF000011), Color(0xFF111122)) // Темный космический
                    } else {
                        listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)) // Светлый небесный
                    }
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val changeX = dragAmount.x / (400f * density) 
                    playerX = (playerX + changeX).coerceIn(0.1f, 0.9f)
                }
            }
    ) {
        // Starfield Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subscribe to gameTick to force redraw every frame
            val tick = gameTick 
            
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Draw Player (Triangle/Ship)
            val pX = playerX * canvasWidth
            val pY = canvasHeight * 0.85f
            val pSize = 40.dp.toPx()
            
            // Reusable Path
            shipPath.reset()
            shipPath.moveTo(pX, pY - pSize/2)
            shipPath.lineTo(pX - pSize/2, pY + pSize/2)
            shipPath.lineTo(pX + pSize/2, pY + pSize/2)
            shipPath.close()

            drawPath(
                path = shipPath,
                color = if (isDarkTheme) Color.Cyan else Color(0xFF1976D2) // Синий для светлой темы
            )
            
            // Draw Enemies (Red blocks)
            for (e in enemies) {
                val eX = e.x * canvasWidth
                val eY = e.y * canvasHeight
                val eSize = 25.dp.toPx()
                
                drawRect(
                    color = if (isDarkTheme) Color.Red else Color(0xFFD32F2F), // Темно-красный для светлой темы
                    topLeft = Offset(eX - eSize/2, eY - eSize/2),
                    size = Size(eSize, eSize)
                )
            }
        }
        
        // Score UI
        Text(
            "Score: $score",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(32.dp).align(androidx.compose.ui.Alignment.TopStart)
        )
        
        if (gameOver) {
            Column(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                 Text(
                    "GAME OVER",
                    color = if (isDarkTheme) Color.Red else Color(0xFFB71C1C),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                 Text(
                    "Final Score: $score",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                 Text(
                    "Tap to restart",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
                
                Button(
                    onClick = {
                        score = 0
                        enemies.clear()
                        gameOver = false
                    },
                    modifier = Modifier.padding(top = 32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restart", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

data class Enemy(var x: Float, var y: Float, var speed: Float)
