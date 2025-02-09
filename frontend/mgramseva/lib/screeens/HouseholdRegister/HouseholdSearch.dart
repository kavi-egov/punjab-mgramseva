import 'package:buttons_tabbar/buttons_tabbar.dart';
import 'package:flutter/material.dart';
import 'package:mgramseva/model/connection/water_connection.dart';
import 'package:mgramseva/providers/household_register_provider.dart';
import 'package:mgramseva/screeens/HouseholdRegister/HouseholdList.dart';
import 'package:mgramseva/utils/Constants/I18KeyConstants.dart';
import 'package:mgramseva/utils/Locilization/application_localizations.dart';
import 'package:mgramseva/utils/TestingKeys/testing_keys.dart';
import 'package:mgramseva/utils/models.dart';
import 'package:mgramseva/widgets/TextFieldBuilder.dart';
import 'package:mgramseva/widgets/footer.dart';
import 'package:mgramseva/widgets/tab_button.dart';
import 'package:provider/provider.dart';


class HouseholdSearch extends StatefulWidget {

  @override
  _HouseholdSearchState createState() => _HouseholdSearchState();
}

class _HouseholdSearchState extends State<HouseholdSearch> with SingleTickerProviderStateMixin {

  @override
  void initState() {
    WidgetsBinding.instance?.addPostFrameCallback((_) => afterViewBuild());
    super.initState();
  }

  afterViewBuild() {
    var householdRegisterProvider =
    Provider.of<HouseholdRegisterProvider>(context, listen: false);
    householdRegisterProvider.searchController.text = "";
    householdRegisterProvider.onChangeOfTab(context, 0);
  }


  @override
  Widget build(BuildContext context) {
    var householdRegisterProvider = Provider.of<HouseholdRegisterProvider>(context, listen: false);
    return  Column(
        children: [
          SizedBox(height: 20,),
          BuildTextField(
            '',
            householdRegisterProvider.searchController,
            inputBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(25.0),
            ),
            prefixIcon: Padding(
                padding: EdgeInsets.all(10),
                child: Icon(Icons.search_sharp)),
            isFilled: true,
            placeHolder: i18.dashboard.SEARCH_NAME_CONNECTION,
            onChange: (val) => householdRegisterProvider.onSearch(val, context),
            key: Keys.household.HOUSEHOLD_SEARCH,
          ),
          SizedBox(height: 10,),
          _buildTabView(),
          Footer()
        ]
    );
  }

  Widget _buildTabView() {
    return Consumer<HouseholdRegisterProvider>(
        builder: (_, householdRegisterProvider, child)
        {
          var tabList = householdRegisterProvider.getCollectionsTabList(context);

          return  Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child:  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: List.generate(tabList.length, (index) => Padding(padding: EdgeInsets.only(top: 16.0, right: 8.0, bottom: 16.0), child: TabButton(tabList[index], isSelected: householdRegisterProvider.isTabSelected(index), onPressed: () => householdRegisterProvider.onChangeOfTab(context, index))))           ),
                  ),
                ),
                SizedBox(height: 15,),
                TextButton.icon(
                  onPressed: () {
                    householdRegisterProvider.createPdfForAllConnections(context, true);
                  },
                  icon: Icon(Icons.download_sharp),
                  label: Text('${ApplicationLocalizations.of(context).translate(i18.common.DOWNLOAD)} '
                      '(${householdRegisterProvider.getDownloadList()} '
                      '${ApplicationLocalizations.of(context).translate(i18.householdRegister.RECORDS)})',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w400,
                    ),
                    textAlign: TextAlign.left,
                  ),
                ),
                SizedBox(height: 10,),
                Consumer<HouseholdRegisterProvider>(
                  builder: (_, householdRegisterProvider, child) =>
                              HouseholdList()
                      ),
              ],
          );
        });
  }

}