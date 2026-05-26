package com.ganjianping.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun QuestionsScreen(app: AppController) {
    OpenListScreen(
        app = app,
        cacheKey = "questions",
        tagSetting = "question_tags",
        parser = { Question.fromJson(it) },
        loader = { updatedAfter -> app.api.allQuestions(updatedAfter) },
        row = { item -> QuestionRow(item) }
    )
}

@Composable
internal fun QuestionRow(item: Question) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.Top
        ) {
            Text("Q", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
            Text(item.question, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            HtmlWebView(
                html = item.answer,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        }
        Tags(item.tags)
    }
}
