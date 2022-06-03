package com.homex.safe_clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription.*
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

class SafeClipboardPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "safe_clipboard")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "getClipboardTextSafe") {
            getClipboardTextSafe(call, result)
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    private fun getClipboardTextSafe(call: MethodCall, result: Result) {
        activity?.let { activity ->

            // First check if we have content on the clipboard at all
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                result.success(null)
                return
            }

            // We only need to check the specific mime type if running on Android 12+ (SDK 31) since
            // the toast doesn't show on lower versions.
            if (Build.VERSION.SDK_INT >= 31) {
                val desiredMimeTypeName = call.argument<String?>("AndroidClipMimeType")
                val confidentScoreCheckValue = call.argument<String?>("AndroidConfidentScoreValue")

                desiredMimeTypeName?.let {
                    val clipDescription = clipboard.primaryClipDescription
                    val mimeType = clipDescription?.getMimeType(0)

                    val desiredMimeType = when (desiredMimeTypeName) {
                        "textHtml" -> MIMETYPE_TEXT_HTML
                        "intent" -> MIMETYPE_TEXT_INTENT
                        "textPlain" -> MIMETYPE_TEXT_PLAIN
                        "uriList" -> MIMETYPE_TEXT_URILIST
                        "unknown" -> MIMETYPE_UNKNOWN
                        else -> null
                    }

                    if (desiredMimeType != mimeType) {
                        result.success(null)
                        return
                    }
                }

                confidentScoreCheckValue?.let {
                    val confidentScore = clipboard.primaryClipDescription?.getConfidenceScore(confidentScoreCheckValue)

                    confidentScore?.let {
                        if(confidentScore < 0.6f) {
                            result.success(null)
                            return
                        }
                    }
                }

            }

            // Get and return the clip data as a string
            val context = activity
            val primaryClip: ClipData? = clipboard.primaryClip
            primaryClip?.let { clipData ->
                if (clipData.itemCount > 0) {
                    val text: CharSequence = clipData.getItemAt(0).coerceToText(context)
                    if (text.isNotEmpty()) {
                        result.success(text.toString())
                        return
                    }
                }
            }
        }

        result.success(null)
    }
}
