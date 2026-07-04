package com.dailytask;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.LocalCacheSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnAddTask;
    private ImageButton btnMainSettings;
    private RecyclerView rvTasks;
    private CalendarView mainCalendarView;
    private TaskAdapter taskAdapter;
    private String currentDateSelected;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;
    private String currentUserGroupId = "";
    private ListenerRegistration taskListenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        LocalCacheSettings cacheSettings = PersistentCacheSettings.newBuilder().build();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build();
        db.setFirestoreSettings(settings);

        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        currentDateSelected = sdf.format(new Date());
        mainCalendarView = findViewById(R.id.mainCalendarView);
        btnAddTask = findViewById(R.id.btnAddTask);
        btnMainSettings = findViewById(R.id.btnMainSettings);
        rvTasks = findViewById(R.id.rvTasks);
        rvTasks.setLayoutManager(new LinearLayoutManager(this));

        mainCalendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            currentDateSelected = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, (month + 1), dayOfMonth);
            startRealtimeTaskListening();
        });

        btnAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddTaskActivity.class);
            intent.putExtra("SELECTED_DATE", currentDateSelected);
            startActivity(intent);
        });

        btnMainSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        fetchUserGroupAndStartListening();
    }

    private void fetchUserGroupAndStartListening() {
        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserGroupId = documentSnapshot.getString("group_id");
                        startRealtimeTaskListening();
                    } else {
                        Intent intent = new Intent(MainActivity.this, GroupSetupActivity.class);
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_offline_cache_loading), Toast.LENGTH_SHORT).show();
                    startRealtimeTaskListening();
                });
    }

    private void startRealtimeTaskListening() {
        if (taskListenerRegistration != null) {
            taskListenerRegistration.remove();
        }

        if (currentUserGroupId == null || currentUserGroupId.isEmpty()) return;

        DatabaseHelper localDb = new DatabaseHelper(this);
        taskListenerRegistration = db.collection("tasks")
                .whereEqualTo("group_id", currentUserGroupId)
                .whereEqualTo("task_date", currentDateSelected)
                .addSnapshotListener((value, error) -> {

                    if (error != null) {
                        List<Task> localTaskList = localDb.getTasksByDate(currentDateSelected);
                        if (localTaskList != null && !localTaskList.isEmpty()) {
                            taskAdapter = new TaskAdapter(localTaskList);
                            rvTasks.setAdapter(taskAdapter);
                            Toast.makeText(MainActivity.this, getString(R.string.toast_offline_loaded_success), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_offline_no_cache), Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    List<Task> cloudTaskList = new ArrayList<>();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            String taskIdStr = doc.getId();
                            String title = doc.getString("title");
                            String member = doc.getString("member");
                            String taskDate = doc.getString("task_date");
                            String taskTime = doc.getString("task_time");
                            String taskNotes = doc.getString("task_notes");
                            String taskStatus = doc.getString("task_status");
                            String taskImage = doc.getString("task_image");
                            String createdBy = doc.getString("created_by");

                            Task task = new Task(taskIdStr, title, member, taskDate, taskTime, taskNotes, taskStatus, taskImage);
                            task.setCloudDocId(doc.getId());
                            task.setCreatedBy(createdBy);
                            cloudTaskList.add(task);
                        }
                    }

                    taskAdapter = new TaskAdapter(cloudTaskList);
                    rvTasks.setAdapter(taskAdapter);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (taskListenerRegistration != null) {
            taskListenerRegistration.remove();
        }
    }
}