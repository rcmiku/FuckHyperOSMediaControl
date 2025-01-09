package com.rcmiku.media.control.tweak

import android.app.AndroidAppHelper
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.session.PlaybackState.CustomAction
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.SeekBar
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.ClassUtils.newInstance
import com.github.kyuubiran.ezxhelper.EzXHelper
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createAfterHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createBeforeHook
import com.github.kyuubiran.ezxhelper.Log
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.rcmiku.media.control.tweak.utils.AppUtils.blur
import com.rcmiku.media.control.tweak.utils.AppUtils.dp
import com.rcmiku.media.control.tweak.utils.AppUtils.drawableToBitmap
import com.rcmiku.media.control.tweak.utils.AppUtils.handleImageEffect
import com.rcmiku.media.control.tweak.utils.AppUtils.mesh
import com.rcmiku.media.control.tweak.utils.AppUtils.scale
import com.rcmiku.media.control.tweak.utils.AppUtils.zoom
import com.rcmiku.media.control.tweak.utils.MeshUtil
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors

class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private var packageName: String? = null
    private var mMediaViewHolder: Any? = null
    private var artwork: Icon? = null
    private val blur = RenderEffect.createBlurEffect(
        100f,
        100f,
        Shader.TileMode.CLAMP
    )
    private var scale = 0.8f
    private val colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setScale(scale, scale, scale, 1f)
    })

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelper.initZygote(startupParam)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXHelper.initHandleLoadPackage(lpparam)
        EzXHelper.setLogTag("MediaControlTweak")
        when (lpparam.packageName) {
            "com.android.systemui" -> {

                val mediaDataManager =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClassOrNull("com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl")
                    else
                        loadClassOrNull("com.android.systemui.media.controls.pipeline.MediaDataManager")

                val customAction =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClassOrNull("com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl\$createActionsFromState\$customActions\$1")
                    else
                        loadClassOrNull("com.android.systemui.media.controls.pipeline.MediaDataManager\$createActionsFromState\$customActions\$1")

                val notificationSettingsManager =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClassOrNull("com.miui.systemui.notification.NotificationSettingsManager")
                    else
                        loadClassOrNull("com.android.systemui.statusbar.notification.NotificationSettingsManager")

                val miuiMediaControlPanel =
                    loadClassOrNull("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel")

                val mediaViewHolder =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClassOrNull("com.android.systemui.media.controls.ui.view.MediaViewHolder")
                    else
                        loadClassOrNull("com.android.systemui.media.controls.models.player.MediaViewHolder")

                val playerTwoCircleView =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClassOrNull("com.miui.systemui.notification.media.PlayerTwoCircleView")
                    else
                        loadClassOrNull("com.android.systemui.statusbar.notification.mediacontrol.PlayerTwoCircleView")

                mediaDataManager?.methodFinder()?.filterByName(
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        "createActionsFromState$1"
                    else
                        "createActionsFromState"
                )?.firstOrNull()
                    ?.createBeforeHook {
                        packageName = it.args[0] as String
                    }

                mediaViewHolder?.constructors?.firstOrNull()?.createAfterHook {
                    val seekBar =
                        it.thisObject.objectHelper()
                            .getObjectOrNullAs<SeekBar>("seekBar")
                    val mediaBg =
                        it.thisObject.objectHelper()
                            .getObjectOrNullAs<ImageView>("mediaBg")
                    seekBar?.setPadding(5.dp, 0.dp, 5.dp, 0.dp)
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        seekBar?.progressDrawable =
                            loadClassOrNull("com.android.systemui.media.controls.ui.SquigglyProgress")?.let { squigglyProgress ->
                                newInstance(
                                    squigglyProgress
                                )
                            } as Drawable
                    }
                    mediaBg?.colorFilter = colorFilter
                    mediaBg?.setRenderEffect(blur)
                    seekBar?.thumb =
                        moduleRes.getDrawable(R.drawable.ic_thumb, moduleRes.newTheme())
                }

                notificationSettingsManager?.constructors?.firstOrNull()?.createAfterHook {
                    it.thisObject.objectHelper().setObject("mMediaAppWhiteList", ArrayList<Any>())
                    it.thisObject.objectHelper()
                        .setObject("mHiddenCustomActionsList", ArrayList<Any>())
                }

                miuiMediaControlPanel?.methodFinder()?.filterByName("bindPlayer")?.firstOrNull()
                    ?.createAfterHook {
                        mMediaViewHolder = it.thisObject.objectHelper()
                            .getObjectOrNullUntilSuperclass("mMediaViewHolder")
                            ?: return@createAfterHook
                        val appIcon =
                            mMediaViewHolder!!.objectHelper()
                                .getObjectOrNullAs<ImageView>("appIcon")
                        appIcon?.drawable?.setTint(Color.TRANSPARENT)
                        artwork =
                            it.args[0].objectHelper().getObjectOrNullAs<Icon>("artwork")
                    }

                customAction?.methodFinder()?.filterByName("invoke")?.firstOrNull()
                    ?.createAfterHook {
                        val mCustomAction = it.args[0] as CustomAction
                        val mediaAction = it.result
                        val context = AndroidAppHelper.currentApplication().applicationContext
                        if (packageName != null) {
                            val actionIcon =
                                Icon.createWithResource(packageName, mCustomAction.icon)
                            var loadDrawable = actionIcon.loadDrawable(context)
                            var resizeHeight = loadDrawable?.intrinsicHeight
                            var resizeWidth = loadDrawable?.intrinsicWidth
                            if (packageName == "com.apple.android.music" && (40.dp < resizeHeight!! && 40.dp < resizeWidth!!)) {
                                resizeHeight = 24.dp
                                resizeWidth = 24.dp
                            }
                            if (40.dp > resizeHeight!! && 40.dp > resizeWidth!!) {
                                val bitmap = drawableToBitmap(loadDrawable!!)
                                loadDrawable = BitmapDrawable(
                                    context.resources, bitmap.scale(resizeHeight, resizeWidth)
                                )
                                mediaAction.objectHelper().setObject("icon", loadDrawable)
                            }
                        }
                    }

                playerTwoCircleView?.methodFinder()?.filterByName("onDraw")?.firstOrNull()
                    ?.createBeforeHook {
                        val mPaint1 =
                            it.thisObject.objectHelper().getObjectOrNullAs<Paint>("mPaint1")
                        val mPaint2 =
                            it.thisObject.objectHelper().getObjectOrNullAs<Paint>("mPaint2")
                        if (mPaint1?.alpha == 0) return@createBeforeHook
                        mPaint1?.alpha = 0
                        mPaint2?.alpha = 0
                        it.thisObject.objectHelper().setObject("mRadius", 0f)
                    }

                playerTwoCircleView?.methodFinder()?.filterByName("setBackground")?.firstOrNull()
                    ?.createBeforeHook {
                        if (mMediaViewHolder != null) {
                            val view = (it.thisObject as ImageView)
                            val context = AndroidAppHelper.currentApplication().applicationContext
                            val loadDrawable = artwork?.loadDrawable(context)
                            if (loadDrawable != null) {

                                val albumView = mMediaViewHolder!!.objectHelper()
                                    .getObjectOrNullAs<ImageView>("albumView")
                                albumView?.outlineProvider = object : ViewOutlineProvider() {
                                    override fun getOutline(view: View, outline: Outline) {
                                        val clip = 1
                                        outline.setRoundRect(
                                            clip,
                                            clip,
                                            view.width - clip,
                                            view.height - clip,
                                            8.dp.toFloat()
                                        )
                                        view.clipToOutline = true
                                    }
                                }
                                Executors.newSingleThreadExecutor().execute {
                                    val bitmap = drawableToBitmap(loadDrawable)
                                    val processedBitmap = runCatching {
                                        bitmap.zoom().blur(16f).handleImageEffect(2f)
                                            .mesh(MeshUtil.getRandomVertices())
                                    }.onFailure { exception ->
                                        Log.ex(exception)
                                    }.getOrNull()

                                    Handler(Looper.getMainLooper()).post {
                                        view.setImageBitmap(processedBitmap)
                                    }
                                }
                            }
                        }
                    }
            }

            else -> return
        }
    }
}