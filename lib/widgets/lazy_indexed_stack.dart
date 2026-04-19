import 'package:flutter/material.dart';

/// Um IndexedStack que carrega suas children apenas quando elas se tornam ativas (Lazy Loading).
/// Evita instanciar WebViews e telas pesadas no boot do aplicativo.
class LazyIndexedStack extends StatefulWidget {
  final int index;
  final List<Widget> children;
  final AlignmentGeometry alignment;
  final TextDirection? textDirection;
  final StackFit sizing;

  const LazyIndexedStack({
    super.key,
    this.alignment = AlignmentDirectional.topStart,
    this.textDirection,
    this.sizing = StackFit.loose,
    required this.index,
    required this.children,
  });

  @override
  State<LazyIndexedStack> createState() => _LazyIndexedStackState();
}

class _LazyIndexedStackState extends State<LazyIndexedStack> {
  late List<bool> _activated;

  List<bool> _buildInitialActivationList() {
    return List<bool>.generate(
      widget.children.length,
      (index) => index == widget.index,
    );
  }

  List<bool> _rebuildActivationListForResizedChildren() {
    return List<bool>.generate(
      widget.children.length,
      (index) =>
          index == widget.index ||
          (index < _activated.length && _activated[index]),
    );
  }

  void _activateCurrentIndex() {
    if (widget.index < 0 || widget.index >= _activated.length) {
      return;
    }
    _activated[widget.index] = true;
  }

  @override
  void initState() {
    super.initState();
    _activated = _buildInitialActivationList();
  }

  @override
  void didUpdateWidget(LazyIndexedStack oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.children.length != widget.children.length) {
      _activated = _rebuildActivationListForResizedChildren();
      return;
    }
    _activateCurrentIndex();
  }

  @override
  Widget build(BuildContext context) {
    return IndexedStack(
      alignment: widget.alignment,
      textDirection: widget.textDirection,
      sizing: widget.sizing,
      index: widget.index,
      children: List.generate(widget.children.length, (i) {
        if (_activated[i]) {
          return widget.children[i];
        }
        return const SizedBox.shrink();
      }),
    );
  }
}
