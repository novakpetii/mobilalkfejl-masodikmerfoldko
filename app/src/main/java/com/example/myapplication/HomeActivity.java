package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 2;

    private static final int REQUEST_MIC_PERMISSION_CODE = 2001;
    private static final int PICK_AUDIO_FILE = 1;
    private static final int REQUEST_PERMISSION_CODE = 1001;
    private static final String TAG = "HomeActivity";

    private FirestoreDatabaseHelper firestoreDbHelper;
    private AudioAdapter adapter;
    private List<AudioFile> audioFileList;
    private RecyclerView recyclerView;
    private String currentUserId;

    private Long filterMinSize = null;
    private Long filterMaxSize = null;
    private List<String> filterFileTypes = new ArrayList<>();
    private Long filterMinDuration = null;
    private Long filterMaxDuration = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (savedInstanceState != null) {
            long savedMinSize = savedInstanceState.getLong("filterMinSize", -1);
            filterMinSize = (savedMinSize == -1) ? null : savedMinSize;
            long savedMaxSize = savedInstanceState.getLong("filterMaxSize", -1);
            filterMaxSize = (savedMaxSize == -1) ? null : savedMaxSize;
            long savedMinDuration = savedInstanceState.getLong("filterMinDuration", -1);
            filterMinDuration = (savedMinDuration == -1) ? null : savedMinDuration;
            long savedMaxDuration = savedInstanceState.getLong("filterMaxDuration", -1);
            filterMaxDuration = (savedMaxDuration == -1) ? null : savedMaxDuration;
            filterFileTypes = savedInstanceState.getStringArrayList("filterFileTypes");
            if (filterFileTypes == null) {
                filterFileTypes = new ArrayList<>();
            }
            Log.d(TAG, "Mentett filterek: minSize = " + filterMinSize + ", maxSize = " + filterMaxSize +
                    ", minDuration = " + filterMinDuration + ", maxDuration = " + filterMaxDuration +
                    ", fileTypes = " + filterFileTypes);
        }

        firestoreDbHelper = new FirestoreDatabaseHelper();
        recyclerView = findViewById(R.id.recyclerViewAudioFiles);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentUserId = "guest";
        }

        Button buttonAdd = findViewById(R.id.buttonAddAudio);
        buttonAdd.setOnClickListener(v -> showAddAudioDialog());


        Button btnFilter = findViewById(R.id.btnFilter);
        btnFilter.setOnClickListener(v -> showFilterDialog());

        refreshList();
    }
    private void showAddAudioDialog() {
        String[] options = {"Meglévő hangfájl kiválasztása", "Új hangfelvétel rögzítése"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hang hozzáadása")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (hasStoragePermission()) {
                            pickAudioFile();
                        } else {
                            requestStoragePermission();
                        }
                    } else if (which == 1) {
                        if (hasMicrophonePermission()) {
                            startRecordingActivity();
                        } else {
                            requestMicrophonePermission();
                        }
                    }
                })
                .show();
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC_PERMISSION_CODE);
    }

    private void startRecordingActivity() {
        Intent intent = new Intent(this, RecordingActivity.class);
        startActivityForResult(intent, REQUEST_RECORD_AUDIO);
    }



    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("filterMinSize", filterMinSize != null ? filterMinSize : -1);
        outState.putLong("filterMaxSize", filterMaxSize != null ? filterMaxSize : -1);
        outState.putLong("filterMinDuration", filterMinDuration != null ? filterMinDuration : -1);
        outState.putLong("filterMaxDuration", filterMaxDuration != null ? filterMaxDuration : -1);
        outState.putStringArrayList("filterFileTypes", new ArrayList<>(filterFileTypes));
        Log.d(TAG, "Szűrő állapot mentve.");
    }

    private void pickAudioFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(Intent.createChooser(intent, "Hangfájl kiválasztása"), PICK_AUDIO_FILE);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_PERMISSION_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickAudioFile();
            } else {
                Toast.makeText(this, "Engedély megtagadva. Nem lehet fájlt kiválasztani.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_MIC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecordingActivity();
            } else {
                Toast.makeText(this, "Engedély megtagadva. Nem lehet mikrofont használni.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO_FILE && resultCode == RESULT_OK && data != null) {
            Uri audioUri = data.getData();
            saveAudioFromUri(audioUri);
        } else if (requestCode == REQUEST_RECORD_AUDIO && resultCode == RESULT_OK && data != null) {
            String filePath = data.getStringExtra("audioFilePath");
            String fileName = data.getStringExtra("audioFileName");
            File file = new File(filePath);
            Uri audioUri = Uri.fromFile(file);
            saveAudioFromUri(audioUri);
        }
    }
    private void saveAudioFromUri(Uri audioUri) {
        if (audioUri == null) return;

        String fileName = getFileName(audioUri);
        long fileSize = getFileSize(audioUri);
        int fileDuration = getAudioDuration(audioUri);
        String fileType = getFileType(audioUri);

        AudioFile newAudioFile = new AudioFile("", fileName, audioUri.toString(), "Nincs címke",
                fileSize, fileDuration, fileType, currentUserId);

        firestoreDbHelper.insertAudioFile(newAudioFile,
                documentReference -> {
                    Toast.makeText(HomeActivity.this, "Hangfájl mentve!", Toast.LENGTH_SHORT).show();
                    refreshList();
                },
                e -> {
                    Toast.makeText(HomeActivity.this, "Hiba a fájl mentésekor", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
    }



    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        long size = 0;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                size = cursor.getLong(sizeIndex);
            }
        }
        return size;
    }

    private int getAudioDuration(Uri uri) {
        int duration = 0;
        try {
            android.media.MediaPlayer mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();
            duration = mediaPlayer.getDuration() / 1000;
            mediaPlayer.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return duration;
    }

    private String getFileType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type != null && type.contains("/")) {
            return type.split("/")[1];
        }
        return "unknown";
    }

    private void refreshList() {
        if (filterMinSize != null || filterMaxSize != null || (filterFileTypes != null && !filterFileTypes.isEmpty())
                || filterMinDuration != null || filterMaxDuration != null) {
            firestoreDbHelper.getUserAudioFilesFiltered(currentUserId, filterMinSize, filterMaxSize, filterFileTypes, filterMinDuration, filterMaxDuration, audioFiles -> {
                audioFileList = audioFiles;
                adapter = new AudioAdapter(HomeActivity.this, audioFileList, firestoreDbHelper);
                recyclerView.setAdapter(adapter);
                Log.d(TAG, "Szűrt lekérdezés eredménye: " + audioFiles.size() + " elemet talált.");
            });
        } else {
            firestoreDbHelper.getUserAudioFiles(currentUserId, audioFiles -> {
                audioFileList = audioFiles;
                adapter = new AudioAdapter(HomeActivity.this, audioFileList, firestoreDbHelper);
                recyclerView.setAdapter(adapter);
                Log.d(TAG, "Összes elem: " + audioFiles.size());
            });
        }
    }

    private void showFilterDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_filter, null);
        final EditText etMinFileSize = view.findViewById(R.id.etMinFileSize);
        final EditText etMaxFileSize = view.findViewById(R.id.etMaxFileSize);
        final Spinner spinnerFileType = view.findViewById(R.id.spinnerFileType);
        final EditText etMinDuration = view.findViewById(R.id.etMinDuration);
        final EditText etMaxDuration = view.findViewById(R.id.etMaxDuration);
        Button btnApplyFilter = view.findViewById(R.id.btnApplyFilter);

        List<String> fileTypeOptions = Arrays.asList("Összes", "mp3", "wav", "flac");
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileTypeOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFileType.setAdapter(spinnerAdapter);

        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);
        dialog.show();

        btnApplyFilter.setOnClickListener(v -> {
            String minSizeStr = etMinFileSize.getText().toString().trim();
            String maxSizeStr = etMaxFileSize.getText().toString().trim();
            Long minMB = !minSizeStr.isEmpty() ? Long.parseLong(minSizeStr) : null;
            Long maxMB = !maxSizeStr.isEmpty() ? Long.parseLong(maxSizeStr) : null;
            filterMinSize = (minMB != null) ? minMB * 1024 * 1024 : null;
            filterMaxSize = (maxMB != null) ? maxMB * 1024 * 1024 : null;

            String selectedType = spinnerFileType.getSelectedItem().toString().trim().toLowerCase();
            if (selectedType.equals("összes")) {
                filterFileTypes = new ArrayList<>();
            } else {
                filterFileTypes = new ArrayList<>();
                filterFileTypes.add(selectedType);
            }

            String minDurationStr = etMinDuration.getText().toString().trim();
            String maxDurationStr = etMaxDuration.getText().toString().trim();
            filterMinDuration = !minDurationStr.isEmpty() ? Long.parseLong(minDurationStr) : null;
            filterMaxDuration = !maxDurationStr.isEmpty() ? Long.parseLong(maxDurationStr) : null;

            refreshList();
            dialog.dismiss();
        });
    }
}
