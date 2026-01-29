package com.souravkaushik.expensetracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.souravkaushik.expensetracker.data.Transaction;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ExportUtils {

    private static final String CHANNEL_ID = "export_channel";
    private static final int NOTIFICATION_ID_PROGRESS = 100;
    private static final int NOTIFICATION_ID_COMPLETE = 101;

    public static void exportToPDF(Context context, List<Transaction> transactions, double income, double expense, long dateMillis) {
        showProgressNotification(context, "Downloading PDF Report...");

        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM_yyyy", Locale.getDefault());
                String fileName = "Expense_Report_" + sdf.format(dateMillis) + ".pdf";
                
                Uri fileUri = createFile(context, fileName, "application/pdf");
                if (fileUri == null) {
                    cancelProgressNotification(context);
                    showErrorToast(context, "Failed to create file");
                    return;
                }

                OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri);
                if (outputStream == null) {
                    cancelProgressNotification(context);
                    showErrorToast(context, "Failed to open stream");
                    return;
                }

                PdfWriter writer = new PdfWriter(outputStream);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);

                // Add Title
                document.add(new Paragraph("Expense Report - " + sdf.format(dateMillis).replace("_", " "))
                        .setBold().setFontSize(20).setTextAlignment(TextAlignment.CENTER));

                // Add Summary
                document.add(new Paragraph("\nSummary"));
                document.add(new Paragraph("Total Income: $" + String.format(Locale.getDefault(), "%.2f", income)));
                document.add(new Paragraph("Total Expense: $" + String.format(Locale.getDefault(), "%.2f", expense)));
                document.add(new Paragraph("Balance: $" + String.format(Locale.getDefault(), "%.2f", income - expense)));
                document.add(new Paragraph("\n"));

                // Create Table
                float[] columnWidths = {2, 3, 2, 3, 4};
                Table table = new Table(columnWidths);

                // Add Header
                table.addHeaderCell("Type");
                table.addHeaderCell("Category");
                table.addHeaderCell("Amount");
                table.addHeaderCell("Date");
                table.addHeaderCell("Note");

                SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                // Add Data
                for (Transaction t : transactions) {
                    table.addCell(t.getType());
                    table.addCell(t.getCategory());
                    table.addCell("$" + String.format(Locale.getDefault(), "%.2f", t.getAmount()));
                    table.addCell(dateSdf.format(new java.util.Date(t.getDate())));
                    table.addCell(t.getNote() != null ? t.getNote() : "");
                }

                document.add(table);
                document.close();
                // OutputStream is closed by PdfWriter/Document

                finishFile(context, fileUri);
                cancelProgressNotification(context);
                showCompletionNotification(context, fileName, fileUri, "application/pdf");

            } catch (Exception e) {
                e.printStackTrace();
                cancelProgressNotification(context);
                showErrorToast(context, "PDF Export Failed");
            }
        }).start();
    }

    public static void exportToCSV(Context context, List<Transaction> transactions, long dateMillis) {
        showProgressNotification(context, "Downloading CSV Report...");

        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM_yyyy", Locale.getDefault());
                String fileName = "Expense_Report_" + sdf.format(dateMillis) + ".csv";

                Uri fileUri = createFile(context, fileName, "text/csv");
                if (fileUri == null) {
                    cancelProgressNotification(context);
                    showErrorToast(context, "Failed to create file");
                    return;
                }

                OutputStream outputStream = context.getContentResolver().openOutputStream(fileUri);
                if (outputStream == null) {
                    cancelProgressNotification(context);
                    showErrorToast(context, "Failed to open stream");
                    return;
                }

                // Write CSV Content
                StringBuilder sb = new StringBuilder();
                sb.append("Type,Category,Amount,Date,Note\n");
                
                SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                for (Transaction t : transactions) {
                    sb.append(t.getType()).append(",");
                    sb.append(t.getCategory()).append(",");
                    sb.append(t.getAmount()).append(",");
                    sb.append(dateSdf.format(new java.util.Date(t.getDate()))).append(",");
                    sb.append(t.getNote() != null ? t.getNote().replace(",", " ") : "").append("\n");
                }

                outputStream.write(sb.toString().getBytes());
                outputStream.close();

                finishFile(context, fileUri);
                cancelProgressNotification(context);
                showCompletionNotification(context, fileName, fileUri, "text/csv");

            } catch (Exception e) {
                e.printStackTrace();
                cancelProgressNotification(context);
                showErrorToast(context, "CSV Export Failed");
            }
        }).start();
    }

    private static Uri createFile(Context context, String fileName, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1); // Mark as pending

            ContentResolver resolver = context.getContentResolver();
            return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        } else {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!path.exists()) path.mkdirs();
            File file = new File(path, fileName);
            return Uri.fromFile(file);
        }
    }

    private static void finishFile(Context context, Uri fileUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0); // Mark as finished
            context.getContentResolver().update(fileUri, values, null, null);
        }
    }
    
    private static Uri getUriForViewing(Context context, Uri fileUri, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return fileUri;
        } else {
            File file = new File(fileUri.getPath());
            // Scan the file so it shows up in downloads/gallery
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{mimeType}, null);
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        }
    }

    private static void showProgressNotification(Context context, String title) {
        createNotificationChannel(context);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText("Please wait...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(0, 0, true); // Indeterminate progress

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, builder.build());
        }
    }

    private static void cancelProgressNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
    }

    private static void showCompletionNotification(Context context, String fileName, Uri rawUri, String mimeType) {
        createNotificationChannel(context);

        Uri contentUri = getUriForViewing(context, rawUri, mimeType);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(contentUri, mimeType);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(fileName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, builder.build());
        }
        
        showSuccessToast(context, fileName);
    }
    
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Downloads";
            String description = "Notifications for downloaded reports";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static void showErrorToast(Context context, String message) {
        new android.os.Handler(context.getMainLooper()).post(() -> 
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        );
    }
    
    private static void showSuccessToast(Context context, String fileName) {
        new android.os.Handler(context.getMainLooper()).post(() -> 
            Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_LONG).show()
        );
    }
}
