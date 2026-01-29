package com.souravkaushik.expensetracker;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.souravkaushik.expensetracker.data.AppDatabase;
import com.souravkaushik.expensetracker.data.Transaction;
import com.souravkaushik.expensetracker.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText inputAmount;
    private Spinner spinnerCategory;
    private MaterialButton btnDate;
    private TextInputEditText inputNote;
    private MaterialButton btnSave;
    private MaterialButton btnDelete;

    private Calendar selectedDate = Calendar.getInstance();
    private String selectedType = "EXPENSE";
    private Transaction transactionToEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        // Initialize Views
        toggleType = findViewById(R.id.toggle_type);
        inputAmount = findViewById(R.id.input_amount);
        spinnerCategory = findViewById(R.id.spinner_category);
        btnDate = findViewById(R.id.btn_date);
        inputNote = findViewById(R.id.input_note);
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);

        // Check if editing
        if (getIntent().hasExtra("transaction")) {
            transactionToEdit = (Transaction) getIntent().getSerializableExtra("transaction");
            setupEditMode();
        } else {
            setupCategories();
            updateDateButton();
        }

        // Setup Date Picker
        btnDate.setOnClickListener(v -> showDatePicker());

        // Setup Type Toggle
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_income) {
                    selectedType = "INCOME";
                } else {
                    selectedType = "EXPENSE";
                }
                setupCategories(); // Refresh categories based on type
            }
        });

        // Save Button Click
        btnSave.setOnClickListener(v -> saveTransaction());
        
        // Delete Button Click
        btnDelete.setOnClickListener(v -> deleteTransaction());
    }

    private void setupEditMode() {
        if (transactionToEdit == null) return;

        selectedType = transactionToEdit.getType();
        if ("INCOME".equals(selectedType)) {
            toggleType.check(R.id.btn_income);
        } else {
            toggleType.check(R.id.btn_expense);
        }

        inputAmount.setText(String.valueOf(transactionToEdit.getAmount()));
        inputNote.setText(transactionToEdit.getNote());
        
        selectedDate.setTimeInMillis(transactionToEdit.getDate());
        updateDateButton();

        setupCategories();
        
        // Select correct category in spinner
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerCategory.getAdapter();
        if (adapter != null) {
            int position = adapter.getPosition(transactionToEdit.getCategory());
            if (position >= 0) {
                spinnerCategory.setSelection(position);
            }
        }

        btnSave.setText("Update Transaction");
        btnDelete.setVisibility(View.VISIBLE);
    }

    private void setupCategories() {
        String[] categories;
        if ("INCOME".equals(selectedType)) {
            categories = new String[]{"Salary", "Freelance", "Investment", "Gift", "Other"};
        } else {
            categories = new String[]{"Food", "Travel", "Rent", "Bills", "Shopping", "Entertainment", "Health", "Education", "Groceries", "Other"};
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateButton();
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButton() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        btnDate.setText(sdf.format(selectedDate.getTime()));
    }

    private void saveTransaction() {
        String amountStr = inputAmount.getText().toString();
        if (amountStr.isEmpty()) {
            inputAmount.setError("Amount is required");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            inputAmount.setError("Invalid amount");
            return;
        }
        
        String category = "";
        if (spinnerCategory.getSelectedItem() != null) {
            category = spinnerCategory.getSelectedItem().toString();
        }
        
        String note = inputNote.getText().toString();
        long date = selectedDate.getTimeInMillis();

        final Transaction transaction;
        if (transactionToEdit != null) {
            transaction = transactionToEdit;
            transaction.setType(selectedType);
            transaction.setAmount(amount);
            transaction.setCategory(category);
            transaction.setDate(date);
            transaction.setNote(note);
        } else {
            transaction = new Transaction(selectedType, amount, category, date, note);
        }

        // Save to Database in background
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (transactionToEdit != null) {
                AppDatabase.getDatabase(this).transactionDao().update(transaction);
            } else {
                AppDatabase.getDatabase(this).transactionDao().insert(transaction);
            }
            runOnUiThread(() -> {
                String message = transactionToEdit != null ? "Transaction Updated" : "Transaction Saved";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                finish(); // Close activity and go back
            });
        });
    }

    private void deleteTransaction() {
        if (transactionToEdit == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase.getDatabase(this).transactionDao().delete(transactionToEdit);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Transaction Deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
