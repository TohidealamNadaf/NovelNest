package com.novelnest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.repository.NovelRepository

class MainActivity : ComponentActivity() {

    private val novelRepository: NovelRepository by lazy {
        (application as NovelNestApplication).novelRepository
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NovelNestTheme {
                NovelNestApp(repository = novelRepository)
            }
        }
    }
}
