import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mgramseva/main.dart' as app;
import 'package:mgramseva/utils/Locilization/application_localizations.dart';
import 'package:mgramseva/utils/TestingKeys/testing_keys.dart';
import 'package:mgramseva/utils/global_variables.dart';
import 'package:mgramseva/widgets/LanguageCard.dart';
import 'package:mgramseva/utils/Constants/I18KeyConstants.dart';

import 'Test Inputs/test_inputs.dart';

void main() {

  testWidgets("Forgot Password Test", (tester) async {
    app.main();
    await tester.pumpAndSettle(Duration(milliseconds: 3000));

    final selectLanguage = find.byType(LanguageCard).at(2);
    final selectLanButton = find.byKey(Keys.language.LANGUAGE_PAGE_CONTINUE_BTN);
    await tester.tap(selectLanguage);
    await tester.pumpAndSettle(Duration(milliseconds: 3000));
    await tester.tap(selectLanButton);
    await tester.pumpAndSettle(Duration(milliseconds: 3000));

    final forgotPassword = find.byKey(Keys.forgotPassword.FORGOT_PASSWORD_BUTTON);
    await tester.tap(forgotPassword);
    await tester.pumpAndSettle(Duration(milliseconds: 3000));

    final enterMobileNumber = find.byKey(Keys.forgotPassword.FORGOT_PASSWORD_MOBILE_NO);
    await tester.ensureVisible(enterMobileNumber);
    await tester.enterText(enterMobileNumber, TestInputs.forgotPassword.FORGOT_PASSWORD_MOBILE_NUMBER);
    await tester.pumpAndSettle(Duration(milliseconds: 3000));

    final continueBtn = find.byKey(Keys.forgotPassword.FORGOT_PASSWORD_CONTINUE_BTN);
    await tester.ensureVisible(continueBtn);
    await tester.tap(continueBtn);
    await tester.pumpAndSettle(Duration(milliseconds: 3000));
  });
}