package com.wakeup

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Widget layar utama: progres habit hari ini + tombol "+1" untuk habit berikutnya yang belum tercapai. */
class HabitWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val v = build(c)
        ids.forEach { m.updateAppWidget(it, v) }
    }

    companion object {
        private val ROWS = listOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4)

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, HabitWidget::class.java))
            if (ids.isEmpty()) return
            val v = build(c)
            ids.forEach { m.updateAppWidget(it, v) }
        }

        private fun build(c: Context): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_habits)
            val list = if (Supa.loggedIn) HabitLocal.all() else emptyList()
            val done = list.count { it.count >= it.target }
            rv.setTextViewText(R.id.widget_title, "Habit hari ini")
            rv.setTextViewText(R.id.widget_sub, when {
                !Supa.loggedIn -> "Masuk dulu di aplikasi"
                list.isEmpty() -> "Belum ada habit"
                else -> "$done dari ${list.size} tercapai"
            })
            ROWS.forEachIndexed { i, id ->
                val h = list.getOrNull(i)
                if (h == null) rv.setViewVisibility(id, View.GONE)
                else {
                    rv.setViewVisibility(id, View.VISIBLE)
                    rv.setTextViewText(id, (if (h.count >= h.target) "✓ " else "○ ") + "${h.icon} ${h.name}  ${h.count}/${h.target}")
                }
            }
            val next = list.firstOrNull { it.count < it.target }
            if (next == null) rv.setViewVisibility(R.id.widget_add, View.GONE)
            else {
                rv.setViewVisibility(R.id.widget_add, View.VISIBLE)
                rv.setTextViewText(R.id.widget_add, "+1 ${next.name}")
                rv.setOnClickPendingIntent(R.id.widget_add, HabitActions.plusOnePending(c, next.id))
            }
            rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 0,
                Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return rv
        }
    }
}
