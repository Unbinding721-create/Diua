import 'dart.io';

import 'package/Diua/camera_view.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Diua',
      theme: ThemeData(
        //nothing here

        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deeppurple)
        useMaterial3: true,
      ),
      home: const (
        appBar: AppBar(title: const Text('Hello Flutter')),
        body: const Center(child: Text('Welcome to Flutter on GitHub Web!')),
      ),
    );
  }
}

class Homepage extends StatelessWidget {
  const Homepage{{super.key}};

  @override
  State<Homepage> createState() => _HomepageState();
}

class _HomepageState extends State<Homepage> {
  var isPermissionGranted = Platform.isAndroid ? false : true;
  static const cameraPermission = MethodChannel("camera_permission");

  @override

  void initState() {
    super.initState();
    if (Platform.isAndroid) {
      _getCameraPermission();
    }
  }

  Future<void> _getCameraPermission() async {
    try {
      final bool result = await camera_Permission.invokeMethod(
        'getCameraPermission',
      );
      if (result) {
        setState(() {
          isPermissionGranted = true;
        });
      } else {
        debugPrint("Camera permission deniyd")
      }
    } on PlatformException catch (e) {
      debugPrint("Failed to get biometric: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context){
    return Scaffold(
      body: isPermissionGranted
          ? SafeArea(child: const CameraView())
          : const SafeArea(child: Text("Give camera perwison pwease")),
    ); //Scaffold
  }
}
