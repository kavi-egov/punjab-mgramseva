import 'package:flutter/material.dart';
import 'package:mgramseva/providers/consumer_details_provider.dart';
import 'package:provider/provider.dart';

class WalkThroughContainer extends StatefulWidget {
  final Function? onnext;

  WalkThroughContainer(this.onnext);
  @override
  State<StatefulWidget> createState() {
    return _WalkhroughContainerState();
  }
}

class _WalkhroughContainerState extends State<WalkThroughContainer> {
  int active = 0;
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<ConsumerProvider>(builder: (_, consumerProvider, child) {
      RenderBox? box = consumerProvider
          .consmerWalkthrougList[consumerProvider.activeindex]
          .key!
          .currentContext!
          .findRenderObject() as RenderBox?;
      Offset position = box!.localToGlobal(Offset.zero);
      print(consumerProvider.activeindex);
      return Stack(children: [
        Positioned(
            left: position.dx,
            top: position.dy,
            child: Container(
                child: Card(
                    child: Column(
              children: [
                consumerProvider
                    .consmerWalkthrougList[consumerProvider.activeindex].widget,
              ],
            )))),
        Positioned(
            right: position.dx,
            top: box.size.height + position.dy,
            child: Container(
                width: MediaQuery.of(context).size.width / 3,
                alignment: Alignment.centerRight,
                padding: EdgeInsets.only(right: 8),
                child: Card(
                    child: Column(children: [
                  Padding(
                      padding: EdgeInsets.all(10),
                      child: Text(
                        consumerProvider
                            .consmerWalkthrougList[consumerProvider.activeindex]
                            .name,
                        style: TextStyle(fontSize: 16),
                      )),
                  Padding(
                      padding: EdgeInsets.all(10),
                      child: Row(
                          mainAxisAlignment: MainAxisAlignment.end,
                          children: [
                            TextButton(
                                onPressed: () async {
                                  widget.onnext!(consumerProvider.activeindex);
                                  await Scrollable.ensureVisible(
                                    consumerProvider
                                        .consmerWalkthrougList[
                                            consumerProvider.activeindex]
                                        .key!
                                        .currentContext!,
                                    duration: new Duration(milliseconds: 100),
                                    alignment: 100.0,
                                  );
                                  setState(() {
                                    active = active + 1;
                                  });
                                },
                                child: const Text('Skip')),
                            ElevatedButton(
                                onPressed: () async {
                                  widget.onnext!(consumerProvider.activeindex);
                                  await Scrollable.ensureVisible(
                                      consumerProvider
                                          .consmerWalkthrougList[
                                              consumerProvider.activeindex]
                                          .key!
                                          .currentContext!,
                                      duration:
                                          new Duration(milliseconds: 100));
                                  setState(() {
                                    active = active + 1;
                                  });
                                },
                                child: const Text('Next'))
                          ]))
                ]))))
      ]);
    });
  }
}
