import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:diua/camera_view.dart';

const MethodChannel _cameraPermissionChannel = MethodChannel('camera_permission');

void main() {
  runApp(MaterialApp(
    title: 'Diua',
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      useMaterial3: true,
    ),
    home: const Homepage(),
  ));
}

class Homepage extends StatefulWidget {
  const Homepage({super.key});

  @override
  State<Homepage> createState() => _HomepageState();
}

class _HomepageState extends State<Homepage> {
  bool? isPermissionGranted;

  @override
  void initState() {
    super.initState();
    _checkPermission();
  }

  Future<void> _checkPermission() async {
    try {
      if (Platform.isAndroid) {
        final bool result =
            await _cameraPermissionChannel.invokeMethod<bool>('checkCameraPermission') ?? false;
        setState(() {
          isPermissionGranted = result;
        });
      } else {
        // iOS or other platforms — assume true or implement platform checks
        setState(() {
          isPermissionGranted = true;
        });
      }
    } on PlatformException catch (e) {
      debugPrint('Platform error while checking camera permission: $e');
      setState(() {
        isPermissionGranted = false;
      });
    }
  }

  Future<void> _requestPermission() async {
    try {
      final bool result =
          await _cameraPermissionChannel.invokeMethod<bool>('requestCameraPermission') ?? false;
      setState(() {
        isPermissionGranted = result;
      });
    } on PlatformException catch (e) {
      debugPrint('Platform error while requesting camera permission: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isPermissionGranted == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Hello Flutter')),
      body: isPermissionGranted!
          ? SafeArea(child: CameraView())
          : Center(
              child: ElevatedButton(
                onPressed: _requestPermission,
                child: const Text('Request Camera Permission'),
              ),
            ),
    );
  }
}
