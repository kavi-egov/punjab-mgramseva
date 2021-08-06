import 'package:mgramseva/screeens/Profile/EditProfile.dart';
import 'package:mgramseva/screeens/ChangePassword/Changepassword.dart';
import 'package:mgramseva/screeens/Home.dart';

class Routes {
  ///Authentication
  static const String LANDING_PAGE = '/';

  static const String LOGIN = '/login';

  static const String SELECT_LANGUAGE = '/selectLanguage';

  static const String HOME = '/home';

  static const String FORGOT_PASSWORD = 'forgotPassword';

  static const String HOUSE_HOLD = 'household/search';

  static const String HOUSEHOLD = 'household/search';

  static const String EDIT_PROFILE = '/home/editProfile';

  static const String CHANGE_PASSWORD = '/home/editProfile/changepassword';

  static const String UPDATE_PASSWORD = 'updatepassword';

  static const String RESET_PASSWORD = 'resetpassword';

  static const String CONSUMER_SEARCH = 'consumer/search';


  /// Expense
  static const String EXPENSES_ADD = '/home/addExpense';

  static const String EXPENSE_SEARCH = '/home/searchExpense';

  static const String EXPENSE_RESULT = '/home/searchExpense/result';


  static const String HOUSEHOLD_DETAILS = 'household/details';

  static const String DASHBOARD = 'dashboard';

  static const String SEARCH_CONSUMER = 'search/consumer';

  static const String BILL_GENERATE = 'bill/generate';

  static const String CONSUMER_CREATE = 'consumer/create';

  static const String SUCCESS_VIEW = 'success_view';

  /// Consumer
}
