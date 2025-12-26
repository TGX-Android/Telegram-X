# Premium Purchase Implementation Note

## 1. License Compatibility Analysis

### Source Licenses
| Repository | License | Compatibility with X (GPLv3) |
|------------|---------|------------------------------|
| TGX-Android/Telegram-X (X) | GPL v3 | N/A (this is our codebase) |
| DrKLO/Telegram (Official Android) | GPL v2 | Compatible* |
| telegramdesktop/tdesktop | GPL v3 + OpenSSL | Compatible |

*GPL v2 code can be incorporated into GPL v3 projects when the GPL v2 license includes "or any later version" clause. For this implementation, we will use a **clean-room approach** - analyzing the behavior and architecture of the official implementation, then reimplementing with original code to ensure full compliance.

### Reuse Strategy
- **Behavior analysis only**: Study the official implementation's architecture and flow
- **Original implementation**: Write new code following X's coding patterns
- **No direct code copying**: All billing code will be original
- **Proper attribution**: Document that the design is inspired by official Telegram clients

---

## 2. Official Telegram Premium Implementation Analysis

### Key Files in Official Client (DrKLO/Telegram)
```
TMessagesProj/src/main/java/org/telegram/messenger/
├── BillingController.java          # Main billing orchestration
├── utils/BillingUtilities.java     # Payload handling, currency formatting
├── BuildVars.java                  # Feature flags (useInvoiceBilling)

TMessagesProj/src/main/java/org/telegram/ui/
├── PremiumPreviewFragment.java     # Premium UI + purchase entry point
├── Components/Premium/             # Premium UI components
├── LoginActivity.java              # Auth code purchase flow
```

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PURCHASE FLOW                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐  │
│  │ UI Entry     │───▶│ BillingController │───▶│ Google Play      │  │
│  │ (Premium     │    │                   │    │ Billing Client   │  │
│  │  Preview)    │    │ - launchBilling() │    │                  │  │
│  └──────────────┘    │ - queryProducts() │    └────────┬─────────┘  │
│                      │ - queryPurchases()│             │            │
│                      └──────────────────┘             │            │
│                               │                        │            │
│                               ▼                        ▼            │
│                      ┌──────────────────┐    ┌──────────────────┐  │
│                      │ BillingUtilities │    │ Purchase         │  │
│                      │                   │    │ Callback         │  │
│                      │ - createPayload() │    │ (onPurchases     │  │
│                      │ - extractPayload()│    │  Updated)        │  │
│                      │ - savePurpose()   │    └────────┬─────────┘  │
│                      └──────────────────┘             │            │
│                                                        ▼            │
│                                               ┌──────────────────┐  │
│                                               │ Telegram Server  │  │
│                                               │                   │  │
│                                               │ assignPlayMarket │  │
│                                               │ Transaction      │  │
│                                               └────────┬─────────┘  │
│                                                        │            │
│                                                        ▼            │
│                                               ┌──────────────────┐  │
│                                               │ Premium Status   │  │
│                                               │ Update           │  │
│                                               │ (via TLRPC.      │  │
│                                               │  Updates)        │  │
│                                               └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Sequence Diagram

```
User          UI              BillingController       Google Play       Telegram Server
  │            │                     │                     │                   │
  │──tap buy──▶│                     │                     │                   │
  │            │──canPurchaseStore()──────────────────────────────────────────▶│
  │            │◀─────────────────────────────────────────────────TL_boolTrue──│
  │            │──launchBillingFlow()▶│                     │                   │
  │            │                     │──launchBillingFlow()▶│                   │
  │            │                     │                     │                   │
  │◀───────────────────Google Play Purchase Sheet──────────│                   │
  │──confirm───────────────────────────────────────────────▶│                   │
  │            │                     │◀─onPurchasesUpdated──│                   │
  │            │                     │                     │                   │
  │            │                     │──assignPlayMarketTransaction────────────▶│
  │            │                     │◀────────────────────TLRPC.Updates───────│
  │            │◀──premium status────│                     │                   │
  │◀─UI update─│                     │                     │                   │
```

### Key Implementation Details

1. **Product Configuration**
   - Product ID: `telegram_premium`
   - Product Type: `SUBS` (Subscription)
   - Subscription periods: 1 month, 12 months

2. **Payload Handling**
   - `obfuscatedAccountId`: Base64-encoded user ID
   - `obfuscatedProfileId`: Serialized payment purpose (TL object)
   - Purpose stored in SharedPreferences for recovery

3. **Server Verification**
   - `TL_payments_canPurchaseStore`: Pre-check before billing flow
   - `TL_payments_assignPlayMarketTransaction`: Submit receipt to server
   - Server returns `TLRPC.Updates` with premium status change

4. **Error Handling**
   - Billing service disconnection: Retry with exponential backoff
   - Product not found: Fall back to invoice billing (bot URL)
   - Purchase restoration: Auto-restore on app launch

5. **Consumable vs Subscription**
   - Premium subscription: Not consumed, auto-renewed
   - Gift Premium: Consumed after server acknowledgment
   - Stars purchases: Consumed after delivery

---

## 3. Proposed Design for X

### Architecture

```
app/src/main/java/org/thunderdog/challegram/
├── billing/
│   ├── BillingManager.java           # Main billing orchestration
│   ├── BillingPayloadHandler.java    # Payload serialization
│   ├── PremiumState.java             # Premium entitlement state
│   └── BillingConfig.java            # Configuration and feature flags
├── ui/
│   ├── PremiumController.java        # Premium preview/purchase UI
│   └── WaitForPremiumController.java # (existing) Login premium gate
└── telegram/
    └── Tdlib.java                    # Add billing-related TDLib calls
```

### Interfaces

