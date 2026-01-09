package blbl.cat3399.core.log

import android.util.Log

object AppLog {
    private const val PREFIX = "BLBL"

    fun v(tag: String, msg: String, tr: Throwable? = null) = log(Log.VERBOSE, tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null) = log(Log.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = log(Log.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Log.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Log.ERROR, tag, msg, tr)

    private fun log(priority: Int, tag: String, msg: String, tr: Throwable?) {
        val fullTag = "$PREFIX/$tag"
        if (tr == null) {
            Log.println(priority, fullTag, msg)
        } else {
            Log.println(priority, fullTag, "$msg\n${Log.getStackTraceString(tr)}")
        }
    }
}

