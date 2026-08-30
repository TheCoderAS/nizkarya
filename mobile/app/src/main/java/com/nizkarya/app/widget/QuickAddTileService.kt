package com.nizkarya.app.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.nizkarya.app.VoiceAddActivity

/**
 * A Quick Settings tile that captures a task by voice.
 *
 * The shade is reachable from inside any other app, which makes it the fastest
 * surface on the phone for the one thing worth doing without losing your
 * place: getting a thought out of your head and into the list.
 */
class QuickAddTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, VoiceAddActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Android 14 replaced the Intent overload with a PendingIntent one and
        // made the old form throw rather than deprecate quietly, so this has to
        // branch rather than suppress.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
