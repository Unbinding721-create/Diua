import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:diua/camera_view.dart';

const MethodChannel _cameraPermissionChannel = MethodChannel('camera_permission');
const MethodChannel _debugChannel = MethodChannel('diua_debug');

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
  String? debugMessage;

  @override
  void initState() {
    super.initState();
    _requestPermission();
    _debugChannel.setMethodCallHandler(_handleDebugMessage);
  }

  Future<dynamic> _handleDebugMessage(MethodCall call) async {
    if (call.method == 'debugMessage' && call.arguments is String) {
      if (mounted) {
        setState(() {
          debugMessage = call.arguments;
        });
        
        // Use a "simple" ScaffoldMessenger to display the message
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(call.arguments),
            duration: Duration(seconds: 2), // Show for 3 seconds
          ),
        );
      }
    }
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
      appBar: AppBar(title: const Text('Diua')),
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
