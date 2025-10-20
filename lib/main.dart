import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:diua/camera_view.dart';

const MethodChannel _cameraPermissionChannel = MethodChannel('camera_permission');
const MethodChannel _debugChannel = MethodChannel('diua_debug');
const MethodChannel _loggingChannel = MethodChannel('diua_logging');

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
    _bootstrapPermissionsAndLogging();
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

  Future<void> _bootstrapPermissionsAndLogging() async {
    try {
      if (Platform.isAndroid) {
        await _loggingChannel.invokeMethod('writeLogLine', 'Bootstrap start');
        // 1) Request storage permission first (no-op on Android 10+)
        await _loggingChannel.invokeMethod<bool>('requestStoragePermission');
        await _loggingChannel.invokeMethod('writeLogLine', 'Storage permission step completed');

        // 2) Ask user where to put the log file (CreateDocument)
        final logUri = await _loggingChannel.invokeMethod<String>('pickLogFile');
        await _loggingChannel.invokeMethod('writeLogLine', 'pickLogFile returned: ${logUri ?? 'null'}');

        if (logUri != null && logUri.isNotEmpty) {
          await _loggingChannel.invokeMethod('writeLogLine', 'Log file selected: $logUri');
        }

        // 3) Now request camera permission
        const methodName = 'getCameraPermission';
        await _loggingChannel.invokeMethod('writeLogLine', 'Requesting camera permission');
        final bool result =
            await _cameraPermissionChannel.invokeMethod<bool>(methodName) ?? false;
        await _loggingChannel.invokeMethod('writeLogLine', 'Camera permission result: $result');

        setState(() {
          isPermissionGranted = result;
        });
      } else {
        setState(() => isPermissionGranted = true);
      }
    } on PlatformException catch (e) {
      debugPrint('Error during bootstrap: $e');
      try { await _loggingChannel.invokeMethod('writeLogLine', 'Bootstrap PlatformException: $e'); } catch (_) {}
      setState(() => isPermissionGranted = false);
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
                onPressed: _bootstrapPermissionsAndLogging,
                child: const Text('Request Camera Permission'),
              ),
            ),
    );
  }
}
