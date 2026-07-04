package com.dailytask;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
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
import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private Button btnEditPickImage, btnEditCaptureImage, btnEditReschedule, btnEditSaveTask; // 🎯 新增 btnEditCaptureImage
    private LinearLayout layoutEditImageContainer;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;

    private String cloudDocId;
    private String createdByUid;
    private String taskId;
    private String taskTitle, taskMember, taskDate, taskTime;
    private ArrayList<String> editImageUris = new ArrayList<>();
    private Uri editCameraImageUri; // 🎯 暫存相機拍攝路徑

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
            getSupportActionBar().setTitle("雲端管理：任務詳情");
        }

        taskId = getIntent().getStringExtra("TASK_ID");
        taskTitle = getIntent().getStringExtra("TASK_TITLE");
        if (taskTitle == null) taskTitle = "無標題任務";

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

        tvPermissionWarning = new TextView(this);
        tvPermissionWarning.setTextSize(14);
        tvPermissionWarning.setPadding(0, 10, 0, 10);
        if (etEditTaskNotes.getParent() instanceof LinearLayout) {
            ((LinearLayout) findViewById(R.id.etEditTaskNotes).getParent()).addView(tvPermissionWarning, 2);
        }

        tvEditTitleHeader.setText("任務: " + taskTitle + " (" + taskMember + ")");
        etEditTaskNotes.setText(taskNotes);
        cbEditStatus.setChecked("已完成".equals(taskStatus));

        if (taskImagesStr != null && !taskImagesStr.isEmpty()) {
            editImageUris.addAll(Arrays.asList(taskImagesStr.split(",")));
        }
        rebuildImagePreviews();

        checkUserAccessControl();

        btnEditPickImage.setOnClickListener(v -> {
            if (editImageUris.size() >= 10) {
                Toast.makeText(EditTaskActivity.this, "已達上限 10 張相片！", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(EditTaskActivity.this, "已達上限 10 張相片！", Toast.LENGTH_SHORT).show();
                    return;
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "DailyTask_EditCapture_" + System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                editCameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, editCameraImageUri);
                startActivityForResult(cameraIntent, 450); // 450 代表編輯頁相機
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
                            updates.put("task_status", "已重新安排");
                            updates.put("task_notes", etEditTaskNotes.getText().toString().trim());
                            updates.put("task_image", buildCombinedImagesStr());

                            db.collection("tasks").document(cloudDocId).update(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(EditTaskActivity.this, "雲端排程已更新至 " + newDate, Toast.LENGTH_LONG).show();
                                        finish();
                                    });
                        }
                    }, startHour + 1, startMinute, true);
                    endTimePicker.setTitle("⏰ 請設定【新結束時間】");
                    endTimePicker.show();
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
                startTimePicker.setTitle("⏰ 請設定【新開始時間】");
                startTimePicker.show();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.setTitle("📅 請選擇【新執行日期】");
            datePickerDialog.show();
        });

        btnEditSaveTask.setOnClickListener(v -> {
            String updatedNotes = etEditTaskNotes.getText().toString().trim();
            String updatedStatus = cbEditStatus.isChecked() ? "已完成" : "未完成";
            String finalImagesStr = buildCombinedImagesStr();

            if (cloudDocId != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("task_notes", updatedNotes);
                updates.put("task_status", updatedStatus);
                updates.put("task_image", finalImagesStr);

                db.collection("tasks").document(cloudDocId).update(updates)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(EditTaskActivity.this, "雲端修改同步成功！", Toast.LENGTH_SHORT).show();
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
                            etEditTaskNotes.setEnabled(false);
                            cbEditStatus.setEnabled(false);

                            btnEditSaveTask.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            btnEditReschedule.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            btnEditPickImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));
                            if (btnEditCaptureImage != null) btnEditCaptureImage.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDC3C7")));

                            tvPermissionWarning.setText("🛑 權限提示：你是家庭普通成員(User)，無權修改其他成員指派的任務。");
                            tvPermissionWarning.setTextColor(Color.parseColor("#E74C3C"));
                        } else {
                            tvPermissionWarning.setText("🔓 權限核准：你是 " + myRole + "，擁有本任務的完整編輯權限。");
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
        tvEditImageCount.setText("相片附件 (長按相片可刪除): " + editImageUris.size() + "/10");

        if (editImageUris.isEmpty()) {
            TextView tvNoImg = new TextView(this);
            tvNoImg.setText("(無相片附件)");
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
                        .setTitle("刪除相片")
                        .setMessage("確定要移走這張相片附件嗎？")
                        .setPositiveButton("確定", (dialog, which) -> {
                            editImageUris.remove(index);
                            rebuildImagePreviews();
                        })
                        .setNegativeButton("取消", null).show();
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
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}