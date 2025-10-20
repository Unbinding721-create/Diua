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
    _requestPermission();
  }

  Future<void> _requestPermission() async {
    const methodName = 'getCameraPermission'; 
    
    try {
      if (Platform.isAndroid) {
        // Calling this will trigger the Kotlin code to check, and if needed, request permissions.
        final bool result =
            await _cameraPermissionChannel.invokeMethod<bool>(methodName) ?? false;
        
        setState(() {
          isPermissionGranted = result;
        });

      } else {
        setState(() {
          isPermissionGranted = true;
        });
      }
    } on PlatformException catch (e) {
      debugPrint('Platform error during camera permission request: $e');
      setState(() {
        isPermissionGranted = false;
      });
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
