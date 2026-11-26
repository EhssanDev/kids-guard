package dev.ehssan.kidsguard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * Cuts the internet for blocked apps. A local VPN is created that only captures the
 * blocked apps' traffic; every packet is read and thrown away, so those apps get no network.
 * Nothing is forwarded and nothing leaves the phone.
 */
class NetBlockService : VpnService() {

    companion object {
        @Volatile var active = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pfd: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var current: Set<String>? = null

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(tick)
        update()
        handler.postDelayed(tick, 20_000)
        return START_STICKY
    }

    private fun update() {
        if (!Rules.netBlock(this)) {
            closeTunnel()
            stopSelf()
            return
        }
        val set = Rules.ruled(this).filter { Rules.blockedNow(this, it) }.toSet()
        if (pfd != null && set == current) return
        closeTunnel()

        val b = Builder()
            .setSession("Kids Guard")
            .addAddress("10.77.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:77::2", 128)
            .addRoute("::", 0)
            .setBlocking(true)
        var added = 0
        for (p in set) {
            try {
                b.addAllowedApplication(p)
                added++
            } catch (e: Exception) {
                // app no longer installed
            }
        }
        if (added == 0) {
            // never route everyone: capture only ourselves, which needs no network
            try {
                b.addAllowedApplication(packageName)
            } catch (e: Exception) {
                return
            }
        }
        val p: ParcelFileDescriptor? = try {
            b.establish()
        } catch (e: Exception) {
            null
        }
        if (p == null) return
        pfd = p
        current = set
        active = true
        val t = Thread {
            try {
                val input = FileInputStream(p.fileDescriptor)
                val buf = ByteArray(32768)
                while (input.read(buf) >= 0) {
                    // drop
                }
            } catch (e: Exception) {
                // tunnel closed
            }
        }
        t.isDaemon = true
        t.start()
        reader = t
    }

    private fun closeTunnel() {
        reader?.interrupt()
        reader = null
        try {
            pfd?.close()
        } catch (e: Exception) {
            // ignore
        }
        pfd = null
        current = null
        active = false
    }

    override fun onRevoke() {
        closeTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        closeTunnel()
        super.onDestroy()
    }
}

object NetBlock {
    /** Starts / refreshes the blocking VPN if the parent enabled it and already granted VPN consent. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        if (!Rules.netBlock(app)) return
        try {
            if (VpnService.prepare(app) != null) return
            app.startService(Intent(app, NetBlockService::class.java))
        } catch (e: Exception) {
            // background start refused; the accessibility tick retries
        }
    }
}
