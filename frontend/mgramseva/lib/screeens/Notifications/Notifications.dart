import 'package:flutter/material.dart';
import 'package:mgramseva/providers/common_provider.dart';
import 'package:mgramseva/providers/notification_screen_provider.dart';
import 'package:mgramseva/utils/Locilization/application_localizations.dart';
import 'package:mgramseva/utils/error_logging.dart';
import 'package:mgramseva/utils/global_variables.dart';
import 'package:mgramseva/utils/loaders.dart';
import 'package:mgramseva/utils/notifyers.dart';
import 'package:mgramseva/widgets/DrawerWrapper.dart';
import 'package:mgramseva/widgets/FormWrapper.dart';
import 'package:mgramseva/widgets/HomeBack.dart';
import 'package:mgramseva/widgets/ListLabelText.dart';
import 'package:mgramseva/widgets/Notifications.dart';
import 'package:mgramseva/widgets/SideBar.dart';
import 'package:mgramseva/widgets/customAppbar.dart';
import 'package:mgramseva/widgets/footer.dart';
import 'package:mgramseva/widgets/pagination.dart';
import 'package:provider/provider.dart';
import 'package:mgramseva/utils/Constants/I18KeyConstants.dart';
class NotificationScreen extends StatefulWidget {

  State<StatefulWidget> createState() {
    return _NotificationScreen();
  }
}

///All Notifications Screen

class _NotificationScreen extends State<NotificationScreen> {

  void initState() {
    WidgetsBinding.instance?.addPostFrameCallback((_) => afterViewBuild());
    super.initState();
  }
  afterViewBuild(){
    var commonProvider =
    Provider.of<CommonProvider>(context, listen: false);
    var notificationProvider = Provider.of<NotificationScreenProvider>(context, listen: false);
    try {
      Provider.of<NotificationScreenProvider>(context, listen: false)
        ..limit = 10
        ..offset = 1
        ..totalCount = 0
        ..notifications.clear()
        ..getNotifications(notificationProvider.offset, notificationProvider.limit);
    }
    catch (e, s) {
      ErrorHandler().allExceptionsHandler(navigatorKey.currentContext!, e);
    }
  }

  @override
  Widget build(BuildContext context) {
    var notificationProvider =
    Provider.of<NotificationScreenProvider>(context, listen: false);
    return Scaffold(
        appBar: CustomAppBar(),
    drawer: DrawerWrapper(
    Drawer(child: SideBar()),
    ),
    body: LayoutBuilder(
        builder: (context, constraints) => Container(
        alignment: Alignment.center,
        margin: constraints.maxWidth < 760
        ? null
            : EdgeInsets.symmetric(
        horizontal: MediaQuery.of(context).size.width / 25),
    child: FormWrapper(
        Stack(
          children: [
            SingleChildScrollView(
                child: Container(
                    child: Column(
                  children: [
                    HomeBack(),
                    StreamBuilder(
                      stream: notificationProvider.streamController.stream,
                          builder: (context, AsyncSnapshot snapshot) {
                      if (snapshot.hasData) {
                      return buildNotificationsView(snapshot.data ?? []);
                      } else if (snapshot.hasError) {
                      return Notifiers.networkErrorPage(context, () {});
                      } else {
                      switch (snapshot.connectionState) {
                      case ConnectionState.waiting:
                      return Loaders.CircularLoader();
                      case ConnectionState.active:
                      return Loaders.CircularLoader();
                      default:
                      return Container();
                      }
                      }
                      }),
                    Padding(padding: EdgeInsets.only(bottom: 32.0), child: Footer()),
                  ]))
            ),
            Align(
                alignment: Alignment.bottomRight,
                child: Consumer<NotificationScreenProvider>(
                    builder: (_, notificationProvider, child) {
                      var totalCount =
                          notificationProvider.totalCount ;
                      return Visibility(
                          visible: totalCount > 0,
                          child: Pagination(
                            limit: notificationProvider.limit,
                            offSet: notificationProvider.offset,
                            callBack: (pageResponse) => notificationProvider
                                .onChangeOfPageLimit(pageResponse),
                            totalCount: totalCount, isDisabled: notificationProvider.enableNotification));
                    })
            ),
          ])

    )),

    ));
  }

  buildNotificationsView(List events) {
    return LayoutBuilder(builder: (context, constraints) {
      return Column(mainAxisSize: MainAxisSize.min, children: [
        Consumer<NotificationScreenProvider>(
          builder: (_, notificationProvider, child) => Visibility(
            visible: notificationProvider.totalCount > 0,
            child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  ListLabelText(ApplicationLocalizations.of(context)
                      .translate(i18.common.ALL_NOTIFICATIONS) +
                      " (" +
                      notificationProvider.totalCount.toString() +
                      ")"),
                ]),
          ),
        ),
        ListView.builder(
            shrinkWrap: true,
            physics: NeverScrollableScrollPhysics(),
            itemCount: events.length,
            itemBuilder: (context, i) {
              var item = events[i];
              return Notifications(item, null, false);
            }),
      ]);
    });
  }
}
