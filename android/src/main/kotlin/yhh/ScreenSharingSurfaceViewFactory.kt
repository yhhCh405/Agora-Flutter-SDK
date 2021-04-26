package yhh

import android.content.Context
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class ScreenSharingSurfaceViewFactory: PlatformViewFactory(StandardMessageCodec.INSTANCE) {
  override fun create(context: Context?, viewId: Int, args: Any?): PlatformView {
    val creationParams = args as Map<String?,Any?>
    return ScreenSharingSurfaceView(context!!,viewId,creationParams)
  }

  inner class ScreenSharingSurfaceView(val context: Context, val id: Int, val creationParams: Map<String?, Any?>?) : PlatformView {
    private val surfaceView: FrameLayout = Const.screenSharingManager?.remoteView!!


    override fun getView(): View {
      return surfaceView
    }

    override fun dispose() {

    }
  }
}