```java
// BillingManager - Main orchestration
public interface BillingManager {
    void initialize();
    void launchPremiumPurchase(Activity activity, Tdlib tdlib, Runnable onSuccess);
    void queryPurchases(Consumer<List<Purchase>> callback);
    void restorePurchases(Tdlib tdlib);
    boolean isReady();
    ProductDetails getPremiumProductDetails();
}

// PremiumState - Source of truth
public interface PremiumState {
    boolean isPremiumActive(Tdlib tdlib);
    void onPremiumStatusChanged(Tdlib tdlib, boolean isPremium);
    void addListener(PremiumStateListener listener);
}

// BillingPayloadHandler - Secure payload management
public interface BillingPayloadHandler {
    Pair<String, String> createPayload(TdApi.InputStorePaymentPurpose purpose, int accountId);
    Pair<Integer, TdApi.InputStorePaymentPurpose> extractPayload(Purchase purchase);
    void clearPayload(Purchase purchase);
}
```

### Feature Flags

```java
public class BillingConfig {
    // Kill switch - disable billing entirely
    public static boolean BILLING_ENABLED = true;

    // Fall back to invoice billing if Play Store unavailable
    public static boolean USE_INVOICE_FALLBACK = true;

    // Enable detailed billing logs (NO tokens/receipts!)
    public static boolean DEBUG_BILLING = BuildConfig.DEBUG;
}
```

---

## 4. Security Considerations

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Local premium flag tampering | Server-side verification; TDLib enforces premium state |
| Receipt replay attack | Server validates receipt uniqueness |
| Token leakage via logs | Never log purchase tokens or receipt JSON |
| Man-in-the-middle | TLS + TDLib encryption |
| Fake purchase injection | Google Play signature verification + server validation |

### Security Implementation

1. **No local premium state storage**
   - Premium status comes only from TDLib/server
   - `UserConfig.isPremium()` reflects server state

2. **Token handling**
   - Purchase tokens never logged
   - Payload stored encrypted in SharedPreferences
   - Cleared immediately after server acknowledgment

3. **Receipt validation**
   - Google Play signature verification (automatic)
   - Server-side validation via `assignPlayMarketTransaction`

---

## 5. Edge Cases Checklist

- [ ] User cancels purchase mid-flow
- [ ] Billing service disconnected during purchase
- [ ] App killed during purchase (pending purchase recovery)
- [ ] Network failure after Google payment but before server assignment
- [ ] Multiple accounts on same device
- [ ] Premium gifted vs self-purchased
- [ ] Subscription renewal handling
- [ ] Subscription downgrade/upgrade
- [ ] Purchase restoration on new device
- [ ] Play Store not available (F-Droid builds)
- [ ] Premium required at login (WaitForPremiumController)

---

## 6. Files to Add/Modify

### New Files
```
app/src/main/java/org/thunderdog/challegram/billing/
├── BillingManager.java           (New - ~400 lines)
├── BillingPayloadHandler.java    (New - ~150 lines)
├── BillingConfig.java            (New - ~50 lines)

app/src/main/res/values/
├── billing_strings.xml           (New - billing-related strings)
```

### Modified Files
```
app/build.gradle.kts              (Add billing dependency)
app/src/main/java/org/thunderdog/challegram/
├── BaseActivity.java             (Initialize billing on app start)
├── telegram/Tdlib.java           (Add assignPlayMarketTransaction call)
├── ui/WaitForPremiumController.java  (Wire up real purchase)
├── ui/SettingsController.java    (Add Premium entry point)
├── navigation/NavigationController.java (Premium destination)

extension/bridge/build.gradle.kts (Add billing for Play builds only)
```

### Dependencies to Add
```kotlin
// app/build.gradle.kts or extension/bridge/build.gradle.kts
implementation("com.android.billingclient:billing:6.1.0")
implementation("com.android.billingclient:billing-ktx:6.1.0") // Optional Kotlin extensions
```

---

## 7. Test Plan

### Unit Tests
- [ ] BillingPayloadHandler serialization/deserialization
- [ ] BillingConfig feature flag behavior
- [ ] PremiumState listener management

### Integration Tests (Manual)
- [ ] Fresh install → Premium purchase → UI updates
- [ ] Kill app during purchase → Relaunch → Purchase recovered
- [ ] Uninstall/reinstall → Purchase restored
- [ ] Account switch → Correct premium state per account
- [ ] Airplane mode purchase attempt → Graceful error

### Security Tests
- [ ] Verify no tokens in logcat (filter for purchase, token, receipt)
- [ ] Verify SharedPreferences encryption
- [ ] Verify premium UI locked without valid server state

---

## 8. Rollback Plan

1. **Feature flag off**: Set `BillingConfig.BILLING_ENABLED = false`
   - Premium UI entry points hidden
   - WaitForPremiumController opens telegram.org URL fallback

2. **Hotfix release**:
   - Revert billing-related commits
   - Keep TDLib premium state handling (no impact)

3. **Data cleanup**:
   - Clear billing SharedPreferences on downgrade
   - No persistent state to corrupt

---

## 9. Implementation Order

### Phase 1: Foundation (No UI changes)
1. Add billing dependency
2. Create BillingManager skeleton
3. Create BillingPayloadHandler
4. Add feature flags

### Phase 2: Core Billing
1. Implement BillingManager initialization
2. Implement product query
3. Implement purchase flow
4. Implement server assignment

### Phase 3: UI Integration
1. Wire WaitForPremiumController
2. Add Premium entry in Settings
3. Add purchase success/failure UI

### Phase 4: Polish
1. Error handling improvements
2. Purchase restoration
3. Edge case handling
4. Testing

---

*Document Version: 1.0*
*Last Updated: 2025-12-26*
*Author: Claude (AI Assistant)*
