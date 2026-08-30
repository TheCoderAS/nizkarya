package com.nizkarya.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nizkarya.app.MainActivity
import com.nizkarya.app.NewTaskActivity
import com.nizkarya.app.R
import com.nizkarya.app.VoiceAddActivity

// ── Next up ──────────────────────────────────────────────────────────────────

/**
 * One row: the next thing you have not done, its time, and a check.
 *
 * For a home screen that stays clean. It answers the only question a to-do app
 * is really asked during the day, which is what now.
 */
class NextUpWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load()
        provideContent { NextUpContent(snapshot) }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

@Composable
private fun NextUpContent(snapshot: WidgetSnapshot) {
    val next = snapshot.rows.firstOrNull { !it.done }
    WidgetFrame {
        if (!snapshot.signedIn) {
            SignedOutBody()
        } else if (next == null) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (snapshot.total == 0) "Nothing planned" else "Day cleared",
                    style = TextStyle(
                        color = WidgetLook.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = snapshot.dateLabel,
                    style = TextStyle(color = WidgetLook.Dim, fontSize = 11.sp)
                )
            }
        } else {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next up",
                    style = TextStyle(
                        color = WidgetLook.Dim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(GlanceModifier.height(3.dp))
                WidgetTaskRow(next)
            }
        }
    }
}

// ── Habits ───────────────────────────────────────────────────────────────────

/** Today's habits, tickable without opening anything. */
class HabitsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load()
        provideContent { HabitsContent(snapshot) }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

@Composable
private fun HabitsContent(snapshot: WidgetSnapshot) {
    WidgetFrame {
        if (!snapshot.signedIn) {
            SignedOutBody()
        } else {
            val kept = snapshot.habits.count { it.done }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Habits",
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = WidgetLook.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "$kept / ${snapshot.habits.size}",
                    style = TextStyle(
                        color = WidgetLook.Habit,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (snapshot.habits.isEmpty()) {
                Text(
                    text = "No habits due today",
                    style = TextStyle(color = WidgetLook.Dim, fontSize = 12.sp)
                )
            } else {
                // Same reason as Today: a list that fills the space beats a
                // fixed cap that leaves it empty.
                LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                    items(snapshot.habits, itemId = { it.id.hashCode().toLong() }) { row ->
                        WidgetTaskRow(row)
                    }
                }
            }
        }
    }
}

// ── Quick add ────────────────────────────────────────────────────────────────

/**
 * Add something without the app.
 *
 * The plus opens the task editor straight away. The mic goes to a transparent
 * activity that runs the system recogniser, feeds the transcript through the
 * quick-add parser and writes the task, so speaking a task never shows the app
 * at all.
 */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { QuickAddContent() }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
private fun QuickAddContent() {
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Image(
            provider = ImageProvider(R.drawable.widget_surface),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<NewTaskActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_add),
                    contentDescription = null,
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = "Add a task",
                    style = TextStyle(
                        color = WidgetLook.Dim,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(actionStartActivity<VoiceAddActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_mic),
                    contentDescription = "Add a task by voice",
                    modifier = GlanceModifier.size(20.dp)
                )
            }
        }
    }
}
