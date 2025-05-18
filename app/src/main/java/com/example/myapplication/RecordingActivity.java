package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.IOException;

public class RecordingActivity extends AppCompatActivity {

    private Button btnStart, btnStop, btnPlay, btnDelete, btnSave;
    private TextView txtTimer;

    private MediaRecorder recorder;
    private MediaPlayer player;
    private String filePath;
    private Handler handler = new Handler();
    private int seconds = 0;

    private FirestoreDatabaseHelper firestoreHelper;

    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            seconds++;
            txtTimer.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            handler.postDelayed(this, 1000);
        }
    };

    private Runnable updatePlaybackTimer = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                int currentPos = player.getCurrentPosition() / 1000; // milliszekundumból másodperc
                txtTimer.setText(String.format("%02d:%02d", currentPos / 60, currentPos % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        firestoreHelper = new FirestoreDatabaseHelper();

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnPlay = findViewById(R.id.btnPlay);
        btnDelete = findViewById(R.id.btnDelete);
        btnSave = findViewById(R.id.btnSave);
        txtTimer = findViewById(R.id.txtTimer);

        filePath = getExternalCacheDir().getAbsolutePath() + "/recorded_audio.3gp";

        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Nincs mikrofon engedély", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnStop.setOnClickListener(v -> stopRecording());
        btnPlay.setOnClickListener(v -> playRecording());
        btnDelete.setOnClickListener(v -> deleteRecording());
        btnSave.setOnClickListener(v -> showSaveDialog());
    }

    private void startRecording() {
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setOutputFile(filePath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.prepare();
            recorder.start();
            seconds = 0;
            handler.post(updateTimer);
            Toast.makeText(this, "Felvétel elindult", Toast.LENGTH_SHORT).show();

            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            btnPlay.setEnabled(false);
            btnDelete.setEnabled(false);
            btnSave.setEnabled(false);

        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(this, "Felvétel elindítása sikertelen: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            recorder.release();
            recorder = null;
            handler.removeCallbacks(updateTimer);
            Toast.makeText(this, "Felvétel leállítva", Toast.LENGTH_SHORT).show();

            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            btnPlay.setEnabled(true);
            btnDelete.setEnabled(true);
            btnSave.setEnabled(true);
        }
    }

    private void playRecording() {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, "Nincs lejátszható felvétel", Toast.LENGTH_SHORT).show();
                return;
            }

            player = new MediaPlayer();
            player.setDataSource(filePath);
            player.prepare();
            player.start();

            handler.post(updatePlaybackTimer);

            player.setOnCompletionListener(mp -> {
                mp.release();
                player = null;
                handler.removeCallbacks(updatePlaybackTimer);
                txtTimer.setText(String.format("%02d:%02d", 0, 0));
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lejátszás sikertelen", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRecording() {
        File file = new File(filePath);
        if (file.exists()) {
            if (file.delete()) {
                Toast.makeText(this, "Felvétel törölve", Toast.LENGTH_SHORT).show();

                btnPlay.setEnabled(false);
                btnDelete.setEnabled(false);
                btnSave.setEnabled(false);
                txtTimer.setText(String.format("%02d:%02d", 0, 0));
            } else {
                Toast.makeText(this, "Felvétel törlése sikertelen", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Mentés");

        final EditText inputFileName = new EditText(this);
        inputFileName.setHint("Fájlnév");
        inputFileName.setSingleLine(true);

        final EditText inputTag = new EditText(this);
        inputTag.setHint("Címke");
        inputTag.setSingleLine(true);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(inputFileName);
        layout.addView(inputTag);
        layout.setPadding(50, 40, 50, 10);

        builder.setView(layout);

        builder.setPositiveButton("Mentés", (dialog, which) -> {
            String fileName = inputFileName.getText().toString().trim();
            String tag = inputTag.getText().toString().trim();

            if (fileName.isEmpty()) {
                Toast.makeText(this, "Adj meg egy fájlnevet!", Toast.LENGTH_SHORT).show();
                return;
            }

            saveAudioFileToFirestore(fileName, tag);
        });

        builder.setNegativeButton("Mégse", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveAudioFileToFirestore(String fileName, String tag) {
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, "A felvétel nem található!", Toast.LENGTH_SHORT).show();
            return;
        }

        long fileSize = file.length();
        int duration = seconds;
        String fileType = "mp3";

        String ownerId = "unknown";
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            ownerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        AudioFile audioFile = new AudioFile(
                null,
                fileName,
                filePath,
                tag,
                fileSize,
                duration,
                fileType,
                ownerId
        );

        firestoreHelper.insertAudioFile(audioFile,
                documentReference -> {
                    Toast.makeText(this, "Felvétel mentve az adatbázisba", Toast.LENGTH_SHORT).show();
                    finish();
                },
                e -> Toast.makeText(this, "Mentés sikertelen: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recorder != null) {
            stopRecording();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        handler.removeCallbacks(updateTimer);
        handler.removeCallbacks(updatePlaybackTimer);
    }
}
