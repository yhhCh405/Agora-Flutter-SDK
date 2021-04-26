import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

class ScreenSharingView extends StatefulWidget {
  @override
  _ScreenSharingViewState createState() => _ScreenSharingViewState();
}

class _ScreenSharingViewState extends State<ScreenSharingView> {
  final String viewType = 'yhh.ScreenSharingSurfaceView';

  @override
  Widget build(BuildContext context) {
  return AndroidView(
    viewType: viewType,
    layoutDirection: TextDirection.ltr,
    creationParams: {},
    creationParamsCodec: const StandardMessageCodec(),
  );
  }
}
