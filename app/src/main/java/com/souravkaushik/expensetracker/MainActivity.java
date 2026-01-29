package com.souravkaushik.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.souravkaushik.expensetracker.AddTransactionActivity;
import com.souravkaushik.expensetracker.CurrencyManager;
import com.souravkaushik.expensetracker.R;
import com.souravkaushik.expensetracker.TransactionAdapter;
import com.souravkaushik.expensetracker.data.AppDatabase;
import com.souravkaushik.expensetracker.data.Transaction;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnItemClickListener {

    private TextView textMonth, textBalance, textTotalIncome, textTotalExpense;
    private RecyclerView recyclerView;
    private TextView textEmptyState;
    private ImageButton btnPrevMonth, btnNextMonth, btnSettings;
    
    private TransactionAdapter adapter;
    private AppDatabase database;
    private Calendar currentMonth;
    private CurrencyManager currencyManager;

    private AdView mAdView;
    private InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currencyManager = new CurrencyManager(this);

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});

        // Load Banner Ad
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        // Load Interstitial Ad
        loadInterstitialAd();

        // Initialize Calendar to current month
        currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        currentMonth.set(Calendar.HOUR_OF_DAY, 0);
        currentMonth.set(Calendar.MINUTE, 0);
        currentMonth.set(Calendar.SECOND, 0);
        currentMonth.set(Calendar.MILLISECOND, 0);

        // Initialize Views
        textMonth = findViewById(R.id.text_month);
        textBalance = findViewById(R.id.text_balance);
        textTotalIncome = findViewById(R.id.text_total_income);
        textTotalExpense = findViewById(R.id.text_total_expense);
        recyclerView = findViewById(R.id.recycler_view);
        textEmptyState = findViewById(R.id.text_empty_state);
        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        btnSettings = findViewById(R.id.btn_settings);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(currencyManager);
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        // Initialize Database
        database = AppDatabase.getDatabase(this);

        // Add Button Click
        fabAdd.setOnClickListener(v -> {
            showInterstitialAdAndNavigate();
        });

        btnPrevMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateMonthDisplay();
            loadData();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateMonthDisplay();
            loadData();
        });

        btnSettings.setOnClickListener(this::showSettingsMenu);

        // Initial Load
        updateMonthDisplay();
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, "ca-app-pub-4041401840560784/1539363552", adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                    }
                });
    }

    private void showInterstitialAdAndNavigate() {
        if (mInterstitialAd != null) {
            mInterstitialAd.show(this);
            mInterstitialAd.setFullScreenContentCallback(new com.google.android.gms.ads.FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mInterstitialAd = null;
                    loadInterstitialAd(); // Load next one
                    startActivity(new Intent(MainActivity.this, AddTransactionActivity.class));
                }
            });
        } else {
            startActivity(new Intent(MainActivity.this, AddTransactionActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        if (mAdView != null) {
            mAdView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
    }

    private void updateMonthDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        textMonth.setText(sdf.format(currentMonth.getTime()));
    }

    private void showSettingsMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
        
        // Ensure menu items are clean
        popup.getMenu().clear();
        popup.getMenu().add(0, 100, 0, "Export to CSV");
        popup.getMenu().add(0, 101, 0, "Export to PDF");
        popup.getMenu().add(0, 103, 0, "Change Currency");
        popup.getMenu().add(0, 102, 0, "Clear All Data");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 100) {
                exportData("csv");
                return true;
            } else if (id == 101) {
                exportData("pdf");
                return true;
            } else if (id == 103) {
                showCurrencySelectionDialog();
                return true;
            } else if (id == 102) {
                clearAllData();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showCurrencySelectionDialog() {
        List<String> currencies = CurrencyManager.getAllCurrencies();
        String[] currencyArray = currencies.toArray(new String[0]);
        
        int currentIndex = currencies.indexOf(currencyManager.getSelectedCurrencyCode());

        new AlertDialog.Builder(this)
                .setTitle("Select Currency")
                .setSingleChoiceItems(currencyArray, currentIndex, (dialog, which) -> {
                    String selectedCode = currencyArray[which];
                    currencyManager.setSelectedCurrency(selectedCode);
                    dialog.dismiss();
                    loadData(); // Refresh UI with new currency
                    Toast.makeText(this, "Currency changed to " + selectedCode, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadData() {
        long startOfMonth = currentMonth.getTimeInMillis();
        
        Calendar endCal = (Calendar) currentMonth.clone();
        endCal.add(Calendar.MONTH, 1);
        long endOfMonth = endCal.getTimeInMillis();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Fetch Transactions for current month
            List<Transaction> transactions = database.transactionDao().getTransactionsByDateRange(startOfMonth, endOfMonth);
            
            // Calculate Totals for current month
            double totalIncome = database.transactionDao().getTotalIncome(startOfMonth, endOfMonth);
            double totalExpense = database.transactionDao().getTotalExpense(startOfMonth, endOfMonth);
            double balance = totalIncome - totalExpense;

            // Update UI
            runOnUiThread(() -> {
                adapter.setTransactions(transactions);
                
                if (transactions.isEmpty()) {
                    textEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    textEmptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }

                textTotalIncome.setText(currencyManager.formatAmount(totalIncome));
                textTotalExpense.setText(currencyManager.formatAmount(totalExpense));
                textBalance.setText(currencyManager.formatAmount(balance));
            });
        });
    }

    private void exportData(String format) {
        long startOfMonth = currentMonth.getTimeInMillis();
        Calendar endCal = (Calendar) currentMonth.clone();
        endCal.add(Calendar.MONTH, 1);
        long endOfMonth = endCal.getTimeInMillis();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Transaction> transactions = database.transactionDao().getTransactionsByDateRange(startOfMonth, endOfMonth);
            double totalIncome = database.transactionDao().getTotalIncome(startOfMonth, endOfMonth);
            double totalExpense = database.transactionDao().getTotalExpense(startOfMonth, endOfMonth);
            
            if (format.equals("pdf")) {
                ExportUtils.exportToPDF(this, transactions, totalIncome, totalExpense, currentMonth.getTimeInMillis());
            } else {
                ExportUtils.exportToCSV(this, transactions, currentMonth.getTimeInMillis());
            }
        });
    }
    
    private void clearAllData() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Data")
                .setMessage("Are you sure you want to delete ALL transactions? This cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        database.transactionDao().deleteAll();
                        loadData(); // Reload UI
                        runOnUiThread(() -> Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClick(Transaction transaction) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra("transaction", transaction);
        startActivity(intent);
    }
}
