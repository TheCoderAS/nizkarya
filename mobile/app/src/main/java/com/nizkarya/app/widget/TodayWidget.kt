package com.nizkarya.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import androidx.glance.unit.ColorProvider
import com.nizkarya.app.MainActivity
import com.nizkarya.app.NewTaskActivity
import com.nizkarya.app.R

/**
 * The day, on the home screen.
 *
 * Resizable, and the list fills whatever height it is given and scrolls past
 * it, so nothing is hidden behind a count. Tapping a circle ticks the thing
 * off where it stands; the header opens the app and the plus opens the
 * editor.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetData.load()
        provideContent { TodayContent(snapshot) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(snapshot: WidgetSnapshot) {
    WidgetFrame {
        if (!snapshot.signedIn) {
            SignedOutBody()
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        text = snapshot.dateLabel,
                        style = TextStyle(
                            color = WidgetLook.Dim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = "${snapshot.done} / ${snapshot.total}",
                        style = TextStyle(
                            color = WidgetLook.Text,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                // Adding is the other half of a to-do widget. Ticking things
                // off without opening the app is only useful if putting them
                // there does not need the app either.
                Box(
                    modifier = GlanceModifier
                        .size(34.dp)
                        .clickable(actionStartActivity<NewTaskActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_add_circle),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = GlanceModifier.size(30.dp)
                    )
                    Image(
                        provider = ImageProvider(R.drawable.widget_add),
                        contentDescription = "Add a task",
                        modifier = GlanceModifier.size(17.dp)
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            if (snapshot.rows.isEmpty()) {
                Text(
                    text = if (snapshot.total == 0) {
                        "Nothing planned today"
                    } else {
                        "All done today"
                    },
                    style = TextStyle(color = WidgetLook.Dim, fontSize = 13.sp)
                )
            } else {
                // A real list, taking whatever height is left and scrolling
                // past it. This used to divide the reported height by a guess
                // at the header and a guess at a row, which is how a widget
                // with room for four ended up saying "and 2 more" over an
                // inch of empty space.
                LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                    items(snapshot.rows, itemId = { it.id.hashCode().toLong() }) { row ->
                        WidgetTaskRow(row)
                    }
                }
            }
        }
    }
}

/**
 * The shared shell: the rounded dark surface.
 *
 * It carries no tap of its own. A clickable spanning the whole surface would
 * sit over the list's collection views and is a good way to lose the taps
 * that matter, so opening the app is attached to the header instead, next to
 * the text that says which day you are looking at.
 *
 * The surface is an Image behind the content rather than a background
 * modifier, because that works the same on every version we support instead of
 * depending on the corner radius API that only arrives at 31.
 */
@Composable
fun WidgetFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Image(
            provider = ImageProvider(R.drawable.widget_surface),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            content = content
        )
    }
}

@Composable
fun SignedOutBody() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NizKarya",
            style = TextStyle(
                color = WidgetLook.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "Sign in to see your day",
            style = TextStyle(color = WidgetLook.Dim, fontSize = 12.sp)
        )
    }
}

/** One line: a check circle you can tap, the title, and the time. */
@Composable
fun WidgetTaskRow(row: WidgetRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .clickable(
                    actionRunCallback<ToggleRowAction>(
                        actionParametersOf(
                            WidgetKeys.Id to row.id,
                            WidgetKeys.IsHabit to row.isHabit
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(
                    if (row.done) R.drawable.widget_check_on else R.drawable.widget_check_off
                ),
                contentDescription = if (row.done) "Done" else "Mark ${row.title} done",
                modifier = GlanceModifier.size(18.dp)
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.title,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = if (row.done) WidgetLook.Dim else WidgetLook.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (row.time != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = row.time,
                style = TextStyle(
                    color = rowTint(row),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

fun rowTint(row: WidgetRow): ColorProvider = when {
    row.done -> WidgetLook.Dim
    row.late -> WidgetLook.Late
    row.isHabit -> WidgetLook.Habit
    else -> WidgetLook.Task
}
