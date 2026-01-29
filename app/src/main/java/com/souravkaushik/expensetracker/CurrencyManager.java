package com.souravkaushik.expensetracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class CurrencyManager {
    private static final String PREF_NAME = "currency_prefs";
    private static final String KEY_CURRENCY_CODE = "selected_currency_code";
    private static final String DEFAULT_CURRENCY = "USD";

    private final SharedPreferences prefs;

    public CurrencyManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setSelectedCurrency(String code) {
        prefs.edit().putString(KEY_CURRENCY_CODE, code).apply();
    }

    public String getSelectedCurrencyCode() {
        return prefs.getString(KEY_CURRENCY_CODE, DEFAULT_CURRENCY);
    }

    public String getSelectedCurrencySymbol() {
        try {
            return Currency.getInstance(getSelectedCurrencyCode()).getSymbol();
        } catch (Exception e) {
            return "$";
        }
    }

    /**
     * Returns a list of all available currency codes sorted alphabetically.
     */
    public static List<String> getAllCurrencies() {
        List<String> codes = new ArrayList<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                Currency currency = Currency.getInstance(locale);
                String code = currency.getCurrencyCode();
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            } catch (Exception ignored) {
            }
        }
        Collections.sort(codes);
        return codes;
    }

    /**
     * Formats amount with the selected symbol (e.g., ₹500.00 or $500.00)
     */
    public String formatAmount(double amount) {
        return getSelectedCurrencySymbol() + String.format(Locale.getDefault(), "%.2f", amount);
    }
}
