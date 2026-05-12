package com.example.mindful_diary

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.PendingIntent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PlannerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_DONE) {
            val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
            togglePlanDone(context, planId)
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, PlannerWidget::class.java)
            )
            for (id in ids) updateWidget(context, mgr, id)
        }
    }

    companion object {
        const val ACTION_TOGGLE_DONE = "com.example.mindful_diary.TOGGLE_DONE"
        const val EXTRA_PLAN_ID = "plan_id"

        private val ACCENT_COLORS = intArrayOf(
            0xFFE8927C.toInt(), // orange
            0xFF5B8CDB.toInt(), // blue
            0xFF9B59B6.toInt(), // purple
            0xFF2ECC71.toInt()  // green
        )

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.planner_widget)
            val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

            // Акцентный цвет кнопки +
            val accentIndex = prefs.getInt("flutter.accentIndex",
                prefs.getInt("accentIndex", 0)).coerceIn(0, ACCENT_COLORS.lastIndex)
            views.setInt(R.id.widget_add_btn, "setColorFilter", ACCENT_COLORS[accentIndex])

            // Планы на сегодня
            val plansJson = prefs.getString("flutter.plans",
                prefs.getString("plans", "{}")) ?: "{}"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val text = buildPlanText(plansJson, today)
            views.setTextViewText(R.id.widget_plans, text)

            // Тап на заголовок → планировщик
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_planner", true)
            }
            val openPi = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPi)
            views.setOnClickPendingIntent(R.id.widget_plans, openPi)

            // Тап на + → добавить план
            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_planner", true)
                putExtra("add_plan", true)
            }
            val addPi = PendingIntent.getActivity(
                context, 2, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_btn, addPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildPlanText(plansJson: String, today: String): String {
            return try {
                val allPlans = JSONObject(plansJson)
                val todayPlans = allPlans.optJSONArray(today)

                if (todayPlans == null || todayPlans.length() == 0) {
                    "Нет планов на сегодня\nНажми + чтобы добавить"
                } else {
                    val sb = StringBuilder()
                    for (i in 0 until todayPlans.length()) {
                        val plan = todayPlans.getJSONObject(i)
                        val text = plan.optString("text", "")
                        val time = if (plan.isNull("time")) "" else plan.optString("time", "")
                        val done = plan.optBoolean("done", false)
                        val check = if (done) "✓" else "•"
                        val timePart = if (time.isNotEmpty()) "$time  " else ""
                        sb.append("$check  $timePart$text\n")
                    }
                    sb.toString().trimEnd()
                }
            } catch (e: Exception) {
                "Нет планов на сегодня"
            }
        }

        private fun togglePlanDone(context: Context, planId: String) {
            val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            for (key in listOf("flutter.plans", "plans")) {
                val json = prefs.getString(key, null) ?: continue
                try {
                    val allPlans = JSONObject(json)
                    val todayPlans = allPlans.optJSONArray(today) ?: continue
                    for (i in 0 until todayPlans.length()) {
                        val plan = todayPlans.getJSONObject(i)
                        if (plan.optString("id") == planId) {
                            plan.put("done", !plan.optBoolean("done", false))
                            allPlans.put(today, todayPlans)
                            prefs.edit().putString(key, allPlans.toString()).apply()
                            return
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}
