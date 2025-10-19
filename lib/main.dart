import 'dart.io';

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
      home: Scaffold(
        appBar: AppBar(title: const Text('Hello Flutter')),
        body: const Center(child: Text('Welcome to Flutter on GitHub Web!')),
      ),
    );
  }
}
