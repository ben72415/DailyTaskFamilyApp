package com.dailytask;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditTaskActivity extends AppCompatActivity {

    private TextView tvEditTitleHeader, tvEditImageCount, tvPermissionWarning;
    private EditText etEditTaskNotes;
    private CheckBox cbEditStatus;
    private Button btnEditPickImage, btnEditCaptureImage, btnEditReschedule, btnEditSaveTask;
    private Button btnEditStartRecord, btnEditStopRecord, btnEditPlayRecord;
    private LinearLayout layoutEditImageContainer;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;

    private String cloudDocId;
    private String createdByUid;
    private String taskId;
    private String taskTitle, taskMember, taskDate, taskTime;
    private ArrayList<String> editImageUris = new ArrayList<>();
    private Uri editCameraImageUri;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String voiceFilePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_task);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_edit_task_cloud));
        }

        taskId = getIntent().getStringExtra("TASK_ID");
        taskTitle = getIntent().getStringExtra("TASK_TITLE");
        if (taskTitle == null) taskTitle = getString(R.string.untitled_task);

        taskMember = getIntent().getStringExtra("TASK_MEMBER");
        taskDate = getIntent().getStringExtra("TASK_DATE");
        taskTime = getIntent().getStringExtra("TASK_TIME");
        String taskNotes = getIntent().getStringExtra("TASK_NOTES");
        String taskStatus = getIntent().getStringExtra("TASK_STATUS");
        String taskImagesStr = getIntent().getStringExtra("TASK_IMAGE");

        cloudDocId = getIntent().getStringExtra("CLOUD_DOC_ID");
        createdByUid = getIntent().getStringExtra("CREATED_BY_UID");

        tvEditTitleHeader = findViewById(R.id.tvEditTitleHeader);
        tvEditImageCount = findViewById(R.id.tvEditImageCount);
        etEditTaskNotes = findViewById(R.id.etEditTaskNotes);
        cbEditStatus = findViewById(R.id.cbEditStatus);
        btnEditPickImage = findViewById(R.id.btnEditPickImage);
        btnEditCaptureImage = findViewById(R.id.btnEditCaptureImage);
        btnEditReschedule = findViewById(R.id.btnEditReschedule);
        btnEditSaveTask = findViewById(R.id.btnEditSaveTask);
        layoutEditImageContainer = findViewById(R.id.layoutEditImageContainer);

        btnEditStartRecord = findViewById(R.id.btnEditStartRecord);
        btnEditStopRecord = findViewById(R.id.btnEditStopRecord);
        btnEditPlayRecord = findViewById(R.id.btnEditPlayRecord);

        voiceFilePath = getExternalCacheDir().getAbsolutePath() + "/Task_Voice_Edit_Temp.3gp";
        tvPermissionWarning = new TextView(this);
        tvPermissionWarning.setTextSize(14);
        tvPermissionWarning.setPadding(0, 10, 0, 10);
        if (etEditTaskNotes.getParent() instanceof LinearLayout) {
            ((LinearLayout) findViewById(R.id.etEditTaskNotes).getParent()).addView(tvPermissionWarning, 2);
        }

        tvEditTitleHeader.setText(getString(R.string.task_header_format, taskTitle, taskMember));
        etEditTaskNotes.setText(taskNotes);
        cbEditStatus.setChecked("Completed".equals(taskStatus));

        if (taskImagesStr != null && !taskImagesStr.isEmpty()) {
            editImageUris.addAll(Arrays.asList(taskImagesStr.split(",")));
        }
        rebuildImagePreviews();

        if (cloudDocId != null) {
            db.collection("tasks").document(cloudDocId).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String cloudVoicePath = documentSnapshot.getString("task_voice");
                    if (cloudVoicePath != null && !cloudVoicePath.isEmpty()) {
                        voiceFilePath = cloudVoicePath;
                        btnEditPlayRecord.setEnabled(true);
                    }
                }
            });
        }

        btnEditStartRecord.setOnClickListener(v -> {
            try {
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setOutputFile(voiceFilePath);
                mediaRecorder.prepare();
                mediaRecorder.start();

                btnEditStartRecord.setEnabled(false);
                btnEditStopRecord.setEnabled(true);
                btnEditPlayRecord.setEnabled(false);
                Toast.makeText(this, getString(R.string.toast_recording_edit_started), Toast.LENGTH_SHORT).show();
            } catch (IOException e) { e.printStackTrace(); }
        });

        btnEditStopRecord.setOnClickListener(v -> {
            if (mediaRecorder != null) {
                try { mediaRecorder.stop(); } catch (RuntimeException e) {}
                mediaRecorder.release();
                mediaRecorder = null;
                btnEditStartRecord.setEnabled(true);
                btnEditStopRecord.setEnabled(false);
                btnEditPlayRecord.setEnabled(true);
                Toast.makeText(this, getString(R.string.toast_recording_edit_finished), Toast.LENGTH_SHORT).show();
            }
        });

        btnEditPlayRecord.setOnClickListener(v -> {
            try {
                if (mediaPlayer != null) { mediaPlayer.release(); }
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(voiceFilePath);
                mediaPlayer.prepare();
                mediaPlayer.start();
                Toast.makeText(this, getString(R.string.toast_recording_playing), Toast.LENGTH_SHORT).show();
            } catch (IOException e) { e.printStackTrace(); }
        });

        checkUserAccessControl();

        btnEditPickImage.setOnClickListener(v -> {
            if (editImageUris.size() >= 10) {
                Toast.makeText(EditTaskActivity.this, getString(R.string.toast_photo_limit_reached), Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, 400);
        });

        if (btnEditCaptureImage != null) {
            btnEditCaptureImage.setOnClickListener(v -> {
                if (editImageUris.size() >= 10) {
                    Toast.makeText(EditTaskActivity.this, getString(R.string.toast_photo_limit_reached), Toast.LENGTH_SHORT).show();
                    return;
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "DailyTask_EditCapture_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                editCameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, editCameraImageUri);
                startActivityForResult(cameraIntent, 450);
            });
        }

        btnEditReschedule.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(EditTaskActivity.this, (view, year, month, dayOfMonth) -> {
                final String newDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, (month + 1), dayOfMonth);

                TimePickerDialog startTimePicker = new TimePickerDialog(EditTaskActivity.this, (view1, startHour, startMinute) -> {
                    final String newStartTime = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute);

                    TimePickerDialog endTimePicker = new TimePickerDialog(EditTaskActivity.this, (view2, endHour, endMinute) -> {
                        String newEndTime = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute);
                        String newTimeRange = newStartTime + " - " + newEndTime;

                        if (cloudDocId != null) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("task_date", newDate);
                            updates.put("task_time", newTimeRange);
                            updates.put("task_status", "Rescheduled"); // 改為英文
                            updates.put("task_notes", etEditTaskNotes.getText().toString().trim());
                            updates.put("task_image", buildCombinedImagesStr());
                            db.collection("tasks").document(cloudDocId).update(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(EditTaskActivity.this, getString(R.string.toast_cloud_schedule_updated, newDate), Toast.LENGTH_LONG).show();
                                        finish();
                                    });
                        }
                    }, startHour + 1, startMinute, true);
                    endTimePicker.setTitle(getString(R.string.dialog_time_picker_end));
                    endTimePicker.show();
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
                startTimePicker.setTitle(getString(R.string.dialog_time_picker_start));
                startTimePicker.show();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.setTitle(getString(R.string.dialog_date_picker));
            datePickerDialog.show();
        });

        btnEditSaveTask.setOnClickListener(v -> {
            String updatedNotes = etEditTaskNotes.getText().toString().trim();
            String updatedStatus = cbEditStatus.isChecked() ? "Completed" : "Pending";
            String finalImagesStr = buildCombinedImagesStr();

            if (cloudDocId != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("task_notes", updatedNotes);
                updates.put("task_status", updatedStatus);
                updates.put("task_image", finalImagesStr);

                File vFile = new File(voiceFilePath);
                if (vFile.exists() && btnEditPlayRecord.isEnabled()) {
                    updates.put("task_voice", voiceFilePath);
                }

                db.collection("tasks").document(cloudDocId).update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(EditTaskActivity.this, getString(R.string.toast_cloud_edit_success), Toast.LENGTH_SHORT).show();
                            finish();
                        });
            }
        });
    }

    private void checkUserAccessControl() {
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String myRole = documentSnapshot.getString("role");

                        if ("User".equals(myRole) && !currentUid.equals(createdByUid)) {
                            btnEditSaveTask.setEnabled(false);
                            btnEditReschedule.setEnabled(false);
                            btnEditPickImage.setEnabled(false);
                            if (btnEditCaptureImage != null) btnEditCaptureImage.setEnabled(false);

                            btnEditStartRecord.setEnabled(false);
                            btnEditStopRecord.setEnabled(false);
                            etEditTaskNotes.setEnabled(false);
                            cbEditStatus.setEnabled(false);

                            btnEditSaveTask.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            btnEditReschedule.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            btnEditPickImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            if (btnEditCaptureImage != null) btnEditCaptureImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));

                            tvPermissionWarning.setText(getString(R.string.permission_denied_warning));
                            tvPermissionWarning.setTextColor(Color.parseColor("#E74C3C"));
                        } else {
                            tvPermissionWarning.setText(getString(R.string.permission_approved_success, myRole));
                            tvPermissionWarning.setTextColor(Color.parseColor("#27AE60"));
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 400 && resultCode == RESULT_OK && data != null) {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    if (editImageUris.size() >= 10) break;
                    Uri uri = clipData.getItemAt(i).getUri();
                    try {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        editImageUris.add(uri.toString());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    editImageUris.add(uri.toString());
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
        if (requestCode == 450 && resultCode == RESULT_OK) {
            if (editCameraImageUri != null) {
                editImageUris.add(editCameraImageUri.toString());
            }
        }
        rebuildImagePreviews();
    }

    private void rebuildImagePreviews() {
        layoutEditImageContainer.removeAllViews();
        tvEditImageCount.setText(getString(R.string.photo_attachment_delete_hint, editImageUris.size()));

        if (editImageUris.isEmpty()) {
            TextView tvNoImg = new TextView(this);
            tvNoImg.setText(getString(R.string.no_photo_attachments));
            tvNoImg.setPadding(20, 20, 20, 20);
            layoutEditImageContainer.addView(tvNoImg);
            return;
        }

        for (int i = 0; i < editImageUris.size(); i++) {
            final String imgUriStr = editImageUris.get(i);
            final int index = i;

            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(150, ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMargins(10, 0, 10, 0);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            try {
                getContentResolver().takePersistableUriPermission(Uri.parse(imgUriStr), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                imageView.setImageURI(Uri.parse(imgUriStr));
            } catch (Exception e) {
                imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            imageView.setOnLongClickListener(view -> {
                if (!btnEditPickImage.isEnabled()) return true;

                new AlertDialog.Builder(EditTaskActivity.this)
                        .setTitle(getString(R.string.dialog_delete_photo_title))
                        .setMessage(getString(R.string.dialog_delete_photo_msg))
                        .setPositiveButton(getString(R.string.action_confirm), (dialog, which) -> {
                            editImageUris.remove(index);
                            rebuildImagePreviews();
                        })
                        .setNegativeButton(getString(R.string.action_cancel), null).show();
                return true;
            });
            layoutEditImageContainer.addView(imageView);
        }
    }

    private String buildCombinedImagesStr() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < editImageUris.size(); i++) {
            sb.append(editImageUris.get(i));
            if (i < editImageUris.size() - 1) sb.append(",");
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaRecorder != null) { mediaRecorder.release(); }
        if (mediaPlayer != null) { mediaPlayer.release(); }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}