import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mgramseva/model/common/metric.dart';
import 'package:mgramseva/providers/common_provider.dart';
import 'package:mgramseva/providers/dashboard_provider.dart';
import 'package:mgramseva/providers/household_register_provider.dart';
import 'package:mgramseva/providers/language.dart';
import 'package:mgramseva/utils/Constants/I18KeyConstants.dart';
import 'package:mgramseva/utils/Locilization/application_localizations.dart';
import 'package:mgramseva/utils/date_formats.dart';
import 'package:mgramseva/utils/global_variables.dart';
import 'package:mgramseva/utils/models.dart';
import 'package:mgramseva/utils/pdf.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:provider/provider.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:universal_html/html.dart' as html;

class HouseholdPdfCreator {

  final List<String> headers;
  final List<List<String>> tableData;
  BuildContext buildContext;
  final bool isDownload;

  HouseholdPdfCreator(this.buildContext, this.headers, this.tableData, this.isDownload);

  pdfPreview() async {
    var householdProvider = Provider.of<HouseholdRegisterProvider>(navigatorKey.currentContext!, listen: false);
    var pdf = pw.Document();
    final ttf = await Provider.of<CommonProvider>(buildContext,
        listen: false).getPdfFontFamily();

    final mgramSevaLogo = await PdfUtils.mgramSevaLogo;
    final digitLogo = await PdfUtils.powerdByDigit;
    final date = DateFormats.getFilteredDate(DateTime.now().toLocal().toString(), dateFormat: "dd/MM/yyyy");
    var localizedText = ApplicationLocalizations.of(navigatorKey.currentContext!).translate(i18.householdRegister.PDF_SUB_TEXT_BELOW_LIST);
    localizedText = localizedText.replaceFirst('<selectedTab>', householdProvider.selectedTab);
    localizedText = localizedText.replaceFirst('<search>', householdProvider.searchController.text.toString());
    localizedText = localizedText.replaceFirst('<date>', date);

    var icons =  pw.Font.ttf(await rootBundle.load('assets/icons/fonts/PdfIcons.ttf'));

    pdf.addPage(
        pw.MultiPage(
            pageFormat: PdfPageFormat.a4,
            footer: (_) => PdfUtils.pdfFooter(digitLogo),
            build: (pw.Context context) {
              return [
                PdfUtils.buildAppBar(buildContext, mgramSevaLogo, icons, ttf),
                pw.Container(
                  padding: pw.EdgeInsets.only(top: 16, bottom: 16, right: 16, left: 16),
                  decoration: pw.BoxDecoration(
                    shape: pw.BoxShape.rectangle,
                    color: PdfColor.fromHex('#FFFFFF'),
                  ),
                  child: pw.Column(
                      mainAxisAlignment: pw.MainAxisAlignment.start,
                      crossAxisAlignment: pw.CrossAxisAlignment.start,
                      children: [
                        pw.Text(ApplicationLocalizations.of(navigatorKey.currentContext!).translate(i18.householdRegister.HOUSEHOLD_REGISTER_LABEL), style: pw.TextStyle(font: ttf, fontSize: 42, fontWeight: pw.FontWeight.bold)),
                        pw.SizedBox(height: 10),
                        pw.Text('${ApplicationLocalizations.of(navigatorKey.currentContext!).translate(i18.householdRegister.AS_OF_DATE)} $date', style: pw.TextStyle(font: ttf, fontSize: 12)),
                        pw.SizedBox(height: 20),
                        pw.Text('${ApplicationLocalizations.of(navigatorKey.currentContext!).translate(
                            i18.householdRegister.CONSUMER_RECORDS)} (${householdProvider.selectedTab})' , style: pw.TextStyle(font: ttf, fontSize: 18, fontWeight: pw.FontWeight.bold)),
                        pw.SizedBox(height: 10),
                        pw.Text( localizedText , style: pw.TextStyle(font: ttf, fontStyle: pw.FontStyle.italic, fontSize: 10, color: PdfColor.fromHex('#474747'),),)
                      ]),
                ),
                pw.SizedBox(
                    height: 20
                ),
                _buildTable(ttf),
                PdfUtils.pdfFooter(digitLogo)
              ];
            }));

    var whatsappText = '${ApplicationLocalizations.of(buildContext).translate(i18.householdRegister.WHATSAPP_TEXT_HOUSEHOLD)}';
    whatsappText = whatsappText.replaceAll('<Date>', date);

    Provider.of<CommonProvider>(buildContext, listen: false).sharePdfOnWhatsApp(buildContext, pdf, 'HouseholdRegister', whatsappText, isDownload: isDownload);


   /* if (kIsWeb) {
      final blob = html.Blob([await pdf.save()]);
      final url = html.Url.createObjectUrlFromBlob(blob);
      final anchor = html.document.createElement('a') as html.AnchorElement
        ..href = url
        ..style.display = 'none'
        ..download = 'Household.pdf';
      html.document.body?.children.add(anchor);
      anchor.click();
      html.document.body?.children.remove(anchor);
      html.Url.revokeObjectUrl(url);
    } else {
      final Directory directory = await getApplicationDocumentsDirectory();
      final file = File('${directory.path}/Household.pdf');
      await file.writeAsBytes(await pdf.save());
      // Navigator.pushReplacement(buildContext,
      //     MaterialPageRoute(builder: (_) => PdfPreview(path: file.path)));
    }*/
  }


  pw.Table _buildTable(pw.Font ttf){
    return pw.Table.fromTextArray(
        cellPadding: pw.EdgeInsets.symmetric(vertical: 10),
        headers: headers,
        headerStyle:
        pw.TextStyle(font: ttf, fontWeight: pw.FontWeight.bold),
        cellStyle: pw.TextStyle(
            font: ttf,
            fontSize: 12
        ),
        cellAlignment: pw.Alignment.center,
        data: tableData,
        oddRowDecoration:
        pw.BoxDecoration(color: PdfColor.fromHex('#EEEEEE'))
      //headerDecoration: pw.BoxDecoration(color: PdfColor.fromRYB(0.98, 0.60, 0.01))
    );
  }
}
