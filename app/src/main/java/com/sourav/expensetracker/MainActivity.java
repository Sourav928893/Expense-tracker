package com.sourav.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sourav.expensetracker.data.AppDatabase;
import com.sourav.expensetracker.data.Transaction;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        adapter = new TransactionAdapter();
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        // Initialize Database
        database = AppDatabase.getDatabase(this);

        // Setup Buttons
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
            startActivity(intent);
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

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
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
        popup.getMenu().add(0, 102, 0, "Clear All Data");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 100) {
                exportData("csv");
                return true;
            } else if (item.getItemId() == 101) {
                exportData("pdf");
                return true;
            } else if (item.getItemId() == 102) {
                clearAllData();
                return true;
            }
            return false;
        });
        popup.show();
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

                textTotalIncome.setText(String.format(Locale.getDefault(), "$%.2f", totalIncome));
                textTotalExpense.setText(String.format(Locale.getDefault(), "$%.2f", totalExpense));
                textBalance.setText(String.format(Locale.getDefault(), "$%.2f", balance));
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
