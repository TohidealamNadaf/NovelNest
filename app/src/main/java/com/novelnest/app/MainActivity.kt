package com.novelnest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelnest.app.core.designsystem.NovelNestTheme
import com.novelnest.app.data.local.NovelNestDatabase
import com.novelnest.app.data.repository.NovelRepositoryImpl

class MainActivity : ComponentActivity() {

    private val novelRepository by lazy {
        val db = NovelNestDatabase.getInstance(applicationContext)
        NovelRepositoryImpl(database = db)
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
