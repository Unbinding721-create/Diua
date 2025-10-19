import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class CameraView extends StatefulWidget {
    const CameraView({super.key});

    @override
    State<CameraView> createState() => _CameraViewState();
}

class _CameraViewState extends State<CameraView> {
    final Map<String, dynamic> creationParams = <String, dynamic>{};

    @override
    Widget build(BuildContext context) {
        return Platform.isAndroid
            ? AndroidView(
                viewType: 'camView',
                layoutDirection: TextDirection.ltr,
                creationParams: creationParams,
                creationParamsCodec: const StandardMessageCodec(),
            ) // Android Viu
        : const Placeholder();
    }
}