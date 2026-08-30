package com.nizkarya.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.unit.ColorProvider
import com.nizkarya.app.MainActivity
import com.nizkarya.app.R

/**
 * The day, on the home screen.
 *
 * Resizable: it draws as many rows as the height it was given can hold, so a
 * 4x2 shows the next few things and a 4x4 shows most of the day. Tapping a
 * circle ticks the thing off where it stands; tapping anywhere else opens the
 * app.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

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
                Column(modifier = GlanceModifier.defaultWeight()) {
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
                Image(
                    provider = ImageProvider(R.drawable.ic_notification),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp)
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            // Roughly how many rows fit once the header has taken its share.
            val fits = ((LocalSize.current.height.value - 78f) / 40f).toInt().coerceIn(1, 8)
            val visible = snapshot.rows.take(fits)

            if (visible.isEmpty()) {
                Text(
                    text = if (snapshot.total == 0) {
                        "Nothing planned today"
                    } else {
                        "All done today"
                    },
                    style = TextStyle(color = WidgetLook.Dim, fontSize = 13.sp)
                )
            } else {
                visible.forEach { row -> WidgetTaskRow(row) }
                val hidden = snapshot.rows.size - visible.size
                if (hidden > 0) {
                    Text(
                        text = "and $hidden more",
                        style = TextStyle(color = WidgetLook.Dim, fontSize = 11.sp),
                        modifier = GlanceModifier.padding(top = 4.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * The shared shell: the rounded dark surface, and a tap anywhere that is not a
 * check circle opens the app.
 *
 * The surface is an Image behind the content rather than a background
 * modifier, because that works the same on every version we support instead of
 * depending on the corner radius API that only arrives at 31.
 */
@Composable
fun WidgetFrame(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_surface),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
            content()
        }
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
