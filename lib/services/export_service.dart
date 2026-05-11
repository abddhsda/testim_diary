// ════════════════════════════════════════════════════
// services/export_service.dart — экспорт данных
//
// Форматы: TXT (читаемый) и JSON (сырые данные)
// Использует share_plus для отправки файла
// ════════════════════════════════════════════════════

import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ExportService {

  // ─── Экспорт в читаемый TXT ──────────────────────────────────
  static Future<void> exportTxt(BuildContext context) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final entriesRaw = prefs.getString('entries') ?? '{}';
      final ratingsRaw = prefs.getString('ratings') ?? '{}';
      final notesRaw   = prefs.getString('notes')   ?? '{}';
      final goal       = prefs.getString('goal')    ?? '';

      final entries = jsonDecode(entriesRaw) as Map;
      final ratings = jsonDecode(ratingsRaw) as Map;
      final notes   = jsonDecode(notesRaw)   as Map;

      final buf = StringBuffer();
      buf.writeln('═══════════════════════════════');
      buf.writeln('  MODO — Экспорт дневника');
      buf.writeln('  ${DateTime.now().toString().substring(0, 10)}');
      buf.writeln('═══════════════════════════════');
      if (goal.isNotEmpty) {
        buf.writeln('Цель: $goal');
        buf.writeln();
      }

      // Сортируем по дате
      final dates = entries.keys.toList()..sort((a, b) => b.compareTo(a));

      for (final date in dates) {
        buf.writeln('───────────────────────────────');
        buf.writeln('📅 $date');
        buf.writeln();

        // Ответы на вопросы
        final answers = List<String>.from(entries[date] as List);
        for (int i = 0; i < answers.length; i++) {
          if (answers[i].isNotEmpty) {
            buf.writeln('  ${i + 1}. ${answers[i]}');
          }
        }

        // Оценки дня
        if (ratings.containsKey(date)) {
          final r = ratings[date] as Map;
          buf.writeln();
          buf.writeln('  📊 Оценки: '
              'Энергия ${r['energy']} | '
              'Продуктивность ${r['productivity']} | '
              'Настроение ${r['mood']} | '
              'Еда ${r['food']} | '
              'Сон ${r['sleep']}');
        }

        // Заметки
        if (notes.containsKey(date) && (notes[date] as String).isNotEmpty) {
          buf.writeln();
          buf.writeln('  ✏️ Заметки: ${notes[date]}');
        }

        buf.writeln();
      }

      await _shareFile(buf.toString(), 'modo_export.txt', 'text/plain');
    } catch (e) {
      _showError(context, e.toString());
    }
  }

  // ─── Экспорт в JSON (сырые данные) ───────────────────────────
  static Future<void> exportJson(BuildContext context) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final data = {
        'exported_at': DateTime.now().toIso8601String(),
        'goal':        prefs.getString('goal') ?? '',
        'goalCategory': prefs.getString('goalCategory') ?? '',
        'entries':     jsonDecode(prefs.getString('entries') ?? '{}'),
        'ratings':     jsonDecode(prefs.getString('ratings') ?? '{}'),
        'notes':       jsonDecode(prefs.getString('notes')   ?? '{}'),
        'plans':       jsonDecode(prefs.getString('plans')   ?? '{}'),
      };

      final pretty = const JsonEncoder.withIndent('  ').convert(data);
      await _shareFile(pretty, 'modo_backup.json', 'application/json');
    } catch (e) {
      _showError(context, e.toString());
    }
  }

  // ─── Вспомогательные ─────────────────────────────────────────
  static Future<void> _shareFile(
      String content, String filename, String mimeType) async {
    final dir  = await getTemporaryDirectory();
    final file = File('${dir.path}/$filename');
    await file.writeAsString(content, encoding: utf8);
    await Share.shareXFiles(
      [XFile(file.path, mimeType: mimeType)],
      subject: 'Modo — экспорт данных',
    );
  }

  static void _showError(BuildContext context, String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Ошибка экспорта: $msg')),
    );
  }
}
