package com.tripex.pose.core.logging

/**
 * Thin logging facade so `:app` / `:data` avoid raw `android.util.Log` call sites.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
