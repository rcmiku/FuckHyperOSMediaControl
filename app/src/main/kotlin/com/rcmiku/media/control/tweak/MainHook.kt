package com.rcmiku.media.control.tweak

import android.app.AndroidAppHelper
import android.content.Context
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
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.ClassUtils.newInstance
import com.github.kyuubiran.ezxhelper.EzXHelper
import com.github.kyuubiran.ezxhelper.EzXHelper.moduleRes
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createAfterHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createBeforeHook
import com.github.kyuubiran.ezxhelper.Log
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.rcmiku.media.control.tweak.components.SquigglyProgress
import com.rcmiku.media.control.tweak.utils.AppUtils.blur
import com.rcmiku.media.control.tweak.utils.AppUtils.dp
import com.rcmiku.media.control.tweak.utils.AppUtils.drawableToBitmap
import com.rcmiku.media.control.tweak.utils.AppUtils.handleImageEffect
import com.rcmiku.media.control.tweak.utils.AppUtils.mesh
import com.rcmiku.media.control.tweak.utils.AppUtils.scale
import com.rcmiku.media.control.tweak.utils.AppUtils.zoom
import com.rcmiku.media.control.tweak.utils.CalculationUtils.setAlphaComponent
import com.rcmiku.media.control.tweak.utils.MeshUtil
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors

