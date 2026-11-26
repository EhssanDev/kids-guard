package dev.ehssan.kidsguard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "إيقاف الحماية يسمح بحذف تطبيق Kids Guard."
    }
}
