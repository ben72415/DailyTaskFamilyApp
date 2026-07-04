package com.dailytask;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddTaskActivity extends AppCompatActivity {

    private Spinner spinnerMember;
    private EditText etTaskTitle, etTaskNotes;
    private Button btnSaveTask, btnSelectStartTime, btnSelectEndTime, btnAddTaskImage, btnCaptureImage; // 🎯 新增 btnCaptureImage
    private TextView tvSelectedTime, tvImageCount;
    private LinearLayout layoutImagePreviewContainer;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;
    private String currentUserGroupId = "";

    private String selectedDate;
    private String startTime = "12:00";
    private String endTime = "13:00";
    private int startHour = 12;
    private int startMinute = 0;
    private final ArrayList<String> selectedImageUris = new ArrayList<>();
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
            fetchUserGroupInfo();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("雲端發布：新增任務");
        }

        // 動態權限檢查 (通知與相機)
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 101);
        }

        selectedDate = getIntent().getStringExtra("SELECTED_DATE");
        if (selectedDate == null) selectedDate = "";

        etTaskTitle = findViewById(R.id.etTaskTitle);
        etTaskNotes = findViewById(R.id.etTaskNotes);
        spinnerMember = findViewById(R.id.spinnerMember);
        btnSelectStartTime = findViewById(R.id.btnSelectStartTime);
        btnSelectEndTime = findViewById(R.id.btnSelectEndTime);
        tvSelectedTime = findViewById(R.id.tvSelectedTime);
        btnSaveTask = findViewById(R.id.btnSaveTask);
        btnAddTaskImage = findViewById(R.id.btnAddTaskImage);


        btnCaptureImage = findViewById(R.id.btnCaptureImage);

        tvImageCount = findViewById(R.id.tvImageCount);
        layoutImagePreviewContainer = findViewById(R.id.layoutImagePreviewContainer);

        ArrayList<String> dynamicMembers = new ArrayList<>();
        dynamicMembers.add("👥 正在同步家庭成員...");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dynamicMembers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMember.setAdapter(adapter);

        updateTimeTextView();


        btnAddTaskImage.setOnClickListener(v -> {
            if (selectedImageUris.size() >= 10) {
                Toast.makeText(AddTaskActivity.this, "已達上限 10 張相片！", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, 300);
        });


        if (btnCaptureImage != null) {
            btnCaptureImage.setOnClickListener(v -> {
                if (selectedImageUris.size() >= 10) {
                    Toast.makeText(AddTaskActivity.this, "已達上限 10 張相片！", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "請先開啟相機權限！", Toast.LENGTH_SHORT).show();
                    return;
                }


                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "DailyTask_Capture_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                startActivityForResult(cameraIntent, 350);
            });
        }

        btnSelectStartTime.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(AddTaskActivity.this,
                    (view, hourOfDay, minuteOfHour) -> {
                        startHour = hourOfDay;
                        startMinute = minuteOfHour;
                        startTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                        updateTimeTextView();
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timePickerDialog.show();
        });

        btnSelectEndTime.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(AddTaskActivity.this,
                    (view, hourOfDay, minuteOfHour) -> {
                        endTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minuteOfHour);
                        updateTimeTextView();
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timePickerDialog.show();
        });

        btnSaveTask.setOnClickListener(v -> {
            String title = etTaskTitle.getText().toString().trim();
            Object selectedMemberObj = spinnerMember.getSelectedItem();
            String member = (selectedMemberObj != null) ? selectedMemberObj.toString() : "未指定成員";
            String notes = etTaskNotes.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(AddTaskActivity.this, "請輸入任務名稱！", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentUserGroupId == null || currentUserGroupId.isEmpty()) {
                Toast.makeText(AddTaskActivity.this, "正在獲取群組資訊，請稍候再試...", Toast.LENGTH_SHORT).show();
                return;
            }

            String finalTimeRange = startTime + " - " + endTime;

            StringBuilder imageStringBuilder = new StringBuilder();
            for (int i = 0; i < selectedImageUris.size(); i++) {
                imageStringBuilder.append(selectedImageUris.get(i));
                if (i < selectedImageUris.size() - 1) imageStringBuilder.append(",");
            }
            String combinedImagesStr = imageStringBuilder.toString();

            String cloudDocIdStr = db.collection("tasks").document().getId();

            Map<String, Object> taskData = new HashMap<>();
            taskData.put("id", cloudDocIdStr);
            taskData.put("title", title);
            taskData.put("member", member);
            taskData.put("task_date", selectedDate != null ? selectedDate : "");
            taskData.put("task_time", finalTimeRange);
            taskData.put("task_notes", notes);
            taskData.put("task_status", "未完成");
            taskData.put("task_image", combinedImagesStr);
            taskData.put("group_id", currentUserGroupId);
            taskData.put("created_by", currentUid != null ? currentUid : "");

            db.collection("tasks").document(cloudDocIdStr)
                    .set(taskData)
                    .addOnSuccessListener(aVoid -> {
                        setTaskAlarm(title, member);
                        Toast.makeText(AddTaskActivity.this, "家庭雲端任務發布成功！", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> Toast.makeText(AddTaskActivity.this, "發布失敗: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });
    }

    void fetchUserGroupInfo() {
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        if (documentSnapshot.exists()) {
                            currentUserGroupId = documentSnapshot.getString("group_id");

                            db.collection("users")
                                    .whereEqualTo("group_id", currentUserGroupId)
                                    .get()
                                    .addOnSuccessListener(queryDocumentSnapshots -> {
                                        ArrayList<String> memberList = new ArrayList<>();
                                        for (DocumentSnapshot userDoc : queryDocumentSnapshots) {
                                            String name = userDoc.getString("name");
                                            String role = userDoc.getString("role");
                                            if (name != null) {
                                                memberList.add(name + " (" + role + ")");
                                            }
                                        }

                                        if (!memberList.isEmpty()) {
                                            ArrayAdapter<String> newAdapter = new ArrayAdapter<>(
                                                    AddTaskActivity.this,
                                                    android.R.layout.simple_spinner_item,
                                                    memberList
                                            );
                                            newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                            spinnerMember.setAdapter(newAdapter);
                                        }
                                    });
                        }
                    }
                });
    }

    private void updateTimeTextView() {
        tvSelectedTime.setText("時間段: " + startTime + " - " + endTime);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        if (requestCode == 300 && resultCode == RESULT_OK && data != null) {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    if (selectedImageUris.size() >= 10) break;
                    Uri uri = clipData.getItemAt(i).getUri();
                    try {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        selectedImageUris.add(uri.toString());
                        addPreviewImageView(uri);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    selectedImageUris.add(uri.toString());
                    addPreviewImageView(uri);
                } catch (Exception e) { e.printStackTrace(); }
            }
        }


        if (requestCode == 350 && resultCode == RESULT_OK) {
            if (cameraImageUri != null) {
                selectedImageUris.add(cameraImageUri.toString());
                addPreviewImageView(cameraImageUri);
            }
        }

        tvImageCount.setText("相片附件 (最多 10 張): " + selectedImageUris.size() + "/10");
    }

    private void addPreviewImageView(Uri uri) {
        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(140, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(10, 0, 10, 0);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageURI(uri);
        layoutImagePreviewContainer.addView(imageView);
    }

    private void setTaskAlarm(String taskTitle, String taskMember) {
        try {
            String[] dateParts = selectedDate.split("-");
            if (dateParts.length < 3) return;
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]) - 1;
            int day = Integer.parseInt(dateParts[2]);

            Calendar alarmCalendar = Calendar.getInstance();
            alarmCalendar.set(Calendar.YEAR, year);
            alarmCalendar.set(Calendar.MONTH, month);
            alarmCalendar.set(Calendar.DAY_OF_MONTH, day);
            alarmCalendar.set(Calendar.HOUR_OF_DAY, startHour);
            alarmCalendar.set(Calendar.MINUTE, startMinute);
            alarmCalendar.set(Calendar.SECOND, 0);

            if (alarmCalendar.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("TASK_TITLE", taskTitle);
            intent.putExtra("TASK_MEMBER", taskMember);

            int requestCode = (int) alarmCalendar.getTimeInMillis();
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), pendingIntent);
                } else {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), pendingIntent);
                    } catch (SecurityException se) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), pendingIntent);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}