package com.example.mindful_diary

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.PendingIntent
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PlannerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Обработка тапа на пункт плана — переключаем done
        if (intent.action == ACTION_TOGGLE_DONE) {
            val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
            togglePlanDone(context, planId)

            // Обновляем все виджеты
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

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.planner_widget)

            val prefs: SharedPreferences = context.getSharedPreferences(
                "FlutterSharedPreferences", Context.MODE_PRIVATE
            )
            val plansJson = prefs.getString("flutter.plans", "{}") ?: "{}"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            try {
                val allPlans = JSONObject(plansJson)
                val todayPlans = allPlans.optJSONArray(today)
                val count = todayPlans?.length() ?: 0

                if (count == 0) {
                    // Нет планов — показываем заглушку
                    views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)

                    // Настраиваем RemoteViewsService для ListView
                    val serviceIntent = Intent(context, PlannerWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                    }
                    views.setRemoteAdapter(R.id.widget_list, serviceIntent)
                    views.setEmptyView(R.id.widget_list, R.id.widget_empty)

                    // PendingIntent-шаблон для тапа на пункт (переключение done)
                    val toggleIntent = Intent(context, PlannerWidget::class.java).apply {
                        action = ACTION_TOGGLE_DONE
                    }
                    val togglePi = PendingIntent.getBroadcast(
                        context, 0, toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    views.setPendingIntentTemplate(R.id.widget_list, togglePi)
                }
            } catch (e: Exception) {
                views.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            }

            // Тап на заголовок → открыть приложение на экране планировщика
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_planner", true)
            }
            val openPi = PendingIntent.getActivity(
                context, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Переключить done у плана по id
        private fun togglePlanDone(context: Context, planId: String) {
            val prefs = context.getSharedPreferences(
                "FlutterSharedPreferences", Context.MODE_PRIVATE
            )
            val plansJson = prefs.getString("flutter.plans", "{}") ?: "{}"
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            try {
                val allPlans = JSONObject(plansJson)
                val todayPlans = allPlans.optJSONArray(today) ?: return

                for (i in 0 until todayPlans.length()) {
                    val plan = todayPlans.getJSONObject(i)
                    if (plan.optString("id") == planId) {
                        plan.put("done", !plan.optBoolean("done", false))
                        break
                    }
                }

                allPlans.put(today, todayPlans)
                prefs.edit().putString("flutter.plans", allPlans.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
