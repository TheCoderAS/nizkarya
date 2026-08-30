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

/** Past this many steps the progress bar stops being readable as steps. */
private const val MAX_SEGMENTS = 12

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
            TodayHeader(snapshot)
            if (snapshot.total > 0) {
                Spacer(GlanceModifier.height(10.dp))
                ProgressTrack(snapshot.done, snapshot.total)
            }
            Spacer(GlanceModifier.height(11.dp))

            if (snapshot.rows.isEmpty()) {
                EmptyBody(snapshot.total)
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
 * The date, the count as a figure you can read across the room, and the add
 * button. Same hierarchy as the app's day header: one number dominates and
 * everything else steps back out of its way.
 */
@Composable
private fun TodayHeader(snapshot: WidgetSnapshot) {
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(1.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${snapshot.done}",
                    style = TextStyle(
                        color = WidgetLook.Text,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = " of ${snapshot.total} done",
                    style = TextStyle(
                        color = WidgetLook.Dim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        // Adding is the other half of a to-do widget. Ticking things off
        // without opening the app is only useful if putting them there does
        // not need the app either.
        AddButton()
    }
}

/** The app's one loud control, flattened into a widget. */
@Composable
private fun AddButton() {
    Box(
        modifier = GlanceModifier
            .size(38.dp)
            .clickable(actionStartActivity<NewTaskActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_add_circle),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.size(34.dp)
        )
        Image(
            provider = ImageProvider(R.drawable.widget_add_on_accent),
            contentDescription = "Add a task",
            modifier = GlanceModifier.size(17.dp)
        )
    }
}

/**
 * Progress as steps rather than a percentage.
 *
 * One segment per item while the day is short enough for that to mean
 * something, so you can see three of five at a glance without reading a
 * number. Past a dozen it stops being countable and the steps just carry the
 * proportion instead.
 */
@Composable
fun ProgressTrack(done: Int, total: Int, habit: Boolean = false) {
    if (total <= 0) return
    val steps = if (total <= MAX_SEGMENTS) total else MAX_SEGMENTS
    // Rounding up, so finishing one thing always lights something up.
    val filled = if (total <= MAX_SEGMENTS) done else (done * steps + total - 1) / total
    val onStep = if (habit) R.drawable.widget_seg_habit else R.drawable.widget_seg_task

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        repeat(steps) { index ->
            if (index > 0) Spacer(GlanceModifier.width(3.dp))
            Image(
                provider = ImageProvider(
                    if (index < filled) onStep else R.drawable.widget_seg_off
                ),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.defaultWeight().height(5.dp)
            )
        }
    }
}

/**
 * The shared shell: the rounded surface with the app's violet lean.
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
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NizKarya",
            style = TextStyle(
                color = WidgetLook.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = "Sign in to see your day",
            style = TextStyle(color = WidgetLook.Dim, fontSize = 12.sp)
        )
    }
}

/** Nothing to show, said in a way that still tells you what to do next. */
@Composable
fun EmptyBody(total: Int) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = if (total == 0) "Nothing planned yet" else "That is the day cleared",
            style = TextStyle(
                color = WidgetLook.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = if (total == 0) "Tap the plus to put something in it" else "Every one of them",
            style = TextStyle(color = WidgetLook.Dim, fontSize = 11.sp)
        )
    }
}

/**
 * One item, drawn as the same block the app's timeline uses: a card, an accent
 * edge that says what kind of thing it is, a check you can tap where it
 * stands, and its time in a tinted pill.
 */
@Composable
fun WidgetTaskRow(row: WidgetRow) {
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Image(
            provider = ImageProvider(
                if (row.done) R.drawable.widget_row_done else R.drawable.widget_row
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(barOf(row)),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.width(3.dp).height(20.dp)
            )
            Spacer(GlanceModifier.width(9.dp))
            Box(
                modifier = GlanceModifier
                    .size(26.dp)
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
                    provider = ImageProvider(checkOf(row)),
                    contentDescription = if (row.done) "Done" else "Mark ${row.title} done",
                    modifier = GlanceModifier.size(19.dp)
                )
            }
            Spacer(GlanceModifier.width(6.dp))
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
                Spacer(GlanceModifier.width(7.dp))
                TimeChip(row)
            }
        }
    }
}

/** The time, in a pill tinted by what the row is. */
@Composable
private fun TimeChip(row: WidgetRow) {
    Box(contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(chipOf(row)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )
        Text(
            text = row.time.orEmpty(),
            modifier = GlanceModifier.padding(
                start = 7.dp, end = 7.dp, top = 3.dp, bottom = 3.dp
            ),
            style = TextStyle(
                color = rowTint(row),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

fun rowTint(row: WidgetRow): ColorProvider = when {
    row.done -> WidgetLook.Dim
    row.late -> WidgetLook.Late
    row.isHabit -> WidgetLook.Habit
    else -> WidgetLook.Task
}

private fun barOf(row: WidgetRow): Int = when {
    row.done -> R.drawable.widget_bar_done
    row.late -> R.drawable.widget_bar_late
    row.isHabit -> R.drawable.widget_bar_habit
    else -> R.drawable.widget_bar_task
}

private fun checkOf(row: WidgetRow): Int = when {
    row.done && row.isHabit -> R.drawable.widget_check_on_habit
    row.done -> R.drawable.widget_check_on
    row.late -> R.drawable.widget_check_late
    else -> R.drawable.widget_check_off
}

private fun chipOf(row: WidgetRow): Int = when {
    row.done -> R.drawable.widget_chip_dim
    row.late -> R.drawable.widget_chip_late
    row.isHabit -> R.drawable.widget_chip_habit
    else -> R.drawable.widget_chip_task
}
