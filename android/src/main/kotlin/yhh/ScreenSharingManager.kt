package yhh

import android.content.ComponentName
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import io.agora.agora_rtc_engine.AgoraRtcEnginePlugin
import io.agora.rtc.Constants.*
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.mediaio.AgoraDefaultSource
import io.agora.rtc.models.ChannelMediaOptions
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoEncoderConfiguration
import io.flutter.embedding.android.FlutterActivity
import yhh.component.Constant.ENGINE
import yhh.externvideosource.ExternalVideoInputManager
import yhh.externvideosource.ExternalVideoInputService
import yhh.externvideosource.IExternalVideoInputService

class ScreenSharingManager(private val rtcEnginePlugin: AgoraRtcEnginePlugin,
                           val activity: FlutterActivity,
                           private val appId: String,
                           private var accessToken: String?) {
  private var mService: IExternalVideoInputService? = null
  private var curMirrorMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
  private val iRtcEngineEventHandler = YIRtcEngineEventHandler()
  private var mServiceConnection: VideoInputServiceConnection? = null
  private var curRenderMode = RENDER_MODE_HIDDEN
  private var remoteUid = -1
  var remoteView: FrameLayout = FrameLayout(activity)
  var localView: FrameLayout = FrameLayout(activity)
  private val mHandler = Handler(Looper.getMainLooper())

  companion object {
    const val PROJECTION_REQ_CODE = 111
    const val DEFAULT_SHARE_FRAME_RATE = 15
  }

  init {
    Log.i("yhh", "Initializing Screenshare manager...")
    if (ENGINE == null) {
      try {
        ENGINE = RtcEngine.create(
          activity,
          appId,
          iRtcEngineEventHandler
        )
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private var engine: RtcEngine? =
//    ENGINE
    rtcEnginePlugin.engine()!!


  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    Log.i("yhh", "Activity result!!!")

    try {
      val metrics = DisplayMetrics()
      activity.windowManager.defaultDisplay.getMetrics(metrics)
      var percent = 0f
      val hp = metrics.heightPixels.toFloat() - 1920f
      val wp = metrics.widthPixels.toFloat() - 1080f
      percent = if (hp < wp) {
        (metrics.widthPixels.toFloat() - 1080f) / metrics.widthPixels.toFloat()
      } else {
        (metrics.heightPixels.toFloat() - 1920f) / metrics.heightPixels.toFloat()
      }
      metrics.heightPixels =
        (metrics.heightPixels.toFloat() - metrics.heightPixels * percent).toInt()
      metrics.widthPixels =
        (metrics.widthPixels.toFloat() - metrics.widthPixels * percent).toInt()
      data!!.putExtra(ExternalVideoInputManager.FLAG_SCREEN_WIDTH, metrics.widthPixels)
      data.putExtra(ExternalVideoInputManager.FLAG_SCREEN_HEIGHT, metrics.heightPixels)
      data.putExtra(
        ExternalVideoInputManager.FLAG_SCREEN_DPI,
        metrics.density.toInt()
      )
      data.putExtra(
        ExternalVideoInputManager.FLAG_FRAME_RATE,
        ScreenSharingManager.DEFAULT_SHARE_FRAME_RATE
      )
      setVideoConfig(
        ExternalVideoInputManager.TYPE_SCREEN_SHARE,
        metrics.widthPixels,
        metrics.heightPixels
      )
      mService!!.setExternalVideoInput(ExternalVideoInputManager.TYPE_SCREEN_SHARE, data)
    } catch (e: RemoteException) {
      e.printStackTrace()
    }
  }

  private fun addLocalPreview() {

    // Create render view by RtcEngine
    val localSurface = RtcEngine.CreateRendererView(activity)

    if (localView.childCount > 0) {
      localView.removeAllViews()
    }
    // Add to the local container
    localView.addView(
      localSurface,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )


    // Setup local video to render your local camera preview
    engine!!.setupLocalVideo(VideoCanvas(localSurface, RENDER_MODE_HIDDEN, 0))
  }

  private fun setRemotePreview(context: Context) {
//        /**Display remote video stream */
    val remoteSurface = RtcEngine.CreateRendererView(context)
//    remoteView!!.setZOrderMediaOverlay(true)

    if (remoteView.childCount > 0) {
      remoteView.removeAllViews()
    }
    remoteView.addView(
      remoteSurface, ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    )


    /**Setup remote video to render */
    engine!!.setupRemoteVideo(VideoCanvas(remoteSurface, curRenderMode, remoteUid))
  }

  fun joinChannel(
    channelId: String,
    clientRole: Int
  ): Int {
    if (clientRole == CLIENT_ROLE_AUDIENCE) {
      setRemotePreview(activity)
      addLocalPreview()
    }
    if (clientRole == CLIENT_ROLE_BROADCASTER) {
      bindVideoService()
      addLocalPreview()

    }
    engine!!.setParameters("{\"che.video.mobile_1080p\":true}")
    engine!!.setChannelProfile(CHANNEL_PROFILE_LIVE_BROADCASTING)
    engine!!.setClientRole(clientRole)
    engine!!.enableVideo()
    engine!!.setVideoSource(AgoraDefaultSource())
    engine!!.setDefaultAudioRoutetoSpeakerphone(false)
    engine!!.setEnableSpeakerphone(false)

    if (TextUtils.equals(accessToken, "") || TextUtils.equals(
        accessToken,
        "<#YOUR ACCESS TOKEN#>"
      )
    ) {
      accessToken = null
    }
    val option = ChannelMediaOptions()
//        option.autoSubscribeAudio = true
    option.autoSubscribeVideo = true

    val res: Int =
      engine!!.joinChannel(accessToken, channelId, "Extra Optional Data", 0, option)
    if (res != 0) {
      // Usually happens with invalid parameters
      // Error code description can be found at:
      // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
      // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
//            showAlert(RtcEngine.getErrorDescription(Math.abs(res)))
      return -1
    }
    return 1

  }

  fun leave() {
    engine!!.leaveChannel()
  }

  fun destroy() {
    unbindVideoService()
//    TEXTUREVIEW = null
    if (engine != null) {
      engine!!.leaveChannel()
    }
    mHandler.post { RtcEngine.destroy() }
    engine = null
  }

  private fun bindVideoService() {
    Log.i("yhh", "Binding video service...")
    val intent = Intent()
    intent.setClass(activity, ExternalVideoInputService::class.java)
    mServiceConnection = VideoInputServiceConnection()
    activity.bindService(intent, mServiceConnection!!, Context.BIND_AUTO_CREATE)
  }

  private fun unbindVideoService() {
    if (mServiceConnection != null) {
      activity.unbindService(mServiceConnection!!)
      mServiceConnection = null
    }
  }

  private fun setVideoConfig(sourceType: Int, width: Int, height: Int) {
    curMirrorMode =
      when (sourceType) {
        ExternalVideoInputManager.TYPE_LOCAL_VIDEO, ExternalVideoInputManager.TYPE_SCREEN_SHARE -> VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        else -> VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
      }
    Log.e("yhh",
      "SDK encoding ->width:$width,height:$height"
    )
    /**Setup video stream encoding configs */
    engine!!.setVideoEncoderConfiguration(
      VideoEncoderConfiguration(
        VideoEncoderConfiguration.VideoDimensions(width, height),
        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
        VideoEncoderConfiguration.STANDARD_BITRATE, curMirrorMode
      )
    )

//        engine!!.setParameters("{\"rtc.log_filter\": 65535}");
  }

  private inner class YIRtcEngineEventHandler : IRtcEngineEventHandler() {
    /**Reports a warning during SDK runtime.
     * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html */
    override fun onWarning(warn: Int) {
      Log.w(
        "yhh",
        String.format(
          "onWarning code %d message %s",
          warn,
          RtcEngine.getErrorDescription(warn)
        )
      )
    }

    /**Reports an error during SDK runtime.
     * Error code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html */
    override fun onError(err: Int) {
      Log.e(
        "yhh",
        String.format("onError code %d message %s", err, RtcEngine.getErrorDescription(err))
      )
//            activity.showAlert(
//                    String.format(
//                            "onError code %d message %s",
//                            err,
//                            RtcEngine.getErrorDescription(err)
//                    )
//            )
    }

    /**Occurs when the local user joins a specified channel.
     * The channel name assignment is based on channelName specified in the joinChannel method.
     * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
     * @param channel Channel name
     * @param uid User ID
     * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered
     */
    override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
      Log.i("yhh", String.format("onJoinChannelSuccess channel %s uid %d", channel, uid))
//            activity.showLongToast(
//                    String.format(
//                            "onJoinChannelSuccess channel %s uid %d",
//                            channel,
//                            uid
//                    )
//            )
//            myUid = uid
//            joined = true
//            handler.post {
//                join.setEnabled(true)
//                join.setText(getString(R.string.leave))
//                camera.setEnabled(false)
//                screenShare.setEnabled(true)
//            }
    }

    override fun onLocalVideoStateChanged(localVideoState: Int, error: Int) {
      super.onLocalVideoStateChanged(localVideoState, error)
      if (localVideoState == 1) {
        Log.e("yhh", "launch successfully")
      }
    }

    /**Since v2.9.0.
     * Occurs when the remote video state changes.
     * PS: This callback does not work properly when the number of users (in the Communication
     * profile) or broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
     * @param uid ID of the remote user whose video state changes.
     * @param state State of the remote video:
     * REMOTE_VIDEO_STATE_STOPPED(0): The remote video is in the default state, probably due
     * to REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3), REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5),
     * or REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7).
     * REMOTE_VIDEO_STATE_STARTING(1): The first remote video packet is received.
     * REMOTE_VIDEO_STATE_DECODING(2): The remote video stream is decoded and plays normally,
     * probably due to REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY (2),
     * REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4), REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6),
     * or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9).
     * REMOTE_VIDEO_STATE_FROZEN(3): The remote video is frozen, probably due to
     * REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1) or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8).
     * REMOTE_VIDEO_STATE_FAILED(4): The remote video fails to start, probably due to
     * REMOTE_VIDEO_STATE_REASON_INTERNAL(0).
     * @param reason The reason of the remote video state change:
     * REMOTE_VIDEO_STATE_REASON_INTERNAL(0): Internal reasons.
     * REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1): Network congestion.
     * REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY(2): Network recovery.
     * REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3): The local user stops receiving the remote
     * video stream or disables the video module.
     * REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote
     * video stream or enables the video module.
     * REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5): The remote user stops sending the video
     * stream or disables the video module.
     * REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the video
     * stream or enables the video module.
     * REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
     * REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8): The remote media stream falls back to the
     * audio-only stream due to poor network conditions.
     * REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9): The remote media stream switches
     * back to the video stream after the network conditions improve.
     * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method until
     * the SDK triggers this callback.
     */
    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
      super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
      Log.i("yhh", "onRemoteVideoStateChanged:uid->$uid, state->$state")
      if (state == REMOTE_VIDEO_STATE_STARTING) {
        /**Check if the context is correct */
        /**Check if the context is correct */
        val context: Context = activity ?: return
        mHandler.post {
          remoteUid = uid
//                    renderMode.setEnabled(true)
//                    renderMode.setText(
//                        java.lang.String.format(
//                            getString(R.string.rendermode),
//                            getString(R.string.hidden)
//                        )
//                    )
          curRenderMode = RENDER_MODE_HIDDEN
          setRemotePreview(context)
        }
      }
    }

    override fun onRemoteVideoStats(stats: RemoteVideoStats) {
      super.onRemoteVideoStats(stats)
      Log.d("yhh", "onRemoteVideoStats: width:" + stats.width + " x height:" + stats.height)
    }

    /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
     * @param uid ID of the user whose audio state changes.
     * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
     * until this callback is triggered.
     */
    override fun onUserJoined(uid: Int, elapsed: Int) {
      super.onUserJoined(uid, elapsed)
      Log.i("yhh", "onUserJoined->$uid")
//            activity.showLongToast(String.format("user %d joined!", uid))
    }

    /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
     * @param uid ID of the user whose audio state changes.
     * @param reason Reason why the user goes offline:
     * USER_OFFLINE_QUIT(0): The user left the current channel.
     * USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
     * packet was received within a certain period of time. If a user quits the
     * call and the message is not passed to the SDK (due to an unreliable channel),
     * the SDK assumes the user dropped offline.
     * USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
     * the host to the audience.
     */
    override fun onUserOffline(uid: Int, reason: Int) {
      Log.i("yhh", String.format("user %d offline! reason:%d", uid, reason))
//            activity.showLongToast(String.format("user %d offline! reason:%d", uid, reason))
      mHandler.post {
        /**Clear render view
         * Note: The video will stay at its last frame, to completely remove it you will need to
         * remove the SurfaceView from its parent */
        engine!!.setupRemoteVideo(VideoCanvas(null, RENDER_MODE_HIDDEN, uid))

      }
    }
  }

  private inner class VideoInputServiceConnection : ServiceConnection {
    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
      Log.i("yhh", "Service connected!!")
      mService = iBinder as IExternalVideoInputService
      /**Start the screen recording service of the system */
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val mpm =
          activity.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        Log.i("yhh", "Requesting media projection permission...")
        activity.startActivityForResult(
          intent,
          PROJECTION_REQ_CODE, null
        )
      }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
      mService = null
    }
  }

}
