package com.dailytask;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GroupSetupActivity extends AppCompatActivity {

    private Button btnCreateGroup, btnJoinGroup;
    private EditText etGroupCode;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUid;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_setup);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            currentUid = mAuth.getCurrentUser().getUid();
        }

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) userName = getString(R.string.unknown_member);

        btnCreateGroup = findViewById(R.id.btnCreateGroup);
        btnJoinGroup = findViewById(R.id.btnJoinGroup);
        etGroupCode = findViewById(R.id.etGroupCode);

        btnCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String generatedGroupCode = generateRandomGroupCode();
                Map<String, Object> groupData = new HashMap<>();
                groupData.put("owner_uid", currentUid);
                groupData.put("group_code", generatedGroupCode);

                db.collection("groups").document(generatedGroupCode)
                        .set(groupData)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                saveUserInfoToCloud(generatedGroupCode, "Admin");
                            }
                        });
            }
        });

        btnJoinGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String inputCode = etGroupCode.getText().toString().trim().toUpperCase();
                if (inputCode.length() < 6) {
                    Toast.makeText(GroupSetupActivity.this, getString(R.string.toast_code_length_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                db.collection("groups").document(inputCode).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                saveUserInfoToCloud(inputCode, "User");
                            } else {
                                Toast.makeText(GroupSetupActivity.this, getString(R.string.toast_code_not_found), Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });
    }

    private void saveUserInfoToCloud(String groupCode, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", currentUid);
        userData.put("name", userName);
        userData.put("group_id", groupCode);
        userData.put("role", role);

        db.collection("users").document(currentUid)
                .set(userData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        String msg = getString(R.string.toast_group_setup_success, role, groupCode);
                        Toast.makeText(GroupSetupActivity.this, msg, Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(GroupSetupActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
    }

    private String generateRandomGroupCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rd = new Random();
        while (sb.length() < 6) {
            int index = (int) (rd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}