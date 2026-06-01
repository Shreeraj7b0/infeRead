package com.infer.inferead.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infer.inferead.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onNavigateToAuth: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
    
    val animationDuration = if (isFirstLaunch) 1000 else 700

    val iconScale = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(50f) }
    val textAlpha = remember { Animatable(0f) }
    val globalScale = remember { Animatable(1f) }
    val globalAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = (animationDuration * 0.4).toInt(), easing = FastOutSlowInEasing)
            )
        }
        
        launch {
            textOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = (animationDuration * 0.4).toInt(), easing = FastOutSlowInEasing)
            )
        }
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = (animationDuration * 0.4).toInt(), easing = LinearEasing)
        )
        
        delay((animationDuration * 0.1).toLong())

        val targetScale = if (isFirstLaunch) 8f else 3f
        
        launch {
            globalScale.animateTo(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = (animationDuration * 0.2).toInt(), easing = FastOutLinearInEasing)
            )
        }
        globalAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = (animationDuration * 0.2).toInt(), easing = FastOutLinearInEasing)
        )
        
        if (isFirstLaunch) {
            prefs.edit().putBoolean("is_first_launch", false).apply()
        }
        onNavigateToAuth()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(globalScale.value)
                .alpha(globalAlpha.value)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .scale(iconScale.value)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "infeRead",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(y = textOffsetY.value.dp)
                    .alpha(textAlpha.value)
            )
        }
    }
}