class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private var pkg: String? = null
    private var mMediaViewHolder: Any? = null
    private val white = Color.WHITE
    private val blur = RenderEffect.createBlurEffect(
        100f,
        100f,
        Shader.TileMode.CLAMP
    )
    private var alpha = 180
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
                        loadClass("com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl")
                    else
                        loadClass("com.android.systemui.media.controls.pipeline.MediaDataManager")

                val customAction =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClass("com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl\$createActionsFromState\$customActions\$1")
                    else
                        loadClass("com.android.systemui.media.controls.pipeline.MediaDataManager\$createActionsFromState\$customActions\$1")

                val notificationSettingsManager =
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        loadClass("com.miui.systemui.notification.NotificationSettingsManager")
                    else
                        loadClass("com.android.systemui.statusbar.notification.NotificationSettingsManager")

                val seekBarObserver =
                    loadClassOrNull("com.android.systemui.media.controls.ui.binder.SeekBarObserver")

                val miuiMediaControlPanel =
                    loadClass("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel")

                val colorSchemeTransition =
                    loadClassOrNull("com.android.systemui.media.controls.ui.animation.ColorSchemeTransition")

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

                seekBarObserver?.methodFinder()?.filterByName("onChanged")?.firstOrNull()
                    ?.createAfterHook {
                        mMediaViewHolder?.let { holder ->
                            val seekBar =
                                holder.objectHelper().getObjectOrNullAs<SeekBar>("seekBar")
                            val playing =
                                it.args[0].objectHelper().getObjectOrNullAs<Boolean>("playing")

                            val squigglyProgress = seekBar?.progressDrawable as? SquigglyProgress
                            squigglyProgress?.let { progress ->
                                if (playing != null && progress.animate != playing) {
                                    progress.animate = playing
                                }
                            }
                        }
                    }

                mediaViewHolder?.constructors?.first()?.createAfterHook {
                    val seekBar =
                        it.thisObject.objectHelper()
                            .getObjectOrNullAs<SeekBar>("seekBar")
                    seekBar?.setPadding(5.dp, 0.dp, 5.dp, 0.dp)
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        seekBar?.progressDrawable = SquigglyProgress().also { squigglyProgress ->
                            squigglyProgress.waveLength =
                                moduleRes.getDimension(R.dimen.media_seekbar_progress_wavelength)
                            squigglyProgress.lineAmplitude =
                                moduleRes.getDimension(R.dimen.media_seekbar_progress_amplitude)
                            squigglyProgress.phaseSpeed =
                                moduleRes.getDimension(R.dimen.media_seekbar_progress_phase)
                            squigglyProgress.strokeWidth =
                                moduleRes.getDimension(R.dimen.media_seekbar_progress_stroke_width)
                            squigglyProgress.transitionEnabled = true
                            squigglyProgress.animate = true
                        }
                    } else {
                        seekBar?.progressDrawable =
                            loadClassOrNull("com.android.systemui.media.controls.ui.SquigglyProgress")?.let { squigglyProgress ->
                                newInstance(
                                    squigglyProgress
                                )
                            } as Drawable
                    }

                    seekBar?.thumb =
                        moduleRes.getDrawable(R.drawable.ic_thumb, moduleRes.newTheme())
                }

                notificationSettingsManager.constructors.first().createAfterHook {
                    it.thisObject.objectHelper().setObject("mMediaAppWhiteList", ArrayList<Any>())
                    it.thisObject.objectHelper()
                        .setObject("mHiddenCustomActionsList", ArrayList<Any>())
                }

                colorSchemeTransition?.methodFinder()?.filterByName("loadDefaultColor")?.first()
                    ?.createBeforeHook {
                        it.result = Color.WHITE
                    }

                mediaDataManager.methodFinder().filterByName(
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        "createActionsFromState$1"
                    else
                        "createActionsFromState"
                ).first()
                    .createBeforeHook {
                        pkg = it.args[0] as String
                    }

                miuiMediaControlPanel.methodFinder().filterByName("bindPlayer").first()
                    .createAfterHook {
                        mMediaViewHolder = it.thisObject.objectHelper()
                            .getObjectOrNullUntilSuperclass("mMediaViewHolder")
                            ?: return@createAfterHook
                        val context = AndroidAppHelper.currentApplication().applicationContext
                        val mIsArtworkUpdate = it.thisObject.objectHelper()
                            .getObjectOrNullAs<Boolean>("mIsArtworkUpdate")
                        if (mMediaViewHolder != null) {
                            val appIcon =
                                mMediaViewHolder!!.objectHelper()
                                    .getObjectOrNullAs<ImageView>("appIcon")
                            val artwork =
                                it.args[0].objectHelper().getObjectOrNullAs<Icon>("artwork")
                            val mediaBg =
                                mMediaViewHolder!!.objectHelper()
                                    .getObjectOrNullAs<ImageView>("mediaBg")

                            appIcon?.drawable?.setTint(Color.TRANSPARENT)
                            val loadDrawable = artwork?.loadDrawable(context)

                            if (loadDrawable != null && mIsArtworkUpdate == true) {
                                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    val albumView = mMediaViewHolder!!.objectHelper()
                                        .getObjectOrNullAs<ImageView>("albumView")

                                    albumView?.outlineProvider = object : ViewOutlineProvider() {
                                        override fun getOutline(view: View, outline: Outline) {
                                            val rect = 2.dp
                                            outline.setRoundRect(
                                                rect,
                                                rect,
                                                view.width - rect,
                                                view.height - rect,
                                                8.dp.toFloat()
                                            )
                                            view.elevation = 10.dp.toFloat()
                                        }
                                    }
                                    albumView?.setClipToOutline(true)
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
                                        mediaBg?.setClipToOutline(true)
                                        mediaBg?.setImageBitmap(processedBitmap)
                                        mediaBg?.colorFilter = colorFilter
                                        mediaBg?.setRenderEffect(blur)
                                    }
                                }
                            }
                        }
                    }

                miuiMediaControlPanel.methodFinder().filterByName("setForegroundColors")
                    .firstOrNull()
                    ?.createBeforeHook {
                        if (mMediaViewHolder != null) {
                            val titleText =
                                mMediaViewHolder!!.objectHelper()
                                    .getObjectOrNullAs<TextView>("titleText")
                            val artistText = mMediaViewHolder!!.objectHelper()
                                .getObjectOrNullAs<TextView>("artistText")
                            val seamlessIcon = mMediaViewHolder!!.objectHelper()
                                .getObjectOrNullAs<ImageView>("seamlessIcon")
                            val elapsedTimeView = mMediaViewHolder!!.objectHelper()
                                .getObjectOrNullAs<TextView>("elapsedTimeView")
                            val totalTimeView = mMediaViewHolder!!.objectHelper()
                                .getObjectOrNullAs<TextView>("totalTimeView")

                            titleText?.setTextColor(white)
                            artistText?.setTextColor(setAlphaComponent(white, alpha))
                            seamlessIcon?.setColorFilter(setAlphaComponent(white, alpha))
                            elapsedTimeView?.setTextColor(setAlphaComponent(white, alpha))
                            totalTimeView?.setTextColor(setAlphaComponent(white, alpha))
                        }
                        it.result = null
                    }

                customAction.methodFinder().filterByName("invoke").first().createAfterHook {
                    val mCustomAction = it.args[0] as CustomAction
                    val mediaAction = it.result
                    val mMediaDataManager = it.thisObject.objectHelper().getObjectOrNull("this$0")
                    val context =
                        mMediaDataManager?.objectHelper()?.getObjectOrNull("context") as Context
                    val createWithResource = Icon.createWithResource(pkg, mCustomAction.icon)
                    var loadDrawable = createWithResource.loadDrawable(context)
                    var resizeHeight = loadDrawable?.intrinsicHeight
                    var resizeWidth = loadDrawable?.intrinsicWidth
                    if (pkg == "com.apple.android.music" && (40.dp < resizeHeight!! && 40.dp < resizeWidth!!)) {
                        resizeHeight /= 2
                        resizeWidth /= 2
                    }
                    if (40.dp > resizeHeight!! && 40.dp > resizeWidth!!) {
                        val bitmap = drawableToBitmap(loadDrawable!!)
                        loadDrawable = BitmapDrawable(
                            context.resources, bitmap.scale(resizeHeight, resizeWidth)
                        )
                        mediaAction.objectHelper().setObject("icon", loadDrawable)
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
            }

            else -> return
        }
    }
}