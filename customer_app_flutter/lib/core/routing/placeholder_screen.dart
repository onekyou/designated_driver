import 'package:flutter/material.dart';

/// Chunk 3 라우트 골격용 placeholder. Chunk 4+에서 실제 화면으로 교체.
class PlaceholderScreen extends StatelessWidget {
  final String title;
  const PlaceholderScreen({required this.title, super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Text('$title (Chunk 4~에서 구현)'),
      ),
    );
  }
}
