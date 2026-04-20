import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/sharing_intent_service.dart';

final sharingIntentProvider = Provider<SharingIntentService>((ref) {
  return ReceiveSharingIntentService();
});
